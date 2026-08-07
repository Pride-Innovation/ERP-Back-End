package pride.bank.erp.inventory;

import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetLifecycle;

/**
 * An asset offered to the disposal picker, with the age judgement already made server-side.
 *
 * <p>Deliberately thin. The picker needs to label a row and decide whether to demand a reason —
 * it does not need the asset graph, and returning entities here would drag users, stores and
 * branches onto the wire for a dropdown.
 */
public record DisposalCandidateView(
        Long id,
        String name,
        String engravedNumber,
        String serialNumber,
        String assetTypeName,
        String storeName,
        String locationName,
        /** Months held, derived from {@code dateReceipt}; 0 when that date is missing or unparseable. */
        long monthsInService,
        /** The category's configured useful life, or null when it has none. */
        Integer usefulLifeMonths,
        /** True when the asset has served its useful life and needs no justification to write off. */
        boolean pastUsefulLife
) {

    public static DisposalCandidateView of(Asset asset) {
        return new DisposalCandidateView(
                asset.getId(),
                asset.getAssetName(),
                asset.getEngravedNumber(),
                asset.getSerialNumber(),
                asset.getAssetType() != null ? asset.getAssetType().getName() : null,
                asset.getCurrentStore() != null ? asset.getCurrentStore().getName() : null,
                asset.getCurrentStore() != null && asset.getCurrentStore().getLocation() != null
                        ? asset.getCurrentStore().getLocation().getName() : null,
                AssetLifecycle.monthsInService(asset.getDateReceipt()),
                asset.getAssetType() != null ? asset.getAssetType().getUsefulLifeMonths() : null,
                AssetLifecycle.isPastUsefulLife(asset));
    }
}
