package pride.bank.erp.grnReport;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.io.IOException;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class GrnReportController {

    private final GrnReportService grnReportService;

    @PostMapping("/upload-grn/{grnReportId}")
    public ResponseEntity<GrnReport> uploadGoodReceivedNote(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @PathVariable Long grnReportId
    ) throws NotFoundException, IOException {
        return new ResponseEntity<>(
                grnReportService.uploadGoodReceivedNote(file, grnReportId),
                HttpStatus.CREATED
        );
    }

    @PutMapping("/download-grn/{grnReportId}")
    public ResponseEntity<GrnReport> downloadGRN(
            @PathVariable Long grnReportId
    ) throws NotFoundException {
        return new ResponseEntity<>(
                grnReportService.downloadGRN(grnReportId), HttpStatus.CREATED
        );
    }
}
