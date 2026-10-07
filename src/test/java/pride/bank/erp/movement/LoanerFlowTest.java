package pride.bank.erp.movement;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;
import pride.bank.erp.access.AccessScopeService;
import pride.bank.erp.access.ScopeSubject;
import pride.bank.erp.appnotification.AppNotificationRepository;
import pride.bank.erp.appnotification.NotificationTarget;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistory;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository;
import pride.bank.erp.assetType.RepairDestination;
import pride.bank.erp.config.JwtService;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.inventory.InventoryService;
import pride.bank.erp.repair.Repair;
import pride.bank.erp.repair.RepairService;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * A loaner is returned by hand, by the person who holds it, once they have their own asset back.
 *
 * <h2>What was wrong</h2>
 * Returning a repaired asset created a second movement, at the same moment, taking a loaner back.
 * It took the <b>newest temporary replacement ever issued</b> to the user - not the one covering this
 * repair, and without checking it was still with them - so a second repair could "return" a loaner
 * already back in the pool. It could collect the loaner before the repaired asset had been approved
 * to travel, leaving the user with nothing to work on. And cancelling the return left it live.
 *
 * <p>Every test runs in a rolled-back transaction against a loaner it lends itself.
 */
@SpringBootTest
@Transactional
class LoanerFlowTest {

    @Autowired LoanerService loanerService;
    @Autowired LoanerReminder loanerReminder;
    @Autowired RepairService repairService;
    @Autowired InventoryService inventoryService;
    @Autowired StoreService storeService;
    @Autowired AssetRepository assetRepository;
    @Autowired AssetAssignmentHistoryRepository assignmentRepository;
    @Autowired MovementRepository movementRepository;
    @Autowired UserRepository userRepository;
    @Autowired AppNotificationRepository notificationRepository;
    @Autowired AccessScopeService accessScopeService;
    @Autowired JwtService jwtService;
    @Autowired MovementService movementService;

    @AfterEach
    void clearPrincipal() {
        RequestContextHolder.resetRequestAttributes();
    }

    // ── Who may return it ────────────────────────────────────────────────────

    /** The holder hands their own loaner back, with no repair permission at all. */
    @Test
    void theHolderReturnsTheirOwnLoaner() throws Exception {
        Fixture f = lend();
        signInAs(f.holder);

        Movement movement = loanerService.returnLoaner(f.loaner.getId(), null);

        assertThat(movement.getMovementType()).isEqualTo(MovementType.RETURN_TO_STORE);
        assertThat(movement.getSourceUser().getId()).isEqualTo(f.holder.getId());
        assertThat(movement.getDestStore().getId())
                .as("back to the store it was lent from")
                .isEqualTo(f.pool.getId());
        assertThat(movement.getRestoreStatusCode())
                .as("so it reads Available for Loan once it is back on the shelf - not Available for Issuance, which issuance would hand out")
                .isEqualTo("loanAvailable");
    }

    @Test
    void somebodyElseCannotReturnAnotherPersonsLoanerWithoutRepairAsset() throws Exception {
        Fixture f = lend();
        User bystander = userRepository.findAll().stream()
                .filter(u -> !u.getId().equals(f.holder.getId()) && !holdsRepairAsset(u))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no second account without REPAIR_ASSET"));
        signInAs(bystander);

        assertThatThrownBy(() -> loanerService.returnLoaner(f.loaner.getId(), null))
                .isInstanceOf(AccessDeniedException.class);
    }

    /** A storekeeper may close out somebody else's loan - the holder who left, or forgot. */
    @Test
    void aStorekeeperWithTheHolderInReachMayReturnItForThem() throws Exception {
        Fixture f = lend();
        signInAs(storekeeperReaching(f.holder));

        assertThat(loanerService.returnLoaner(f.loaner.getId(), null).getSourceUser().getId())
                .isEqualTo(f.holder.getId());
    }

    @Test
    void aLoanerAlreadyOnItsWayBackIsNotReturnedTwice() throws Exception {
        Fixture f = lend();
        signInAs(f.holder);
        loanerService.returnLoaner(f.loaner.getId(), null);

        assertThatThrownBy(() -> loanerService.returnLoaner(f.loaner.getId(), null))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("already being returned");
    }

    /** Only an open temporary assignment is a loan; an ordinary assignment is not handed back this way. */
    @Test
    void anAssetNotOnLoanIsRefused() throws Exception {
        Fixture f = lend();
        inventoryService.assignAssetToUser(f.loaner, f.holder, false);
        signInAs(f.holder);

        assertThatThrownBy(() -> loanerService.returnLoaner(f.loaner.getId(), null))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("not out on a temporary loan");
    }

