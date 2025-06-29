package pride.bank.erp.stock;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.status.Status;
import pride.bank.erp.supplier.Supplier;

import java.time.LocalDateTime;
import java.util.List;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class StockResponseDTO {
    private Long id;
    private String referenceNumber;
    private String name;
    private int totalCost;
    private int balanceCost;
    private String grnNumber;
    private String lpoNumber;
    private Status status;
    private Branch branch;
    private Supplier supplier;
    private LocalDateTime createDate;
    private LocalDateTime lastModified;
    private Long createdBy;
    private Long lastModifiedBy;
    private List<CommodityWithDetailsDao> commodities;
}
