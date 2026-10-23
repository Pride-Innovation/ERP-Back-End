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
    /**
     * The branch that <b>owns</b> this stock — {@code StoreBalance.branch}, not the store's address.
     *
     * <h2>Read this before scoping anything by it</h2>
     * The name is a trap and is kept only because the frontend reads it. {@code StoreView.locationId}
     * on the sibling view means the opposite thing: <em>where the store stands</em>. Here it means
     * <em>whose the goods are</em>, and the two diverge precisely where it matters — the IT and
     * Disposal stores are bank-wide singletons held at Head Office, so a Gulu commodity sitting in one
     * of them has {@code storeId} = the IT store, but this field = Gulu.
     *
     * <p>That is deliberate and is what lets a branch see its own items inside a shared store. Scoping
     * a balance by the store's location instead would hand Head Office the whole estate and show every
     * branch an empty shelf.
     */
    private Long locationId;
    /** Name of the owning branch above — again the balance's branch, not the store's location. */
    private String locationName;
    private Long departmentId;
    private String departmentName;
    private Long commodityId;
    private String commodityName;
    private String assetTypeName;
    private int quantity;
    /** Reorder threshold (0 = none configured). */
    private int minLevel;
    /** True when a threshold is set and on-hand is at or below it. */
    private boolean lowStock;
    /**
     * What the store can still promise: on hand less what open movements and unapproved issuances
     * have spoken for ({@link StockAvailabilityService}). Filled only when one store's balances are
     * asked for - the movement form's source store - and null elsewhere, where it is not computed.
     */
    private Integer available;
}
