package pride.bank.erp.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;
import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchDAO;
import pride.bank.erp.commodity.Commodity;
import pride.bank.erp.status.Status;
import pride.bank.erp.stock.Stock;
import pride.bank.erp.supplier.Supplier;
import pride.bank.erp.supplier.SupplierDTO;
import pride.bank.erp.user.User;
import pride.bank.erp.user.UserDAO;
import pride.bank.erp.user.UserDTO;

import java.time.LocalDateTime;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AssetDAO {
    private Long id;
    private String assetName;
    private String hostname;
    private String detailNetBookValue;
    private String engravedNumber;
    private String dateReceipt;
    private String make;
    private String unitOfMeasure;
    /** The text LPO of an asset with no stock order; null where the stock order supplies it. */
    private String lpoReference;
    private String purchaseCost;
    private String costOfTheAsset;
    private String netValueB;
    private String model;
    private String serialNumber;
    private String ram;
    private String cpuSpeed;
    private String hardDiskSize;
    private String macAddress;
    private String ipAddress;
    private String interfaceType;
    private String assetDepreciationRate;
    /** Derived: total depreciation to date (gross cost − current net book value). Read-only. */
    private String accumulatedDepreciation;
    /** Derived: depreciation charge for the current year at the current value. Read-only. */
    private String annualDepreciation;
    /** Derived: monthly depreciation charge (current year's charge ÷ 12). Read-only. */
    private String monthlyDepreciation;
    /** Derived: "In service" or "Ready for disposal" once the asset's age reaches its useful life. */
    private String disposalStatus;

    /**
     * Whose write-off this was - the Disposal report's "Disposed From". Set only on that listing
     * ({@code byDisposingBranch}); a disposed asset's own {@code branch} reads Head Office, where the
     * Disposal store stands, so it cannot answer.
     */
    private Long disposedFromBranchId;
    private String disposedFromBranchName;
    private String description;
    private String image;

    private Supplier supplier;
    private User assignedTo;
    private Branch branch;
    private Status assetStatus;
    private AssetType assetType;
    private Commodity commodity;
    private Stock stock;

    private LocalDateTime createDate;
    private LocalDateTime lastModified;
    private Long createdBy;
    private Long lastModifiedBy;
    private String lastRepairedBy;
    /** Pool stock for temporary replacement loans (see {@code Asset#isTemporaryPool()}). */
    private boolean temporaryPool;
}
