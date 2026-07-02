/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetTypeDTO {
    private Long id;
    private String name;
    private String description;
    private String shortCode;
    private String ownerGroupEmail;
    /** Annual reducing-balance depreciation rate (percent) for this category. */
    private Double depreciationRate;
    /** Useful life in months; an asset is flagged for disposal once its age reaches this. */
    private Integer usefulLifeMonths;
    private Map<String, String> fieldConfig;

    @Builder.Default
    private List<AssetTypeCustomAttributeDTO> customAttributes = new ArrayList<>();
}
