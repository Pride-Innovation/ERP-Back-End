package pride.bank.erp.asset;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AssetGroupDTO {
    private String type;
    private Long totalItems;
    private List<AssetDTO> assets;

    @Data
    @AllArgsConstructor
    public static class AssetDTO {
        private Long id;
        private String name;
        private String engravingNumber;
        private String status;
        private String serialNumber;
    }
}
