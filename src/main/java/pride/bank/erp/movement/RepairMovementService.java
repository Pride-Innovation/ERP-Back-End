package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.RepairDestination;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.status.Status;
import pride.bank.erp.status.StatusService;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreService;
import pride.bank.erp.store.StoreType;

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
    private final AssetRepository assetRepository;
    private final StoreService storeService;
    private final StoreRepository storeRepository;
    private final StatusService statusService;

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
                .items(List.of(assetItem(dto.getAssetId())))
                .build());

        setStatus(asset, CODE_IN_MAINTENANCE);
        log.info("Repair transfer movement {} created for asset {} (routing {})",
                movement.getId(), asset.getId(), routing);

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

    /** §15: issue a temporary replacement asset from the IT store to a user. */
    @Transactional
    public Movement issueTemporaryReplacement(TempReplacementDTO dto) throws NotFoundException {
        Store itStore = headOfficeStore(StoreType.IT);

        return movementService.createMovement(MovementDTO.builder()
                .movementType(MovementType.TEMP_REPLACEMENT)
                .sourceStoreId(itStore.getId())
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

    /** §16: return a repaired asset to its originating location (and optionally the temp asset). */
    @Transactional
    public Movement returnAfterRepair(ReturnAfterRepairDTO dto) throws NotFoundException {
        Asset repaired = findAsset(dto.getAssetId());
        // Source is wherever the asset is currently held (IT store, Admin store, or the Admin custody
        // store that coordinated an external repair) — resolved uniformly rather than hardcoding IT.
        Store source = currentStoreOf(repaired);
        Store destStore = storeService.resolveAdminStore(dto.getDestLocationId());

        Movement movement = movementService.createMovement(MovementDTO.builder()
                .movementType(MovementType.RETURN_AFTER_REPAIR)
                .sourceStoreId(source.getId())
                .destStoreId(destStore.getId())
                .recipientUserId(dto.getRecipientUserId())
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

        // Optionally return the temporary replacement asset back to the IT store.
        if (dto.getTempAssetId() != null) {
            Asset temp = findAsset(dto.getTempAssetId());
            Store tempSource = currentStoreOf(temp);
            movementService.createMovement(MovementDTO.builder()
                    .movementType(MovementType.RETURN_AFTER_REPAIR)
                    .sourceStoreId(tempSource.getId())
                    .destStoreId(itStore.getId())
                    .remarks("Return of temporary replacement asset")
                    .items(List.of(assetItem(dto.getTempAssetId())))
                    .build());
        }
        return movement;
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

    /** The store currently holding the asset, falling back to its branch's ADMIN store. */
    private Store currentStoreOf(Asset asset) throws NotFoundException {
        if (asset.getCurrentStore() != null) return asset.getCurrentStore();
        if (asset.getBranch() == null) {
            throw new NotFoundException("Asset " + asset.getId() + " has no branch to resolve a store from");
        }
        return storeService.resolveAdminStore(asset.getBranch());
    }

    private Store headOfficeStore(StoreType type) throws NotFoundException {
        return storeRepository.findByStoreType(type).stream().findFirst()
                .orElseThrow(() -> new NotFoundException("No " + type + " store configured"));
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
