package pride.bank.erp.request;

public record AssetRequestSummaryDTO(
        String assetType,
        long totalRequested,
        long totalDelivered
) {
}
