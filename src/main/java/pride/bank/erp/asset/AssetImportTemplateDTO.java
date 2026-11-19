/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import lombok.Builder;
import lombok.Data;

import java.util.List;
import java.util.Map;

/**
 * Everything the browser needs to build an asset import template, in one call.
 *
 * <p>Assembled server-side rather than left to the frontend to gather from five endpoints. Two
 * reasons: the template's dropdowns and the importer's validation must be the same sets — a template
 * offering a status the server then rejects is worse than no dropdown at all — and the row limits
 * are properties of the server's own configuration, so they belong here rather than duplicated as
 * frontend constants that drift when the configuration changes.
 */
@Data
@Builder
public class AssetImportTemplateDTO {

    /** The category being imported into; its name titles the sheet. */
    private Long assetTypeId;
    private String assetTypeName;

    /**
     * Field name → {@code required} | {@code optional} | {@code hidden}, straight from
     * {@code AssetType.fieldConfig}.
     *
     * <p>The same map that drives the create form, so the template shows exactly the columns this
     * category uses, marks the same ones required, and stays correct when someone changes the
     * configuration in settings — rather than being a second list that quietly falls out of step.
     */
    private Map<String, String> fieldConfig;

    // ── Dropdown sources ─────────────────────────────────────────────────────

    private List<String> branches;
    private List<String> suppliers;
    /** Scoped to this category — a commodity name is only unique within one. */
    private List<String> commodities;
    /** Display names of the statuses an asset may be imported in. */
    private List<String> statuses;
    /** "PBL0001 — Jane Doe": the staff number the importer matches on, plus a name to pick by. */
    private List<String> staff;
    /** Active units from Settings → Units of Measure. A blank cell takes the commodity's unit. */
    private List<String> unitsOfMeasure;

    // ── Limits, so the browser can enforce them before uploading anything ─────

    /** Rows per request. The browser chunks a file into batches of this size. */
    private int batchSize;
    /** Rows per file, checked before the first request is sent. */
    private int maxRows;
}
