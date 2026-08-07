package pride.bank.erp.asset;

import pride.bank.erp.assetType.AssetType;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.OffsetDateTime;
import java.time.temporal.ChronoUnit;

/**
 * Age-in-service questions about an asset, in one place.
 *
 * <p>Extracted so disposal eligibility and the depreciation panel answer from the same arithmetic.
 * They were separate before, which meant an asset could read "Ready for disposal" on its detail page
 * while the disposal flow had no opinion at all.
 *
 * <p>Everything here tolerates bad data rather than throwing: {@code dateReceipt} is stored as free
 * text, so blank and unparseable values are real and common. They resolve to zero months in service,
 * which reads as "too new to dispose" — the safe direction, since the alternative would wave through
 * every asset with a missing date.
 */
public final class AssetLifecycle {

    private AssetLifecycle() {
    }

    /** Whole months between the receipt date and today; 0 when missing, unparseable or future-dated. */
    public static long monthsInService(String dateReceipt) {
        if (dateReceipt == null || dateReceipt.isBlank()) return 0L;
        LocalDate date = parseDateFlexible(dateReceipt.trim());
        if (date == null) return 0L;
        return Math.max(0L, ChronoUnit.MONTHS.between(date, LocalDate.now()));
    }

    /**
     * Whether the asset has been held for at least its category's useful life.
     *
     * <p>False when the category has no useful life configured — an unconfigured category is not
     * evidence that an asset is spent.
     */
    public static boolean isPastUsefulLife(Asset asset) {
        if (asset == null) return false;
        AssetType type = asset.getAssetType();
        Integer usefulLifeMonths = (type != null) ? type.getUsefulLifeMonths() : null;
        if (usefulLifeMonths == null || usefulLifeMonths <= 0) return false;
        return monthsInService(asset.getDateReceipt()) >= usefulLifeMonths;
    }

    /** Parses the several shapes {@code dateReceipt} has been written in over the years. */
    public static LocalDate parseDateFlexible(String value) {
        if (value == null || value.isBlank()) return null;
        try { return LocalDateTime.parse(value).toLocalDate(); } catch (Exception ignored) { }
        try { return OffsetDateTime.parse(value).toLocalDate(); } catch (Exception ignored) { }
        try { return LocalDate.parse(value); } catch (Exception ignored) { }
        int t = value.indexOf('T');
        if (t > 0) {
            try { return LocalDate.parse(value.substring(0, t)); } catch (Exception ignored) { }
        }
        return null;
    }
}
