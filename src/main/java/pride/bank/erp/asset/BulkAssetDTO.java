package pride.bank.erp.asset;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class BulkAssetDTO {
    private Long no;
    private String productName;
    private String assetTag;
    private String assetSerialNo;
    private String model;
    private String user;
    private String location;
    private Integer dateOfPmVerification;
}