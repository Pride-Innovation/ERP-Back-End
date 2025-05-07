package pride.bank.erp.commodity;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.requestCommodity.RequestCommodity;
import pride.bank.erp.stockCommodity.StockCommodity;

import java.util.HashSet;
import java.util.Set;

@Entity
@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class Commodity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;
    private String name;
    private String groupName;

    // This is optional but can be useful to easily access commodities associated with a request
    @OneToMany(mappedBy = "commodity", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<RequestCommodity> requestCommodities = new HashSet<>();

    // This is optional but can be useful to easily access commodities associated with a stock
    @OneToMany(mappedBy = "commodity", cascade = CascadeType.ALL, orphanRemoval = true)
    @JsonIgnore
    private Set<StockCommodity> stockCommodities = new HashSet<>();

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "asset_type_id")
    private AssetType assetType;
}
