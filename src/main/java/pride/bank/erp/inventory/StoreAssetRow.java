package pride.bank.erp.inventory;

import pride.bank.erp.asset.Asset;

/**
 * One row of a store's "Assets Held" table - what the table and its export show, and nothing else.
 *
 * <p>The listing it replaces returned whole {@code Asset} entities, with their holder, stock line,
 * branch and supplier graphs, to draw seven columns. A page of these is a fraction of that, and it
 * carries the one thing the entity cannot: the branch the asset was sent from.
 *
 * @param originBranchId   the branch that sent the asset here, read from the newest movement's
 *                         origin - the same lookup the visibility rule uses, so the column and the
 *                         scoping cannot disagree. Null when nothing recorded where it came from.
 * @param storeId          the store actually holding it. On a branch's Admin page the table also
 *                         lists that branch's assets away at the Head Office Admin store, so a row is
 *                         not always in the store the page is about, and the page says which.
 */
public record StoreAssetRow(
        Long id,
        String assetName,
        String model,
        Long assetTypeId,
        String assetTypeName,
        String engravedNumber,
        String serialNumber,
        String statusCode,
        String statusName,
        boolean loanable,
        String holdingBranchName,
        Long originBranchId,
        String originBranchName,
        Long storeId,
        String storeName
) {
    static StoreAssetRow of(Asset a, Long originBranchId, String originBranchName) {
        return new StoreAssetRow(
                a.getId(),
                a.getAssetName(),
                a.getModel(),
                a.getAssetType() != null ? a.getAssetType().getId() : null,
                a.getAssetType() != null ? a.getAssetType().getName() : null,
                a.getEngravedNumber(),
                a.getSerialNumber(),
                a.getAssetStatus() != null ? a.getAssetStatus().getStatus() : null,
                a.getAssetStatus() != null ? a.getAssetStatus().getName() : null,
                a.isTemporaryPool(),
                a.getBranch() != null ? a.getBranch().getName() : null,
                originBranchId,
                originBranchName,
                a.getCurrentStore() != null ? a.getCurrentStore().getId() : null,
                a.getCurrentStore() != null ? a.getCurrentStore().getName() : null);
    }
}
