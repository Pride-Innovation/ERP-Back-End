package pride.bank.erp.asset;

/**
 * One cell of the branch × category asset grid: how many assets of one category sit at one branch.
 *
 * Distinct from {@link AssetTypeSummaryView}, which collapses the branch dimension away and so
 * cannot answer "where are they?" — only "how many in total?". The dashboard's all-branches
 * widget needs the former; the KPI totals still use the latter.
 *
 * {@code branchName} is null for assets that have not been assigned to a branch. Those rows are
 * kept rather than filtered so the grid's totals still reconcile with the category totals.
 */
public interface AssetBranchSummaryView {
    String getBranchName();

    String getAssetTypeName();

    Long getTotalCount();
}
