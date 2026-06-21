package pride.bank.erp.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.store.StoreType;

/** Flat, read-only view of a consumable balance for cross-location / department reporting (§11-12). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreBalanceView {
    private Long balanceId;
    private Long storeId;
    private String storeName;
    private StoreType storeType;
    private Long locationId;
    private String locationName;
    private Long departmentId;
    private String departmentName;
    private Long commodityId;
    private String commodityName;
    private String assetTypeName;
    private int quantity;
}
