package pride.bank.erp.asset;

import java.time.LocalDateTime;

public interface AssetTypeSummaryView {
    String getAssetTypeName();

    Long getTotalCount();

    LocalDateTime getLastUpdatedDate();
}
