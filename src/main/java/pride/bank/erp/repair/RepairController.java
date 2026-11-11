package pride.bank.erp.repair;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import pride.bank.erp.advice.WafStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.helper.Constants;

import java.time.LocalDateTime;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class RepairController {

    private final RepairService repairService;

    /*
     * There is deliberately no POST here any more.
     *
     * `POST /assets/repairs/{assetId}` used to book an asset in for repair from the assets page's
     * row menu. It set a status and wrote a maintenance record, and did nothing else: no movement,
     * so the asset never went anywhere; no custody hand-over, so the trail still said its user held
     * it; no approval; and no branch scope, so any holder of REPAIR_ASSET could book any asset in
     * the bank by id. Both the status ids it used were hardcoded and both named the wrong status.
     *
     * Sending an asset for repair is now `POST /movements/repair-transfer`, which moves it, takes
     * custody at the right moment, requires approval, scopes to the asset's branch - and opens the
     * maintenance record this endpoint used to write, so nothing is lost by its absence. It was
     * never used in anger: the table held 0 repair rows against 24 repair movements.
     *
     * The reads below and the PUT stay. Reading the history and closing a repair on the bench are
     * real and separate duties.
     */

    /**
     * Every repair across the estate — the maintenance report's source.
     *
     * <p>Distinct from {@code /assets/repairs/{assetId}} below, which answers only for one asset and
     * so could never support a report asking what has been repaired bank-wide.
     */
    @GetMapping(value = "/assets/repairs")
    public ResponseEntity<Page<RepairDTO>> listAllRepairs(
            @RequestParam(required = false, defaultValue = "50") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) Long assetTypeId,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime startDate,
            @RequestParam(required = false)
            @org.springframework.format.annotation.DateTimeFormat(iso = org.springframework.format.annotation.DateTimeFormat.ISO.DATE_TIME)
            java.time.LocalDateTime endDate,
            // The branch the repair belongs to (the one that sent it). Refused below ALL scope unless
            // it is the caller's own; the report only offers it to callers who see every branch.
            @RequestParam(required = false) Long branchId,
            // INTERNAL / EXTERNAL / NONE (no technician recorded yet).
            @RequestParam(required = false) String repairedBy
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize,
                // `id` breaks ties: two repairs booked the same instant otherwise page unpredictably.
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "repairStartDate", "id"));
        return new ResponseEntity<>(
                repairService.listAllRepairs(status, assetTypeId, startDate, endDate, branchId, repairedBy, pageable),
                HttpStatus.OK);
    }

    @GetMapping(value = "/assets/repairs/{assetId}")
    public ResponseEntity<Page<RepairDTO>> listRepairDetails(
            @PathVariable Long assetId,
            @RequestParam(required = false, defaultValue = "10") int pageSize,
            @RequestParam(required = false, defaultValue = "0") int pageNumber
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize);
        Page<RepairDTO> response = repairService.listRepairDetails(assetId, pageable);
        return new ResponseEntity<>(response, HttpStatus.OK);
    }

    @PutMapping(value = "/assets/repairs/{repairId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> completeRepair(
            @PathVariable Long repairId,
            @RequestParam(value = "repairEndDate") LocalDateTime repairEndDate,
            @RequestParam(value = "completionNotes", required = false) String completionNotes,
            @RequestParam(value = "completionDocuments", required = false) MultipartFile[] files,
            @RequestParam(value = "technicianType", required = false) TechnicianType technicianType,
            @RequestParam(value = "technicianUserId", required = false) Long technicianUserId,
            @RequestParam(value = "technicianConsultantId", required = false) Long technicianConsultantId
    ) throws pride.bank.erp.exception.NotFoundException {
        /*
         * Only the upload failure is caught here. This used to catch every exception into a 440, so
         * a refusal (AccessDeniedException) and a validation failure - now including "record who
         * repaired it" - both reached the user as "Error creating repair" and a server fault, rather
         * than as the refusal and the 400 the application's handlers already produce for them.
         */
        try {
            Repair repair = repairService.completeRepair(
                    repairId,
                    repairEndDate,
                    completionNotes,
                    files,
                    TechnicianSelectionDTO.builder()
                            .technicianType(technicianType)
                            .technicianUserId(technicianUserId)
                            .technicianConsultantId(technicianConsultantId)
                            .build());
            return ResponseEntity.status(HttpStatus.CREATED).body(repair);
        } catch (java.io.IOException ex) {
            return ResponseEntity.status(WafStatus.SERVER_FAULT)
                    .body("The completion documents could not be stored: " + ex.getMessage());
        }
    }

    /**
     * Records or corrects who worked on a repair, without closing it.
     *
     * <p>Under {@code /assets/repairs/**}, so the filter chain's PUT rule for that route -
     * {@code REPAIR_ASSET} - already governs it; no new matcher was needed.
     */
    @PutMapping(value = "/assets/repairs/{repairId}/technician", consumes = MediaType.APPLICATION_JSON_VALUE)
    public ResponseEntity<RepairDTO> assignTechnician(
            @PathVariable Long repairId,
            @RequestBody TechnicianSelectionDTO technician
    ) throws pride.bank.erp.exception.NotFoundException {
        return ResponseEntity.ok(repairService.assignTechnician(repairId, technician));
    }
}
