package pride.bank.erp.repair;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.helper.Constants;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class RepairController {

    private final RepairService repairService;

    @PostMapping(value = "/assets/{assetId}/repairs", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<?> createRepair(
            @PathVariable Long assetId,
            @RequestParam("repair") RepairDTO repairDTO,
            @RequestParam(value = "documents", required = false) MultipartFile[] files
    ) {
        try {
            Repair repair = repairService.createRepair(assetId, repairDTO, files);
            return ResponseEntity.status(HttpStatus.CREATED).body(repair);
        } catch (Exception ex) {
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                    .body("Error creating repair: " + ex.getMessage());
        }
    }
}
