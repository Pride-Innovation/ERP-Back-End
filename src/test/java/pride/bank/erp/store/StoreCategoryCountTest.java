package pride.bank.erp.store;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.data.domain.PageRequest;

import pride.bank.erp.assetType.AssetType;
import pride.bank.erp.assetType.AssetTypeRepository;
import pride.bank.erp.branch.Branch;
import pride.bank.erp.branch.BranchRepository;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The category strip must count exactly what the listing beside it lists.
 *
 * <h2>What this replaced</h2>
 * The store page's "Inventory by category" tiles had no endpoint of their own, so the browser called
 * {@code GET /store} <b>once per category</b> with {@code pageSize=1} purely to read
 * {@code totalElements}. Twelve categories is twelve round trips to draw one row of tiles, twelve
 * COUNT queries, and — when the branch is outside the caller's reach — twelve refusals instead of
 * one.
 *
 * <p>The one thing that made the loop defensible was that it could not disagree with the table: both
 * ran the same predicate. Replacing it with a {@code GROUP BY} puts that predicate in a second place,
 * and a second place is where the two screens start answering differently about the same commodity —
 * this codebase has that bug on record twice (the store page's invented stock-level bands, and the
 * asset condition roll-up). So the equivalence is pinned here rather than assumed.
 *
 * <p>Run against live data, like the other store tests: what matters is that the two agree about the
 * rows that actually exist, including the branches that hold nothing.
 */
@SpringBootTest
class StoreCategoryCountTest {

    @Autowired StoreBalanceRepository balances;
    @Autowired StoreService storeService;
    @Autowired AssetTypeRepository assetTypes;
    @Autowired BranchRepository branches;

    /** Per category and in total, for both a named container and none. */
    @Test
    void theStripCountsWhatTheListingWouldList() {
        List<AssetType> categories = assetTypes.findAll();
        assertThat(categories).as("no categories configured — this test would prove nothing").isNotEmpty();

        for (Branch branch : branches.findAll()) {
            for (StoreType container : new StoreType[]{ StoreType.ADMIN, null }) {
                Map<Long, Long> strip = storeService.countByAssetType(branch.getId(), container);

                for (AssetType category : categories) {
                    long listing = balances.search(branch.getId(), category.getId(), container,
                            null, null, PageRequest.of(0, 1)).getTotalElements();

                    assertThat(strip.getOrDefault(category.getId(), 0L))
                            .as("%s / %s / %s — the tile and the table must agree",
                                    branch.getName(), container, category.getName())
                            .isEqualTo(listing);
                }
            }
        }
    }

    /**
     * A category holding nothing is absent, not zero.
     *
     * <p>The page hides empty tiles, so returning a zero for every category in the bank would make it
     * decide the same thing from more data — and would put the count of categories, rather than the
     * count of stock, into every response.
     */
    @Test
    void emptyCategoriesAreOmittedRatherThanZeroed() {
        for (Branch branch : branches.findAll()) {
            assertThat(storeService.countByAssetType(branch.getId(), StoreType.ADMIN).values())
                    .as("%s — a zero here would be a tile the page then has to hide", branch.getName())
                    .allSatisfy(count -> assertThat(count).isPositive());
        }
    }

    /**
     * Naming a branch you may not see is refused, not quietly rewritten.
     *
     * <p>The strip is a second way into the same figures the listing guards, and a count is a
     * disclosure: "Head Office holds 26 lines of stock" is the answer somebody was refused the detail
     * of. Pinned on the source because standing up a second principal would pin the fixture rather
     * than the rule.
     */
    @Test
    void theStripIsScopedAndGatedLikeTheListing() {
        String source = read("src/main/java/pride/bank/erp/store/StoreController.java");
        int endpoint = source.indexOf("/store/category-counts");
        assertThat(endpoint).as("the endpoint must exist").isGreaterThan(0);

        String handler = source.substring(Math.max(0, endpoint - 400), Math.min(source.length(), endpoint + 700));
        assertThat(handler)
                .as("a count is a disclosure; naming another branch must be refused")
                .contains("branchScope.requireAccessTo(branchId)");
        assertThat(handler)
                .as("the page's own route demands READ_STORE — the endpoint must ask for no less")
                .contains("READ_STORE");
    }

    private static String read(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
