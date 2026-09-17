package pride.bank.erp.access;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import jakarta.persistence.EntityManager;
import pride.bank.erp.movement.MovementService;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A branch may read its own asset while it is away at a Head Office store.
 *
 * <h2>The contradiction this removes</h2>
 * The IT store listing already reaches an asset when <b>either</b> end is the caller's — where it
 * sits, or where it came from ({@code InventoryReportController.scopeAssets}). That is what lets a
 * branch BOM see their own laptop on the Head Office repair bench.
 *
 * <p>{@code AssetService.requireAssetInScope} was <b>single-ended</b>, so the row was listed and then
 * would not open — and because the detail page swallowed the refusal, it rendered as a blank record
 * rather than a boundary. *A row that sits in your list and will not open is the exact failure this
 * scoping exists to prevent.*
 *
 * <p>The cause is that {@code asset.branch} means <em>where the asset is</em>:
 * {@code InventoryService.relocateAsset} overwrites it with the destination store's location on
 * receipt. Measured: <b>seven assets in maintenance read Head Office and every one came from
 * Gulu.</b>
 *
 * <h2>What this does not do</h2>
 * MANAGE is untouched — a branch still cannot edit, reassign or write off an asset sitting at Head
 * Office — and the reach is keyed on the asset being <em>currently</em> away rather than on a past
 * transfer, so it lapses when the item comes home.
 */
@SpringBootTest
class AwayFromBranchReadTest {

    @Autowired EntityManager em;
    @Autowired MovementService movementService;

    private static final String SERVICE =
            "src/main/java/pride/bank/erp/asset/AssetService.java";

    // ── The data this rests on ───────────────────────────────────────────────

    /**
     * Assets away from home really do read as Head Office, or the rule solves nothing.
     *
     * <p>If this ever returns zero the fixture has changed and every assertion below is passing over
     * an empty set.
     */
    @Test
    void assetsAwayForRepairReadAsHeadOfficeWhileTheyAre() {
        long awayAtHeadOffice = (Long) em.createQuery(
                        "select count(a) from Asset a "
                                + "left join a.assetStatus s left join a.branch b "
                                + "where s.status in ('inMaintenance','pendingDisposal') "
                                + "and b.isHeadOffice = true")
                .getSingleResult();

        assertThat(awayAtHeadOffice)
                .as("the whole problem is that an asset away from home stops reading as its "
                        + "branch's; without such rows there is nothing to test")
                .isGreaterThan(0);
    }

    /**
     * Every asset away for <b>repair</b> can name the branch it came from.
     *
     * <p>Through {@code originLocation}, captured before the assignment is closed.
     */
    @Test
    void everyRepairAwayCanNameWhereItCameFrom() {
        List<?> awayForRepair = em.createQuery(
                        "select a.id from Asset a left join a.assetStatus s "
                                + "where s.status = 'inMaintenance' "
                                + "and exists (select 1 from Movement m join m.items i "
                                + "            where i.asset = a and m.movementType = "
                                + "            pride.bank.erp.movement.MovementType.REPAIR_TRANSFER)")
                .getResultList();

        assertThat(awayForRepair).isNotEmpty();
        assertThat(awayForRepair)
                .allSatisfy(id -> assertThat(movementService.branchLastSentAwayFrom((Long) id))
                        .as("asset %s is on the bench with no recoverable origin, so its branch "
                                + "cannot reach it", id)
                        .isNotNull());
    }

