package pride.bank.erp.consignment;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.movement.Movement;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Consignments — the physical journeys that movements ride on.
 *
 * <p>Assembly and lifecycle live here; a movement's own approval and hand-over stay on the movement
 * endpoints. Nothing here completes a movement: arrival lands the goods in the destination store,
 * and each recipient signs for their own items afterwards.
 */
@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class ConsignmentController {

    private final ConsignmentService consignmentService;
    private final ConsignmentRepository consignmentRepository;
    private final ConsignmentMapper mapper;

    @PreAuthorize("hasAuthority('" + Constants.CREATE_MOVEMENT + "')")
    @PostMapping("/consignments")
    public ResponseEntity<ConsignmentResponseDTO> create(@RequestBody ConsignmentDTO dto) throws NotFoundException {
        return new ResponseEntity<>(detail(consignmentService.createConsignment(dto)), HttpStatus.CREATED);
    }

    @PreAuthorize("hasAuthority('" + Constants.READ_MOVEMENT + "')")
    @GetMapping("/consignments/{consignmentId}")
    public ResponseEntity<ConsignmentResponseDTO> get(@PathVariable Long consignmentId) throws NotFoundException {
        // Reached by id, so the permission alone was never enough: it admitted the caller and nothing
        // afterwards asked whose journey it was.
        return new ResponseEntity<>(detail(consignmentService.findConsignmentInScope(consignmentId)),
                HttpStatus.OK);
    }

    /**
     * The consignments register, filtered and paged by the server.
     *
     * <h2>What this replaces</h2>
     * It declared two parameters and applied them in a ternary — {@code status != null ?
     * findByStatus : branchId != null ? findForBranch : findAll} — with three consequences:
     *
     * <ul>
     *   <li><b>Choosing a status silently discarded the branch.</b> This is not the
     *       undeclared-parameter trap: both were declared, documented and accepted, then dropped by
     *       the control flow, which looks even more correct from the outside.</li>
     *   <li><b>Choosing neither returned every consignment in the bank</b> to any holder of
     *       {@code READ_MOVEMENT}, and {@code branchId} came straight from the client, so a branch
     *       user could read another branch's journeys by asking for them. The scope is now
     *       server-resolved and the parameter is gone.</li>
     *   <li><b>The page's other eight filters had nowhere to go</b>, so they ran in the browser over
     *       the hundred rows it had fetched.</li>
     * </ul>
     */
    @PreAuthorize("hasAuthority('" + Constants.READ_MOVEMENT + "')")
    @GetMapping("/consignments")
    public ResponseEntity<Page<ConsignmentResponseDTO>> list(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) String sortBy,
            @RequestParam(required = false) String sortDirection,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) List<ConsignmentStatus> status,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String courier,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String timeliness,
            @RequestParam(required = false) String load
    ) {
        ConsignmentSearchCriteria criteria = criteria(dateFrom, dateTo, status, source, destination,
                courier, trackingNumber, plateNumber, timeliness, load);

        Page<Consignment> page = consignmentService.search(
                criteria, page(pageNumber, pageSize, sort(sortBy, sortDirection)));

        // Counts rather than contents: a page of journeys should not fan out into every movement's
        // item lines just to render a list.
        return new ResponseEntity<>(
                page.map(c -> mapper.toSummary(c, consignmentService.movementsOn(c.getId()).size())),
                HttpStatus.OK);
    }

    /**
     * Status totals for the same filters, so the tabs and the register agree.
     *
     * <p>Separate from the listing because the tabs describe the whole matching set rather than the
     * page in front of the user, and must not move as they page. Deliberately not narrowed by the
     * status itself, or every tab but the selected one would read zero.
     */
    @PreAuthorize("hasAuthority('" + Constants.READ_MOVEMENT + "')")
    @GetMapping("/consignments/status-counts")
    public ResponseEntity<Map<ConsignmentStatus, Long>> statusCounts(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateFrom,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime dateTo,
            @RequestParam(required = false) String source,
            @RequestParam(required = false) String destination,
            @RequestParam(required = false) String courier,
            @RequestParam(required = false) String trackingNumber,
            @RequestParam(required = false) String plateNumber,
            @RequestParam(required = false) String timeliness,
            @RequestParam(required = false) String load
    ) {
        return new ResponseEntity<>(
                consignmentService.countByStatus(criteria(dateFrom, dateTo, null, source, destination,
                        courier, trackingNumber, plateNumber, timeliness, load)),
                HttpStatus.OK);
    }

    /** The dropdown values, from the whole scoped register rather than the loaded page. */
    @PreAuthorize("hasAuthority('" + Constants.READ_MOVEMENT + "')")
    @GetMapping("/consignments/filter-options")
    public ResponseEntity<ConsignmentSearchDao.FilterOptions> filterOptions() {
        return new ResponseEntity<>(consignmentService.filterOptions(), HttpStatus.OK);
    }

    private static ConsignmentSearchCriteria criteria(
            LocalDateTime dateFrom, LocalDateTime dateTo, List<ConsignmentStatus> status, String source,
            String destination, String courier, String trackingNumber, String plateNumber,
            String timeliness, String load) {
        return ConsignmentSearchCriteria.builder()
                .dateFrom(dateFrom).dateTo(dateTo).statuses(status)
                .source(source).destination(destination).courier(courier)
                .trackingNumber(trackingNumber).plateNumber(plateNumber)
                .timeliness(timeliness).load(load)
                .build();
    }

    /**
     * The columns a consignment listing may be sorted by.
     *
     * <p>A whitelist because {@code sortBy} reaches {@code root.get(...)}: an unknown field is an
     * {@code IllegalArgumentException} and therefore a 500, from nothing worse than a stale bookmark.
     */
    private static final Set<String> SORTABLE = Set.of(
            "id", "createDate", "status", "dispatchDate", "expectedDeliveryDate", "arrivalDate");

    /** Generous enough for the exports, bounded so one request cannot pull the whole register. */
    private static final int MAX_PAGE_SIZE = 5000;

    private static Sort sort(String sortBy, String sortDirection) {
        Sort.Direction direction = "asc".equalsIgnoreCase(sortDirection)
                ? Sort.Direction.ASC : Sort.Direction.DESC;
        if (sortBy == null || sortBy.isBlank() || !SORTABLE.contains(sortBy)) {
            return Sort.by(Sort.Direction.DESC, "id");
        }
        return Sort.by(direction, sortBy);
    }

    private static Pageable page(int pageNumber, int pageSize, Sort sort) {
        return PageRequest.of(
                Math.max(0, pageNumber),
                Math.min(Math.max(1, pageSize), MAX_PAGE_SIZE),
                sort);
    }

    /**
     * Draft journeys already running this route, so a movement can join one instead of starting
     * another — which is the whole point of loading a van once.
     */
    @PreAuthorize("hasAuthority('" + Constants.READ_MOVEMENT + "')")
    @GetMapping("/consignments/open")
    public ResponseEntity<List<ConsignmentResponseDTO>> open(
            @RequestParam Long sourceLocationId,
            @RequestParam Long destLocationId
    ) {
        return new ResponseEntity<>(
                consignmentService.openConsignmentsFor(sourceLocationId, destLocationId).stream()
                        .map(c -> mapper.toSummary(c, consignmentService.movementsOn(c.getId()).size()))
                        .toList(),
                HttpStatus.OK);
    }

    // ── Assembly ─────────────────────────────────────────────────────────────

    @PreAuthorize("hasAuthority('" + Constants.CREATE_MOVEMENT + "')")
    @PostMapping("/consignments/{consignmentId}/movements/{movementId}")
    public ResponseEntity<ConsignmentResponseDTO> addMovement(
            @PathVariable Long consignmentId, @PathVariable Long movementId) throws NotFoundException {
        return new ResponseEntity<>(detail(consignmentService.addMovement(consignmentId, movementId)), HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.CREATE_MOVEMENT + "')")
    @DeleteMapping("/consignments/{consignmentId}/movements/{movementId}")
    public ResponseEntity<ConsignmentResponseDTO> removeMovement(
            @PathVariable Long consignmentId, @PathVariable Long movementId) throws NotFoundException {
        return new ResponseEntity<>(detail(consignmentService.removeMovement(consignmentId, movementId)), HttpStatus.OK);
    }

    // ── Journey ──────────────────────────────────────────────────────────────

    /**
     * The tracking number this load travels under, issued if it has none. The dispatch form calls it
     * as it opens, because the consignment note it prints carries the number and is signed before
     * Dispatch. Same permission as dispatching - it is the first step of it.
     */
    @PreAuthorize("hasAuthority('" + Constants.DISPATCH_MOVEMENT + "')")
    @PostMapping("/consignments/{consignmentId}/tracking-number")
    public ResponseEntity<java.util.Map<String, String>> reserveTrackingNumber(@PathVariable Long consignmentId)
            throws NotFoundException {
        String number = consignmentService.reserveTrackingNumber(consignmentId);
        return ResponseEntity.ok(java.util.Collections.singletonMap("trackingNumber", number));
    }

    @PreAuthorize("hasAuthority('" + Constants.DISPATCH_MOVEMENT + "')")
    @PostMapping("/consignments/{consignmentId}/dispatch")
    public ResponseEntity<ConsignmentResponseDTO> dispatch(
            @PathVariable Long consignmentId, @RequestBody ConsignmentDispatchDTO dto) throws NotFoundException {
        return new ResponseEntity<>(detail(consignmentService.dispatch(consignmentId, dto)), HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.DISPATCH_MOVEMENT + "')")
    @PostMapping("/consignments/{consignmentId}/in-transit")
    public ResponseEntity<ConsignmentResponseDTO> markInTransit(@PathVariable Long consignmentId)
            throws NotFoundException {
        return new ResponseEntity<>(detail(consignmentService.markInTransit(consignmentId)), HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.RECEIVE_MOVEMENT + "')")
    @PostMapping("/consignments/{consignmentId}/arrive")
    public ResponseEntity<ConsignmentResponseDTO> markArrived(@PathVariable Long consignmentId)
            throws NotFoundException {
        return new ResponseEntity<>(detail(consignmentService.markArrived(consignmentId)), HttpStatus.OK);
    }

    /**
     * Hands landed items to their recipients — several movements at once.
     *
     * <p>Each named movement is received exactly as it would be individually; this only saves the
     * branch from opening ten screens to clear one van. Anything not listed stays outstanding.
     */
    @PreAuthorize("hasAuthority('" + Constants.RECEIVE_MOVEMENT + "')")
    @PostMapping("/consignments/{consignmentId}/receive")
    public ResponseEntity<ConsignmentResponseDTO> receive(
            @PathVariable Long consignmentId, @RequestBody ConsignmentReceiveDTO dto) throws NotFoundException {
        return new ResponseEntity<>(detail(consignmentService.receive(consignmentId, dto)), HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.CANCEL_MOVEMENT + "')")
    @PostMapping("/consignments/{consignmentId}/cancel")
    public ResponseEntity<ConsignmentResponseDTO> cancel(
            @PathVariable Long consignmentId, @RequestBody(required = false) Map<String, String> body)
            throws NotFoundException {
        String reason = body != null ? body.get("reason") : null;
        return new ResponseEntity<>(detail(consignmentService.cancel(consignmentId, reason)), HttpStatus.OK);
    }

    private ConsignmentResponseDTO detail(Consignment consignment) {
        List<Movement> movements = consignmentService.movementsOn(consignment.getId());
        return mapper.toDto(consignment, movements);
    }
}
