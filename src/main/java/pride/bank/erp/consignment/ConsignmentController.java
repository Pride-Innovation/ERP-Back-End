package pride.bank.erp.consignment;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.movement.Movement;

import java.util.List;
import java.util.Map;

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
        return new ResponseEntity<>(detail(consignmentService.findConsignment(consignmentId)), HttpStatus.OK);
    }

    @PreAuthorize("hasAuthority('" + Constants.READ_MOVEMENT + "')")
    @GetMapping("/consignments")
    public ResponseEntity<Page<ConsignmentResponseDTO>> list(
            @RequestParam(defaultValue = "0") int pageNumber,
            @RequestParam(defaultValue = "20") int pageSize,
            @RequestParam(required = false) ConsignmentStatus status,
            @RequestParam(required = false) Long branchId
    ) {
        var pageable = PageRequest.of(pageNumber, pageSize, Sort.by(Sort.Direction.DESC, "id"));
        Page<Consignment> page = (status != null)
                ? consignmentRepository.findByStatus(status, pageable)
                : (branchId != null)
                        ? consignmentRepository.findForBranch(branchId, pageable)
                        : consignmentRepository.findAll(pageable);
        // Counts rather than contents: a page of journeys should not fan out into every movement's
        // item lines just to render a list.
        return new ResponseEntity<>(
                page.map(c -> mapper.toSummary(c, consignmentService.movementsOn(c.getId()).size())),
                HttpStatus.OK);
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
