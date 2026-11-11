package pride.bank.erp.inventory;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.TreeMap;
import java.util.function.Function;

/**
 * Filtering, sorting and paging for a store's "Assets Held" table.
 *
 * <h2>Why in memory, and why after scoping</h2>
 * What a caller may see of a store is decided by {@code InventoryReportController.scopeAssets}: an
 * asset is in reach when the branch it sits at <b>or</b> the branch it was sent from is the caller's.
 * That second half is read from movements, after the query, so a page cut in SQL would be cut before
 * the rule ran - wrong totals, and pages that differ in size. Everything here therefore runs on the
 * already-scoped list, which is the only order that cannot leak and cannot miscount.
 *
 * <p>Writing the rule in SQL instead would mean a second copy of it, and two copies of a visibility
 * rule drift. A store holds tens to a few hundred assets (the whole bank holds about 250), so the
 * cost of doing it here is nothing.
 *
 * <p>Pure: no Spring, no repositories, so every rule below is tested directly.
 */
public final class StoreAssetListing {

    private StoreAssetListing() {
    }

    /** Generous enough for an export of a whole store; a bound so a typo cannot ask for millions. */
    public static final int MAX_PAGE_SIZE = 5000;

    /** Every field may be null, meaning "no restriction". */
    public record Criteria(String search, Long assetTypeId, String statusCode, Boolean loanable,
                           Long originBranchId) {
    }

    public record Page(List<StoreAssetRow> content, int pageNumber, int pageSize, long totalElements,
                       int totalPages, long loanableTotal) {
    }

    public record Option(String value, String label) {
    }

    /**
     * What the dropdowns may offer - only values present among the rows the caller can see, so a
     * dropdown never offers a value that can only return an empty table.
     *
     * @param originBranches empty unless the "Sent from" filter applies to this store and caller
     * @param originColumn   whether this store receives assets from other branches, so the table shows
     *                       "Sent From" - decided here so the page does not keep its own list of stores
     * @param awayIncluded   whether the table also lists the branch's assets away at the Head Office
     *                       Admin store, so the page shows where each row is held
     */
    public record FilterOptions(List<Option> categories, List<Option> statuses, List<Option> originBranches,
                                boolean originColumn, boolean awayIncluded) {
    }

    /** Columns the table can be ordered by. Anything else falls back to the asset name. */
    static final Map<String, Function<StoreAssetRow, String>> SORT_KEYS = Map.of(
            "assetName", StoreAssetRow::assetName,
            "assetTypeName", StoreAssetRow::assetTypeName,
            "engravedNumber", StoreAssetRow::engravedNumber,
            "serialNumber", StoreAssetRow::serialNumber,
            "statusName", StoreAssetRow::statusName,
            "originBranchName", StoreAssetRow::originBranchName);

    public static List<StoreAssetRow> filter(List<StoreAssetRow> rows, Criteria c) {
        String needle = c.search() == null ? "" : c.search().trim().toLowerCase(Locale.ROOT);
        return rows.stream()
                .filter(r -> needle.isEmpty() || matches(r, needle))
                .filter(r -> c.assetTypeId() == null || c.assetTypeId().equals(r.assetTypeId()))
                .filter(r -> blank(c.statusCode()) || c.statusCode().equals(r.statusCode()))
                .filter(r -> c.loanable() == null || c.loanable() == r.loanable())
                .filter(r -> c.originBranchId() == null || c.originBranchId().equals(r.originBranchId()))
                .toList();
    }

    /**
     * Sorts and cuts one page. The order always ends in {@code id}, so rows with equal sort values
     * keep one order between requests and paging cannot repeat or drop a row (trap #3).
     */
    public static Page page(List<StoreAssetRow> filtered, int pageNumber, int pageSize,
                            String sortBy, String sortDirection) {
        int size = Math.max(1, Math.min(pageSize, MAX_PAGE_SIZE));
        int number = Math.max(0, pageNumber);

        // Map.of rejects a null key outright, so an unset column must be caught before the lookup.
        Function<StoreAssetRow, String> key = sortBy == null
                ? StoreAssetRow::assetName
                : SORT_KEYS.getOrDefault(sortBy, StoreAssetRow::assetName);
        Comparator<StoreAssetRow> byKey = Comparator.comparing(
                (StoreAssetRow r) -> lower(key.apply(r)), Comparator.nullsLast(Comparator.naturalOrder()));
        if ("DESC".equalsIgnoreCase(sortDirection)) byKey = byKey.reversed();
        Comparator<StoreAssetRow> order = byKey.thenComparing(
                StoreAssetRow::id, Comparator.nullsLast(Comparator.naturalOrder()));

        List<StoreAssetRow> sorted = new ArrayList<>(filtered);
        sorted.sort(order);

        int from = (int) Math.min((long) number * size, sorted.size());
        int to = Math.min(from + size, sorted.size());
        long loanable = filtered.stream().filter(StoreAssetRow::loanable).count();
        int totalPages = (int) Math.ceil(sorted.size() / (double) size);

        return new Page(List.copyOf(sorted.subList(from, to)), number, size, sorted.size(), totalPages, loanable);
    }

    public static FilterOptions options(List<StoreAssetRow> rows, boolean originColumn, boolean offerOrigins) {
        return options(rows, originColumn, offerOrigins, false);
    }

    public static FilterOptions options(List<StoreAssetRow> rows, boolean originColumn, boolean offerOrigins,
                                        boolean awayIncluded) {
        return new FilterOptions(
                distinct(rows, r -> r.assetTypeId() == null ? null : String.valueOf(r.assetTypeId()),
                        StoreAssetRow::assetTypeName),
                distinct(rows, StoreAssetRow::statusCode, StoreAssetRow::statusName),
                offerOrigins
                        ? distinct(rows, r -> r.originBranchId() == null ? null : String.valueOf(r.originBranchId()),
                                StoreAssetRow::originBranchName)
                        : List.of(),
                originColumn,
                awayIncluded);
    }

    // ── internals ───────────────────────────────────────────────────────────────────────────────

    private static boolean matches(StoreAssetRow r, String needle) {
        for (String v : new String[]{r.assetName(), r.engravedNumber(), r.serialNumber(), r.model(),
                r.assetTypeName()}) {
            if (v != null && v.toLowerCase(Locale.ROOT).contains(needle)) return true;
        }
        return false;
    }

    /** Value → label, sorted by label, values without a value dropped. */
    private static List<Option> distinct(List<StoreAssetRow> rows, Function<StoreAssetRow, String> value,
                                         Function<StoreAssetRow, String> label) {
        Map<String, String> byValue = new TreeMap<>();
        for (StoreAssetRow r : rows) {
            String v = value.apply(r);
            if (v == null) continue;
            byValue.putIfAbsent(v, Objects.requireNonNullElse(label.apply(r), v));
        }
        return byValue.entrySet().stream()
                .map(e -> new Option(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(o -> o.label().toLowerCase(Locale.ROOT)))
                .toList();
    }

    private static String lower(String s) {
        return s == null ? null : s.toLowerCase(Locale.ROOT);
    }

    private static boolean blank(String s) {
        return s == null || s.isBlank();
    }
}
