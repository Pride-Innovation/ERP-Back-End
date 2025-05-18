/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.supplier;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.stock.Stock;

import java.util.List;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Supplier {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String name;
    private String address;
    private String telephone;
    private String email;

    @OneToMany(mappedBy = "supplier", cascade = CascadeType.PERSIST)
    @JsonIgnore
    private List<Stock> stocks;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "commodity_id")
    private Commodity commodity;
}
