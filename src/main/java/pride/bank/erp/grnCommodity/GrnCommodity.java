package pride.bank.erp.grnCommodity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.grnReport.GrnReport;

import java.math.BigDecimal;
import java.time.LocalDateTime;


@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class GrnCommodity {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "grn_report_id", nullable = false)
    private GrnReport grnReport;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "commodity_id", nullable = false)
    private Commodity commodity;

    private int deliveredQuantity;
    private int orderedQuantity;
    @Column(precision = 19, scale = 4)
    private BigDecimal costPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal purchasePrice;

    private int totalDeliveredQuantity;
    private int totalOrderedQuantity;
    @Column(precision = 19, scale = 4)
    private BigDecimal totalCostPrice;

    @Column(precision = 19, scale = 4)
    private BigDecimal totalPurchasePrice;
    @Builder.Default
    private Boolean disposed = false;
    private LocalDateTime disposalDate;
}
