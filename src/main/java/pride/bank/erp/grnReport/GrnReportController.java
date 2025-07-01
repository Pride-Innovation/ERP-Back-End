package pride.bank.erp.grnReport;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.io.IOException;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class GrnReportController {

    private final GrnReportService grnReportService;

    @PostMapping("/upload-grn")
    public ResponseEntity<GrnReport> uploadGoodReceivedNote(
            @RequestParam(value = "file", required = false) MultipartFile file,
            @RequestParam("stockId") Long stockId,
            @RequestParam("name") String name
    ) throws NotFoundException, IOException {
        return new ResponseEntity<>(
                grnReportService.uploadGoodReceivedNote(file, stockId, name),
                HttpStatus.CREATED
        );
    }
}
