package pride.bank.erp.asset;

import lombok.AllArgsConstructor;
import lombok.Data;

import java.util.List;

@Data
@AllArgsConstructor
public class AssetGroupDTO {
    private String type;
    /**
     * The category's id, so the dashboard can link a row through to the asset's detail page.
     *
     * <p>The asset routes are per-category — {@code /assets/general/{assetTypeId}/view/{id}} — so a
     * payload carrying only the category *name* cannot produce a working link, which is why the
     * rows in "My Assets" were not clickable.
     */
    private Long typeId;
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
        /** Where it is — the branch holding it, for a person whose items span more than one. */
        private String location;
    }
}
