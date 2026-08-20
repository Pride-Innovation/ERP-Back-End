package pride.bank.erp.repair;

import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.asset.AssetDAO;
import pride.bank.erp.helper.Constants;

import java.time.LocalDateTime;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class RepairController {

    private final RepairService repairService;

    @PostMapping(value = "/assets/repairs/{assetId}", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createRepair(
            @PathVariable Long assetId,
            @RequestParam("repairStartDate") LocalDateTime repairStartDate,
            @RequestParam(value = "repairEndDate", required = false) LocalDateTime repairEndDate,
            @RequestParam("technician") String technician,
            @RequestParam("repairReason") String repairReason,
            @RequestParam(value = "documents", required = false) MultipartFile[] files
    ) {
        try {
            AssetDAO response = repairService.createRepair(
                    assetId,
                    repairStartDate,
                    repairEndDate,
                    technician,
                    repairReason,
                    files);
            return ResponseEntity.status(HttpStatus.CREATED).body(response);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating repair: " + ex.getMessage());
        }
    }

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
            java.time.LocalDateTime endDate
    ) {
        Pageable pageable = PageRequest.of(pageNumber, pageSize,
                org.springframework.data.domain.Sort.by(org.springframework.data.domain.Sort.Direction.DESC, "repairStartDate"));
        return new ResponseEntity<>(
                repairService.listAllRepairs(status, assetTypeId, startDate, endDate, pageable), HttpStatus.OK);
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
            @RequestParam(value = "completionDocuments", required = false) MultipartFile[] files
    ) {
        try {
            Repair repair = repairService.completeRepair(
                    repairId,
                    repairEndDate,
                    completionNotes,
                    files);
            return ResponseEntity.status(HttpStatus.CREATED).body(repair);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating repair: " + ex.getMessage());
        }
    }
}
