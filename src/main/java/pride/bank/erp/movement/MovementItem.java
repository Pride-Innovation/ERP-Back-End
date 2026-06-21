package pride.bank.erp.movement;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import org.hibernate.envers.Audited;
import org.hibernate.envers.NotAudited;
import pride.bank.erp.asset.Asset;
import pride.bank.erp.commodity.Commodity;

/**
 * A line on a {@link Movement}. Exactly one of {@link #asset} (a serialized asset, quantity 1) or
 * {@link #commodity} (a consumable, with {@link #quantity}) is set — enforced by the service.
 * {@link #serialNumber} / {@link #assetTag} are snapshots kept for audit (§18).
 */
@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Audited
@Table(name = "movement_item")
public class MovementItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "movement_id", nullable = false)
    @JsonIgnore
    private Movement movement;

    /** Set for a serialized asset line; null for a consumable line. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_id")
    @NotAudited
    private Asset asset;

    /** Set for a consumable line; null for an asset line. */
    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "commodity_id")
    @NotAudited
    private Commodity commodity;

    /** Units moved (1 for an asset line). */
    @Column(nullable = false)
    @Builder.Default
    private int quantity = 1;

    private String serialNumber;
    private String assetTag;
}
