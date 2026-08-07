package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

/** Asset-lifecycle movement endpoints for repair, temporary replacement, return and disposal (§14-17). */
@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class RepairMovementController {

    private final RepairMovementService repairMovementService;
    private final MovementMapper movementMapper;

    /** §14: send a faulty asset to the Head Office IT store for repair. */
    @PostMapping("/movements/repair-transfer")
    public ResponseEntity<MovementResponseDTO> transferForRepair(@RequestBody RepairTransferDTO dto) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(repairMovementService.transferForRepair(dto)), HttpStatus.CREATED);
    }

    /** §15: issue a temporary replacement asset from the IT store to a user. */
    @PostMapping("/movements/temp-replacement")
    public ResponseEntity<MovementResponseDTO> issueTemporaryReplacement(@RequestBody TempReplacementDTO dto) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(repairMovementService.issueTemporaryReplacement(dto)), HttpStatus.CREATED);
    }

    /** §16: return a repaired asset to its originating location. */
    @PostMapping("/movements/return-after-repair")
    public ResponseEntity<MovementResponseDTO> returnAfterRepair(@RequestBody ReturnAfterRepairDTO dto) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(repairMovementService.returnAfterRepair(dto)), HttpStatus.CREATED);
    }

    /** §17: move an irreparable asset to the Disposal store. */
    @PostMapping("/movements/disposal")
    public ResponseEntity<MovementResponseDTO> disposeAsset(@RequestBody DisposalDTO dto) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(repairMovementService.disposeAsset(dto)), HttpStatus.CREATED);
    }

    // ── Form previews (read-only) ────────────────────────────────────────────
    //
    // Each returns what the corresponding form should display before submitting: the resolved source
    // and destination, and whether the movement crosses a location boundary — which is what decides
    // if courier details are asked for. Derived here rather than in the browser so the form and the
    // write path cannot disagree.

    @GetMapping("/movements/repair-transfer/preview")
    public ResponseEntity<MovementFlowPreview> previewRepairTransfer(
            @RequestParam Long assetId,
            @RequestParam(required = false) pride.bank.erp.assetType.RepairDestination repairDestination
    ) throws NotFoundException {
        return new ResponseEntity<>(
                repairMovementService.previewRepairTransfer(assetId, repairDestination), HttpStatus.OK);
    }

    @GetMapping("/movements/return-after-repair/preview")
    public ResponseEntity<MovementFlowPreview> previewReturnAfterRepair(@RequestParam Long assetId)
            throws NotFoundException {
        return new ResponseEntity<>(repairMovementService.previewReturnAfterRepair(assetId), HttpStatus.OK);
    }

    @GetMapping("/movements/temp-replacement/preview")
    public ResponseEntity<MovementFlowPreview> previewTempReplacement(
            @RequestParam Long tempAssetId,
            @RequestParam Long recipientUserId
    ) throws NotFoundException {
        return new ResponseEntity<>(
                repairMovementService.previewTempReplacement(tempAssetId, recipientUserId), HttpStatus.OK);
    }
}
