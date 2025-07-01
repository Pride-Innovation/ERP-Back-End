package pride.bank.erp.grnReport;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;
import pride.bank.erp.exception.NotFoundException;
import pride.bank.erp.helper.FileUpload;
import pride.bank.erp.stock.Stock;
import pride.bank.erp.stock.StockService;

import java.io.IOException;

@Service
@RequiredArgsConstructor
public class GrnReportService {
    private final FileUpload fileUpload;
    private final StockService stockService;
    private final GrnReportRepository grnReportRepository;

    public GrnReport uploadGoodReceivedNote(MultipartFile file, Long stockId, String name)
            throws IOException, NotFoundException {
        String filePath = fileUpload.uploadFileToLocalDirectory(file);
        Stock stock = stockService.findOneStock(stockId);

        GrnReport report = GrnReport
                .builder()
                .documentPath(filePath)
                .name(name)
                .stock(stock)
                .build();
        return grnReportRepository.save(report);
    }
}
