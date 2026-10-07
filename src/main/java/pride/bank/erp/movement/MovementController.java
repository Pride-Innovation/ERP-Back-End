package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class MovementController {

    private final MovementService movementService;
    private final MovementApprovalService movementApprovalService;
    private final MovementApprovalRecordRepository movementApprovalRecordRepository;
    private final MovementRepository movementRepository;
    private final MovementMapper movementMapper;
    private final pride.bank.erp.request.RequestService requestService;
    private final pride.bank.erp.config.CurrentUserService currentUserService;
    private final pride.bank.erp.access.AccessScopeService accessScopeService;

    @PreAuthorize("hasAuthority('" + Constants.CREATE_MOVEMENT + "')")
    @PostMapping("/movements")
    public ResponseEntity<MovementResponseDTO> createMovement(@RequestBody MovementDTO dto) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(movementService.createMovement(dto)), HttpStatus.CREATED);
    }

    /** Approve the current tier of a standalone movement's approval ladder. */
    @PreAuthorize("hasAuthority('" + Constants.APPROVE_MOVEMENT + "')")
    @PostMapping("/movements/{movementId}/approve")
    public ResponseEntity<MovementResponseDTO> approveMovement(
            @PathVariable Long movementId,
            @RequestBody(required = false) Map<String, String> body
    ) throws NotFoundException {
        String comment = (body != null) ? body.get("comment") : null;
        return new ResponseEntity<>(
                movementMapper.toDto(movementApprovalService.approve(movementId, comment)), HttpStatus.OK);
    }

    /** Reject a standalone movement pending approval — cancels it and notifies the initiator. */
    @PreAuthorize("hasAuthority('" + Constants.APPROVE_MOVEMENT + "')")
    @PostMapping("/movements/{movementId}/reject")
    public ResponseEntity<MovementResponseDTO> rejectMovement(
            @PathVariable Long movementId,
            @RequestBody(required = false) Map<String, String> body
    ) throws NotFoundException {
        String comment = (body != null) ? body.get("comment") : null;
        return new ResponseEntity<>(
                movementMapper.toDto(movementApprovalService.reject(movementId, comment)), HttpStatus.OK);
    }

    /**
     * The caller's movement-approval inbox: movements in DRAFT routed to them.
     *
     * <p>An inbox of decisions you are expected to make, so it answers to APPROVE_MOVEMENT rather
     * than READ_MOVEMENT.
     *
     * <h2>It is now the caller's own inbox</h2>
     * The approver stays a path variable because the front end and the dashboard both address it
     * that way, but asking for <em>someone else's</em> is refused. Before this it was not, and the
     * consequence was larger than reading a colleague's to-do list: this queries the repository
     * directly, with **no branch scoping**, so anyone holding APPROVE_MOVEMENT could name any
     * approver and read DRAFT movements from any branch — walking straight around the scope the
     * movements listing applies.
     *
     * <p>The exception is the explicit cross-branch grant, which is what an override is. This mirrors
     * the approval action itself, which had the same shape of fault: it attributed the decision to
     * whatever approver id the client sent.
     */
    @PreAuthorize("hasAuthority('" + Constants.APPROVE_MOVEMENT + "')")
    @GetMapping("/movements/pending-approval/{approverId}")
    public ResponseEntity<Page<MovementResponseDTO>> pendingApprovals(
            @PathVariable Long approverId,
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber
    ) {
        requireOwnInbox(approverId);
        Pageable pageable = page(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        return new ResponseEntity<>(
                movementRepository.findByCurrentApprover_IdAndStatus(approverId, MovementStatus.DRAFT, pageable)
                        .map(movementMapper::toDto),
                HttpStatus.OK);
    }

    /** Attach a delivery/release document (e.g. signed HO release note) to a movement. */
    @PreAuthorize("hasAuthority('" + Constants.CREATE_MOVEMENT + "')")
    @PostMapping(value = "/movements/{movementId}/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MovementResponseDTO> attachDocument(
            @PathVariable Long movementId,
            @RequestParam("file") MultipartFile file
    ) throws NotFoundException {
        return new ResponseEntity<>(
                movementMapper.toDto(movementService.attachDocument(movementId, file)), HttpStatus.OK);
    }

    /**
     * Upload a document ahead of movement creation and get back its stored path, for flows that must
     * carry documents in the create payload (e.g. an EXTERNAL repair transfer's dispatch documents).
     */
    @PreAuthorize("hasAuthority('" + Constants.CREATE_MOVEMENT + "')")
    @PostMapping(value = "/movements/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadStandaloneDocument(
            @RequestParam("file") MultipartFile file
    ) {
        return new ResponseEntity<>(
                Map.of("path", movementService.uploadStandaloneDocument(file)),
                HttpStatus.CREATED);
    }

    /**
     * Movement approval audit trail (pending / approved / rejected per tier).
     *
     * <p>Mapped to a DTO rather than returned as entities: each record holds a LAZY movement, and
     * initialising that proxy while writing the response exceeded the database's 61-table join limit.
     */
    @PreAuthorize("hasAuthority('" + Constants.READ_MOVEMENT + "')")
    @GetMapping("/movements/{movementId}/approvals")
    public ResponseEntity<List<MovementApprovalRecordDTO>> approvalTrail(@PathVariable Long movementId) {
        return new ResponseEntity<>(
                movementMapper.toApprovalDtoList(
                        movementApprovalRecordRepository.findByMovement_IdOrderByCreateDateAsc(movementId)),
                HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.READ_MOVEMENT + "')")
    @GetMapping("/movements/{movementId}")
    public ResponseEntity<MovementResponseDTO> getMovement(@PathVariable Long movementId) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(movementService.getMovementForViewing(movementId)), HttpStatus.OK);
    }

    /**
     * The movements register, filtered and paged by the server.
     *
     * <h2>Every filter is declared, deliberately</h2>
     * Spring drops a request parameter no endpoint names, without complaining, so a filter the page
     * sends to a signature that does not list it looks like it works and quietly returns the
     * unfiltered list. This endpoint previously declared only the two paging parameters while the
     * page carried an eleven-field filter bar — which is why that bar ran in the browser over the
     * first hundred rows instead.
     *
     * <p>{@code sortBy} / {@code sortDirection} are the table headers. Whatever they ask for, the DAO
     * appends {@code id} so a page boundary always falls in the same place.
     */
    @PreAuthorize("hasAuthority('" + Constants.READ_MOVEMENT + "')")
    @GetMapping("/movements")
    public ResponseEntity<Page<MovementResponseDTO>> listMovements(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false) MovementCategory movementCategory,
            @RequestParam(required = false) MovementStatus status,
            @RequestParam(required = false) ReceiptStatus receiptStatus,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String courier,
            @RequestParam(required = false) String initiator
    ) {
        MovementSearchCriteria criteria = criteria(dateFrom, dateTo, movementType, movementCategory,
                status, receiptStatus, trackingNumber, source, destination, courier, initiator);

        Pageable pageable = page(pageNumber, pageSize, sort(sortBy, sortDirection));
        return new ResponseEntity<>(
                movementService.listMovements(criteria, pageable).map(movementMapper::toDto),
                HttpStatus.OK);
    }

    /**
     * Status totals for the same filters, so the page's tiles and its heading agree.
     *
     * <p>Separate from the listing rather than folded into it because the tiles must not change as
     * the user pages: they describe the whole matching set, not the page in front of them.
     */
    @PreAuthorize("hasAuthority('" + Constants.READ_MOVEMENT + "')")
    @GetMapping("/movements/status-counts")
    public ResponseEntity<Map<MovementStatus, Long>> statusCounts(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) MovementType movementType,
            @RequestParam(required = false) MovementCategory movementCategory,
            @RequestParam(required = false) MovementStatus status,
            @RequestParam(required = false) ReceiptStatus receiptStatus,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String courier,
            @RequestParam(required = false) String initiator
    ) {
        return new ResponseEntity<>(
                movementService.countMovementsByStatus(criteria(dateFrom, dateTo, movementType,
                        movementCategory, status, receiptStatus, trackingNumber, source,
                        destination, courier, initiator)),
                HttpStatus.OK);
    }

    /**
     * The values the filter dropdowns should offer, from the whole scoped register.
     *
     * <p>They were derived in the browser from the loaded rows, so the options shrank to whatever
     * was on the page — a filter unable to offer a value it would have matched.
     */
    @PreAuthorize("hasAuthority('" + Constants.READ_MOVEMENT + "')")
    @GetMapping("/movements/filter-options")
    public ResponseEntity<MovementSearchDao.FilterOptions> filterOptions() {
        return new ResponseEntity<>(movementService.movementFilterOptions(), HttpStatus.OK);
    }

    private static MovementSearchCriteria criteria(
            LocalDateTime dateFrom, LocalDateTime dateTo, MovementType movementType,
            MovementCategory movementCategory, MovementStatus status, ReceiptStatus receiptStatus,
            String trackingNumber, String source, String destination, String courier,
            String initiator) {
        return MovementSearchCriteria.builder()
                .dateFrom(dateFrom).dateTo(dateTo)
                .movementType(movementType).movementCategory(movementCategory)
                .status(status).receiptStatus(receiptStatus)
                .trackingNumber(trackingNumber)
                .source(source).destination(destination)
                .courier(courier).initiator(initiator)
                .build();
    }

    /**
     * The columns a movement listing may be sorted by.
     *
     * <p>A whitelist because {@code sortBy} reaches {@code root.get(...)} in the DAO: an unknown
     * field is an {@code IllegalArgumentException} and therefore a 500, from nothing worse than a
     * stale bookmark or a typo. Criteria paths are not SQL, so this is not an injection risk — it is
     * a robustness one, and a listing should not fall over because it was asked for an ordering it
     * does not have.
     */
    private static final java.util.Set<String> SORTABLE = java.util.Set.of(
            "id", "createDate", "status", "movementType", "movementCategory",
            "receiptStatus", "dispatchDate", "expectedDeliveryDate");

    /**
     * How many rows one request may ask for.
     *
     * <p>The exports legitimately ask for thousands, so the cap is generous rather than tight; what
     * it prevents is a single request pulling the whole register into memory — an unbounded
     * {@code pageSize} on a growing table is a slow outage waiting for the row count to arrive.
     */
    private static final int MAX_PAGE_SIZE = 5000;

    /** Newest first unless the table asks otherwise; the DAO adds the id tie-break either way. */
    private static Sort sort(String sortBy, String sortDirection) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.ASC : Sort.Direction.DESC;

        // An unrecognised column falls back to newest-first rather than failing: the caller asked for
        // an ordering that does not exist, which is not a reason to refuse them the register.
        if (sortBy == null || sortBy.isBlank() || !SORTABLE.contains(sortBy)) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        return Sort.by(direction, sortBy);
    }

    /**
     * Refuses a request for another person's approval inbox.
     *
     * <p>Permissive for the holder of {@code MANAGE_ALL_BRANCH_MOVEMENTS}, the explicit "may act on
     * any branch's movements" grant — which the super administrator holds like anyone else.
     */
    private void requireOwnInbox(Long approverId) {
        pride.bank.erp.user.User current = currentUserService.getCurrentUser();
        Long callerId = current != null ? current.getId() : null;

        if (callerId != null && callerId.equals(approverId)) return;
        if (accessScopeService.scopeFor(pride.bank.erp.access.ScopeSubject.MOVEMENTS,
                pride.bank.erp.access.ScopeAction.MANAGE) == pride.bank.erp.dashboard.DashboardScope.ALL) {
            return;
        }
        throw new org.springframework.security.access.AccessDeniedException(
                "You can only read your own approval inbox.");
    }

    /** Clamped so one request cannot ask for the whole table. */
    private static Pageable page(int pageNumber, int pageSize, Sort sort) {
        return PageRequest.of(
                Math.max(0, pageNumber),
                Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE),
                sort);
    }

    /**
     * The movements fulfilling one request — "where are my items?".
     *
     * <h2>Why this asks about the request, not about movements</h2>
     * It required {@code READ_MOVEMENT}, which most requesters do not hold: measured on live data,
     * seventeen of twenty-three accounts could raise a request but not see it being fulfilled. The
     * panel on the request detail page therefore answered 403 to the very person who raised it.
     *
     * <p>Granting them {@code READ_MOVEMENT} would have been much the larger change — it opens the
     * whole movements register — so the question is asked the other way round: <b>may you read this
     * request?</b> {@code requireReadableById} is the same rule the request's own page uses, so it
     * already admits the requester, an approver the workflow routed it to, and an issuer fulfilling
     * it, and nobody else.
     *
     * <p>{@code findByRequest} still narrows each row on its own ends and parties, so holding
     * {@code READ_MOVEMENT} does not widen what comes back here either.
     */
    @PreAuthorize("hasAnyAuthority('" + Constants.READ_REQUEST + "', '"
            + Constants.READ_MOVEMENT + "')")
    @GetMapping("/movements/by-request/{requestId}")
    public ResponseEntity<List<MovementResponseDTO>> listByRequest(@PathVariable Long requestId)
            throws NotFoundException {
        requestService.requireReadableById(requestId);
        return new ResponseEntity<>(movementMapper.toDtoList(movementService.findByRequest(requestId)), HttpStatus.OK);
    }

    /**
     * The tracking number this movement travels under, issued if it has none - see
     * {@link MovementService#reserveTrackingNumber}. Called as the dispatch form opens, because the
     * release note it prints carries the number and is signed before Dispatch.
     */
    @PreAuthorize("hasAuthority('" + Constants.DISPATCH_MOVEMENT + "')")
    @PostMapping("/movements/{movementId}/tracking-number")
    public ResponseEntity<java.util.Map<String, String>> reserveTrackingNumber(@PathVariable Long movementId)
            throws NotFoundException {
        String number = movementService.reserveTrackingNumber(movementId);
        return ResponseEntity.ok(java.util.Collections.singletonMap("trackingNumber", number));
    }

    /** Dispatches an inter-location movement with courier details; the tracking number is issued (§9, §10). */
    @PreAuthorize("hasAuthority('" + Constants.DISPATCH_MOVEMENT + "')")
    @PostMapping("/movements/{movementId}/dispatch")
    public ResponseEntity<MovementResponseDTO> dispatchMovement(
            @PathVariable Long movementId,
            @RequestBody MovementDispatchDTO dto
    ) throws NotFoundException {
        return new ResponseEntity<>(
                movementMapper.toDto(movementService.dispatchMovement(movementId, dto)), HttpStatus.OK);
    }

    /** Optional in-transit marker for a dispatched inter-location movement. */
    @PreAuthorize("hasAuthority('" + Constants.DISPATCH_MOVEMENT + "')")
    @PostMapping("/movements/{movementId}/in-transit")
    public ResponseEntity<MovementResponseDTO> markInTransit(@PathVariable Long movementId) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(movementService.markInTransit(movementId)), HttpStatus.OK);
    }

    /** Receives a movement at its destination and applies its inventory effects (§10, §22). */
    @PreAuthorize("hasAuthority('" + Constants.RECEIVE_MOVEMENT + "')")
    @PostMapping("/movements/{movementId}/receive")
    public ResponseEntity<MovementResponseDTO> receiveMovement(
            @PathVariable Long movementId,
            @RequestBody(required = false) MovementReceiveDTO dto
    ) throws NotFoundException {
        return new ResponseEntity<>(
                movementMapper.toDto(movementService.receiveMovement(movementId, dto)), HttpStatus.OK);
    }

    /** Cancels a movement without applying any inventory effect. */
    @PreAuthorize("hasAuthority('" + Constants.CANCEL_MOVEMENT + "')")
    @PostMapping("/movements/{movementId}/cancel")
    public ResponseEntity<MovementResponseDTO> cancelMovement(
            @PathVariable Long movementId,
            @RequestBody(required = false) Map<String, String> body
    ) throws NotFoundException {
        String reason = (body != null) ? body.get("reason") : null;
        return new ResponseEntity<>(
                movementMapper.toDto(movementService.cancelMovement(movementId, reason)), HttpStatus.OK);
    }

    /**
     * Completes a movement and applies its inventory effects. Convenience for intra-location
     * movements; inter-location movements should use {@code /dispatch} then {@code /receive}.
     *
     * <p>Guarded by RECEIVE_MOVEMENT rather than a permission of its own: this is the intra-location
     * equivalent of receiving, and it lands the goods. Giving it a weaker permission would leave a
     * way to apply a movement's inventory effects without the authority to receive one.
     */
    @PreAuthorize("hasAuthority('" + Constants.RECEIVE_MOVEMENT + "')")
    @PostMapping("/movements/{movementId}/complete")
    public ResponseEntity<MovementResponseDTO> completeMovement(
            @PathVariable Long movementId,
            @RequestBody(required = false) Map<String, String> body
    ) throws NotFoundException {
        String remarks = (body != null) ? body.get("remarks") : null;
        return new ResponseEntity<>(
                movementMapper.toDto(movementService.completeMovement(movementId, remarks)), HttpStatus.OK);
    }
}
