/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

@Entity
@Table(
        name = "asset_type_custom_attribute",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_asset_type_custom_attribute_key",
                columnNames = {"asset_type_id", "attribute_key"}
        )
)
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetTypeCustomAttribute {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "asset_type_id", nullable = false)
    @JsonIgnore
    private AssetType assetType;

    /** Slug, lowercase + underscore. Unique within parent. Stored as attribute_key (SQL reserved word). */
    @Column(name = "attribute_key", nullable = false, length = 80)
    private String key;

    @Column(name = "label", nullable = false, length = 120)
    private String label;

    @Enumerated(EnumType.STRING)
    @Column(name = "data_type", nullable = false, length = 16)
    private AttributeDataType dataType;

    /** Populated when dataType = SELECT, else null. */
    // MSSQL (uncomment + comment-out the MySQL line below when switching back):
    // @Column(name = "options_json", columnDefinition = "NVARCHAR(MAX)")
    // MySQL:
    @Column(name = "options_json", columnDefinition = "LONGTEXT")
    @Convert(converter = AttributeOptionsConverter.class)
    private List<String> options;

    @Column(name = "is_required", nullable = false)
    @Builder.Default
    private boolean required = false;

    /** 1-based ordering within parent. */
    @Column(name = "display_order", nullable = false)
    private int displayOrder;

    @Column(name = "helper_text", length = 255)
    private String helperText;
}
