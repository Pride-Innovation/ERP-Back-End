/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.access.AccessScopeService;
import pride.bank.erp.access.ScopeSubject;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistory;
import pride.bank.erp.assetAssignmentHostory.AssetAssignmentHistoryRepository;
import pride.bank.erp.auditing.trail.AuditEventType;
import pride.bank.erp.auditing.trail.AuditModule;
import pride.bank.erp.auditing.trail.AuditService;
import pride.bank.erp.auditing.trail.AuditSeverity;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.inventory.InventoryService;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.user.User;

import java.util.List;
import java.util.Optional;

/**
 * Temporary replacements, from the moment they are out until somebody decides how they end.
 *
 * <h2>Why a loaner is returned by hand</h2>
 * Returning a repaired asset used to create a second movement, at the same moment, taking a loaner
 * back from the user. Three things were wrong with that, and none could be fixed by refining it:
 *
 * <ul>
 *   <li><b>It guessed which loaner.</b> It took the newest temporary-replacement movement ever
 *       issued to the user - not the one covering this repair, and without checking the loaner was
 *       still with them. A user's second repair could "return" a loaner already back in the pool or
 *       lent to somebody else.</li>
 *   <li><b>It moved too early.</b> The loaner's leg needed no approval while the repaired asset's
 *       did, so the loaner could be collected before the user's own asset had even been approved to
 *       travel - leaving them with nothing to work on.</li>
 *   <li><b>It could not be undone.</b> Cancelling the return left the loaner's leg live.</li>
 * </ul>
 *
 * <p>So nothing moves a loaner automatically any more. It ends one of two ways, each a deliberate
 * act: <b>returned</b> to the store it was lent from ({@link #returnLoaner}), or <b>made
 * permanent</b> when the asset it stood in for is not coming back ({@link #makePermanent}). What
 * keeps loaners from quietly going missing is visibility, not automation: the holder is reminded
 * when their own asset comes back ({@link LoanerReminder}), and storekeepers see every loaner still
 * out ({@link #listOutstanding}).
 *
 * <h2>The truth about who holds a loaner</h2>
 * An open assignment flagged {@code temporary}, never the movement that issued it. A movement says a
 * loaner was <em>sent</em>; only the assignment says it is still there.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class LoanerService {

    private static final String CODE_ISSUANCE_AVAILABLE = "issuanceAvailable";
    private static final String CODE_ASSET_ASSIGNED = "assetAssigned";
    private static final List<MovementStatus> FINISHED =
            List.of(MovementStatus.COMPLETED, MovementStatus.CANCELLED);

    private final AssetAssignmentHistoryRepository assignmentRepository;
    private final MovementRepository movementRepository;
    private final MovementService movementService;
    private final AssetRepository assetRepository;
    private final InventoryService inventoryService;
    private final StatusService statusService;
    private final StoreService storeService;
    private final AccessScopeService accessScopeService;
    private final CurrentUserService currentUserService;
    private final AuditService auditService;

    // ── Reading ──────────────────────────────────────────────────────────────

    /** The caller's own loaners. Needs nothing beyond signing in: they are the caller's to hand back. */
    @Transactional(readOnly = true)
    public List<LoanerDTO> listMine() {
        User me = currentUserService.getCurrentUser();
        if (me == null) throw new AccessDeniedException("The signed-in user could not be identified.");
        return assignmentRepository.findOpenLoansForUser(me.getId()).stream().map(this::toDto).toList();
    }

    /**
     * Every loaner still out that the caller may see, oldest first - so the one out longest, which is
     * the one most likely forgotten, is at the top.
     *
     * <p>Narrowed per row by the holder's branch and the holder, the same pair an asset's own scope
     * uses, so a branch storekeeper sees their branch's loans and Head Office sees all of them.
     */
    @Transactional(readOnly = true)
    public List<LoanerDTO> listOutstanding() {
        return assignmentRepository.findOpenLoans().stream()
                .filter(loan -> accessScopeService.canView(ScopeSubject.ASSETS,
                        branchIdOf(loan.getUser()), loan.getUser().getId()))
                .map(this::toDto)
                .toList();
    }

    // ── Ending a loan ────────────────────────────────────────────────────────

    /**
     * Hands a loaner back to the store it was lent from.
     *
     * <p>The holder may do this themselves - it is their loan. Anybody else needs
     * {@code REPAIR_ASSET} and the holder within their reach: a storekeeper closing out a loan for
     * somebody who has left, or who never got round to it.
     *
     * <p>Recorded as an ordinary {@link MovementType#RETURN_TO_STORE} from the holder, so it follows
     * the same rules as any other movement: a return within one location completes on receipt; one
     * that crosses a location is approved and dispatched like everything else that travels. On
     * receipt the temporary assignment closes and the loaner reads available to lend again.
     */
    @Transactional
    public Movement returnLoaner(Long assetId, LoanerReturnDTO dto) throws NotFoundException {
        AssetAssignmentHistory loan = openLoanOf(assetId);
        User holder = loan.getUser();
        requireMayActOn(holder, false);
        requireNoReturnUnderWay(assetId);

        Asset loaner = loan.getAsset();
        Store destination = returnStoreFor(loaner, holder);
        LoanerReturnDTO request = dto != null ? dto : new LoanerReturnDTO();

        Movement movement = movementService.createMovement(MovementDTO.builder()
                .movementType(MovementType.RETURN_TO_STORE)
                .sourceUserId(holder.getId())
                .destStoreId(destination.getId())
                // Back on the shelf and lendable. Issuing it marked it Assigned; without this it would
                // sit in the store reading as though it were still out with the borrower.
                .restoreStatusCode(CODE_ISSUANCE_AVAILABLE)
                .remarks(isBlank(request.getRemarks())
                        ? "Return of temporary replacement " + label(loaner)
                        : request.getRemarks().trim())
                .proceedWithoutApproval(request.getProceedWithoutApproval())
                .bypassReason(request.getBypassReason())
                .items(List.of(MovementItemDTO.builder().assetId(assetId).quantity(1).build()))
                .build());

        log.info("Loaner {} return started as movement {} from user {} to {}",
                assetId, movement.getId(), holder.getId(), destination.getName());
        return movement;
    }

    /**
     * Turns a loan into an ordinary assignment, for when the asset it stood in for is not coming
     * back - written off, or simply replaced.
     *
     * <p>Without this the person would hold a "temporary" asset indefinitely, the outstanding list
     * would carry it forever, and the asset would still be flagged as pool stock somebody might try
     * to lend. {@code REPAIR_ASSET} only: deciding that a loaner is now somebody's for good is the
     * storekeeper's call, not the borrower's.
     */
    @Transactional
    public void makePermanent(Long assetId) throws NotFoundException {
        AssetAssignmentHistory loan = openLoanOf(assetId);
        User holder = loan.getUser();
        requireMayActOn(holder, true);
        requireNoReturnUnderWay(assetId);

        Asset asset = loan.getAsset();
        // Closes the temporary assignment and opens a permanent one, so the history shows both: lent
        // on one date, kept from another.
        inventoryService.assignAssetToUser(asset, holder, false);
        asset.setTemporaryPool(false);
        asset.setAssetStatus(statusService.findByStatusCode(CODE_ASSET_ASSIGNED));
        Asset saved = assetRepository.save(asset);

        auditService.record(AuditEventType.UPDATED, AuditModule.ASSETS, "LOANER_MADE_PERMANENT",
                "Temporary replacement " + label(saved) + " made a permanent assignment to "
                        + personName(holder),
                AuditSeverity.INFO, "Asset", saved.getId());
    }

    // ── Rules ────────────────────────────────────────────────────────────────

    private AssetAssignmentHistory openLoanOf(Long assetId) throws NotFoundException {
        Asset asset = assetRepository.findById(assetId)
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                .orElseThrow(() -> new NotFoundException("Asset " + assetId + " not found"));
        AssetAssignmentHistory active = assignmentRepository.findActiveByAssetId(asset.getId());
        if (active == null || !active.isTemporary()) {
            throw new IllegalArgumentException(label(asset) + " is not out on a temporary loan.");
        }
        return active;
    }

    /**
     * The holder acting on their own loan, or someone entitled to act for them.
     *
     * @param storekeeperOnly true where even the holder may not act alone - making a loan permanent
     */
    private void requireMayActOn(User holder, boolean storekeeperOnly) {
        User me = currentUserService.getCurrentUser();
        if (me == null) throw new AccessDeniedException("The signed-in user could not be identified.");

        boolean self = holder.getId().equals(me.getId());
        if (self && !storekeeperOnly) return;

        boolean storekeeper = me.getAuthorities().stream()
                .anyMatch(a -> Constants.REPAIR_ASSET.equals(a.getAuthority()));
        if (!storekeeper) {
            throw new AccessDeniedException(storekeeperOnly
                    ? "Only someone who manages repairs can make a loan permanent."
                    : "You can only return a temporary asset that is on loan to you.");
        }
        accessScopeService.requireCanManage(ScopeSubject.ASSETS, branchIdOf(holder), holder.getId());
    }

    /** One return at a time - a second would be a movement for an asset already on its way back. */
    private void requireNoReturnUnderWay(Long assetId) {
        pendingReturnOf(assetId).ifPresent(m -> {
            throw new IllegalStateException("This temporary asset is already being returned (movement #"
                    + m.getId() + ").");
        });
    }

    private Optional<Movement> pendingReturnOf(Long assetId) {
        return movementRepository.findFirstByItems_Asset_IdAndMovementTypeAndStatusNotInOrderByIdDesc(
                assetId, MovementType.RETURN_TO_STORE, FINISHED);
    }

    /**
     * Where a returned loaner goes: the store it was lent from, which is where its pool lives. A loan
     * with no issuing movement on record - set up by hand before the flow existed - goes to the
     * holder's own branch Admin store, from where it can be moved on like any other stock.
     */
    private Store returnStoreFor(Asset loaner, User holder) throws NotFoundException {
        Optional<Movement> issue = issueOf(loaner, holder);
        if (issue.isPresent() && issue.get().getSourceStore() != null) {
            return issue.get().getSourceStore();
        }
        if (holder.getBranch() == null) {
            throw new IllegalArgumentException(personName(holder)
                    + " has no branch on record, so there is no store to return the loaner to.");
        }
        return storeService.resolveAdminStore(holder.getBranch());
    }

    /** The temporary-replacement movement that put this loaner with this holder. */
    private Optional<Movement> issueOf(Asset loaner, User holder) {
        return movementRepository
                .findFirstByItems_Asset_IdAndMovementTypeOrderByIdDesc(loaner.getId(), MovementType.TEMP_REPLACEMENT)
                .filter(m -> m.getRecipientUser() != null && m.getRecipientUser().getId().equals(holder.getId()));
    }

    // ── Shaping ──────────────────────────────────────────────────────────────

    private LoanerDTO toDto(AssetAssignmentHistory loan) {
        Asset loaner = loan.getAsset();
        User holder = loan.getUser();
        Optional<Movement> issue = issueOf(loaner, holder);

        pride.bank.erp.repair.Repair covers = issue.map(Movement::getRepair).orElse(null);
        Asset covered = covers != null ? covers.getAsset() : null;
        boolean coveredBack = covered != null && covered.getAssignedTo() != null
                && covered.getAssignedTo().getId().equals(holder.getId());

        String returnStore;
        try {
            returnStore = returnStoreFor(loaner, holder).getName();
        } catch (Exception unresolved) {
            returnStore = null;
        }

        return LoanerDTO.builder()
                .assetId(loaner.getId())
                .engravedNumber(loaner.getEngravedNumber())
                .assetName(loaner.getAssetName())
                .category(loaner.getAssetType() != null ? loaner.getAssetType().getName() : null)
                .holderId(holder.getId())
                .holderName(personName(holder))
                .holderBranchName(holder.getBranch() != null ? holder.getBranch().getName() : null)
                .since(loan.getStartDate())
                .coversAssetId(covered != null ? covered.getId() : null)
                .coversAssetLabel(covered != null ? label(covered) : null)
                .coversRepairStatus(covers != null ? covers.getStatus() : null)
                .coveredAssetBack(coveredBack)
                .returnStoreName(returnStore)
                .pendingReturnMovementId(pendingReturnOf(loaner.getId()).map(Movement::getId).orElse(null))
                .build();
    }

    static String label(Asset asset) {
        String tag = asset.getEngravedNumber() != null ? asset.getEngravedNumber() : ("#" + asset.getId());
        return asset.getAssetName() != null ? tag + " — " + asset.getAssetName() : tag;
    }

    private static Long branchIdOf(User user) {
        return user != null && user.getBranch() != null ? user.getBranch().getId() : null;
    }

    private static String personName(User user) {
        String name = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }
}
