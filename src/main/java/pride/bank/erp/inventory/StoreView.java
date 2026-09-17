package pride.bank.erp.inventory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.store.StoreType;

/** Read-only view of a store container for the stores directory (§11-12). */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class StoreView {
    private Long id;
    private String name;
    private StoreType storeType;
    private Long locationId;
    private String locationName;
    private Long departmentId;
    private String departmentName;
    private boolean active;
}
