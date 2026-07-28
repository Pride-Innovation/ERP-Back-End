package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.RepairDestination;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.inventory.InventoryService;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.store.StoreType;
import pride.bank.erp.user.User;

import java.util.List;

/**
 * Orchestrates the asset-lifecycle movements of CLAUDE.md §14-17 (repair transfer, temporary
 * replacement, return after repair, disposal). Each operation builds a {@link Movement} via
 * {@link MovementService}; the inventory/asset effects are applied when the movement is received
 * (see {@link pride.bank.erp.inventory.InventoryService#applyMovementCompletion}). Asset <i>status</i>
 * changes that should take effect immediately (e.g. "In Maintenance") are stamped here at initiation.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class RepairMovementService {

    private static final String CODE_IN_MAINTENANCE = "inMaintenance";
    private static final String CODE_PENDING_DISPOSAL = "pendingDisposal";

    private final MovementService movementService;
    private final MovementRepository movementRepository;
    private final AssetRepository assetRepository;
    private final StoreService storeService;
    private final StoreRepository storeRepository;
    private final StatusService statusService;
    private final BranchRepository branchRepository;
    private final InventoryService inventoryService;

    /**
     * §14: send a faulty asset for repair, routed by its category configuration.
     *
     * <p>A non-repairable category is diverted straight to disposal. Otherwise the destination
     * follows the category's {@link RepairDestination} (overridable on the DTO): {@code IT} → the
     * Head Office IT store, {@code ADMIN} → the Head Office Admin store, {@code EXTERNAL} → an outside
     * consultant (custody coordinated by the Head Office Admin store, with a mandatory consultant and
     * dispatch documents). Optionally issues a temporary replacement to the affected user.
     */
    @Transactional
    public Movement transferForRepair(RepairTransferDTO dto) throws NotFoundException {
        Asset asset = findAsset(dto.getAssetId());
        AssetType category = asset.getAssetType();

        // Non-repairable category → skip the workshop, send straight to disposal.
        if (category != null && !category.isRepairable()) {
            log.info("Asset {} category '{}' is non-repairable — diverting to disposal", asset.getId(), category.getName());
            return disposeAsset(DisposalDTO.builder()
                    .assetId(dto.getAssetId())
                    .remarks(dto.getRemarks() != null ? dto.getRemarks()
                            : "Auto-diverted to disposal: category is not repairable")
                    .build());
        }

        RepairDestination routing = dto.getRepairDestination() != null
                ? dto.getRepairDestination()
                : (category != null && category.getRepairDestination() != null
                        ? category.getRepairDestination()
                        : RepairDestination.IT);

        // Captured BEFORE the assignment is closed below — once it is closed, the system no longer
        // knows who had the asset, and this is what the return leg aims at.
        User originHolder = activeHolderOf(asset);
        Branch originLocation = asset.getBranch();

        Store source = currentStoreOf(asset);
        Store custody;
        Long consultantId = null;
        switch (routing) {
            case ADMIN -> custody = headOfficeAdminStore();
            case EXTERNAL -> {
                if (dto.getConsultantId() == null) {
                    throw new IllegalArgumentException("An external repair requires a consultant");
                }
                if (dto.getDeliveryDocuments() == null || dto.getDeliveryDocuments().isEmpty()) {
                    throw new IllegalArgumentException("An external repair requires at least one dispatch document");
                }
                // Admin coordinates vendor dispatch/receipt; the consultant link records who holds it.
                custody = headOfficeAdminStore();
                consultantId = dto.getConsultantId();
            }
            default -> custody = headOfficeStore(StoreType.IT);
        }

        Movement movement = movementService.createMovement(MovementDTO.builder()
                .movementType(MovementType.REPAIR_TRANSFER)
                .sourceStoreId(source.getId())
                .destStoreId(custody.getId())
                .repairId(dto.getRepairId())
                .consultantId(consultantId)
                .courierService(dto.getCourierService())
                .trackingNumber(dto.getTrackingNumber())
                .dispatchDate(dto.getDispatchDate())
                .expectedDeliveryDate(dto.getExpectedDeliveryDate())
                .deliveryDocuments(dto.getDeliveryDocuments())
                .remarks(dto.getRemarks())
                // Officer-initiated faulty-item transfer is a standalone movement → climbs the
                // initiator's approval ladder before it can be dispatched.
                .requiresApproval(true)
                .originHolderId(originHolder != null ? originHolder.getId() : null)
                .originLocationId(originLocation != null ? originLocation.getId() : null)
                .items(List.of(assetItem(dto.getAssetId())))
                .build());

        // The branch admin has physically taken the item off the user, so record that hand-over now
        // instead of at Head Office receipt. Previously the asset stayed "assigned to Jane" for the
        // whole journey — including while it sat in a courier's van — so if it went missing in
        // transit the trail said the user still had it.
        inventoryService.returnAssetToStore(asset, source);
        setStatus(asset, CODE_IN_MAINTENANCE);
        log.info("Repair transfer movement {} created for asset {} (routing {}); custody taken by {} "
                        + "on behalf of {}",
                movement.getId(), asset.getId(), routing, source.getName(),
                originHolder != null ? originHolder.getId() : "no assigned holder");

        // Optionally hand the affected user a temporary replacement while the repair runs (§15).
        if (dto.getTempAssetId() != null && dto.getAffectedUserId() != null) {
            issueTemporaryReplacement(TempReplacementDTO.builder()
                    .tempAssetId(dto.getTempAssetId())
                    .recipientUserId(dto.getAffectedUserId())
                    .remarks("Temporary replacement while asset " + asset.getId() + " is under repair")
                    .build());
        }
        return movement;
    }

    /**
     * §15: issue a temporary replacement asset to a user, from wherever that pool asset actually
     * sits — the IT store for IT-routed categories, the Head Office Admin store for others (e.g. a
     * pool of loaner chairs) — not always the IT store.
     */
    @Transactional
    public Movement issueTemporaryReplacement(TempReplacementDTO dto) throws NotFoundException {
        Asset temp = findAsset(dto.getTempAssetId());
        Store source = currentStoreOf(temp);

        return movementService.createMovement(MovementDTO.builder()
                .movementType(MovementType.TEMP_REPLACEMENT)
                .sourceStoreId(source.getId())
                .recipientUserId(dto.getRecipientUserId())
                .courierService(dto.getCourierService())
                .trackingNumber(dto.getTrackingNumber())
                .dispatchDate(dto.getDispatchDate())
                .expectedDeliveryDate(dto.getExpectedDeliveryDate())
                .deliveryDocuments(dto.getDeliveryDocuments())
                .remarks(dto.getRemarks())
                .items(List.of(assetItem(dto.getTempAssetId())))
                .build());
    }

    /**
     * §16: return a repaired asset to whoever had it, at wherever they now are.
     *
     * <p>The destination is <b>derived</b>, not asked for. In order of preference:
     * <ol>
     *   <li>the holder recorded on the asset's repair transfer — the authoritative answer;</li>
     *   <li>the asset's own {@code assignedTo}, which is how repairs started before origins were
     *       recorded, so anything already in flight still returns correctly with no migration;</li>
     *   <li>an explicit override on the DTO, which now needs a reason.</li>
     * </ol>
     *
     * <p>If that holder has since moved branch but is still employed, the asset follows them to
     * their current branch. If they have left the bank, it goes back to the branch it came from,
     * unassigned and flagged as pool stock available for temporary issuance — it is never
     * auto-assigned to someone who is gone.
     */
    @Transactional
    public Movement returnAfterRepair(ReturnAfterRepairDTO dto) throws NotFoundException {
        Asset repaired = findAsset(dto.getAssetId());
        // Source is wherever the asset is currently held (IT store, Admin store, or the Admin custody
        // store that coordinated an external repair) — resolved uniformly rather than hardcoding IT.
        Store source = currentStoreOf(repaired);

        ReturnTarget target = resolveReturnTarget(repaired, dto);
        Store destStore = storeService.resolveAdminStore(target.locationId());

        Movement movement = movementService.createMovement(MovementDTO.builder()
                .movementType(MovementType.RETURN_AFTER_REPAIR)
                .sourceStoreId(source.getId())
                .destStoreId(destStore.getId())
                .recipientUserId(target.recipientUserId())
                .courierService(dto.getCourierService())
                .trackingNumber(dto.getTrackingNumber())
                .dispatchDate(dto.getDispatchDate())
                .expectedDeliveryDate(dto.getExpectedDeliveryDate())
                .deliveryDocuments(dto.getDeliveryDocuments())
                .remarks(dto.getRemarks())
                // Admin returns the repaired item — a standalone movement that climbs the initiator's
                // (admin's) approval ladder before dispatch.
                .requiresApproval(true)
                .items(List.of(assetItem(dto.getAssetId())))
                .build());

        // If the user has left, the asset comes back to the branch rather than to a person, and is
        // made available for temporary issuance so it can be lent out instead of sitting idle.
        if (target.markAvailableForTemporaryIssuance()) {
            repaired.setTemporaryPool(true);
            assetRepository.save(repaired);
            log.info("Asset {} returns to branch {} unassigned and available for temporary issuance — "
                    + "its previous holder is no longer active", repaired.getId(), target.locationId());
        }

        // Return the temporary replacement to its own pool store — IT store for IT-routed
        // categories, Head Office Admin store for others. It carries the same logistics as the leg
        // above: created bare, an inter-location return stranded in INITIATED because nobody knew
        // it still needed dispatching.
        if (dto.getTempAssetId() != null) {
            Asset temp = findAsset(dto.getTempAssetId());
            Store tempSource = currentStoreOf(temp);
            Store poolStore = repairCustodyStoreFor(temp);
            movementService.createMovement(MovementDTO.builder()
                    .movementType(MovementType.RETURN_AFTER_REPAIR)
                    .sourceStoreId(tempSource.getId())
                    .destStoreId(poolStore.getId())
                    .courierService(dto.getCourierService())
                    .trackingNumber(dto.getTrackingNumber())
                    .dispatchDate(dto.getDispatchDate())
                    .expectedDeliveryDate(dto.getExpectedDeliveryDate())
                    .deliveryDocuments(dto.getDeliveryDocuments())
                    .remarks("Return of temporary replacement asset issued while asset "
                            + repaired.getId() + " was under repair")
                    .items(List.of(assetItem(dto.getTempAssetId())))
                    .build());
        }
        return movement;
    }

    /** Where a repaired asset is going, and to whom. */
    private record ReturnTarget(Long locationId, Long recipientUserId,
                                boolean markAvailableForTemporaryIssuance) {
    }

    /**
     * Works out where a repaired asset belongs, preferring the recorded origin over anything a
     * caller supplies. See {@link #returnAfterRepair} for the rules.
     */
    private ReturnTarget resolveReturnTarget(Asset repaired, ReturnAfterRepairDTO dto)
            throws NotFoundException {

        User holder = originHolderFor(repaired);

        // An explicit override still wins — a supervisor may genuinely want it elsewhere — but it
        // has to be justified, so a mistyped dropdown can't quietly redirect somebody's laptop.
        if (dto.getDestLocationId() != null || dto.getRecipientUserId() != null) {
            boolean differsFromOrigin = holder != null
                    && dto.getRecipientUserId() != null
                    && !dto.getRecipientUserId().equals(holder.getId());
            if (differsFromOrigin && isBlank(dto.getOverrideReason())) {
                throw new IllegalArgumentException(String.format(
                        "This asset was sent for repair by %s. Returning it to someone else needs a "
                                + "reason (overrideReason).", displayName(holder)));
            }
            Long locationId = dto.getDestLocationId() != null
                    ? dto.getDestLocationId()
                    : branchIdOf(holder);
            if (locationId == null) {
                throw new IllegalArgumentException("A destination location is required to return this asset");
            }
            return new ReturnTarget(locationId, dto.getRecipientUserId(), false);
        }

        if (holder == null) {
            throw new IllegalArgumentException(
                    "This asset has no recorded holder from its repair transfer, so its destination "
                            + "cannot be derived. Supply destLocationId (and recipientUserId).");
        }

        // Still employed — the asset follows them, including to a branch they have moved to.
        if (holder.isEnabled()) {
            Long branchId = branchIdOf(holder);
            if (branchId == null) {
                throw new IllegalArgumentException(String.format(
                        "%s has no branch on record, so the asset cannot be routed back to them.",
                        displayName(holder)));
            }
            return new ReturnTarget(branchId, holder.getId(), false);
        }

        // Left the organisation: back to where it came from, unassigned, available to lend out.
        Long originBranchId = originLocationFor(repaired);
        if (originBranchId == null) originBranchId = branchIdOf(holder);
        if (originBranchId == null) {
            throw new IllegalArgumentException(
                    "The previous holder has left and no origin branch was recorded — supply destLocationId.");
        }
        return new ReturnTarget(originBranchId, null, true);
    }

    /** The holder recorded on this asset's repair transfer, else its current assignment. */
    private User originHolderFor(Asset asset) {
        return movementRepository
                .findFirstByItems_Asset_IdAndMovementTypeOrderByIdDesc(asset.getId(), MovementType.REPAIR_TRANSFER)
                .map(Movement::getOriginHolder)
                .orElse(asset.getAssignedTo());
    }

    /** The branch recorded on this asset's repair transfer, if any. */
    private Long originLocationFor(Asset asset) {
        return movementRepository
                .findFirstByItems_Asset_IdAndMovementTypeOrderByIdDesc(asset.getId(), MovementType.REPAIR_TRANSFER)
                .map(Movement::getOriginLocation)
                .map(Branch::getId)
                .orElse(null);
    }

    private static Long branchIdOf(User user) {
        return (user != null && user.getBranch() != null) ? user.getBranch().getId() : null;
    }

    private static String displayName(User user) {
        if (user == null) return "the previous holder";
        String name = ((user.getFirstName() != null ? user.getFirstName() : "") + " "
                + (user.getLastName() != null ? user.getLastName() : "")).trim();
        return name.isEmpty() ? ("user " + user.getId()) : name;
    }

    private static boolean isBlank(String value) {
        return value == null || value.trim().isEmpty();
    }

    /** §17: move an irreparable asset to the Head Office Disposal store. */
    @Transactional
    public Movement disposeAsset(DisposalDTO dto) throws NotFoundException {
        Asset asset = findAsset(dto.getAssetId());
        Store source = currentStoreOf(asset);
        Store disposalStore = headOfficeStore(StoreType.DISPOSAL);

        Movement movement = movementService.createMovement(MovementDTO.builder()
                .movementType(MovementType.DISPOSAL_TRANSFER)
                .sourceStoreId(source.getId())
                .destStoreId(disposalStore.getId())
                .remarks(dto.getRemarks())
                .items(List.of(assetItem(dto.getAssetId())))
                .build());

        setStatus(asset, CODE_PENDING_DISPOSAL);
        log.info("Disposal movement {} created for asset {}", movement.getId(), asset.getId());
        return movement;
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private MovementItemDTO assetItem(Long assetId) {
        return MovementItemDTO.builder().assetId(assetId).quantity(1).build();
    }

    private Asset findAsset(Long assetId) throws NotFoundException {
        return assetRepository.findById(assetId)
                .orElseThrow(() -> new NotFoundException("Asset " + assetId + " not found"));
    }

    /** Who currently holds the asset, if anyone — captured before custody moves to the admin. */
    private User activeHolderOf(Asset asset) {
        return asset.getAssignedTo();
    }

    /** The store currently holding the asset, falling back to its branch's ADMIN store. */
    private Store currentStoreOf(Asset asset) throws NotFoundException {
        if (asset.getCurrentStore() != null) return asset.getCurrentStore();
        if (asset.getBranch() == null) {
            throw new NotFoundException("Asset " + asset.getId() + " has no branch to resolve a store from");
        }
        return storeService.resolveAdminStore(asset.getBranch());
    }

    /**
     * The store a repair-pool asset of this category belongs in — the IT store for IT-routed
     * categories, the Head Office Admin store otherwise (ADMIN-routed, EXTERNAL-routed, or
     * unconfigured). Used to resolve where a temporary-replacement asset should live, instead of
     * assuming it's always the IT store.
     */
    private Store repairCustodyStoreFor(Asset asset) throws NotFoundException {
        AssetType category = asset.getAssetType();
        RepairDestination routing = (category != null && category.getRepairDestination() != null)
                ? category.getRepairDestination() : RepairDestination.IT;
        return routing == RepairDestination.IT ? headOfficeStore(StoreType.IT) : headOfficeAdminStore();
    }

    /**
     * The organisation's single {@link StoreType#IT} / {@link StoreType#DISPOSAL} store.
     *
     * <p>There is exactly one of each for the whole bank, held at Head Office — that singleton is
     * what makes "repair only happens at Head Office" true. {@code StoreSeeder} creates it and
     * refuses to let a second one exist, so taking the first result here is unambiguous by
     * construction rather than by luck.
     */
    private Store headOfficeStore(StoreType type) throws NotFoundException {
        return storeRepository.findByStoreType(type).stream().findFirst()
                .orElseThrow(() -> new NotFoundException(String.format(
                        "The organisation has no %s store. It is created at the branch flagged as "
                                + "Head Office — flag one and restart the application.", type)));
    }

    /**
     * The Head Office location-level ADMIN store — custody for ADMIN-routed and EXTERNAL repairs.
     * Every branch has an ADMIN store, so unlike IT/Disposal it must be resolved via the Head
     * Office branch rather than "the first store of this type".
     */
    private Store headOfficeAdminStore() throws NotFoundException {
        Branch headOffice = branchRepository.findFirstByIsHeadOfficeTrue()
                .orElseThrow(() -> new NotFoundException("No Head Office branch is configured"));
        return storeService.resolveAdminStore(headOffice);
    }

    private void setStatus(Asset asset, String statusCode) {
        try {
            Status status = statusService.findByStatusCode(statusCode);
            asset.setAssetStatus(status);
            assetRepository.save(asset);
        } catch (NotFoundException e) {
            log.warn("Status '{}' not found; leaving asset {} status unchanged", statusCode, asset.getId());
        }
    }
}
