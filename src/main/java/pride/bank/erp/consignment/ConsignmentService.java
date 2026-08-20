package pride.bank.erp.consignment;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.auditing.trail.AuditEventType;
import pride.bank.erp.auditing.trail.AuditModule;
import pride.bank.erp.auditing.trail.AuditSeverity;
import pride.bank.erp.branch.BranchRepository;
import pride.bank.erp.config.CurrentUserService;
import pride.bank.erp.courier.Courier;
import pride.bank.erp.courier.CourierService;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.inventory.InventoryService;
import pride.bank.erp.movement.Movement;
import pride.bank.erp.movement.MovementCategory;
import pride.bank.erp.movement.MovementReceiveDTO;
import pride.bank.erp.movement.MovementRepository;
import pride.bank.erp.movement.MovementService;
import pride.bank.erp.movement.MovementStatus;
import pride.bank.erp.movement.ReceiptStatus;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreService;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Runs the physical journey: assembling a load, handing it to a courier, tracking it, and landing it.
 *
 * <p>The movements riding on it keep their own approval and their own hand-over. This service never
 * completes a movement — arrival puts the goods in the destination store, and each recipient signs
 * for their own items afterwards.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConsignmentService {

    private final ConsignmentRepository consignmentRepository;
    private final MovementRepository movementRepository;
    private final BranchRepository branchRepository;
    private final StoreRepository storeRepository;
    private final StoreService storeService;
    private final CourierService courierService;
    private final InventoryService inventoryService;
    private final CurrentUserService currentUserService;
    /** Hand-overs delegate here so there is only ever one implementation of receiving a movement. */
    private final MovementService movementService;
    private final pride.bank.erp.auditing.trail.AuditService auditService;

    // ── Assembly ─────────────────────────────────────────────────────────────

    @Transactional
    public Consignment createConsignment(ConsignmentDTO dto) throws NotFoundException {
        Branch source = branch(dto.getSourceLocationId(), "Source location");
        Branch destination = branch(dto.getDestLocationId(), "Destination location");
        if (source.getId().equals(destination.getId())) {
            throw new IllegalArgumentException(
                    "A consignment carries goods between locations. Source and destination are both "
                            + source.getName() + ".");
        }

        Consignment consignment = consignmentRepository.save(Consignment.builder()
                .status(ConsignmentStatus.DRAFT)
                .sourceLocation(source)
                .destLocation(destination)
                .remarks(dto.getRemarks())
                .initiator(currentUserService.getCurrentUser())
                .build());

        // Reference derived from the id, so two people opening consignments at once cannot collide
        // on a sequence — the same approach GRN numbering uses.
        consignment.setReference(String.format("CNS/%d/%06d",
                LocalDateTime.now().getYear(), consignment.getId()));
        consignment = consignmentRepository.save(consignment);

        if (dto.getMovementIds() != null) {
            for (Long movementId : dto.getMovementIds()) {
                addMovement(consignment.getId(), movementId);
            }
        }
        log.info("Created consignment {} ({} → {})", consignment.getReference(),
                source.getName(), destination.getName());
        audit(AuditEventType.CREATED, "CONSIGNMENT_CREATED", consignment,
                "Opened consignment " + consignment.getReference() + " ("
                        + source.getName() + " → " + destination.getName() + ")",
                AuditSeverity.INFO);
        return findConsignment(consignment.getId());
    }

    /**
     * Loads a movement onto a journey.
     *
     * <p>The checks are what make a shared journey safe. A movement must be approved and not yet
     * travelling, must be going to this consignment's destination, and must not already be riding on
     * something else. Its source store may differ from its neighbours' — Admin and IT loading the
     * same van is normal — so only the location has to match.
     */
    @Transactional
    public Consignment addMovement(Long consignmentId, Long movementId) throws NotFoundException {
        Consignment consignment = findConsignment(consignmentId);
        requireDraft(consignment, "add movements to");

        Movement movement = movementRepository.findDetailById(movementId)
                .orElseThrow(() -> new NotFoundException("Movement " + movementId + " not found"));

        if (movement.getConsignment() != null
                && !movement.getConsignment().getId().equals(consignmentId)) {
            throw new IllegalArgumentException("Movement " + movementId + " is already on consignment "
                    + movement.getConsignment().getReference());
        }
        if (movement.getStatus() != MovementStatus.INITIATED) {
            throw new IllegalArgumentException("Movement " + movementId + " is " + movement.getStatus()
                    + ". Only an approved movement that has not yet been dispatched can be loaded.");
        }
        if (movement.getMovementCategory() != MovementCategory.INTER_LOCATION) {
            throw new IllegalArgumentException("Movement " + movementId
                    + " stays within one location, so it does not travel on a consignment.");
        }
        Long movementDest = destinationLocationId(movement);
        if (movementDest == null || !movementDest.equals(consignment.getDestLocation().getId())) {
            throw new IllegalArgumentException("Movement " + movementId
                    + " is not going to " + consignment.getDestLocation().getName() + ".");
        }
        Long movementSource = sourceLocationId(movement);
        if (movementSource != null && !movementSource.equals(consignment.getSourceLocation().getId())) {
            throw new IllegalArgumentException("Movement " + movementId + " leaves from a different "
                    + "location than this consignment.");
        }

        movement.setConsignment(consignment);
        movementRepository.save(movement);
        log.info("Loaded movement {} onto consignment {}", movementId, consignment.getReference());
        return findConsignment(consignmentId);
    }

    /** Takes a movement back off a draft journey, leaving it free to travel another way. */
    @Transactional
    public Consignment removeMovement(Long consignmentId, Long movementId) throws NotFoundException {
        Consignment consignment = findConsignment(consignmentId);
        requireDraft(consignment, "remove movements from");

        Movement movement = movementRepository.findDetailById(movementId)
                .orElseThrow(() -> new NotFoundException("Movement " + movementId + " not found"));
        if (movement.getConsignment() == null || !movement.getConsignment().getId().equals(consignmentId)) {
            throw new IllegalArgumentException("Movement " + movementId + " is not on this consignment");
        }
        movement.setConsignment(null);
        movementRepository.save(movement);
        return findConsignment(consignmentId);
    }

    // ── Journey ──────────────────────────────────────────────────────────────

    /**
     * Hands the load to a courier. Requires the same signed dispatch note a single movement does —
     * one note now covers the whole van rather than one per request.
     */
    @Transactional
    public Consignment dispatch(Long consignmentId, ConsignmentDispatchDTO dto) throws NotFoundException {
        Consignment consignment = findConsignment(consignmentId);
        requireDraft(consignment, "dispatch");

        List<Movement> movements = movementsOn(consignmentId);
        if (movements.isEmpty()) {
            throw new IllegalArgumentException("This consignment is empty — load at least one movement first.");
        }
        // The same bar a single movement has to clear. A van carrying several requests is the
        // stronger case for a plate number, not the weaker one — without it a load in transit cannot
        // be identified if it goes missing.
        if (dto.getPlateNumber() == null || dto.getPlateNumber().isBlank()) {
            throw new IllegalArgumentException("A vehicle plate number is required to dispatch");
        }
        if (dto.getDeliveryDocuments() == null || dto.getDeliveryDocuments().isEmpty()) {
            throw new IllegalArgumentException(
                    "At least one signed dispatch document is required. Generate the dispatch note, "
                            + "get it signed by the courier, and upload it first.");
        }

        Courier courier = courierService.resolveForDispatch(dto.getCourierId(), dto.getCourierName());

        consignment.setCourier(courier);
        consignment.setCourierService(courier.getName());
        consignment.setPlateNumber(dto.getPlateNumber());
        consignment.setTrackingNumber(dto.getTrackingNumber());
        consignment.setDispatchDate(dto.getDispatchDate() != null ? dto.getDispatchDate() : LocalDateTime.now());
        consignment.setExpectedDeliveryDate(dto.getExpectedDeliveryDate());
        consignment.setDeliveryDocuments(new ArrayList<>(dto.getDeliveryDocuments()));
        consignment.setStatus(ConsignmentStatus.DISPATCHED);
        consignmentRepository.save(consignment);

        // Stamped down onto each movement as well. The per-movement detail view, the dispatch note
        // and the exports all read these fields, and a movement that showed no courier while sitting
        // in a van would be worse than duplicated data.
        for (Movement movement : movements) {
            movement.setCourier(courier);
            movement.setCourierService(courier.getName());
            movement.setPlateNumber(dto.getPlateNumber());
            movement.setTrackingNumber(dto.getTrackingNumber());
            movement.setDispatchDate(consignment.getDispatchDate());
            movement.setExpectedDeliveryDate(dto.getExpectedDeliveryDate());
            // Appended rather than replaced, for the same reason a solo dispatch appends: a movement
            // may already carry paperwork of its own (an EXTERNAL repair's signed vendor hand-over)
            // that the consignment's dispatch note does not supersede.
            MovementService.appendDeliveryDocuments(movement, dto.getDeliveryDocuments());
            movement.setStatus(MovementStatus.DISPATCHED);
            movementRepository.save(movement);
        }
        log.info("Dispatched consignment {} with {} movement(s) via {} (plate {})",
                consignment.getReference(), movements.size(), courier.getName(), dto.getPlateNumber());
        audit(AuditEventType.UPDATED, "CONSIGNMENT_DISPATCHED", consignment,
                "Dispatched consignment " + consignment.getReference() + " carrying "
                        + movements.size() + " movement(s) via " + courier.getName()
                        + (isBlank(dto.getPlateNumber()) ? "" : ", plate " + dto.getPlateNumber()),
                AuditSeverity.INFO);
        return findConsignment(consignmentId);
    }

    /**
     * Custody passes to the courier: every movement's stock leaves its source store and sits against
     * the courier until the load lands.
     */
    @Transactional
    public Consignment markInTransit(Long consignmentId) throws NotFoundException {
        Consignment consignment = findConsignment(consignmentId);
        if (consignment.getStatus() != ConsignmentStatus.DISPATCHED) {
            throw new IllegalStateException("Only a dispatched consignment can be marked in transit");
        }
        Store courierStore = courierStoreFor(consignment);

        for (Movement movement : movementsOn(consignmentId)) {
            if (!movement.isCustodyTransferSettled()) {
                inventoryService.applyCustodyHandoff(movement, courierStore);
                movement.setCustodyTransferSettled(true);
            }
            movement.setStatus(MovementStatus.IN_TRANSIT);
            movementRepository.save(movement);
        }
        consignment.setStatus(ConsignmentStatus.IN_TRANSIT);
        audit(AuditEventType.UPDATED, "CONSIGNMENT_IN_TRANSIT", consignment,
                "Consignment " + consignment.getReference()
                        + " in transit — custody transferred to " + courierStore.getName(),
                AuditSeverity.INFO);
        return consignmentRepository.save(consignment);
    }

    /**
     * The load lands. Stock moves from the courier into the destination's Admin store and stops
     * there — this is the branch receipt.
     *
     * <p>Movements are left {@code IN_TRANSIT} on purpose. Each one still has to reach its own
     * recipient, and completing them here would record a hand-over that has not happened. The
     * branch now sees a list of items to give out, each already tied to the request that asked for
     * it.
     */
    @Transactional
    public Consignment markArrived(Long consignmentId) throws NotFoundException {
        Consignment consignment = findConsignment(consignmentId);
        if (consignment.getStatus() != ConsignmentStatus.IN_TRANSIT) {
            throw new IllegalStateException(
                    "Only a consignment in transit can be marked as arrived (current: "
                            + consignment.getStatus() + ")");
        }
        Store courierStore = courierStoreFor(consignment);
        Store landingStore = storeService.resolveAdminStore(consignment.getDestLocation());

        for (Movement movement : movementsOn(consignmentId)) {
            inventoryService.applyConsignmentArrival(movement, courierStore, landingStore);
            movementRepository.save(movement);
        }

        consignment.setLandingStore(landingStore);
        consignment.setStatus(ConsignmentStatus.ARRIVED);
        consignment.setArrivalDate(LocalDateTime.now());
        consignment.setReceivingOfficer(currentUserService.getCurrentUser());
        log.info("Consignment {} arrived at {} — stock credited to {}", consignment.getReference(),
                consignment.getDestLocation().getName(), landingStore.getName());
        audit(AuditEventType.UPDATED, "CONSIGNMENT_ARRIVED", consignment,
                "Consignment " + consignment.getReference() + " arrived at "
                        + consignment.getDestLocation().getName() + " — stock credited to "
                        + landingStore.getName(),
                AuditSeverity.INFO);
        return consignmentRepository.save(consignment);
    }

    /**
     * Hands over landed items to their recipients — several movements in one action.
     *
     * <p>Delegates each line to {@code MovementService#receiveMovement} rather than reimplementing
     * the hand-over, so the ledger, the assignment effects and every guard behave exactly as they do
     * when a movement is received on its own. This is a convenience over that, not a second path
     * through it.
     *
     * <p>Already-completed movements are skipped rather than rejected: a branch working through a
     * large load in stages will inevitably re-submit one, and failing the whole batch for it would
     * be worse than useless.
     */
    @Transactional
    public Consignment receive(Long consignmentId, ConsignmentReceiveDTO dto) throws NotFoundException {
        Consignment consignment = findConsignment(consignmentId);
        if (consignment.getStatus() != ConsignmentStatus.ARRIVED) {
            throw new IllegalStateException(
                    "The goods have to arrive before they can be handed over. This consignment is "
                            + consignment.getStatus() + ".");
        }
        if (dto == null || dto.getMovements() == null || dto.getMovements().isEmpty()) {
            throw new IllegalArgumentException("Choose at least one movement to hand over.");
        }

        Map<Long, Movement> onBoard = movementsOn(consignmentId).stream()
                .collect(Collectors.toMap(Movement::getId, m -> m));

        int handedOver = 0;
        for (ConsignmentReceiveDTO.Line line : dto.getMovements()) {
            Movement movement = onBoard.get(line.getMovementId());
            if (movement == null) {
                throw new IllegalArgumentException("Movement " + line.getMovementId()
                        + " is not on consignment " + consignment.getReference() + ".");
            }
            if (movement.getStatus() == MovementStatus.COMPLETED) continue;

            ReceiptStatus receiptStatus = line.getReceiptStatus() != null
                    ? line.getReceiptStatus() : ReceiptStatus.RECEIVED_OK;
            if (receiptStatus != ReceiptStatus.RECEIVED_OK && isBlank(line.getRemarks())) {
                throw new IllegalArgumentException("Movement " + line.getMovementId()
                        + " is being received with a discrepancy, so it needs a note saying what was wrong.");
            }

            movementService.receiveMovement(movement.getId(), MovementReceiveDTO.builder()
                    .receiptStatus(receiptStatus)
                    .remarks(!isBlank(line.getRemarks()) ? line.getRemarks() : dto.getRemarks())
                    .build());
            handedOver++;
        }

        log.info("Handed over {} movement(s) from consignment {}", handedOver, consignment.getReference());
        // The individual hand-overs are each recorded by MovementService#finalizeCompletion. This row
        // is the batch itself, so the trail shows one branch session rather than only its parts.
        audit(AuditEventType.UPDATED, "CONSIGNMENT_HANDED_OVER", consignment,
                "Handed over " + handedOver + " movement(s) from consignment "
                        + consignment.getReference(),
                AuditSeverity.INFO);
        return findConsignment(consignmentId);
    }

    /** Movements on this journey that still have to reach someone. */
    @Transactional(readOnly = true)
    public List<Movement> outstandingOn(Long consignmentId) {
        return movementsOn(consignmentId).stream()
                .filter(m -> m.getStatus() != MovementStatus.COMPLETED
                        && m.getStatus() != MovementStatus.CANCELLED)
                .toList();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    /** Abandons a journey before it starts, releasing its movements to travel another way. */
    @Transactional
    public Consignment cancel(Long consignmentId, String reason) throws NotFoundException {
        Consignment consignment = findConsignment(consignmentId);
        if (consignment.getStatus() != ConsignmentStatus.DRAFT) {
            throw new IllegalStateException(
                    "Only a consignment that has not been dispatched can be cancelled. This one is "
                            + consignment.getStatus() + ".");
        }
        for (Movement movement : movementsOn(consignmentId)) {
            movement.setConsignment(null);
            movementRepository.save(movement);
        }
        consignment.setStatus(ConsignmentStatus.CANCELLED);
        if (reason != null && !reason.isBlank()) {
            consignment.setRemarks(reason);
        }
        audit(AuditEventType.DELETED, "CONSIGNMENT_CANCELLED", consignment,
                "Cancelled consignment " + consignment.getReference()
                        + (isBlank(reason) ? "" : " — " + reason),
                AuditSeverity.WARNING);
        return consignmentRepository.save(consignment);
    }

    /** Records a consignment event, tagged to the consignment itself. */
    private void audit(AuditEventType eventType, String action, Consignment consignment,
                       String description, AuditSeverity severity) {
        auditService.record(eventType, AuditModule.MOVEMENT, action, description, severity,
                "Consignment", consignment.getId());
    }

    // ── Reads ────────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public Consignment findConsignment(Long consignmentId) throws NotFoundException {
        return consignmentRepository.findDetailById(consignmentId)
                .orElseThrow(() -> new NotFoundException("Consignment " + consignmentId + " not found"));
    }

    @Transactional(readOnly = true)
    public List<Movement> movementsOn(Long consignmentId) {
        return movementRepository.findByConsignment_Id(consignmentId);
    }

    /**
     * Draft journeys already heading from here to there, so a new movement can join one instead of
     * starting another — the whole point of consolidating a van-load.
     */
    @Transactional(readOnly = true)
    public List<Consignment> openConsignmentsFor(Long sourceLocationId, Long destLocationId) {
        return consignmentRepository.findByStatusAndSourceLocation_IdAndDestLocation_Id(
                ConsignmentStatus.DRAFT, sourceLocationId, destLocationId);
    }

    // ── Helpers ──────────────────────────────────────────────────────────────

    private void requireDraft(Consignment consignment, String action) {
        if (consignment.getStatus() != ConsignmentStatus.DRAFT) {
            throw new IllegalStateException("Cannot " + action + " a consignment that is already "
                    + consignment.getStatus() + ".");
        }
    }

    private Store courierStoreFor(Consignment consignment) throws NotFoundException {
        if (consignment.getCourier() == null) {
            throw new IllegalStateException("Consignment " + consignment.getReference()
                    + " has no courier — dispatch it first");
        }
        return storeRepository.findByCourier_Id(consignment.getCourier().getId())
                .orElseThrow(() -> new NotFoundException(
                        "Courier " + consignment.getCourier().getId() + " has no custody store"));
    }

    private Branch branch(Long id, String label) throws NotFoundException {
        if (id == null) throw new IllegalArgumentException(label + " is required");
        return branchRepository.findById(id)
                .orElseThrow(() -> new NotFoundException(label + " " + id + " not found"));
    }

    /** A movement's destination location, whether it ends at a store or with a person. */
    private static Long destinationLocationId(Movement movement) {
        if (movement.getDestStore() != null && movement.getDestStore().getLocation() != null) {
            return movement.getDestStore().getLocation().getId();
        }
        if (movement.getRecipientUser() != null && movement.getRecipientUser().getBranch() != null) {
            return movement.getRecipientUser().getBranch().getId();
        }
        return null;
    }

    private static Long sourceLocationId(Movement movement) {
        if (movement.getSourceStore() != null && movement.getSourceStore().getLocation() != null) {
            return movement.getSourceStore().getLocation().getId();
        }
        if (movement.getSourceUser() != null && movement.getSourceUser().getBranch() != null) {
            return movement.getSourceUser().getBranch().getId();
        }
        return null;
    }
}
