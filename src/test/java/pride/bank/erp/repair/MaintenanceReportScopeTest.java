package pride.bank.erp.repair;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.access.AccessScopeService;
import pride.bank.erp.access.ScopeAction;
import pride.bank.erp.access.ScopeSubject;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.dashboard.DashboardScope;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The maintenance report: a repair belongs to the branch that sent the asset, Head Office sees them
 * all, and a branch sees its own.
 *
 * <p>It used to scope on the asset's current branch or its holder's. On the bench neither is the
 * sender's - receipt rewrites the branch to Head Office and the transfer closes the assignment - so,
 * measured when fixed, a Gulu viewer saw none of the nine open repairs, every one of them Gulu's.
 */
@SpringBootTest
@Transactional
class MaintenanceReportScopeTest {

    @Autowired RepairService repairService;
    @Autowired RepairRepository repairRepository;
    @Autowired AccessScopeService accessScopeService;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    private static final PageRequest ALL_ROWS = PageRequest.of(0, 5000);

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    /** Every repair, by the branch it belongs to - computed independently of the report query. */
    private Map<Long, Long> ownerByRepair() {
        List<Repair> repairs = repairRepository.findAll();
        Map<Long, Long> owners = new HashMap<>();
        for (Object[] row : repairRepository.findOwningBranches(repairs.stream().map(Repair::getId).toList())) {
            owners.putIfAbsent((Long) row[0], (Long) row[1]);
        }
        for (Repair r : repairs) {
            if (!owners.containsKey(r.getId()) && r.getAsset() != null && r.getAsset().getBranch() != null) {
                owners.put(r.getId(), r.getAsset().getBranch().getId());
            }
        }
        return owners;
    }

    private List<RepairDTO> report(Long branchId, String repairedBy) {
        return repairService.listAllRepairs(null, null, null, null, branchId, repairedBy, ALL_ROWS).getContent();
    }

    private Set<Long> ids(List<RepairDTO> rows) {
        return rows.stream().map(RepairDTO::getId).collect(Collectors.toSet());
    }

    @Test
    void headOfficeSeesEveryRepairAndTheBranchFilterNarrowsToTheSender() {
        Optional<User> viewer = findViewer(true);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());
        Map<Long, Long> owners = ownerByRepair();

        List<RepairDTO> all = report(null, null);
        assertThat(ids(all)).isEqualTo(repairRepository.findAll().stream().map(Repair::getId).collect(Collectors.toSet()));
        assertThat(all).allSatisfy(dto -> assertThat(dto.getSentFromBranchId())
                .as("repair %d's Branch column", dto.getId()).isEqualTo(owners.get(dto.getId())));

        for (Long branch : Set.copyOf(owners.values())) {
            Set<Long> expected = owners.entrySet().stream().filter(e -> branch.equals(e.getValue()))
                    .map(Map.Entry::getKey).collect(Collectors.toSet());
            assertThat(ids(report(branch, null))).as("branch %d", branch).isEqualTo(expected);
        }
    }

    /** The case that was broken: a branch sees its own repairs while the asset is on the Head Office bench. */
    @Test
    void aBranchSeesExactlyTheRepairsItSent() {
        Map<Long, Long> owners = ownerByRepair();
        int exercised = 0;
        for (Long branch : Set.copyOf(owners.values())) {
            Optional<User> viewer = findBranchViewer(branch);
            if (viewer.isEmpty()) continue;
            signInAs(viewer.get());
            Set<Long> expected = owners.entrySet().stream().filter(e -> branch.equals(e.getValue()))
                    .map(Map.Entry::getKey).collect(Collectors.toSet());
            Set<Long> shown = ids(report(null, null));
            System.out.printf("maintenance report test: branch %d sees %d repair(s)%n", branch, shown.size());
            assertThat(shown).as("branch %d", branch).isEqualTo(expected);
            signOut();
            exercised++;
        }
        assertThat(exercised).as("no branch viewer owned a repair - the scoped path went unexercised").isPositive();
    }

    @Test
    void aBranchCannotAskForAnotherBranchsRepairs() {
        Map<Long, Long> owners = ownerByRepair();
        for (Long branch : Set.copyOf(owners.values())) {
            Optional<User> viewer = findBranchViewer(branch);
            if (viewer.isEmpty()) continue;
            signInAs(viewer.get());
            Long other = owners.values().stream().filter(b -> !b.equals(branch)).findFirst().orElse(branch + 100000);
            assertThatThrownBy(() -> report(other, null)).isInstanceOf(AccessDeniedException.class);
            return;
        }
    }

    /** Internal, external and not-yet-recorded partition the whole set, and each matches what it says. */
    @Test
    void repairedByPartitionsTheRepairs() {
        Optional<User> viewer = findViewer(true);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());

        List<RepairDTO> internal = report(null, "INTERNAL");
        List<RepairDTO> external = report(null, "EXTERNAL");
        List<RepairDTO> none = report(null, "NONE");
        assertThat(internal).allSatisfy(r -> assertThat(r.getTechnicianType()).isEqualTo(TechnicianType.INTERNAL));
        assertThat(external).allSatisfy(r -> assertThat(r.getTechnicianType()).isEqualTo(TechnicianType.EXTERNAL));
        assertThat(none).allSatisfy(r -> assertThat(r.getTechnicianType()).isNull());
        assertThat(internal.size() + external.size() + none.size()).isEqualTo(report(null, null).size());
        assertThatThrownBy(() -> report(null, "VENDOR")).isInstanceOf(IllegalArgumentException.class);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private boolean seesAll() {
        return accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.VIEW) == DashboardScope.ALL;
    }

    private Optional<User> findViewer(boolean allBranches) {
        for (User u : userRepository.findAll()) {
            if (u.getBranch() == null) continue;
            signInAs(u);
            boolean all = seesAll();
            signOut();
            if (all == allBranches) return Optional.of(u);
        }
        return Optional.empty();
    }

    /** Someone at this branch who does not see every branch. */
    private Optional<User> findBranchViewer(Long branchId) {
        for (User u : userRepository.findAll()) {
            if (u.getBranch() == null || !branchId.equals(u.getBranch().getId())) continue;
            signInAs(u);
            boolean all = seesAll();
            signOut();
            if (!all) return Optional.of(u);
        }
        return Optional.empty();
    }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
