package pride.bank.erp.grnReport;

import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.FileUpload;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class GrnReportService {
    private final FileUpload fileUpload;
    private final GrnReportRepository grnReportRepository;

    public GrnReport uploadGoodReceivedNote(
            MultipartFile file,
            Long grnReportID
    )
            throws IOException, NotFoundException {
        String filePath = fileUpload.uploadFileToLocalDirectory(file);
        GrnReport grnReport = findOneGRNReport(grnReportID);
        grnReport.setDocumentPath(filePath);
        return grnReportRepository.save(grnReport);
    }

    public GrnReport findOneGRNReport(Long id) throws NotFoundException {
        return grnReportRepository.findById(id).orElseThrow(
                () -> new NotFoundException(String.format("GRN Report with ID %s not found", id))
        );
    }

    public GrnReport downloadGRN(Long grnReportId) throws NotFoundException {
        GrnReport report = findOneGRNReport(grnReportId);
        report.setGrnDownloaded(true);
        return grnReportRepository.save(report);
    }

    /**
     * Loads the uploaded (signed) GRN document for authenticated streaming. Replaces the previous
     * scheme where documents were served as public static assets from the web root.
     */
    public Resource loadDocument(Long grnReportId) throws NotFoundException, IOException {
        GrnReport report = findOneGRNReport(grnReportId);
        if (report.getDocumentPath() == null || report.getDocumentPath().isBlank()) {
            throw new NotFoundException("No signed GRN document has been uploaded for report " + grnReportId);
        }
        return fileUpload.loadFileAsResource(report.getDocumentPath());
    }
}
