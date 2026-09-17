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

/**
 * Compact view of an {@link AssetType} suitable for embedding in another resource
 * (e.g. {@code RequestDTO.assetType}). Carries id, name and shortCode only.
 */
@Data
@Builder
@AllArgsConstructor
@NoArgsConstructor
public class AssetTypeRefDTO {
    private Long id;
    private String name;
    private String shortCode;

    public static AssetTypeRefDTO from(AssetType entity) {
        if (entity == null) {
            return null;
        }
        return AssetTypeRefDTO.builder()
                .id(entity.getId())
                .name(entity.getName())
                .shortCode(entity.getShortCode())
                .build();
    }
}
