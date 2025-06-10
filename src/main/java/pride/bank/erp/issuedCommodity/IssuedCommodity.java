package pride.bank.erp.issuedCommodity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.issuance.Issuance;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IssuedCommodity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "issuance_id", nullable = false)
    private Issuance issuance;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "commodity_id", nullable = false)
    private Commodity commodity;

    private int quantity;
}
