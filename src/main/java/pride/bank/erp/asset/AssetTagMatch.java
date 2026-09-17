/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.asset;

import lombok.Builder;
import lombok.Data;

/**
 * What the navigation bar's tag search hands back.
 *
 * <h2>Why a DTO rather than the asset</h2>
 * The endpoint returned {@code ResponseEntity<Asset>} — the entity itself, with its supplier, stock,
 * holder, branch, status and every eagerly-fetched association behind them. A search box that
 * anybody with {@code READ_ASSET} can type into is the wrong place to publish a whole record: the
 * caller wants to know <em>which asset this is and where to click</em>, and the detail page behind
 * the click has its own guard for the rest.
 *
 * <p>Six fields, and each earns its place. The id is what the link needs. The engraved number is
 * echoed back because the match is case-insensitive, so what was typed and what is recorded may
 * differ and the reader should see the recorded form. Name, branch and status are what distinguish
 * two results from each other when a tag is carried by more than one asset.
 */
@Data
@Builder
public class AssetTagMatch {

    private Long id;

    /**
     * The asset's category, and it is not decoration.
     *
     * <p>The detail page lives at {@code /assets/general/:typeId/view/:id} — <b>both</b> segments are
     * required. Without this the browser could identify the asset and still not open it, which is
     * exactly what happened: the search navigated to {@code /assets/{id}}, matched no route, and the
     * router answered with its own developer error page.
     */
    private Long assetTypeId;

    private String engravedNumber;
    private String assetName;
    private String serialNumber;

    /** Where the asset is, which is not always where its holder is — see {@code assetLocationLabel}. */
    private String branchName;

    private String status;
}
