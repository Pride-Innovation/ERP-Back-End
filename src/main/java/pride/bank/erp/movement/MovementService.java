package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.commodity.CommodityRepository;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.consultant.Consultant;
import pride.bank.erp.consultant.ConsultantRepository;
import pride.bank.erp.consignment.Consignment;
import pride.bank.erp.consignment.ConsignmentStatus;
import pride.bank.erp.courier.Courier;
import pride.bank.erp.courier.CourierService;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.FileUpload;
import pride.bank.erp.inventory.InventoryService;
import pride.bank.erp.issuedAsset.IssuedAssetLifecycleService;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import pride.bank.erp.repair.Repair;
import pride.bank.erp.repair.RepairRepository;
import pride.bank.erp.request.Request;
import pride.bank.erp.request.RequestRepository;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserRepository;

import java.time.LocalDateTime;
import pride.bank.erp.auditing.trail.AuditEventType;
import pride.bank.erp.auditing.trail.AuditModule;
import pride.bank.erp.auditing.trail.AuditSeverity;

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
    private final MovementSearchDao movementSearchDao;
    private final StoreRepository storeRepository;
    private final UserRepository userRepository;
    private final BranchRepository branchRepository;
    private final pride.bank.erp.config.BranchScopeService branchScopeService;

    /** Decides whether a <em>particular</em> movement is within the caller's reach. */
    private final pride.bank.erp.access.AccessScopeService accessScopeService;
    private final AssetRepository assetRepository;
    private final CommodityRepository commodityRepository;
    private final RequestRepository requestRepository;
    private final RepairRepository repairRepository;
    private final ConsultantRepository consultantRepository;
    private final CurrentUserService currentUserService;
    private final InventoryService inventoryService;
    private final MovementApprovalService movementApprovalService;
    private final FileUpload fileUpload;
    private final CourierService courierService;
    private final IssuedAssetLifecycleService issuedAssetLifecycleService;
    /**
     * Stock physically leaving a building is the highest-consequence thing this application does, so
     * every state change on a movement goes on the audit trail.
     */
    private final pride.bank.erp.auditing.trail.AuditService auditService;

    /** Reminds a holder to hand back a loaner once their own asset comes home from repair. */
    private final LoanerReminder loanerReminder;

    @Transactional
    public Movement createMovement(MovementDTO dto) throws NotFoundException {
        if (dto.getMovementType() == null) {
            throw new IllegalArgumentException("movementType is required");
        }
        // Exactly one source: a store, or a user handing items back (RETURN_TO_STORE).
        boolean hasSourceStore = dto.getSourceStoreId() != null;
        boolean hasSourceUser = dto.getSourceUserId() != null;
        if (hasSourceStore == hasSourceUser) {
            throw new IllegalArgumentException(
                    "A movement needs exactly one source — either sourceStoreId or sourceUserId");
        }
        if (dto.getItems() == null || dto.getItems().isEmpty()) {
            throw new IllegalArgumentException("A movement must have at least one item");
        }

        Store source = hasSourceStore
                ? storeRepository.findById(dto.getSourceStoreId())
                .orElseThrow(() -> new NotFoundException("Source store " + dto.getSourceStoreId() + " not found"))
                : null;

        User sourceUser = hasSourceUser
                ? userRepository.findById(dto.getSourceUserId())
                .orElseThrow(() -> new NotFoundException("Source user " + dto.getSourceUserId() + " not found"))
                : null;

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
        if (sourceUser != null && dest == null) {
            throw new IllegalArgumentException(
                    "A return from a user must name the destination store the items are handed back into");
        }

        Request request = (dto.getRequestId() != null)
                ? requestRepository.findById(dto.getRequestId())
                .orElseThrow(() -> new NotFoundException("Request " + dto.getRequestId() + " not found"))
                : null;

        Repair repair = (dto.getRepairId() != null)
                ? repairRepository.findById(dto.getRepairId())
                .orElseThrow(() -> new NotFoundException("Repair " + dto.getRepairId() + " not found"))
                : null;

        Consultant consultant = (dto.getConsultantId() != null)
                ? consultantRepository.findById(dto.getConsultantId())
                .orElseThrow(() -> new NotFoundException("Consultant " + dto.getConsultantId() + " not found"))
                : null;

        MovementCategory category = deriveCategory(source, sourceUser, dest, recipient);

        Movement movement = Movement.builder()
                .movementType(dto.getMovementType())
                .movementCategory(category)
                .status(MovementStatus.INITIATED)
                .sourceStore(source)
                .sourceUser(sourceUser)
                .destStore(dest)
                .recipientUser(recipient)
                .originHolder(resolveUser(dto.getOriginHolderId()))
                .originLocation(resolveBranch(dto.getOriginLocationId()))
                .restoreStatusCode(dto.getRestoreStatusCode())
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
                .consultant(consultant)
                .build();

        movement.setItems(buildItems(dto.getItems(), movement));

        Movement saved = movementRepository.save(movement);
        log.info("Created {} movement {} ({} → {})", category, saved.getId(),
                source != null ? source.getName() : "user " + sourceUser.getId(),
                dest != null ? dest.getName() : "user " + (recipient != null ? recipient.getId() : null));

        audit(AuditEventType.CREATED, "MOVEMENT_CREATED", saved,
                "Created " + typeLabel(saved) + " movement #" + saved.getId()
                        + " (" + routeLabel(saved) + ") with " + saved.getItems().size() + " item(s)",
                AuditSeverity.INFO);

        if (requiresApproval(dto, category, request)) {
            boolean bypass = Boolean.TRUE.equals(dto.getProceedWithoutApproval());
            if (bypass && isBlank(dto.getBypassReason())) {
                throw new BadRequestException(
                        "Proceeding without approval needs a reason, so the exception is on the record.");
            }
            movementApprovalService.startApproval(saved, bypass, dto.getBypassReason());
        }
        return saved;
    }

    // ── Audit trail ───────────────────────────────────────────────────────────

    /** Records a movement event under the Movement module, tagged to the movement itself. */
    private void audit(AuditEventType eventType, String action, Movement movement,
                       String description, AuditSeverity severity) {
        auditService.record(eventType, AuditModule.MOVEMENT, action, description, severity,
                "Movement", movement.getId());
    }

    private static String typeLabel(Movement movement) {
        return movement.getMovementType() == null ? "movement"
                : movement.getMovementType().name().replace('_', ' ').toLowerCase();
    }

    /** "Head Office IT Store → Gulu Branch", with either end possibly being a person. */
    private static String routeLabel(Movement movement) {
        String from = movement.getSourceStore() != null
                ? movement.getSourceStore().getName()
                : personName(movement.getSourceUser());
        String to = movement.getDestStore() != null
                ? movement.getDestStore().getName()
                : personName(movement.getRecipientUser());
        return (from == null ? "—" : from) + " → " + (to == null ? "—" : to);
    }

    private static String personName(User user) {
        if (user == null) return null;
        String name = ((user.getFirstName() == null ? "" : user.getFirstName()) + " "
                + (user.getLastName() == null ? "" : user.getLastName())).trim();
        return name.isEmpty() ? user.getEmail() : name;
    }

    /**
     * Whether this movement must clear the initiator's approval ladder before it can be dispatched.
     *
     * <p>Decided by policy rather than by the caller. The create form used to expose this as a free
     * checkbox defaulting to {@code false}, so a user could move stock across locations with no
     * oversight at all, while the repair flows forced approval unconditionally — the same act
     * controlled or uncontrolled depending only on which screen it was started from.
     *
     * <ul>
     *   <li>Movements carrying a {@link Request} are exempt: the request already cleared its own
     *       workflow, and a second, different approval would both duplicate that control and strand
     *       the fulfilment legs created automatically on its behalf.</li>
     *   <li>Callers may still opt in explicitly — the repair and return flows do (§14, §16), since
     *       those are intra-location as often as not.</li>
     *   <li>Everything else that crosses a location boundary needs approval, including a Head
     *       Office-initiated branch restock, which has no request to inherit a control from.</li>
     * </ul>
     */
    private boolean requiresApproval(MovementDTO dto, MovementCategory category, Request request) {
        if (request != null) return false;
        if (Boolean.TRUE.equals(dto.getRequiresApproval())) return true;
        return category == MovementCategory.INTER_LOCATION;
    }

    /**
     * Attaches a delivery/release document (e.g. the signed HO release note for an out-of-Head-Office
     * dispatch) to a movement — uploaded to local storage and appended to {@code deliveryDocuments}.
     */
    @Transactional
    public Movement attachDocument(Long movementId, MultipartFile file) throws NotFoundException {
        Movement movement = findMovement(movementId);
        requireMovementInScope(movement, pride.bank.erp.access.ScopeAction.MANAGE);
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No document file was provided");
        }
        try {
            String path = fileUpload.uploadFileToLocalDirectory(file);
            if (path != null) {
                if (movement.getDeliveryDocuments() == null) {
                    movement.setDeliveryDocuments(new ArrayList<>());
                }
                movement.getDeliveryDocuments().add(path);
                movement = movementRepository.save(movement);
            }
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload movement document: " + e.getMessage(), e);
        }
        return movement;
    }

    /**
     * Uploads a document before its movement exists and returns the stored path for the creation
     * payload. Needed by flows that must carry documents at creation time — an EXTERNAL repair
     * transfer is rejected without dispatch documents, so they cannot be attached after the fact.
     */
    public String uploadStandaloneDocument(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("No document file was provided");
        }
        try {
            String path = fileUpload.uploadFileToLocalDirectory(file);
            if (path == null) {
                throw new IllegalArgumentException("No document file was provided");
            }
            return path;
        } catch (IOException e) {
            throw new RuntimeException("Failed to upload movement document: " + e.getMessage(), e);
        }
    }

    /**
     * Dispatches an inter-location movement (§9, §10): resolves the courier (vetted by id, or
     * auto-created ad-hoc by name), records the plate number and signed dispatch document(s), and
     * moves it to {@link MovementStatus#DISPATCHED}. No ledger effect yet — the items are still
     * booked at the source store until custody actually transfers at {@link #markInTransit}.
     * Intra-location movements have no dispatch leg.
     */
    @Transactional
    public Movement dispatchMovement(Long movementId, MovementDispatchDTO dto) throws NotFoundException {
        Movement movement = findMovement(movementId);
        requireMovementInScope(movement, pride.bank.erp.access.ScopeAction.MANAGE);
        if (movement.getMovementCategory() != MovementCategory.INTER_LOCATION) {
            throw new IllegalStateException("Only inter-location movements are dispatched");
        }
        requireNotOnConsignment(movement, "dispatched");
        // DRAFT means the movement is still climbing its approval ladder — startApproval parks it
        // there and only the final tier flips it back to INITIATED. Accepting DRAFT here let a
        // cross-location movement be dispatched with none of its approvals given, for the whole
        // window between creation and sign-off. The Dispatch button was already hidden for DRAFT,
        // so the ladder was enforced by the UI alone; the consignment path (ConsignmentService
        // #addMovement) has always required INITIATED.
        if (movement.getStatus() == MovementStatus.DRAFT) {
            throw new IllegalStateException("Movement " + movementId + " is still awaiting approval"
                    + (movement.getCurrentApprover() != null
                            ? " by " + movement.getCurrentApprover().getFirstName() + " "
                              + movement.getCurrentApprover().getLastName()
                            : "")
                    + " and cannot be dispatched yet.");
        }
        if (movement.getStatus() != MovementStatus.INITIATED) {
            throw new IllegalStateException("Movement " + movementId + " cannot be dispatched from status " + movement.getStatus());
        }
        if (isBlank(dto.getTrackingNumber())) {
            throw new IllegalArgumentException("A tracking number is required to dispatch");
        }
        if (isBlank(dto.getPlateNumber())) {
            throw new IllegalArgumentException("A vehicle plate number is required to dispatch");
        }
        if (dto.getDeliveryDocuments() == null || dto.getDeliveryDocuments().isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one signed dispatch document is required to dispatch. Generate the "
                            + "dispatch note, get it signed by the courier, and upload it first.");
        }

        Courier courier = courierService.resolveForDispatch(dto.getCourierId(), dto.getCourierName());

        movement.setCourier(courier);
        movement.setPlateNumber(dto.getPlateNumber());
        movement.setCourierService(courier.getName());
        movement.setTrackingNumber(dto.getTrackingNumber());
        movement.setDispatchDate(dto.getDispatchDate() != null ? dto.getDispatchDate() : LocalDateTime.now());
        movement.setExpectedDeliveryDate(dto.getExpectedDeliveryDate());
        // Appended, not replaced. An EXTERNAL repair transfer is rejected at creation without its
        // signed vendor hand-over documents, and replacing the list here destroyed them the moment
        // the movement was dispatched — leaving only the courier's note behind.
        appendDeliveryDocuments(movement, dto.getDeliveryDocuments());
        movement.setStatus(MovementStatus.DISPATCHED);
        log.info("Dispatched movement {} to courier {} (plate {}, tracking {})",
                movementId, courier.getName(), dto.getPlateNumber(), dto.getTrackingNumber());
        Movement saved = movementRepository.save(movement);
        audit(AuditEventType.UPDATED, "MOVEMENT_DISPATCHED", saved,
                "Dispatched movement #" + saved.getId() + " (" + routeLabel(saved) + ") via "
                        + courier.getName()
                        + (isBlank(dto.getPlateNumber()) ? "" : ", plate " + dto.getPlateNumber())
                        + (isBlank(dto.getTrackingNumber()) ? "" : ", tracking " + dto.getTrackingNumber()),
                AuditSeverity.INFO);
        return saved;
    }

    /**
     * Marks a dispatched movement in-transit — compulsory, not optional: {@link #receiveMovement}
     * refuses any inter-location movement that skipped this step. This is also where custody of the
     * items actually transfers: the first ledger hop debits the source store and credits the
     * courier's custody store, guarded by {@code custodyTransferSettled} so it only ever applies once.
     */
    @Transactional
    public Movement markInTransit(Long movementId) throws NotFoundException {
        Movement movement = findMovement(movementId);
        requireMovementInScope(movement, pride.bank.erp.access.ScopeAction.MANAGE);
        requireNotOnConsignment(movement, "marked in transit");
        if (movement.getStatus() != MovementStatus.DISPATCHED) {
            throw new IllegalStateException("Only a dispatched movement can be marked in transit");
        }
        if (movement.getCourier() == null) {
            throw new IllegalStateException("Movement " + movementId + " has no courier assigned — dispatch it first");
        }
        if (!movement.isCustodyTransferSettled()) {
            Store courierStore = storeRepository.findByCourier_Id(movement.getCourier().getId())
                    .orElseThrow(() -> new NotFoundException(
                            "Courier " + movement.getCourier().getId() + " has no custody store"));
            inventoryService.applyCustodyHandoff(movement, courierStore);
            movement.setCustodyTransferSettled(true);
        }
        movement.setStatus(MovementStatus.IN_TRANSIT);
        Movement saved = movementRepository.save(movement);
        // The point custody actually changes hands — the items are now booked to the courier, not
        // the source store. Worth its own row rather than being folded into dispatch.
        audit(AuditEventType.UPDATED, "MOVEMENT_IN_TRANSIT", saved,
                "Movement #" + saved.getId() + " in transit — custody transferred to "
                        + saved.getCourier().getName(),
                AuditSeverity.INFO);
        return saved;
    }

    /**
     * Receives a movement at its destination and finalizes it. Inter-location movements must have
     * been marked {@code IN_TRANSIT} first (dispatch alone is not enough — see {@link #markInTransit});
     * intra-location movements are received immediately. Applies the inventory ledger unless it was
     * already settled elsewhere.
     */
    @Transactional
    public Movement receiveMovement(Long movementId, MovementReceiveDTO dto) throws NotFoundException {
        Movement movement = findMovement(movementId);
        requireMovementInScope(movement, pride.bank.erp.access.ScopeAction.MANAGE);
        if (movement.getStatus() == MovementStatus.COMPLETED) {
            return movement; // idempotent
        }
        if (movement.getStatus() == MovementStatus.CANCELLED) {
            throw new IllegalStateException("Cannot receive a cancelled movement");
        }
        if (movement.getMovementCategory() == MovementCategory.INTER_LOCATION
                && movement.getStatus() != MovementStatus.IN_TRANSIT) {
            throw new IllegalStateException("Inter-location movement " + movementId
                    + " must be marked in-transit before it can be received (current: " + movement.getStatus() + ")");
        }
        requireConsignmentArrived(movement);
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
        requireMovementInScope(movement, pride.bank.erp.access.ScopeAction.MANAGE);
        if (movement.getStatus() == MovementStatus.COMPLETED) {
            return movement; // idempotent
        }
        if (movement.getStatus() == MovementStatus.CANCELLED) {
            throw new IllegalStateException("Cannot complete a cancelled movement");
        }
        // Same precondition as receiveMovement. Without it this endpoint was a way around the
        // compulsory custody chain: an inter-location movement could be completed with no courier,
        // plate number, tracking number or signed dispatch note, and its assets never stamped
        // In Transit — the ledger would still balance, but the custody trail would be fiction.
        if (movement.getMovementCategory() == MovementCategory.INTER_LOCATION
                && movement.getStatus() != MovementStatus.IN_TRANSIT) {
            throw new IllegalStateException("Inter-location movement " + movementId
                    + " must be dispatched and marked in-transit before it can be completed (current: "
                    + movement.getStatus() + ")");
        }
        requireConsignmentArrived(movement);
        return finalizeCompletion(movement, ReceiptStatus.RECEIVED_OK, remarks);
    }

    /** Cancels a movement without applying any inventory effect. */
    @Transactional
    public Movement cancelMovement(Long movementId, String reason) throws NotFoundException {
        Movement movement = findMovement(movementId);
        requireMovementInScope(movement, pride.bank.erp.access.ScopeAction.MANAGE);
        if (movement.getStatus() == MovementStatus.COMPLETED) {
            throw new IllegalStateException("Cannot cancel a completed movement");
        }
        movement.setStatus(MovementStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            movement.setRemarks(reason);
        }
        Movement saved = movementRepository.save(movement);

        // A canceled fulfilment movement means the issued items are not being delivered — return
        // any of the linked request's issued assets to the pool so they can be issued again.
        if (movement.getRequest() != null) {
            issuedAssetLifecycleService.restoreToAvailable(movement.getRequest());
        }

        audit(AuditEventType.DELETED, "MOVEMENT_CANCELLED", saved,
                "Cancelled movement #" + saved.getId() + " (" + routeLabel(saved) + ")"
                        + (isBlank(reason) ? "" : " — " + reason),
                AuditSeverity.WARNING);
        return saved;
    }

    /** Applies the ledger (unless already settled), stamps receipt/completion fields, and saves. */
    private Movement finalizeCompletion(Movement movement, ReceiptStatus receiptStatus, String remarks) {
        if (!movement.isInventorySettled()) {
            inventoryService.applyMovementCompletion(movement, debitSourceFor(movement));
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
        Movement saved = movementRepository.save(movement);
        // A repaired asset reaching its holder is the moment any loaner standing in for it is due.
        loanerReminder.onCompleted(saved);
        // Anything other than a clean receipt is flagged: a short or damaged delivery is the
        // discrepancy someone will need to reconstruct later.
        boolean clean = receiptStatus == ReceiptStatus.RECEIVED_OK;
        audit(AuditEventType.UPDATED, "MOVEMENT_COMPLETED", saved,
                "Completed movement #" + saved.getId() + " (" + routeLabel(saved) + ") — received "
                        + receiptStatus.name().replace('_', ' ').toLowerCase()
                        + (isBlank(remarks) ? "" : ": " + remarks),
                clean ? AuditSeverity.INFO : AuditSeverity.WARNING);
        return saved;
    }

    /*
     * maybeCreateDeliveryLeg has been removed.
     *
     * It used to fire here: once a cross-location fulfilment was received into the branch store, it
     * silently created a second movement from that store to the requester, because one movement
     * could not represent both the custody stop and the final hand-over. One request produced two
     * rows, the second one appearing on its own, and a failure was swallowed into a log line nobody
     * read.
     *
     * A Consignment now owns the journey, so the movement runs source → requester throughout and the
     * branch stop is an event on the consignment: arrival credits the destination store
     * (InventoryService#applyConsignmentArrival), and the movement completes when its recipient
     * signs, drawing its items back out of that store. See {@link #debitSourceFor}.
     */

    /**
     * Which balance a completing movement draws down.
     *
     * <ol>
     *   <li>Arrived on a consignment → the store the goods landed in. The courier was already
     *       debited when the load arrived, so debiting it again here would double-count.</li>
     *   <li>Carried by a courier on its own → the courier's custody store, where the items have sat
     *       since {@code IN_TRANSIT}.</li>
     *   <li>Neither → its own source store, as an intra-location movement always does.</li>
     * </ol>
     */
    private Store debitSourceFor(Movement movement) {
        Consignment consignment = movement.getConsignment();
        if (consignment != null && consignment.getStatus() == ConsignmentStatus.ARRIVED
                && consignment.getLandingStore() != null) {
            return consignment.getLandingStore();
        }
        if (movement.getCourier() != null) {
            return storeRepository.findByCourier_Id(movement.getCourier().getId())
                    .orElse(movement.getSourceStore());
        }
        return movement.getSourceStore();
    }

    /**
     * Adds dispatch paperwork to whatever the movement already carries.
     *
     * <p>Both dispatch paths used to assign the list outright. That was fine while the only
     * documents were the courier's, but an EXTERNAL repair transfer cannot be created without its
     * signed vendor hand-over documents, so replacing the list silently destroyed them — and only
     * on cross-location external repairs, which are the ones where the paperwork matters most.
     *
     * <p>Shared with {@code ConsignmentService#dispatch} so a movement in a van and a movement on
     * its own keep their documents the same way.
     */
    public static void appendDeliveryDocuments(Movement movement, List<String> documents) {
        if (documents == null || documents.isEmpty()) {
            return;
        }
        if (movement.getDeliveryDocuments() == null) {
            movement.setDeliveryDocuments(new ArrayList<>());
        }
        for (String document : documents) {
            if (document != null && !movement.getDeliveryDocuments().contains(document)) {
                movement.getDeliveryDocuments().add(document);
            }
        }
    }

    /**
     * A movement travelling with others cannot be driven on its own.
     *
     * <p>The consignment owns dispatch and transit for everything it carries, and its ledger hops
     * apply to every movement at once. Letting one be dispatched separately would put its stock
     * against a courier the rest of the load knows nothing about, and leave two records of the same
     * van disagreeing about where the goods are.
     */
    private void requireNotOnConsignment(Movement movement, String action) {
        Consignment consignment = movement.getConsignment();
        if (consignment != null && consignment.getStatus() != ConsignmentStatus.CANCELLED) {
            throw new IllegalStateException("Movement " + movement.getId() + " travels on consignment "
                    + consignment.getReference() + " and is " + action + " with it, not on its own.");
        }
    }

    /**
     * The goods have to be at the destination before anyone can sign for them.
     *
     * <p>For a movement on a consignment that means the load has arrived. Completing it earlier
     * would draw stock out of a store it has not reached yet.
     */
    private void requireConsignmentArrived(Movement movement) {
        Consignment consignment = movement.getConsignment();
        if (consignment == null || consignment.getStatus() == ConsignmentStatus.CANCELLED) return;
        if (consignment.getStatus() != ConsignmentStatus.ARRIVED) {
            throw new IllegalStateException("Consignment " + consignment.getReference() + " is "
                    + consignment.getStatus() + ". The goods have to arrive before this movement can "
                    + "be handed over.");
        }
    }

    private boolean isBlank(String s) {
        return s == null || s.isBlank();
    }

    /**
     * Loads a movement for internal use, without a scope check.
     *
     * <p>Unguarded on purpose, and for the same reason as {@code AssetService#findOne}: the approval,
     * consignment and issuance flows reach a movement while acting for the system rather than for
     * the person who happened to trigger them. Gating here would refuse a Head Office issuer
     * fulfilling a branch's request, which is the workflow working correctly.
     *
     * <p>Use {@link #getMovementForViewing} for anything a person asked to see.
     */
    @Transactional(readOnly = true)
    public Movement findMovement(Long movementId) throws NotFoundException {
        // findDetailById, never findById — see MovementRepository#findDetailById for why.
        return movementRepository.findDetailById(movementId)
                .orElseThrow(() -> new NotFoundException("Movement " + movementId + " not found"));
    }

    /**
     * The detail read behind {@code GET /movements/{id}}.
     *
     * <p>Separate from {@link #findMovement} rather than a flag on it, because the lifecycle actions
     * must not run this: a caller holding {@code MANAGE_ALL_BRANCH_MOVEMENTS} but not the matching
     * view multiplier would be refused by a VIEW check before their MANAGE right was ever consulted.
     * The two questions are asked in the right order by not mixing them.
     */
    @Transactional(readOnly = true)
    public Movement getMovementForViewing(Long movementId) throws NotFoundException {
        Movement movement = findMovement(movementId);
        requireMovementInScope(movement, pride.bank.erp.access.ScopeAction.VIEW);
        return movement;
    }

    /**
     * Movements the caller may see: everything for a cross-branch viewer, otherwise only those
     * touching their own branch at either end.
     *
     * <p>A caller with no branch on record is told so rather than handed an empty page. Returning
     * nothing made a configuration fault indistinguishable from "there are no movements", which is
     * the least useful thing an empty table can mean. This matches what the inventory listings
     * already do via {@code BranchScopeService#resolve}.
     */
    @Transactional(readOnly = true)
    public Page<Movement> listMovements(Pageable pageable) {
        return listMovements(new MovementSearchCriteria(), pageable);
    }

    /**
     * The movements register, filtered and paged in the database.
     *
     * <p>The scope is stamped on here and never bound from the request — a client-supplied scope is
     * not a scope. Everything else on the criteria came from the caller and is optional.
     *
     * <p>The two hand-written finders this replaces are kept for their other callers, but the
     * listing no longer uses them: neither could filter, and neither ordered by anything, so paging
     * over them repeated and dropped rows (trap #3).
     */
    @Transactional(readOnly = true)
    public Page<Movement> listMovements(MovementSearchCriteria criteria, Pageable pageable) {
        applyScope(criteria);
        return movementSearchDao.search(criteria, pageable);
    }

    /**
     * The branch a movement listing is narrowed to, or {@code null} for no restriction.
     *
     * <h2>Null means two opposite things, and only one of them is safe</h2>
     * {@code AccessScopeService.resolveBranch} answers null both for a caller who may see every
     * branch <em>and</em> for a caller with no branch on record — and downstream, null reads as "no
     * restriction". So a user with no duty station and no cross-branch grant was handed the whole
     * bank's movements, which is the exact opposite of what their permissions say.
     *
     * <p>This is the trap {@code BranchScopeService#resolve} was written to close for the inventory
     * listings; the movement listing called the resolver directly and never got that protection.
     * A caller with no branch is now told so rather than silently widened — an unconfigured account
     * is a configuration fault, and answering it with everybody's data is the worst way to report
     * one.
     */
    /**
     * Stamps the caller's reach onto a search — by branch, by person, or not at all.
     *
     * <h2>SELF used to collapse into BRANCH</h2>
     * This returned a branch id for everything below ALL, so SELF and BRANCH were the same query and
     * an officer granted {@code READ_MOVEMENT} saw their whole branch's register. A movement has no
     * owner column to narrow on, which is why the SELF rule is expressed as person <em>links</em> —
     * see {@code MovementSearchCriteria#scopedUserId}.
     *
     * <p>The two are mutually exclusive: at SELF the branch is not also applied, or a movement I am a
     * party to at another branch — a transfer coming to me, say — would be filtered back out.
     */
    private void applyScope(MovementSearchCriteria criteria) {
        pride.bank.erp.dashboard.DashboardScope scope = accessScopeService.scopeFor(
                pride.bank.erp.access.ScopeSubject.MOVEMENTS,
                pride.bank.erp.access.ScopeAction.VIEW);

        if (scope == pride.bank.erp.dashboard.DashboardScope.ALL) {
            return;   // legitimately every branch
        }

        if (scope == pride.bank.erp.dashboard.DashboardScope.SELF) {
            Long me = accessScopeService.currentUserId();
            if (me == null) {
                throw new org.springframework.security.access.AccessDeniedException(
                        "Your account could not be identified, so movements cannot be shown.");
            }
            criteria.setScopedUserId(me);
            return;
        }

        Long own = accessScopeService.currentUserBranchId();
        if (own == null) {
            // Null means "no restriction" downstream, so a branchless account must be refused rather
            // than handed the whole register.
            throw new org.springframework.security.access.AccessDeniedException(
                    "Your account is not attached to a branch, so movements cannot be shown. "
                            + "Ask an administrator to set your branch.");
        }
        criteria.setScopedBranchId(own);
    }

    /**
     * How many movements sit in each status, over everything the filters match.
     *
     * <p>Feeds the page's status tiles. They used to count the rows the browser happened to hold
     * while the heading above them showed the true total, so the two disagreed silently once the
     * register grew past the fetch size.
     */
    @Transactional(readOnly = true)
    public java.util.Map<MovementStatus, Long> countMovementsByStatus(MovementSearchCriteria criteria) {
        applyScope(criteria);
        return movementSearchDao.countByStatus(criteria);
    }

    /**
     * The values the filter dropdowns should offer.
     *
     * <p>Derived from the scoped register rather than from the loaded page, so the panel cannot fail
     * to offer a value it would have matched.
     */
    @Transactional(readOnly = true)
    public MovementSearchDao.FilterOptions movementFilterOptions() {
        // Scope only, no filters: the dropdowns must not narrow as choices are made, or picking a
        // source would empty the destination list and the panel could never be widened again.
        MovementSearchCriteria scopeOnly = new MovementSearchCriteria();
        applyScope(scopeOnly);
        return movementSearchDao.filterOptions(scopeOnly);
    }

    /**
     * Every branch a movement touches.
     *
     * <p>Four possible ends, and no movement has all of them: a store's location at either end, and
     * a person's duty station at either end (a return has no source store, an issuance no source
     * user). Mirrors {@code MovementRepository#findForBranch} deliberately — a guard that computed
     * the branch differently from the listing would show someone a row they could not then open,
     * which is worse than either rule on its own.
     */
    private static java.util.Set<Long> branchesTouchedBy(Movement movement) {
        java.util.Set<Long> branches = new java.util.LinkedHashSet<>();
        if (movement.getSourceStore() != null && movement.getSourceStore().getLocation() != null) {
            branches.add(movement.getSourceStore().getLocation().getId());
        }
        if (movement.getDestStore() != null && movement.getDestStore().getLocation() != null) {
            branches.add(movement.getDestStore().getLocation().getId());
        }
        if (movement.getSourceUser() != null && movement.getSourceUser().getBranch() != null) {
            branches.add(movement.getSourceUser().getBranch().getId());
        }
        if (movement.getRecipientUser() != null && movement.getRecipientUser().getBranch() != null) {
            branches.add(movement.getRecipientUser().getBranch().getId());
        }
        branches.remove(null);
        return branches;
    }

    /**
     * Refuses unless this movement touches a branch the caller may act on.
     *
     * <p>Applied to the lifecycle actions — dispatch, in-transit, receive, complete, cancel — and to
     * the detail read, because each of those acts on a movement someone names by id. Before this,
     * the route guard checked that the caller held {@code DISPATCH_MOVEMENT} and nothing afterwards
     * asked whose stock was moving: a branch storekeeper could dispatch another branch's consignment
     * by id, having merely been unable to find it in the listing.
     *
     * <p><b>Not</b> applied to {@code createMovement}. Issuance, asset return and consignment
     * fulfilment all create movements on the system's behalf and legitimately span two branches — a
     * Head Office issuer sending stock to a branch is the ordinary case, not an intrusion. The
     * actions that follow are where a person acts on a movement, and that is where the check
     * belongs.
     */
    private void requireMovementInScope(Movement movement, pride.bank.erp.access.ScopeAction action) {
        java.util.Set<Long> branches = branchesTouchedBy(movement);
        java.util.Set<Long> people = peoplePartyTo(movement);
        if (action == pride.bank.erp.access.ScopeAction.MANAGE) {
            accessScopeService.requireCanManageAnyOf(
                    pride.bank.erp.access.ScopeSubject.MOVEMENTS, branches, people);
        } else {
            accessScopeService.requireCanViewAnyOf(
                    pride.bank.erp.access.ScopeSubject.MOVEMENTS, branches, people);
        }
    }

    /**
     * Everyone party to a movement — what "mine" means at SELF scope.
     *
     * <p>A movement has no owner column, so being on one is a union: whoever raised it, whoever it
     * left, whoever it is going to, whoever signed for it, and whoever asked for the item it carries.
     *
     * <p><b>Mirrors {@code MovementSearchDao}'s SELF predicate exactly.</b> If the two ever answer
     * differently, the register shows a row the detail page refuses, or hides one it would have
     * opened — which is the whole failure this scoping exists to avoid.
     */
    private static java.util.Set<Long> peoplePartyTo(Movement movement) {
        java.util.Set<Long> people = new java.util.LinkedHashSet<>();
        if (movement.getInitiator() != null) people.add(movement.getInitiator().getId());
        if (movement.getSourceUser() != null) people.add(movement.getSourceUser().getId());
        if (movement.getRecipientUser() != null) people.add(movement.getRecipientUser().getId());
        if (movement.getReceivingOfficer() != null) people.add(movement.getReceivingOfficer().getId());
        if (movement.getRequest() != null && movement.getRequest().getRequester() != null) {
            people.add(movement.getRequest().getRequester().getId());
        }
        people.remove(null);
        return people;
    }

    /**
     * The movements raised to fulfil one request.
     *
     * <p>Guarded on {@code READ_MOVEMENT} at the endpoint but previously not scoped, so a request id
     * was enough to read another branch's movements — the same by-id gap the single read and the
     * lifecycle actions had. Each row is checked on its own ends, because a fulfilment movement
     * legitimately runs between two branches and the caller need only be at one of them.
     */
    @Transactional(readOnly = true)
    public List<Movement> findByRequest(Long requestId) {
        return movementRepository.findByRequest_Id(requestId).stream()
                .filter(m -> accessScopeService.canViewAnyOf(
                        pride.bank.erp.access.ScopeSubject.MOVEMENTS,
                        branchesTouchedBy(m), peoplePartyTo(m)))
                .toList();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    /** INTER when the destination is in a different location from the source; INTRA otherwise. */
    private MovementCategory deriveCategory(Store source, User sourceUser, Store dest, User recipient) {
        // A return has no source store, so the location it leaves from is the holder's branch.
        Long sourceLoc = null;
        if (source != null && source.getLocation() != null) {
            sourceLoc = source.getLocation().getId();
        } else if (sourceUser != null && sourceUser.getBranch() != null) {
            sourceLoc = sourceUser.getBranch().getId();
        }
        Long destLoc = null;
        if (dest != null && dest.getLocation() != null) {
            destLoc = dest.getLocation().getId();
        } else if (recipient != null && recipient.getBranch() != null) {
            destLoc = recipient.getBranch().getId();
        }
        boolean interLocation = sourceLoc != null && destLoc != null && !sourceLoc.equals(destLoc);
        return interLocation ? MovementCategory.INTER_LOCATION : MovementCategory.INTRA_LOCATION;
    }

    private User resolveUser(Long userId) throws NotFoundException {
        if (userId == null) return null;
        return userRepository.findById(userId)
                .orElseThrow(() -> new NotFoundException("User " + userId + " not found"));
    }

    private Branch resolveBranch(Long branchId) throws NotFoundException {
        if (branchId == null) return null;
        return branchRepository.findById(branchId)
                .orElseThrow(() -> new NotFoundException("Branch " + branchId + " not found"));
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

    /**
     * The branch an asset was last sent away from, for repair or for disposal.
     *
     * <h2>Two sources, because the two flows record different things</h2>
     * A repair transfer captures {@code originLocation} deliberately — snapshotted before the
     * assignment is closed, and the column the return leg aims at. <b>A disposal captures nothing:</b>
     * {@code disposeAsset} never sets it. So this falls back to the movement's <em>source store's</em>
     * location, which both builders always set.
     *
     * <p>Measured across the twelve assets currently away from home: the two agree on every repair,
     * and the source store is the only signal for a disposal. One asset has neither, because it has
     * no transfer movement at all — nothing ever recorded where it came from, and no rule can
     * recover it.
     *
     * <p>Answers where the asset <em>came from</em> only. Whether that should widen anybody's reach
     * is the asset's question, and {@code AssetService} asks it — see the status gate there.
     *
     * @return the branch id, or null when this asset has never been sent away
     */
    @Transactional(readOnly = true)
    public Long branchLastSentAwayFrom(Long assetId) {
        if (assetId == null) return null;

        return movementRepository
                .findFirstByItems_Asset_IdAndMovementTypeInOrderByIdDesc(
                        assetId,
                        java.util.List.of(MovementType.REPAIR_TRANSFER, MovementType.DISPOSAL_TRANSFER))
                .map(movement -> {
                    pride.bank.erp.branch.Branch origin = movement.getOriginLocation();
                    if (origin != null) return origin.getId();

                    // Disposal records no origin; the store it left is the next best answer, and it
                    // is always set.
                    return movement.getSourceStore() != null && movement.getSourceStore().getLocation() != null
                            ? movement.getSourceStore().getLocation().getId()
                            : null;
                })
                .orElse(null);
    }

}
