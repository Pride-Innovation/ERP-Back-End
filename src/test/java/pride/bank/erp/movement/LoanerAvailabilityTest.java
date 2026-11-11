package pride.bank.erp.movement;

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
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.dashboard.DashboardScope;
import pride.bank.erp.inventory.InventoryReportController;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A loaner named on one temporary replacement is not offered for the next, and cannot be lent twice.
 *
 * <p>Creating a temporary replacement only names the loaner on a movement - the asset itself is not
 * touched until dispatch and receipt - so the loaner list (both repair modals read it) offered the
 * same chair again, and the server accepted a second loan of it. Nothing live was in that state when
 * fixed (all 14 temporary replacements completed), so the scenario is made here, inside the
 * rolled-back transaction, through the real service.
 */
@SpringBootTest
@Transactional
class LoanerAvailabilityTest {

    @Autowired RepairMovementService repairMovementService;
    @Autowired MovementService movementService;
    @Autowired InventoryReportController inventoryReportController;
    @Autowired AssetRepository assetRepository;
    @Autowired AccessScopeService accessScopeService;
    @Autowired UserRepository userRepository;
    @Autowired JwtService jwtService;

    @AfterEach
    void signOut() {
        RequestContextHolder.resetRequestAttributes();
        SecurityContextHolder.clearContext();
    }

    private Set<Long> offered() {
        return inventoryReportController.temporaryPoolAssets().getBody().stream()
                .map(Asset::getId).collect(Collectors.toSet());
    }

    private Movement lend(Long loanerId, Long recipientId) throws Exception {
        return repairMovementService.issueTemporaryReplacement(TempReplacementDTO.builder()
                .tempAssetId(loanerId).recipientUserId(recipientId)
                .proceedWithoutApproval(true).bypassReason("test")
                .build());
    }

    @Test
    void theListOffersOnlyLoanersFitToLend() {
        if (!signInAsLender()) return;
        List<Asset> offered = inventoryReportController.temporaryPoolAssets().getBody();
        System.out.printf("loaner test: %d loaner(s) offered%n", offered.size());
        assertThat(offered).allSatisfy(a -> {
            assertThat(LoanerAvailability.LENDABLE_STATUS_CODES)
                    .as("asset %d reads %s", a.getId(), a.getAssetStatus().getStatus())
                    .contains(a.getAssetStatus().getStatus());
            assertThat(a.getAssignedTo()).isNull();
        });
        // The pool chair under repair was offered before; nothing in maintenance may be.
        assertThat(offered).noneSatisfy(a -> assertThat(a.getAssetStatus().getStatus()).isEqualTo("inMaintenance"));
    }

    @Test
    void aLoanerOnATemporaryReplacementIsNotOfferedOrLentAgainUntilThatIsCancelled() throws Exception {
        if (!signInAsLender()) return;
        Set<Long> before = offered();
        if (before.isEmpty()) {
            System.out.println("loaner test: NO loaner available - unexercised");
            return;
        }
        Long loaner = before.iterator().next();
        User recipient = userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && Boolean.TRUE.equals(u.isEnabled()))
                .findFirst().orElseThrow();

        // The first temporary replacement: created, not dispatched.
        Movement first = lend(loaner, recipient.getId());
        assertThat(first.getStatus()).isNotIn(MovementStatus.COMPLETED, MovementStatus.CANCELLED);
        assertThat(assetRepository.findById(loaner).orElseThrow().getAssignedTo())
                .as("nothing on the loaner changes yet - which is why the list needed this").isNull();

        // The next one is not offered it, and cannot take it.
        assertThat(offered()).doesNotContain(loaner);
        assertThatThrownBy(() -> lend(loaner, recipient.getId()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temporary replacement #" + first.getId());

        // Everything else still offered.
        Set<Long> others = new java.util.HashSet<>(before);
        others.remove(loaner);
        assertThat(offered()).containsAll(others);

        // Cancelling the first frees it.
        movementService.cancelMovement(first.getId(), "test");
        assertThat(offered()).contains(loaner);
    }

    @Test
    void aRepairTransferCannotLendALoanerAlreadyPromised() throws Exception {
        if (!signInAsLender()) return;
        Set<Long> pool = offered();
        if (pool.isEmpty()) return;
        Long loaner = pool.iterator().next();
        User recipient = userRepository.findAll().stream()
                .filter(u -> u.getBranch() != null && Boolean.TRUE.equals(u.isEnabled()))
                .findFirst().orElseThrow();
        Movement first = lend(loaner, recipient.getId());

        // Any asset id: the loaner is refused before the repair itself is looked at further.
        Asset someAsset = assetRepository.findAll().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()) && !a.getId().equals(loaner))
                .filter(a -> a.getAssetType() != null && a.getAssetType().isRepairable())
                .findFirst().orElseThrow();
        RepairTransferDTO dto = new RepairTransferDTO();
        dto.setAssetId(someAsset.getId());
        dto.setTempAssetId(loaner);
        dto.setFaultDescription("test");
        long movementsBefore = movementService.listMovements(new MovementSearchCriteria(),
                org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements();

        assertThatThrownBy(() -> repairMovementService.transferForRepair(dto))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("temporary replacement #" + first.getId());
        assertThat(movementService.listMovements(new MovementSearchCriteria(),
                org.springframework.data.domain.PageRequest.of(0, 1)).getTotalElements())
                .as("refused before the repair movement was made").isEqualTo(movementsBefore);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    /** Someone who may lend any branch's loaners: REPAIR_ASSET, and MANAGE over every branch's assets. */
    private boolean signInAsLender() {
        for (User u : userRepository.findAll()) {
            if (u.getBranch() == null) continue;
            if (u.getAuthorities().stream().noneMatch(a -> "REPAIR_ASSET".equals(a.getAuthority()))) continue;
            signInAs(u);
            boolean all = accessScopeService.scopeFor(ScopeSubject.ASSETS, ScopeAction.MANAGE) == DashboardScope.ALL
                    && accessScopeService.scopeFor(ScopeSubject.MOVEMENTS, ScopeAction.MANAGE) == DashboardScope.ALL;
            if (all) return true;
            signOut();
        }
        System.out.println("loaner test: NO all-branches lender found - unexercised");
        return false;
    }

    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(user, null, user.getAuthorities()));
    }
}