    /** On receipt the loan closes and the loaner is lendable stock again. */
    @Test
    void receivingTheReturnClosesTheLoan() throws Exception {
        Fixture f = lend();
        signInAs(f.holder);
        Movement movement = loanerService.returnLoaner(f.loaner.getId(), null);

        inventoryService.applyMovementCompletion(movement, null);

        assertThat(assignmentRepository.findOpenLoansForUser(f.holder.getId()))
                .extracting(a -> a.getAsset().getId())
                .doesNotContain(f.loaner.getId());
        Asset back = assetRepository.findById(f.loaner.getId()).orElseThrow();
        assertThat(back.getAssignedTo()).isNull();
        assertThat(back.getCurrentStore().getId()).isEqualTo(f.pool.getId());
        assertThat(back.getAssetStatus().getStatus()).isEqualTo("loanAvailable");
    }

    // ── What the holder sees ─────────────────────────────────────────────────

    /** Which repair a loaner covers is what tells somebody holding two of them which is which. */
    @Test
    void theHolderSeesWhichRepairTheLoanerCovers() throws Exception {
        Fixture f = lend();
        signInAs(f.holder);

        LoanerDTO mine = loanerService.listMine().stream()
                .filter(l -> l.getAssetId().equals(f.loaner.getId()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("the holder's own loaner is not listed"));

        assertThat(mine.getCoversAssetId()).isEqualTo(f.covered.getId());
        assertThat(mine.getCoversRepairStatus()).isEqualTo("Pending");
        assertThat(mine.getReturnStoreName()).isEqualTo(f.pool.getName());
        assertThat(mine.getPendingReturnMovementId()).isNull();
    }

    // ── Making it permanent ──────────────────────────────────────────────────

    @Test
    void theHolderCannotMakeTheirOwnLoanPermanent() throws Exception {
        Fixture f = lend();
        signInAs(f.holder);

        assertThatThrownBy(() -> loanerService.makePermanent(f.loaner.getId()))
                .as("keeping a loaner for good is the storekeeper's decision, not the borrower's")
                .isInstanceOf(AccessDeniedException.class);
    }

    @Test
    void aStorekeeperCanMakeALoanPermanent() throws Exception {
        Fixture f = lend();
        signInAs(storekeeperReaching(f.holder));

        loanerService.makePermanent(f.loaner.getId());

        AssetAssignmentHistory active = assignmentRepository.findActiveByAssetId(f.loaner.getId());
        assertThat(active.getUser().getId()).isEqualTo(f.holder.getId());
        assertThat(active.isTemporary()).as("an ordinary assignment now").isFalse();
        assertThat(assetRepository.findById(f.loaner.getId()).orElseThrow().isTemporaryPool())
                .as("no longer pool stock somebody might try to lend")
                .isFalse();
    }

    // ── The reminder ─────────────────────────────────────────────────────────

    /** When the covered asset reaches its holder, the loaner standing in for it is named. */
    @Test
    void theHolderIsRemindedWhenTheirOwnAssetComesBack() throws Exception {
        Fixture f = lend();

        loanerReminder.onCompleted(returnOf(f.covered, f.holder));

        assertThat(remindersFor(f)).hasSize(1);
    }

    /** A loaner tied to a different repair is not due yet: that asset is still away. */
    @Test
    void aLoanerCoveringAnotherRepairIsNotDueYet() throws Exception {
        Fixture f = lend();
        Asset unrelated = anotherAsset(f.loaner, f.covered);

        loanerReminder.onCompleted(returnOf(unrelated, f.holder));

        assertThat(remindersFor(f)).isEmpty();
    }

    // ── What is gone ─────────────────────────────────────────────────────────

    /**
     * Returning a repaired asset no longer touches a loaner.
     *
     * <p>Read from the source, comments stripped, because the explanation of what was removed names
     * it - and deleting the explanation to satisfy a test is the wrong trade.
     */
    @Test
    void returnAfterRepairNoLongerMovesALoaner() throws Exception {
        String code = codeOnly(Files.readString(
                Path.of("src/main/java/pride/bank/erp/movement/RepairMovementService.java")));

        assertThat(code)
                .doesNotContain("resolveLoanerToReturn")
                .doesNotContain("findTempReplacementsForUser");

        assertThat(Arrays.stream(MovementRepository.class.getMethods()).map(java.lang.reflect.Method::getName))
                .as("it answered 'who holds this loaner' from movements, and so kept finding loaners "
                        + "that had already come back")
                .doesNotContain("findTempReplacementsForUser");
    }

    // ── fixture ──────────────────────────────────────────────────────────────

    private record Fixture(User holder, Asset loaner, Asset covered, Store pool) {
    }

    /**
     * Lends an asset to somebody without REPAIR_ASSET, tied to an open repair on another asset, from
     * an Admin store at the holder's own branch - so returning it never crosses a location and needs
     * no approver.
     */
    private Fixture lend() throws Exception {
        User holder = userRepository.findAll().stream()
                .filter(u -> u.isEnabled() && u.getBranch() != null && !holdsRepairAsset(u))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no enabled account with a branch and without REPAIR_ASSET"));
        Store pool = storeService.resolveAdminStore(holder.getBranch());

        List<Asset> assets = assetRepository.findAll().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()) && !Boolean.TRUE.equals(a.getDisposed()))
                .limit(2)
                .toList();
        assertThat(assets).as("two assets to lend and to cover").hasSize(2);
        Asset loaner = assets.get(0);
        Asset covered = assets.get(1);

