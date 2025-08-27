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
            Repair repair = repairService.createRepair(
                    assetId,
                    repairStartDate,
                    repairEndDate,
                    technician,
                    repairReason,
                    files);
            return ResponseEntity.status(HttpStatus.CREATED).body(repair);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating repair: " + ex.getMessage());
        }
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
