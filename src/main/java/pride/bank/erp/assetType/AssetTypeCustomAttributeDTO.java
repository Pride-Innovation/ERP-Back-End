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

import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AssetTypeCustomAttributeDTO {
    private Long id;
    private String key;
    private String label;
    private AttributeDataType dataType;
    private List<String> options;
    private boolean required;
    /** 1-based ordering. Mapped from entity's displayOrder. */
    private int order;
    private String helperText;
}
