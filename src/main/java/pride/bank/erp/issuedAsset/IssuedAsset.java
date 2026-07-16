package pride.bank.erp.issuedAsset;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.issuance.Issuance;

/**
 * Links an {@link Asset} to the {@link Issuance} it was handed out under — the asset-side
 * counterpart of {@link pride.bank.erp.issuedCommodity.IssuedCommodity}. Lets
 * {@link pride.bank.erp.movement.FulfillmentService} discover which assets (not just which
 * commodities) belong on a cross-location fulfilment movement.
 */
@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class IssuedAsset {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "issuance_id", nullable = false)
    private Issuance issuance;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_id", nullable = false)
    private Asset asset;
}
