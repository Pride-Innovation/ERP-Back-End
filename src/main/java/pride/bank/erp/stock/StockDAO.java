package pride.bank.erp.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.grnReport.GrnReport;
import pride.bank.erp.status.Status;
import pride.bank.erp.supplier.Supplier;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StockDAO {
    private Long id;
    private String referenceNumber;
    private String name;
    private BigDecimal totalCost;
    private BigDecimal balanceCost;
    private String grnNumber;
    private String lpoNumber;
    private String poNumber;
    private LocalDateTime orderDate;
    private LocalDateTime deliveryDate;
    private LocalDateTime invoiceDate;
    private Status status;
    private Branch branch;
    private Supplier supplier;
    private LocalDateTime createDate;
    private LocalDateTime lastModified;
    private Long createdBy;
    /** The creator's name, filled for the listing - the report's "Stocked By" column. */
    private String createdByName;
    private Long lastModifiedBy;
    private List<CommodityWithDetailsDao> commodities;
    private List<GrnReport> grnReports;
    private Boolean canNotBeDeleted;
}
