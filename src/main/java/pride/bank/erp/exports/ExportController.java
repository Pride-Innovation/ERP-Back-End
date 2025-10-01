package pride.bank.erp.exports;

import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import pride.bank.erp.helper.Constants;
import pride.bank.erp.stock.StockDAO;

import java.time.LocalDateTime;
import java.util.List;

@RestController
@RequestMapping(Constants.GENERAL_ROUTE)
@RequiredArgsConstructor
public class ExportController {

    private final ExportService exportService;

    @GetMapping("/export/inventory")
    public ResponseEntity<List<StockDAO>> fetchAllStocks(
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime startDate,

            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME)
            LocalDateTime endDate
    ) {
        return ResponseEntity.ok(exportService.fetchAllStocks(
                startDate,
                endDate
        ));
    }
}
