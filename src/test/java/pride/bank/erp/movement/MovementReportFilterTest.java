package pride.bank.erp.movement;

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

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * The movement report's server-side filters: Branch (for viewers who see every branch) and asset
 * category. Both used to run in the browser - the category one could never match, because the
 * response carried no category - and Branch compared names against a rule the server does not use.
 */
@SpringBootTest
@Transactional
class MovementReportFilterTest {

    @Autowired MovementService movementService;
    @Autowired MovementRepository movementRepository;
    @Autowired MovementMapper movementMapper;
    @Autowired AccessScopeService accessScopeService;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    private static final PageRequest ALL_ROWS = PageRequest.of(0, 5000);

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    /** The four ends, read off the entity - independent of the query under test. */
    private static Set<Long> branchesOf(Movement m) {
        Set<Long> ends = new HashSet<>();
        if (m.getSourceStore() != null && m.getSourceStore().getLocation() != null) ends.add(m.getSourceStore().getLocation().getId());
        if (m.getDestStore() != null && m.getDestStore().getLocation() != null) ends.add(m.getDestStore().getLocation().getId());
        if (m.getSourceUser() != null && m.getSourceUser().getBranch() != null) ends.add(m.getSourceUser().getBranch().getId());
        if (m.getRecipientUser() != null && m.getRecipientUser().getBranch() != null) ends.add(m.getRecipientUser().getBranch().getId());
        return ends;
    }

    private static Set<Long> categoriesOf(Movement m) {
        return m.getItems().stream()
                .flatMap(i -> Stream.of(
                        i.getAsset() != null && i.getAsset().getAssetType() != null ? i.getAsset().getAssetType().getId() : null,
                        i.getCommodity() != null && i.getCommodity().getAssetType() != null ? i.getCommodity().getAssetType().getId() : null))
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());
    }

    private Set<Long> list(Long branchId, Long assetTypeId) {
        MovementSearchCriteria c = new MovementSearchCriteria();
        c.setBranchId(branchId);
        c.setAssetTypeId(assetTypeId);
        return movementService.listMovements(c, ALL_ROWS).getContent().stream()
                .map(Movement::getId).collect(Collectors.toSet());
    }

    @Test
    void theBranchFilterReturnsExactlyTheMovementsTouchingThatBranch() {
        Optional<User> viewer = findViewer(true, null);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());

        List<Movement> all = movementRepository.findAll();
        Set<Long> branches = all.stream().flatMap(m -> branchesOf(m).stream()).collect(Collectors.toSet());
        assertThat(branches).as("movements with no branch end - nothing to filter").isNotEmpty();
        for (Long branch : branches) {
            Set<Long> expected = all.stream().filter(m -> branchesOf(m).contains(branch))
                    .map(Movement::getId).collect(Collectors.toSet());
            assertThat(list(branch, null)).as("branch %d", branch).isEqualTo(expected);
        }
    }

    @Test
    void aBranchViewerMayNameOnlyTheirOwnBranch() {
        Optional<User> viewer = findViewer(false, null);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());
        Long own = accessScopeService.currentUserBranchId();

        assertThat(list(own, null)).as("their own branch narrows nothing they could not already see")
                .isEqualTo(list(null, null));
        assertThatThrownBy(() -> list(own + 100000, null)).isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void theCategoryFilterMatchesAnyLineInThatCategory() {
        Optional<User> viewer = findViewer(true, null);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());

        List<Movement> all = movementRepository.findAll();
        Set<Long> categories = all.stream().flatMap(m -> categoriesOf(m).stream()).collect(Collectors.toSet());
        assertThat(categories).isNotEmpty();
        for (Long category : categories) {
            Set<Long> expected = all.stream().filter(m -> categoriesOf(m).contains(category))
                    .map(Movement::getId).collect(Collectors.toSet());
            Set<Long> got = list(null, category);
            assertThat(got).as("category %d", category).isEqualTo(expected);
            // Counted once each: an EXISTS, not a join that repeats a movement per matching line.
            MovementSearchCriteria c = new MovementSearchCriteria();
            c.setAssetTypeId(category);
            assertThat(movementService.listMovements(c, ALL_ROWS).getTotalElements()).isEqualTo(expected.size());
        }
    }

    @Test
    void theReportCanNameEachLinesCategoryAndTheRequester() {
        Optional<User> viewer = findViewer(true, null);
        if (viewer.isEmpty()) return;
        signInAs(viewer.get());

        for (Movement m : movementRepository.findAll()) {
            MovementResponseDTO dto = movementMapper.toDto(m);
            for (MovementResponseDTO.ItemSummary item : dto.getItems()) {
                if (item.getAsset() != null) {
                    MovementItem line = m.getItems().stream().filter(i -> i.getId().equals(item.getId())).findFirst().orElseThrow();
                    String expected = line.getAsset().getAssetType() == null ? null : line.getAsset().getAssetType().getName();
                    assertThat(item.getAsset().getAssetTypeName()).isEqualTo(expected);
                }
            }
            if (m.getRequest() != null && m.getRequest().getRequester() != null) {
                assertThat(dto.getRequest().getRequesterName()).isNotBlank();
            }
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Optional<User> findViewer(boolean allBranches, Long branchId) {
        for (User u : userRepository.findAll()) {
            if (u.getBranch() == null) continue;
            if (branchId != null && !branchId.equals(u.getBranch().getId())) continue;
            if (u.getAuthorities().stream().noneMatch(a -> "READ_MOVEMENT".equals(a.getAuthority()))) continue;
            signInAs(u);
            boolean all = accessScopeService.scopeFor(ScopeSubject.MOVEMENTS, ScopeAction.VIEW) == DashboardScope.ALL;
            signOut();
            if (all == allBranches) return Optional.of(u);
        }
        System.out.printf("movement report test: NO %s viewer found%n", allBranches ? "all-branches" : "branch");
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
