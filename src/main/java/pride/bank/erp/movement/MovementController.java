package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.util.List;
import java.util.Map;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class MovementController {

    private final MovementService movementService;

    @PostMapping("/movements")
    public ResponseEntity<Movement> createMovement(@RequestBody MovementDTO dto) throws NotFoundException {
        return new ResponseEntity<>(movementService.createMovement(dto), HttpStatus.CREATED);
    }

    @GetMapping("/movements/{movementId}")
    public ResponseEntity<Movement> getMovement(@PathVariable Long movementId) throws NotFoundException {
        return new ResponseEntity<>(movementService.findMovement(movementId), HttpStatus.OK);
    }

    @GetMapping("/movements")
    public ResponseEntity<Page<Movement>> listMovements(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        return new ResponseEntity<>(movementService.listMovements(pageable), HttpStatus.OK);
    }

    @GetMapping("/movements/by-request/{requestId}")
    public ResponseEntity<List<Movement>> listByRequest(@PathVariable Long requestId) {
        return new ResponseEntity<>(movementService.findByRequest(requestId), HttpStatus.OK);
    }

    /** Dispatches an inter-location movement with courier/tracking details (§9, §10). */
    @PostMapping("/movements/{movementId}/dispatch")
    public ResponseEntity<Movement> dispatchMovement(
            @PathVariable Long movementId,
            @RequestBody MovementDispatchDTO dto
    ) throws NotFoundException {
        return new ResponseEntity<>(movementService.dispatchMovement(movementId, dto), HttpStatus.OK);
    }

    /** Optional in-transit marker for a dispatched inter-location movement. */
    @PostMapping("/movements/{movementId}/in-transit")
    public ResponseEntity<Movement> markInTransit(@PathVariable Long movementId) throws NotFoundException {
        return new ResponseEntity<>(movementService.markInTransit(movementId), HttpStatus.OK);
    }

    /** Receives a movement at its destination and applies its inventory effects (§10, §22). */
    @PostMapping("/movements/{movementId}/receive")
    public ResponseEntity<Movement> receiveMovement(
            @PathVariable Long movementId,
            @RequestBody(required = false) MovementReceiveDTO dto
    ) throws NotFoundException {
        return new ResponseEntity<>(movementService.receiveMovement(movementId, dto), HttpStatus.OK);
    }

    /** Cancels a movement without applying any inventory effect. */
    @PostMapping("/movements/{movementId}/cancel")
    public ResponseEntity<Movement> cancelMovement(
            @PathVariable Long movementId,
            @RequestBody(required = false) Map<String, String> body
    ) throws NotFoundException {
        String reason = (body != null) ? body.get("reason") : null;
        return new ResponseEntity<>(movementService.cancelMovement(movementId, reason), HttpStatus.OK);
    }

    /**
     * Completes a movement and applies its inventory effects. Convenience for intra-location
     * movements; inter-location movements should use {@code /dispatch} then {@code /receive}.
     */
    @PostMapping("/movements/{movementId}/complete")
    public ResponseEntity<Movement> completeMovement(
            @PathVariable Long movementId,
            @RequestBody(required = false) Map<String, String> body
    ) throws NotFoundException {
        String remarks = (body != null) ? body.get("remarks") : null;
        return new ResponseEntity<>(movementService.completeMovement(movementId, remarks), HttpStatus.OK);
    }
}
