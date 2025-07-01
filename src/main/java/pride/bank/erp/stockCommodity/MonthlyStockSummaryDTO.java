package pride.bank.erp.stockCommodity;

public interface MonthlyStockSummaryDTO {
    String getMonth(); // format: YYYY-MM

    String getAssetType();

    Long getTotalQuantity();
}
