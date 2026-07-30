package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

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

    @PostMapping("/movements")
    public ResponseEntity<MovementResponseDTO> createMovement(@RequestBody MovementDTO dto) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(movementService.createMovement(dto)), HttpStatus.CREATED);
    }

    /** Approve the current tier of a standalone movement's approval ladder. */
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
    @PostMapping("/movements/{movementId}/reject")
    public ResponseEntity<MovementResponseDTO> rejectMovement(
            @PathVariable Long movementId,
            @RequestBody(required = false) Map<String, String> body
    ) throws NotFoundException {
        String comment = (body != null) ? body.get("comment") : null;
        return new ResponseEntity<>(
                movementMapper.toDto(movementApprovalService.reject(movementId, comment)), HttpStatus.OK);
    }

    /** The current user's (or a given user's) movement-approval inbox: movements in DRAFT assigned to them. */
    @GetMapping("/movements/pending-approval/{approverId}")
    public ResponseEntity<Page<MovementResponseDTO>> pendingApprovals(
            @PathVariable Long approverId,
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        return new ResponseEntity<>(
                movementRepository.findByCurrentApprover_IdAndStatus(approverId, MovementStatus.DRAFT, pageable)
                        .map(movementMapper::toDto),
                HttpStatus.OK);
    }

    /** Attach a delivery/release document (e.g. signed HO release note) to a movement. */
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
    @PostMapping(value = "/movements/documents", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<Map<String, String>> uploadStandaloneDocument(
            @RequestParam("file") MultipartFile file
    ) {
        return new ResponseEntity<>(
                Map.of("path", movementService.uploadStandaloneDocument(file)),
                HttpStatus.CREATED);
    }

    /** Movement approval audit trail (pending / approved / rejected per tier). */
    @GetMapping("/movements/{movementId}/approvals")
    public ResponseEntity<List<MovementApprovalRecord>> approvalTrail(@PathVariable Long movementId) {
        return new ResponseEntity<>(
                movementApprovalRecordRepository.findByMovement_IdOrderByCreateDateAsc(movementId),
                HttpStatus.OK);
    }

    @GetMapping("/movements/{movementId}")
    public ResponseEntity<MovementResponseDTO> getMovement(@PathVariable Long movementId) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(movementService.findMovement(movementId)), HttpStatus.OK);
    }

    @GetMapping("/movements")
    public ResponseEntity<Page<MovementResponseDTO>> listMovements(
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        return new ResponseEntity<>(movementService.listMovements(pageable).map(movementMapper::toDto), HttpStatus.OK);
    }

    @GetMapping("/movements/by-request/{requestId}")
    public ResponseEntity<List<MovementResponseDTO>> listByRequest(@PathVariable Long requestId) {
        return new ResponseEntity<>(movementMapper.toDtoList(movementService.findByRequest(requestId)), HttpStatus.OK);
    }

    /** Dispatches an inter-location movement with courier/tracking details (§9, §10). */
    @PostMapping("/movements/{movementId}/dispatch")
    public ResponseEntity<MovementResponseDTO> dispatchMovement(
            @PathVariable Long movementId,
            @RequestBody MovementDispatchDTO dto
    ) throws NotFoundException {
        return new ResponseEntity<>(
                movementMapper.toDto(movementService.dispatchMovement(movementId, dto)), HttpStatus.OK);
    }

    /** Optional in-transit marker for a dispatched inter-location movement. */
    @PostMapping("/movements/{movementId}/in-transit")
    public ResponseEntity<MovementResponseDTO> markInTransit(@PathVariable Long movementId) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(movementService.markInTransit(movementId)), HttpStatus.OK);
    }

    /** Receives a movement at its destination and applies its inventory effects (§10, §22). */
    @PostMapping("/movements/{movementId}/receive")
    public ResponseEntity<MovementResponseDTO> receiveMovement(
            @PathVariable Long movementId,
            @RequestBody(required = false) MovementReceiveDTO dto
    ) throws NotFoundException {
        return new ResponseEntity<>(
                movementMapper.toDto(movementService.receiveMovement(movementId, dto)), HttpStatus.OK);
    }

    /** Cancels a movement without applying any inventory effect. */
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
     */
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
