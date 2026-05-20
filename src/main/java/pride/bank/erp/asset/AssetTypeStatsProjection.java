package pride.bank.erp.asset;

public interface AssetTypeStatsProjection {
    String getAssetType();

    Long getTotal();

    Long getInMaintenance();

    Long getUnassigned();

    Long getAssigned();
}
