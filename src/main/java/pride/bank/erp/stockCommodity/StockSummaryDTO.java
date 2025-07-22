package pride.bank.erp.stockCommodity;

public record StockSummaryDTO(
        String assetType,
        String mostStockedItem,
        long mostStockedQuantity,
        String leastStockedItem,
        long leastStockedQuantity
) {
}
