package pride.bank.erp.asset;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.access.AccessScopeService;
import pride.bank.erp.access.ScopeAction;
import pride.bank.erp.access.ScopeSubject;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.dashboard.DashboardScope;
import pride.bank.erp.movement.DisposalDTO;
import pride.bank.erp.movement.Movement;
import pride.bank.erp.movement.MovementService;
import pride.bank.erp.movement.RepairMovementService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The Disposal report: a write-off belongs to the branch it was disposed from, permanently. Head
 * Office sees every write-off and where each came from; a branch sees its own.
 *
 * <p>By location this could not work: every disposed asset ends up in the Head Office Disposal store
 * and reads Head Office. And the disposal transfer's source store cannot say on its own either - an
 * asset written off from the repair bench leaves from the Head Office IT store - so the disposal now
 * records its owner, as the repair transfer does.
 *
 * <p>Only one real disposal exists (Head Office's own), so the branch case is made here through the
 * real disposal flow, on a branch asset sitting at the IT store for repair, inside the rolled-back
 * transaction.
 */
@SpringBootTest
@Transactional
class DisposalReportScopeTest {

    @Autowired AssetService assetService;
    @Autowired AssetRepository assetRepository;
    @Autowired RepairMovementService repairMovementService;
    @Autowired MovementService movementService;
    @Autowired AccessScopeService accessScopeService;
    @Autowired BranchRepository branchRepository;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private List<AssetDAO> disposalReport(Long branchId) {
        return assetService.fetchAllAssets(5000, "DESC", 0, "id", null, null, null, null, null, null,
                        true, null, null, null, null, null, null, false, false, branchId, null, null, false, true)
                .getContent();
    }

    /** A branch asset on the Head Office bench, written off from there through the real flow. */
    private Optional<Asset> disposeABranchAssetFromTheBench() throws Exception {
        Optional<User> admin = findViewer(DashboardScope.ALL);
        if (admin.isEmpty()) return Optional.empty();
        signInAs(admin.get());

        Asset benched = assetRepository.findAll().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()) && !Boolean.TRUE.equals(a.getDisposed()))
                .filter(AssetService::isAwayFromItsBranch)
                .filter(a -> a.getCurrentStore() != null && a.getBranch() != null && a.getBranch().isHeadOffice())
                .filter(a -> {
                    Long sender = movementService.branchLastSentAwayFrom(a.getId());
                    return sender != null && branchRepository.findById(sender).map(b -> !b.isHeadOffice()).orElse(false);
                })
                .findFirst().orElse(null);
        if (benched == null) {
            System.out.println("disposal report test: NO branch asset on the bench - unexercised");
            return Optional.empty();
        }

        DisposalDTO dto = new DisposalDTO();
        dto.setAssetId(benched.getId());
        dto.setEarlyDisposalReason("Unrepairable - test");
        dto.setProceedWithoutApproval(true);
        dto.setBypassReason("test");
        Movement disposal = repairMovementService.disposeAsset(dto);

        Long sender = movementService.branchLastSentAwayFrom(benched.getId());
        assertThat(disposal.getSourceStore().getLocation().isHeadOffice())
                .as("written off from the bench, so its source store is at Head Office").isTrue();
        assertThat(disposal.getOriginLocation()).as("the disposal records whose write-off it is").isNotNull();
        assertThat(disposal.getOriginLocation().getId()).isEqualTo(sender);

        // Completion is a later step of the flow; the report reads the flag.
        benched.setDisposed(true);
        assetRepository.saveAndFlush(benched);
        signOut();
        return Optional.of(benched);
    }

    @Test
    void headOfficeSeesEveryWriteOffWithWhereItCameFrom() throws Exception {
        Optional<Asset> disposed = disposeABranchAssetFromTheBench();
        if (disposed.isEmpty()) return;
        Long owner = movementService.branchLastSentAwayFrom(disposed.get().getId());

        signInAs(findViewer(DashboardScope.ALL).orElseThrow());
        List<AssetDAO> all = disposalReport(null);
        AssetDAO row = all.stream().filter(d -> d.getId().equals(disposed.get().getId())).findFirst().orElseThrow();
        assertThat(row.getDisposedFromBranchId()).as("Disposed From names the branch, not Head Office").isEqualTo(owner);
        assertThat(all).allSatisfy(d -> assertThat(d.getDisposedFromBranchName()).isNotBlank());

        // The Branch filter means "disposed from", the same rule as the column.
        assertThat(disposalReport(owner)).allSatisfy(d -> assertThat(d.getDisposedFromBranchId()).isEqualTo(owner))
                .extracting(AssetDAO::getId).contains(disposed.get().getId());
    }

    @Test
    void aBranchSeesItsOwnWriteOffsAndHeadOfficeStaffDoNotSeeTheirs() throws Exception {
        Optional<Asset> disposed = disposeABranchAssetFromTheBench();
        if (disposed.isEmpty()) return;
        Long owner = movementService.branchLastSentAwayFrom(disposed.get().getId());

        Optional<User> branchViewer = findViewer(DashboardScope.BRANCH, owner);
        if (branchViewer.isPresent()) {
            signInAs(branchViewer.get());
            List<AssetDAO> mine = disposalReport(null);
            assertThat(mine).extracting(AssetDAO::getId).contains(disposed.get().getId());
            assertThat(mine).allSatisfy(d -> assertThat(d.getDisposedFromBranchId()).isEqualTo(owner));
            signOut();
        }

        // A Head Office viewer scoped to Head Office: the asset stands there, and is still not theirs.
        Long headOffice = branchRepository.findFirstByIsHeadOfficeTrue().map(Branch::getId).orElseThrow();
        Optional<User> hoViewer = findViewer(DashboardScope.BRANCH, headOffice);
        if (hoViewer.isPresent()) {
            signInAs(hoViewer.get());
            assertThat(disposalReport(null)).extracting(AssetDAO::getId).doesNotContain(disposed.get().getId());
        }
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private Optional<User> findViewer(DashboardScope wanted) {
        return findViewer(wanted, null);
    }

    private Optional<User> findViewer(DashboardScope wanted, Long branchId) {
        for (User u : userRepository.findAll()) {
            if (u.getBranch() == null) continue;
            if (branchId != null && !branchId.equals(u.getBranch().getId())) continue;
            if (u.getAuthorities().stream().noneMatch(a -> "READ_ASSET".equals(a.getAuthority()))) continue;
            signInAs(u);
            DashboardScope scope = accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.VIEW);
            boolean manages = wanted != DashboardScope.ALL
                    || accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.MANAGE) == DashboardScope.ALL;
            signOut();
            if (scope == wanted && manages) return Optional.of(u);
        }
        System.out.printf("disposal report test: NO %s viewer%s%n", wanted, branchId == null ? "" : " at branch " + branchId);
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
