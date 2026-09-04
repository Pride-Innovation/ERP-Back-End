package pride.bank.erp.grnReport;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.Constants;

import java.io.IOException;
import java.nio.file.Files;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class GrnReportController {

    private final GrnReportService grnReportService;

    /*
     * Both GRN routes sit outside INVENTORY_ROUTE (`/stocks/**`) and so matched no rule at all —
     * any signed-in user could attach a signed goods-received note to a delivery, or regenerate one.
     * A GRN is the document evidencing that stock arrived, so writing one is an inventory update.
     */
    @PreAuthorize("hasAuthority('" + Constants.UPDATE_INVENTORY + "')")
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

    @PreAuthorize("hasAuthority('" + Constants.UPDATE_INVENTORY + "')")
    @PutMapping("/download-grn/{grnReportId}")
    public ResponseEntity<GrnReport> downloadGRN(
            @PathVariable Long grnReportId
    ) throws NotFoundException {
        return new ResponseEntity<>(
                grnReportService.downloadGRN(grnReportId), HttpStatus.CREATED
        );
    }

    /**
     * Streams the uploaded (signed) GRN document behind authentication, replacing the previous
     * scheme where signed documents were public static assets in the web root.
     */
    @GetMapping("/grn-reports/{grnReportId}/document")
    public ResponseEntity<Resource> viewGrnDocument(
            @PathVariable Long grnReportId
    ) throws NotFoundException, IOException {
        Resource resource = grnReportService.loadDocument(grnReportId);

        String contentType = Files.probeContentType(resource.getFile().toPath());
        if (contentType == null) {
            contentType = MediaType.APPLICATION_OCTET_STREAM_VALUE;
        }

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(contentType))
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline; filename=\"" + resource.getFilename() + "\"")
                .body(resource);
    }
}