        Repair repair = repairService.openForTransfer(covered, "Fault under test", RepairDestination.IT,
                null, null, List.of());

        inventoryService.returnAssetToStore(loaner, pool);
        loaner.setTemporaryPool(true);
        assetRepository.save(loaner);

        // Issued through the real path, tied to the repair - exactly what the form now does. Within
        // one location, so it needs no approver. Initiated by the holder only because a movement
        // needs an initiator; who issues a loaner is not what these tests are about.
        signInAs(holder);
        movementService.createMovement(MovementDTO.builder()
                .movementType(MovementType.TEMP_REPLACEMENT)
                .sourceStoreId(pool.getId())
                .recipientUserId(holder.getId())
                .repairId(repair.getId())
                .items(List.of(MovementItemDTO.builder().assetId(loaner.getId()).quantity(1).build()))
                .build());
        RequestContextHolder.resetRequestAttributes();

        // What receiving that movement does to the loaner.
        inventoryService.assignAssetToUser(loaner, holder, true);
        return new Fixture(holder, loaner, covered, pool);
    }

    private Movement returnOf(Asset asset, User holder) {
        Movement movement = Movement.builder()
                .movementType(MovementType.RETURN_AFTER_REPAIR)
                .recipientUser(holder)
                .build();
        movement.setItems(List.of(MovementItem.builder().movement(movement).asset(asset).quantity(1).build()));
        return movement;
    }

    /** Ids only: printing the notifications themselves recurses through User and Token toString. */
    private List<Long> remindersFor(Fixture f) {
        return notificationRepository.findAll().stream()
                .filter(n -> n.getEntityType() == NotificationTarget.LOANER
                        && f.loaner.getId().equals(n.getEntityId())
                        && n.getRecipient() != null && f.holder.getId().equals(n.getRecipient().getId()))
                .map(n -> n.getId())
                .toList();
    }

    private Asset anotherAsset(Asset... not) {
        List<Long> excluded = Arrays.stream(not).map(Asset::getId).toList();
        return assetRepository.findAll().stream()
                .filter(a -> !excluded.contains(a.getId()) && !Boolean.TRUE.equals(a.getDeleted()))
                .findFirst()
                .orElseThrow();
    }

    /** Somebody holding REPAIR_ASSET whose asset scope reaches the holder. */
    private User storekeeperReaching(User holder) {
        for (User candidate : userRepository.findAll()) {
            if (!candidate.isEnabled() || !holdsRepairAsset(candidate) || candidate.getId().equals(holder.getId())) continue;
            signInAs(candidate);
            if (accessScopeService.canManage(ScopeSubject.ASSETS, holder.getBranch().getId(), holder.getId())) {
                return candidate;
            }
        }
        throw new AssertionError("nobody holding REPAIR_ASSET can reach " + holder.getEmail());
    }

    private static boolean holdsRepairAsset(User user) {
        return user.getAuthorities().stream().anyMatch(a -> Constants.REPAIR_ASSET.equals(a.getAuthority()));
    }

    /** CurrentUserService decodes the JWT on the bound request - a SecurityContext does nothing. */
    private void signInAs(User user) {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("Authorization", "Bearer " + jwtService.generateToken(user));
        RequestContextHolder.setRequestAttributes(new ServletRequestAttributes(request));
    }

    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("(?m)//.*$", "");
    }
}