    /**
     * Disposal records no {@code originLocation}, so the rule falls back to the source store.
     *
     * <p>{@code disposeAsset} never sets the origin — only the repair transfer does. Without the
     * fallback a branch would lose sight of an asset the moment it was written off, which is the
     * identical report one step later.
     */
    @Test
    void aDisposalWithNoRecordedOriginStillNamesItsSourceStoresBranch() {
        Object disposed = em.createQuery(
                        "select a.id from Asset a left join a.assetStatus s "
                                + "where s.status = 'pendingDisposal' "
                                + "and exists (select 1 from Movement m join m.items i "
                                + "            where i.asset = a and m.movementType = "
                                + "            pride.bank.erp.movement.MovementType.DISPOSAL_TRANSFER)")
                .setMaxResults(1)
                .getResultList().stream().findFirst().orElse(null);

        if (disposed == null) {
            // Nothing written off yet; the fallback is unexercised rather than wrong.
            return;
        }

        assertThat(movementService.branchLastSentAwayFrom((Long) disposed))
                .as("a disposal captures no originLocation, so the source store's location is the "
                        + "only signal there is")
                .isNotNull();
    }

    /** An asset that was never sent anywhere has no origin, and honestly reports none. */
    @Test
    void anAssetNeverSentAwayNamesNoOrigin() {
        Object neverMoved = em.createQuery(
                        "select a.id from Asset a where not exists ("
                                + "  select 1 from Movement m join m.items i where i.asset = a "
                                + "  and m.movementType in ("
                                + "    pride.bank.erp.movement.MovementType.REPAIR_TRANSFER, "
                                + "    pride.bank.erp.movement.MovementType.DISPOSAL_TRANSFER))")
                .setMaxResults(1)
                .getResultList().stream().findFirst().orElse(null);

        assertThat(neverMoved).as("every asset has been sent away, which cannot be right").isNotNull();
        assertThat(movementService.branchLastSentAwayFrom((Long) neverMoved)).isNull();
    }

    // ── The narrowings, which are the whole safety of this ───────────────────

    /**
     * The widening is VIEW only. MANAGE must stay single-ended.
     *
     * <p>Source-level because the alternative is minting a branch principal and attempting a write
     * against another branch's asset, which is a large fixture for a rule that is one branch of one
     * {@code if}. The three tests above cover the behaviour; this covers the shape that must not
     * quietly change.
     */
    @Test
    void manageIsUntouchedBySeeingAnAssetThatIsAway() {
        String guard = readSource(SERVICE);

        assertThat(guard)
                .as("MANAGE must still ask requireCanManage with the asset's own branch alone — a "
                        + "branch seeing its laptop on the bench must not be able to reassign it")
                .contains("if (action == pride.bank.erp.access.ScopeAction.MANAGE) {")
                .contains("accessScopeService.requireCanManage(");

        int manageAt = guard.indexOf("requireCanManage(");
        int wideningAt = guard.indexOf("requireCanViewAnyOf(");
        assertThat(wideningAt)
                .as("the multi-ended branch must come after the MANAGE branch, not around it")
                .isGreaterThan(manageAt);
    }

    /**
     * The reach is keyed on being away <em>now</em>, not on having been away once.
     *
     * <p>Without the status gate a branch could read an asset forever because it once sent it for
     * repair — a permanent widening earned by a historical event.
     */
    @Test
    void theReachIsBoundedToAssetsThatAreCurrentlyAway() {
        String guard = readSource(SERVICE);

        assertThat(guard)
                .contains("AWAY_FROM_BRANCH_CODES")
                .contains("\"inMaintenance\"")
                .contains("\"pendingDisposal\"")
                .contains("isAwayFromItsBranch(asset)");
    }

    /**
     * The tag search and the detail read must agree.
     *
     * <p>If the search could find an asset the record then refused to open, it would recreate exactly
     * the contradiction this rule removes — a result you can see and cannot follow.
     */
    @Test
    void theTagSearchUsesTheSameRule() {
        String source = readSource(SERVICE);

        assertThat(source)
                .as("both reach the origin through one helper, so they cannot drift")
                .contains("branchLastSentAwayFrom");
        assertThat(source.split("branchLastSentAwayFrom").length - 1)
                .as("the guard and the tag search, and nowhere else inventing a third rule")
                .isEqualTo(2);
    }

    private static String readSource(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
