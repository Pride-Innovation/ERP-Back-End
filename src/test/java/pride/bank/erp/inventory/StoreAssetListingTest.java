package pride.bank.erp.inventory;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The store "Assets Held" table's filtering, sorting and paging - pure, so each rule is checked
 * directly. Scoping is covered against real data in {@link StoreAssetPageScopeTest}.
 */
class StoreAssetListingTest {

    private static StoreAssetRow row(long id, String name, String category, long categoryId, String status,
                                     boolean loanable, Long origin) {
        return new StoreAssetRow(id, name, null, categoryId, category, "ENG-" + id, "SN-" + id,
                status, status == null ? null : status + " label", loanable, "Head Office",
                origin, origin == null ? null : "Branch " + origin, 1L, "Head Office Admin Store");
    }

    /** Twelve rows, several sharing a name so the tie-break has something to do. */
    private static List<StoreAssetRow> rows() {
        List<StoreAssetRow> rows = new ArrayList<>();
        for (long i = 1; i <= 12; i++) {
            rows.add(row(i, i % 3 == 0 ? "Laptop" : "Asset " + (char) ('A' + i), i % 2 == 0 ? "Computers" : "Furniture",
                    i % 2 == 0 ? 2 : 6, i % 4 == 0 ? "inMaintenance" : "sentToStore", i % 5 == 0,
                    i % 3 == 0 ? 7L : null));
        }
        return rows;
    }

    private static final StoreAssetListing.Criteria NONE = new StoreAssetListing.Criteria(null, null, null, null, null);

    /** Walking every page returns every row exactly once, even when names repeat. */
    @Test
    void pagingNeitherRepeatsNorDropsARow() {
        List<StoreAssetRow> all = rows();
        Set<Long> seen = new HashSet<>();
        int pages = StoreAssetListing.page(all, 0, 5, "assetName", "ASC").totalPages();
        assertThat(pages).isEqualTo(3);

        for (int p = 0; p < pages; p++) {
            StoreAssetListing.page(all, p, 5, "assetName", "ASC").content()
                    .forEach(r -> assertThat(seen.add(r.id())).as("row %d appeared twice", r.id()).isTrue());
        }
        assertThat(seen).hasSize(all.size());
    }

    /** Totals describe the filtered set, not the page - the header counts must hold on every page. */
    @Test
    void totalsAreOverTheWholeMatchNotThePage() {
        StoreAssetListing.Page page = StoreAssetListing.page(rows(), 0, 2, "assetName", "ASC");
        assertThat(page.content()).hasSize(2);
        assertThat(page.totalElements()).isEqualTo(12);
        assertThat(page.loanableTotal()).isEqualTo(2);   // ids 5 and 10
    }

    @Test
    void eachFilterNarrowsOnItsOwnField() {
        assertThat(StoreAssetListing.filter(rows(), new StoreAssetListing.Criteria("laptop", null, null, null, null)))
                .extracting(StoreAssetRow::id).containsExactlyInAnyOrder(3L, 6L, 9L, 12L);
        assertThat(StoreAssetListing.filter(rows(), new StoreAssetListing.Criteria("eng-11", null, null, null, null)))
                .extracting(StoreAssetRow::id).containsExactly(11L);
        assertThat(StoreAssetListing.filter(rows(), new StoreAssetListing.Criteria(null, 2L, null, null, null)))
                .allMatch(r -> r.assetTypeId() == 2L).hasSize(6);
        assertThat(StoreAssetListing.filter(rows(), new StoreAssetListing.Criteria(null, null, "inMaintenance", null, null)))
                .extracting(StoreAssetRow::id).containsExactlyInAnyOrder(4L, 8L, 12L);
        assertThat(StoreAssetListing.filter(rows(), new StoreAssetListing.Criteria(null, null, null, true, null)))
                .extracting(StoreAssetRow::id).containsExactlyInAnyOrder(5L, 10L);
        assertThat(StoreAssetListing.filter(rows(), new StoreAssetListing.Criteria(null, null, null, false, null)))
                .hasSize(10);
        assertThat(StoreAssetListing.filter(rows(), new StoreAssetListing.Criteria(null, null, null, null, 7L)))
                .extracting(StoreAssetRow::id).containsExactlyInAnyOrder(3L, 6L, 9L, 12L);
        assertThat(StoreAssetListing.filter(rows(), NONE)).hasSize(12);
    }

    /** An unknown sort column falls back to the name rather than failing; DESC reverses it. */
    @Test
    void sortingIsWhitelistedAndReversible() {
        List<StoreAssetRow> asc = StoreAssetListing.page(rows(), 0, 50, "nonsense", "ASC").content();
        List<StoreAssetRow> desc = StoreAssetListing.page(rows(), 0, 50, "assetName", "DESC").content();
        assertThat(asc.get(0).assetName()).isEqualTo("Asset B");
        assertThat(desc.get(0).assetName()).isEqualTo("Laptop");
        // Equal names keep id order in both directions' ties.
        assertThat(asc.stream().filter(r -> r.assetName().equals("Laptop")).map(StoreAssetRow::id))
                .containsExactly(3L, 6L, 9L, 12L);
    }

    /** A page size is bounded, and a page past the end is empty rather than an error. */
    @Test
    void pageSizeAndNumberAreBounded() {
        assertThat(StoreAssetListing.page(rows(), 0, 0, null, null).pageSize()).isEqualTo(1);
        assertThat(StoreAssetListing.page(rows(), 0, 1_000_000, null, null).pageSize())
                .isEqualTo(StoreAssetListing.MAX_PAGE_SIZE);
        assertThat(StoreAssetListing.page(rows(), 99, 5, null, null).content()).isEmpty();
        assertThat(StoreAssetListing.page(rows(), -3, 5, null, null).pageNumber()).isZero();
    }

    /** Every option offered matches at least one row, and "Sent from" appears only when asked for. */
    @Test
    void optionsOfferOnlyValuesThatCanMatch() {
        StoreAssetListing.FilterOptions withOrigins = StoreAssetListing.options(rows(), true, true);
        assertThat(withOrigins.originColumn()).isTrue();
        assertThat(withOrigins.categories()).extracting(StoreAssetListing.Option::label)
                .containsExactly("Computers", "Furniture");
        assertThat(withOrigins.statuses()).extracting(StoreAssetListing.Option::value)
                .containsExactlyInAnyOrder("inMaintenance", "sentToStore");
        assertThat(withOrigins.originBranches()).extracting(StoreAssetListing.Option::value).containsExactly("7");

        for (StoreAssetListing.Option o : withOrigins.categories()) {
            assertThat(StoreAssetListing.filter(rows(),
                    new StoreAssetListing.Criteria(null, Long.valueOf(o.value()), null, null, null))).isNotEmpty();
        }
        // The column and the filter are separate answers: a branch caller gets the column, not the filter.
        StoreAssetListing.FilterOptions columnOnly = StoreAssetListing.options(rows(), true, false);
        assertThat(columnOnly.originColumn()).isTrue();
        assertThat(columnOnly.originBranches()).isEmpty();
        assertThat(StoreAssetListing.options(rows(), false, false).originColumn()).isFalse();
    }
}
