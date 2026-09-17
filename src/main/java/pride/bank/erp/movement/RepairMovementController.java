package pride.bank.erp.movement;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

/**
 * Asset-lifecycle movement endpoints for repair, temporary replacement, return and disposal (§14-17).
 *
 * <h2>Why these answer to REPAIR_ASSET rather than CREATE_MOVEMENT</h2>
 * They are asset actions that happen to be implemented as movements - which is exactly the reasoning
 * already written against {@link #disposeAsset} for {@code DISPOSE_ASSET}. Repair had not been given
 * the same treatment, so sending an asset away to be fixed asked only "may you raise a movement".
 *
 * <p>The consequence was measurable rather than theoretical: <b>four accounts at Gulu and Mbarara
 * held {@code CREATE_MOVEMENT} without {@code REPAIR_ASSET}</b>, so they could send an asset for
 * repair through this modal and not through the assets page, and {@code REPAIR_ASSET} - which
 * governs four accounts - was walked around entirely by the route people actually use.
 *
 * <p>Nobody lost an action on the day this shipped: {@code RepairPermissionSeeder} grants
 * {@code REPAIR_ASSET} once to every role already holding {@code CREATE_MOVEMENT}, the same
 * continuity backfill the user-access split used. What happens to it after that is the
 * administrator's decision in Settings.
 *
 * <p>The previews take a <b>disjunction</b>, and must. They are read-only descriptions of what an
 * action would do, so gating them on {@code READ_MOVEMENT} alone would show the form to someone
 * entitled to perform the repair and then refuse to tell them where the asset is going - the
 * "renders a control whose endpoint then refuses it" failure, one screen further in.
 */
@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class RepairMovementController {

    private final RepairMovementService repairMovementService;
    private final MovementMapper movementMapper;

    /** §14: send a faulty asset to the Head Office IT store for repair. */
    @PreAuthorize("hasAuthority('" + Constants.REPAIR_ASSET + "')")
    @PostMapping("/movements/repair-transfer")
    public ResponseEntity<MovementResponseDTO> transferForRepair(@RequestBody RepairTransferDTO dto) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(repairMovementService.transferForRepair(dto)), HttpStatus.CREATED);
    }

    /** §15: issue a temporary replacement asset from the IT store to a user. */
    @PreAuthorize("hasAuthority('" + Constants.REPAIR_ASSET + "')")
    @PostMapping("/movements/temp-replacement")
    public ResponseEntity<MovementResponseDTO> issueTemporaryReplacement(@RequestBody TempReplacementDTO dto) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(repairMovementService.issueTemporaryReplacement(dto)), HttpStatus.CREATED);
    }

    /** §16: return a repaired asset to its originating location. */
    @PreAuthorize("hasAuthority('" + Constants.REPAIR_ASSET + "')")
    @PostMapping("/movements/return-after-repair")
    public ResponseEntity<MovementResponseDTO> returnAfterRepair(@RequestBody ReturnAfterRepairDTO dto) throws NotFoundException {
        return new ResponseEntity<>(movementMapper.toDto(repairMovementService.returnAfterRepair(dto)), HttpStatus.CREATED);
    }

    /**
     * §17: move an irreparable asset to the Disposal store.
     *
     * <p>Deliberately carries no {@code @PreAuthorize}. Disposal is gated by DISPOSE_ASSET in
     * {@code SecurityConfiguration} alongside the rest of the asset lifecycle, because it is an
     * asset action that happens to be implemented as a movement — the asset register is where
     * someone looks to ask who may write an asset off. Same for the preview below.
     * {@code AssetRouteAuthorizationTest} pins both.
     */
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

    @PreAuthorize("hasAnyAuthority('" + Constants.READ_MOVEMENT + "', '" + Constants.REPAIR_ASSET + "')")
    @GetMapping("/movements/repair-transfer/preview")
    public ResponseEntity<MovementFlowPreview> previewRepairTransfer(
            @RequestParam Long assetId,
            @RequestParam(required = false) pride.bank.erp.assetType.RepairDestination repairDestination
    ) throws NotFoundException {
        return new ResponseEntity<>(
                repairMovementService.previewRepairTransfer(assetId, repairDestination), HttpStatus.OK);
    }

    @PreAuthorize("hasAnyAuthority('" + Constants.READ_MOVEMENT + "', '" + Constants.REPAIR_ASSET + "')")
    @GetMapping("/movements/return-after-repair/preview")
    public ResponseEntity<MovementFlowPreview> previewReturnAfterRepair(@RequestParam Long assetId)
            throws NotFoundException {
        return new ResponseEntity<>(repairMovementService.previewReturnAfterRepair(assetId), HttpStatus.OK);
    }

    @GetMapping("/movements/disposal/preview")
    public ResponseEntity<MovementFlowPreview> previewDisposal(@RequestParam Long assetId)
            throws NotFoundException {
        return new ResponseEntity<>(repairMovementService.previewDisposal(assetId), HttpStatus.OK);
    }

    @PreAuthorize("hasAnyAuthority('" + Constants.READ_MOVEMENT + "', '" + Constants.REPAIR_ASSET + "')")
    @GetMapping("/movements/temp-replacement/preview")
    public ResponseEntity<MovementFlowPreview> previewTempReplacement(
            @RequestParam Long tempAssetId,
            @RequestParam Long recipientUserId
    ) throws NotFoundException {
        return new ResponseEntity<>(
                repairMovementService.previewTempReplacement(tempAssetId, recipientUserId), HttpStatus.OK);
    }
}
