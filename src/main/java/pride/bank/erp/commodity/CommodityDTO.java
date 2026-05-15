package pride.bank.erp.commodity;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class CommodityDTO {
    @NotNull(message = "Asset type is required")
    private Long assetType;

    @NotBlank(message = "Group name is required")
    private String groupName;

    @NotBlank(message = "Commodity name is required")
    private String name;
}
