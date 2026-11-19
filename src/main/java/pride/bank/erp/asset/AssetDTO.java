/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class AssetDTO {
    private String assetName;
    private String hostname;
    private String detailNetBookValue;
    private String engravedNumber;
    private String dateReceipt;
    private String make;
    private String unitOfMeasure;
    /**
     * Editable only on an asset with no stock order. Optional; blank clears it. A received asset's LPO
     * is its order's and is not changed by an edit.
     */
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
    private String description;
    private String image;
    private Long supplier;
    private Long assignedTo;
    private Long branch;
    private Long assetStatus;
    private Long assetType;
    private Long category;
    private String lpoNumber;
    /** Pool stock for temporary replacement loans (see {@link Asset#isTemporaryPool()}). */
    private Boolean temporaryPool;
}
