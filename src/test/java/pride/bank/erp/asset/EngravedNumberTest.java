package pride.bank.erp.asset;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.persistence.EntityManager;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * An engraved number identifies one asset, and the tag search must respect who is asking.
 *
 * <h2>What was wrong, measured</h2>
 * <ul>
 *   <li><b>Three engraved numbers were carried by two assets each.</b> The finder returned
 *       {@code Optional<Asset>}, which does not mean "at most one" — it throws on a second row. So
 *       searching any of those six assets produced
 *       {@code IncorrectResultSizeDataAccessException}: a 500, for a search box on every page.</li>
 *   <li><b>Only the bulk import checked uniqueness.</b> {@code createAsset} and {@code updateAsset}
 *       set the field straight from the request, so the form beside the import created the very
 *       duplicates the import refused.</li>
 *   <li><b>The search had no scope at all</b> and returned the whole entity, so any holder of
 *       {@code READ_ASSET} could find any asset in the bank by tag and be sent to a detail page that
 *       then refused them.</li>
 *   <li><b>The unique constraint existed — on the wrong column.</b> {@code tag_name} carries
 *       {@code @Column(unique = true)} and is NULL on all 252 rows; {@code engraved_number}, the one
 *       the application actually uses, had none.</li>
 * </ul>
 */
@SpringBootTest
class EngravedNumberTest {

    @Autowired AssetService assetService;
    @Autowired AssetRepository assetRepository;
    @Autowired EntityManager em;

    // ── The constraint, and the repair that made it possible ─────────────────

    /**
     * No engraved number is carried by two assets.
     *
     * <p>Asserted against live data rather than a fixture, because this is a claim about the estate
     * as it stands — the thing V25 repaired and the unique index now keeps true.
     */
    @Test
    void noEngravedNumberIsCarriedByTwoAssets() {
        @SuppressWarnings("unchecked")
        List<Object[]> duplicates = em.createQuery(
                        "select a.engravedNumber, count(a) from Asset a "
                                + "where a.engravedNumber is not null and trim(a.engravedNumber) <> '' "
                                + "group by a.engravedNumber having count(a) > 1")
                .getResultList();

        assertThat(duplicates)
                .as("V25 de-duplicated these and added uk_asset_engraved_number; a reappearance means "
                        + "the index was dropped or a write path stopped checking")
                .isEmpty();
    }

    /** The database enforces it, not just the service — which is what survives two concurrent saves. */
    @Test
    void theDatabaseCarriesTheConstraint() {
        @SuppressWarnings("unchecked")
        List<Object[]> index = em.createNativeQuery(
                        "select index_name, non_unique from information_schema.statistics "
                                + "where table_schema = database() and table_name = 'asset' "
                                + "and column_name = 'engraved_number'")
                .getResultList();

        assertThat(index)
                .as("a service check without a constraint is advice: two saves racing each other both "
                        + "read 'no such number' and both commit")
                .isNotEmpty();
        assertThat(((Number) index.get(0)[1]).intValue())
                .as("the index must be UNIQUE (non_unique = 0)")
                .isZero();
    }

    /**
     * NULL stays allowed, and that is the decision rather than an oversight.
     *
     * <p>33 assets have no engraved number, all of them Head Office rows at {@code requireUpdate} —
     * "not yet completed after stocking". The stocking flow creates them that way on purpose, so the
     * number can be filled in when somebody reads the label off the item. MySQL treats NULLs as
     * distinct in a unique index, which is what lets both rules hold at once.
     */
    @Test
    void assetsAwaitingCompletionMayStillHaveNoNumber() {
        long untagged = (Long) em.createQuery(
                "select count(a) from Asset a where a.engravedNumber is null").getSingleResult();

        assertThat(untagged)
                .as("if this reaches zero the fixture has changed and the NULL case is untested")
                .isGreaterThan(0);
    }

    // ── One rule, three write paths ──────────────────────────────────────────

    /** The check the form never had. */
    @Test
    void takingAnExistingNumberIsRefusedAndNamesTheAssetHoldingIt() {
        Asset existing = anAssetWithATag();

        assertThatThrownBy(() ->
                assetService.requireEngravedNumberAvailable(existing.getEngravedNumber(), null))
                .isInstanceOf(pride.bank.erp.exception.BadRequestException.class)
                .hasMessageContaining(existing.getEngravedNumber())
                .as("naming the asset is the point — 'already recorded' leaves the operator to go and "
                        + "find it, which on a bulk import means reading a spreadsheet by hand")
                .hasMessageContaining(String.valueOf(existing.getId()));
    }

    /** Re-saving a record without touching its number must not collide with itself. */
    @Test
    void anAssetDoesNotClashWithItsOwnNumber() {
        Asset existing = anAssetWithATag();

        assertThat(
                catchThrowable(() -> assetService.requireEngravedNumberAvailable(
                        existing.getEngravedNumber(), existing.getId())))
                .as("every update would otherwise be refused, including one that changed only a cost")
                .isNull();
    }

    /** Absent is allowed, or receiving stock would break. */
    @Test
    void noNumberAtAllIsAccepted() {
        assertThat(catchThrowable(() -> assetService.requireEngravedNumberAvailable(null, null)))
                .isNull();
        assertThat(catchThrowable(() -> assetService.requireEngravedNumberAvailable("   ", null)))
                .isNull();
    }

    /**
     * Matching is case-insensitive, and not because of anything the Java says.
     *
     * <p>The column is {@code utf8mb4_0900_ai_ci}, so MySQL compares it that way — which is why the
     * search and the import's {@code ...IgnoreCase} guard already agreed before any of this, and why
     * the unique index treats {@code PBL/IT/0412} and {@code pbl/it/0412} as one number. Asserted
     * because it is a property of the schema that the code silently depends on.
     */
    @Test
    void theNumberIsMatchedWithoutRegardToCase() {
        Asset existing = anAssetWithATag();

        assertThatThrownBy(() -> assetService.requireEngravedNumberAvailable(
                existing.getEngravedNumber().toLowerCase(), null))
                .isInstanceOf(pride.bank.erp.exception.BadRequestException.class);
    }

    // ── The search ───────────────────────────────────────────────────────────

    /**
     * A duplicate must degrade into telling the truth, not into a 500.
     *
     * <p>The finder returns a list now. There should be no duplicate left to exercise it, which is
     * exactly why the shape matters: the guarantee is the index, and this is what happens if the
     * index is ever lost.
     */
    @Test
    void theFinderReturnsAListSoASecondMatchCannotThrow() {
        Asset existing = anAssetWithATag();

        List<Asset> found = assetRepository.findLiveByEngravedNumber(existing.getEngravedNumber());

        assertThat(found).isNotEmpty();
        assertThat(found).allSatisfy(a -> assertThat(a.getDeleted()).isNotEqualTo(Boolean.TRUE));
    }

    /** An empty or absent tag asks nothing rather than matching everything. */
    @Test
    void anEmptySearchMatchesNothing() {
        assertThat(assetService.findAssetsByTagName(null)).isEmpty();
        assertThat(assetService.findAssetsByTagName("   ")).isEmpty();
    }

    private Asset anAssetWithATag() {
        return assetRepository.findAll().stream()
                .filter(a -> a.getEngravedNumber() != null && !a.getEngravedNumber().isBlank())
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no asset carries an engraved number"));
    }

    private static Throwable catchThrowable(Runnable action) {
        try {
            action.run();
            return null;
        } catch (Throwable t) {
            return t;
        }
    }
}
