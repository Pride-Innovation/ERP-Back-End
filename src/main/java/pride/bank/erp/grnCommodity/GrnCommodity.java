package pride.bank.erp.grnCommodity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.grnReport.GrnReport;


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
    private Integer costPrice;
    private Integer purchasePrice;
}
