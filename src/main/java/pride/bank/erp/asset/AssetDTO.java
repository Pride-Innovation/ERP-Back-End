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
}
