package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.inventory.InventoryService;
import pride.bank.erp.repair.Repair;
import pride.bank.erp.repair.RepairRepository;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Phase 2 Movement core: create movements (deriving the inter/intra category) and complete them
 * (applying the inventory ledger via {@link InventoryService}). The richer dispatch→receive state
 * machine and approval routing arrive in later phases; this layer is the foundation they build on.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class MovementService {

    private final MovementRepository movementRepository;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final AssetRepository assetRepository;
    private final CommodityRepository commodityRepository;
    private final RequestRepository requestRepository;
    private final RepairRepository repairRepository;
    private final CurrentUserService currentUserService;
    private final InventoryService inventoryService;

    @Transactional
    public Movement createMovement(MovementDTO dto) throws NotFoundException {
        if (dto.getMovementType() == null) {
            throw new IllegalArgumentException("movementType is required");
        }
        if (dto.getSourceStoreId() == null) {
            throw new IllegalArgumentException("sourceStoreId is required");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new IllegalArgumentException("A movement must have at least one item");
        }

        Store source = storeRepository.findById(dto.getSourceStoreId())
                .orElseThrow(() -> new NotFoundException("Source store " + dto.getSourceStoreId() + " not found"));

        Store dest = (dto.getDestStoreId() != null)
                ? storeRepository.findById(dto.getDestStoreId())
                .orElseThrow(() -> new NotFoundException("Destination store " + dto.getDestStoreId() + " not found"))
                : null;

        User recipient = (dto.getRecipientUserId() != null)
                ? userRepository.findById(dto.getRecipientUserId())
                .orElseThrow(() -> new NotFoundException("Recipient user " + dto.getRecipientUserId() + " not found"))
                : null;

        if (dest == null && recipient == null) {
            throw new IllegalArgumentException("A movement needs a destination store or a recipient user");
        }

        Request request = (dto.getRequestId() != null)
                ? requestRepository.findById(dto.getRequestId())
                .orElseThrow(() -> new NotFoundException("Request " + dto.getRequestId() + " not found"))
                : null;

        Repair repair = (dto.getRepairId() != null)
                ? repairRepository.findById(dto.getRepairId())
                .orElseThrow(() -> new NotFoundException("Repair " + dto.getRepairId() + " not found"))
                : null;

        MovementCategory category = deriveCategory(source, dest, recipient);

        Movement movement = Movement.builder()
                .movementType(dto.getMovementType())
                .movementCategory(category)
                .status(MovementStatus.INITIATED)
                .sourceStore(source)
                .destStore(dest)
                .recipientUser(recipient)
                .courierService(dto.getCourierService())
                .trackingNumber(dto.getTrackingNumber())
                .dispatchDate(dto.getDispatchDate())
                .expectedDeliveryDate(dto.getExpectedDeliveryDate())
                .deliveryDocuments(dto.getDeliveryDocuments() != null ? dto.getDeliveryDocuments() : new ArrayList<>())
                .receiptStatus(ReceiptStatus.PENDING)
                .remarks(dto.getRemarks())
                .initiator(currentUserService.getCurrentUser())
                .request(request)
                .repair(repair)
                .build();

        movement.setItems(buildItems(dto.getItems(), movement));

        Movement saved = movementRepository.save(movement);
        log.info("Created {} movement {} ({} → {})", category, saved.getId(),
                source.getName(), dest != null ? dest.getName() : "user " + (recipient != null ? recipient.getId() : null));
        return saved;
    }

    /**
     * Dispatches an inter-location movement (§9, §10): records courier/tracking details and moves it
     * to {@link MovementStatus#DISPATCHED}. Intra-location movements have no dispatch leg.
     */
    @Transactional
    public Movement dispatchMovement(Long movementId, MovementDispatchDTO dto) throws NotFoundException {
        Movement movement = findMovement(movementId);
        if (movement.getMovementCategory() != MovementCategory.INTER_LOCATION) {
            throw new IllegalStateException("Only inter-location movements are dispatched");
        }
        if (movement.getStatus() != MovementStatus.INITIATED && movement.getStatus() != MovementStatus.DRAFT) {
            throw new IllegalStateException("Movement " + movementId + " cannot be dispatched from status " + movement.getStatus());
        }
        if (isBlank(dto.getCourierService()) || isBlank(dto.getTrackingNumber())) {
            throw new IllegalArgumentException("Courier service and tracking number are required to dispatch");
        }

        movement.setCourierService(dto.getCourierService());
        movement.setTrackingNumber(dto.getTrackingNumber());
        movement.setDispatchDate(dto.getDispatchDate() != null ? dto.getDispatchDate() : LocalDateTime.now());
        movement.setExpectedDeliveryDate(dto.getExpectedDeliveryDate());
        if (dto.getDeliveryDocuments() != null) {
            movement.setDeliveryDocuments(dto.getDeliveryDocuments());
        }
        movement.setStatus(MovementStatus.DISPATCHED);
        log.info("Dispatched movement {} via {} (tracking {})", movementId, dto.getCourierService(), dto.getTrackingNumber());
        return movementRepository.save(movement);
    }

    /** Optional in-transit marker for a dispatched inter-location movement. */
    @Transactional
    public Movement markInTransit(Long movementId) throws NotFoundException {
        Movement movement = findMovement(movementId);
        if (movement.getStatus() != MovementStatus.DISPATCHED) {
            throw new IllegalStateException("Only a dispatched movement can be marked in transit");
        }
        movement.setStatus(MovementStatus.IN_TRANSIT);
        return movementRepository.save(movement);
    }

    /**
     * Receives a movement at its destination and finalises it. Inter-location movements must have
     * been dispatched first (BOM/admin confirms receipt, §10); intra-location movements are received
     * immediately. Applies the inventory ledger unless it was already settled elsewhere.
     */
    @Transactional
    public Movement receiveMovement(Long movementId, MovementReceiveDTO dto) throws NotFoundException {
        Movement movement = findMovement(movementId);
        if (movement.getStatus() == MovementStatus.COMPLETED) {
            return movement; // idempotent
        }
        if (movement.getStatus() == MovementStatus.CANCELLED) {
            throw new IllegalStateException("Cannot receive a cancelled movement");
        }
        if (movement.getMovementCategory() == MovementCategory.INTER_LOCATION
                && movement.getStatus() != MovementStatus.DISPATCHED
                && movement.getStatus() != MovementStatus.IN_TRANSIT) {
            throw new IllegalStateException("Inter-location movement " + movementId
                    + " must be dispatched before it can be received (current: " + movement.getStatus() + ")");
        }
        ReceiptStatus receiptStatus = (dto != null && dto.getReceiptStatus() != null)
                ? dto.getReceiptStatus() : ReceiptStatus.RECEIVED_OK;
        String remarks = (dto != null) ? dto.getRemarks() : null;
        return finalizeCompletion(movement, receiptStatus, remarks);
    }

    /**
     * Completes a movement and applies its inventory effects. Convenience entry point for
     * intra-location movements; inter-location movements should go through {@link #receiveMovement}.
     */
    @Transactional
    public Movement completeMovement(Long movementId, String remarks) throws NotFoundException {
        Movement movement = findMovement(movementId);
        if (movement.getStatus() == MovementStatus.COMPLETED) {
            return movement; // idempotent
        }
        if (movement.getStatus() == MovementStatus.CANCELLED) {
            throw new IllegalStateException("Cannot complete a cancelled movement");
        }
        return finalizeCompletion(movement, ReceiptStatus.RECEIVED_OK, remarks);
    }

    /** Cancels a movement without applying any inventory effect. */
    @Transactional
    public Movement cancelMovement(Long movementId, String reason) throws NotFoundException {
        Movement movement = findMovement(movementId);
        if (movement.getStatus() == MovementStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed movement");
        }
        movement.setStatus(MovementStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            movement.setRemarks(reason);
        }
        return movementRepository.save(movement);
    }

    /** Applies the ledger (unless already settled), stamps receipt/completion fields, and saves. */
    private Movement finalizeCompletion(Movement movement, ReceiptStatus receiptStatus, String remarks) {
        if (!movement.isInventorySettled()) {
            inventoryService.applyMovementCompletion(movement);
            movement.setInventorySettled(true);
        }
        movement.setStatus(MovementStatus.COMPLETED);
        movement.setReceivingOfficer(currentUserService.getCurrentUser());
        movement.setReceiptDate(LocalDateTime.now());
        movement.setReceiptStatus(receiptStatus);
        movement.setCompletionDate(LocalDateTime.now());
        if (remarks != null && !remarks.isBlank()) {
            movement.setRemarks(remarks);
        }
        return movementRepository.save(movement);
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    @Transactional(readOnly = true)
    public Movement findMovement(Long movementId) throws NotFoundException {
        return movementRepository.findById(movementId)
                .orElseThrow(() -> new NotFoundException("Movement " + movementId + " not found"));
    }

    @Transactional(readOnly = true)
    public Page<Movement> listMovements(Pageable pageable) {
        return movementRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public List<Movement> findByRequest(Long requestId) {
        return movementRepository.findByRequest_Id(requestId);
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** INTER when the destination is in a different location from the source; INTRA otherwise. */
    private MovementCategory deriveCategory(Store source, Store dest, User recipient) {
        Long sourceLoc = (source.getLocation() != null) ? source.getLocation().getId() : null;
        Long destLoc = null;
        if (dest != null && dest.getLocation() != null) {
            destLoc = dest.getLocation().getId();
        } else if (recipient != null && recipient.getBranch() != null) {
            destLoc = recipient.getBranch().getId();
        }
        boolean interLocation = sourceLoc != null && destLoc != null && !sourceLoc.equals(destLoc);
        return interLocation ? MovementCategory.INTER_LOCATION : MovementCategory.INTRA_LOCATION;
    }

    private List<MovementItem> buildItems(List<MovementItemDTO> itemDtos, Movement movement) throws NotFoundException {
        List<MovementItem> items = new ArrayList<>();
        for (MovementItemDTO dto : itemDtos) {
            boolean hasAsset = dto.getAssetId() != null;
            boolean hasCommodity = dto.getCommodityId() != null;
            if (hasAsset == hasCommodity) {
                throw new IllegalArgumentException("Each item must reference exactly one of assetId or commodityId");
            }

            MovementItem.MovementItemBuilder builder = MovementItem.builder().movement(movement);
            if (hasAsset) {
                Asset asset = assetRepository.findById(dto.getAssetId())
                        .orElseThrow(() -> new NotFoundException("Asset " + dto.getAssetId() + " not found"));
                builder.asset(asset)
                        .quantity(1)
                        .serialNumber(asset.getSerialNumber())
                        .assetTag(asset.getTagName());
            } else {
                Commodity commodity = commodityRepository.findById(dto.getCommodityId())
                        .orElseThrow(() -> new NotFoundException("Commodity " + dto.getCommodityId() + " not found"));
                int qty = (dto.getQuantity() != null) ? dto.getQuantity() : 0;
                if (qty <= 0) {
                    throw new IllegalArgumentException("quantity must be positive for commodity line " + commodity.getName());
                }
                builder.commodity(commodity).quantity(qty);
            }
            items.add(builder.build());
        }
        return items;
    }
}
