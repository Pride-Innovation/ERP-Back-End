/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.assetType;

import com.fasterxml.jackson.annotation.JsonIgnore;
import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class AssetType {
    @Id
    @GeneratedValue(strategy = GenerationType.AUTO)
    private Long id;
    private String name;
    private String description;
    private String shortCode;

    /**
     * Email group notified to fulfil a request after approvals
     * (e.g. it-infra@bank for Computer, admin@bank for Furniture).
     */
    @Column(name = "owner_group_email", length = 255)
    private String ownerGroupEmail;

    /**
     * Whether stocking items of this category should auto-create trackable {@code Asset}
     * records (durable goods such as Computers, Furniture, Vehicles). Consumables
     * (Stationery, Cleaning, Healthcare, etc.) leave this {@code false}.
     *
     * <p>Replaces the old hardcoded category-name check in {@code StockService} so the
     * "stocking creates assets and notifies the owning team" behaviour is configurable
     * per category.
     */
    @Column(name = "tracks_assets", columnDefinition = "BIT NOT NULL DEFAULT 0")
    @Builder.Default
    private boolean tracksAssets = false;

    /**
     * Per-field visibility configuration.
     * Each key is a form-field name; each value is one of: "required", "optional", "hidden".
     * Stored as a JSON string in a large-text column.
     */
    // MSSQL (uncomment + comment-out the MySQL line below when switching back):
    // @Column(name = "field_config", columnDefinition = "NVARCHAR(MAX)")
    // MySQL:
    @Column(name = "field_config", columnDefinition = "LONGTEXT")
    @Convert(converter = FieldConfigConverter.class)
    private Map<String, String> fieldConfig;

    @OneToMany(mappedBy = "assetType", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @OrderBy("displayOrder ASC")
    @JsonIgnore
    @Builder.Default
    private List<AssetTypeCustomAttribute> customAttributes = new ArrayList<>();
}
