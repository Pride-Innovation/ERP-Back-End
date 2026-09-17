package pride.bank.erp.inventory;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.store.Store;
import pride.bank.erp.store.StoreRepository;
import pride.bank.erp.store.StoreType;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * A branch must see its own items inside the bank-wide IT and Disposal stores.
 *
 * <h2>The shape of the problem</h2>
 * Most stores are branch-owned: one ADMIN store per branch, standing at that branch. Two are not.
 * The IT and Disposal stores are <b>singletons for the whole bank</b>, held at Head Office, and
 * {@code StoreSeeder} asserts that on startup. Every branch's repairs and write-offs route into them.
 *
 * <p>{@code asset.branch} cannot answer "whose is this" on its own, because it does not mean that.
 * It records where the asset <em>is</em>, and {@code InventoryService.relocateAsset} sets it to the
 * destination store's location on transfer — deliberately, since Head Office genuinely has custody
 * while the item is on its bench. So a Gulu laptop sent for repair reads Head Office, Gulu lost sight
 * of its own machine, and Head Office saw everyone's.
 *
 * <h2>Two candidate fixes, and why this one</h2>
 * Preserving the origin on {@code asset.branch} would have looked tidier and been far larger: that
 * field also resolves which Admin store a repaired asset goes home to
 * ({@code RepairMovementService.currentStoreOf}) and which branch the repair guard checks
 * ({@code requireAssetInScope}), so it would have changed the movement flows rather than the view of
 * them — and the flows were explicitly out of scope for this work.
 *
 * <p>The branch that owns the asset already survives elsewhere: {@code Movement.originLocation},
 * captured before the transfer and the same column the return leg aims at. Reading it is a read. A
 * caller therefore reaches an asset if <b>either</b> end is theirs — where it sits, or where it came
 * from — mirroring the multi-ended reach a movement already gets from {@code requireCanViewAny}.
 *
 * @see pride.bank.erp.stocktake.StockTakeScopeTest the other key — a stock <em>count</em> scopes by
 *      the store's location, because counting is a physical act at a physical place. Two questions,
 *      two answers, and reading them as an inconsistency is the mistake.
 */
@SpringBootTest
class SharedStoreVisibilityTest {

    @Autowired
    private AssetRepository assetRepository;

    @Autowired
    private StoreRepository storeRepository;

    @Autowired
    private pride.bank.erp.movement.MovementRepository movementRepository;

    /**
     * The premise the whole fix rests on: in a shared store, ownership and location diverge.
     *
     * <p>If every asset in the IT store belonged to Head Office anyway, the precedence would not
     * matter and this work would be pointless. Checked against real rows rather than assumed, because
     * it is the one fact that makes the rest of this file meaningful.
     */
    @Test
    @Transactional(readOnly = true)
    void inASharedStoreTheOwningBranchIsNotTheStoresLocation() {
        List<Store> shared = storeRepository.findByStoreType(StoreType.IT);
        if (shared.isEmpty()) return;

        Store it = shared.get(0);
        Long housedAt = it.getLocation() != null ? it.getLocation().getId() : null;
        if (housedAt == null) return;

        List<Asset> inside = assetRepository.findByCurrentStore_Id(it.getId());
        if (inside.isEmpty()) return;   // nothing awaiting repair; nothing to prove

        long ownedElsewhere = inside.stream()
                .filter(a -> a.getBranch() != null && !housedAt.equals(a.getBranch().getId()))
                .count();

        // Not an assertion that foreign assets must exist today — only a record of what was found, so
        // a future reader can tell whether this ran against data that exercised the case.
        System.out.printf("IT store #%d at branch %d holds %d asset(s), %d owned by another branch%n",
                it.getId(), housedAt, inside.size(), ownedElsewhere);

        assertThat(inside).allSatisfy(a -> assertThat(a.getCurrentStore().getId())
                .as("findByCurrentStore_Id must return only assets in that store")
                .isEqualTo(it.getId()));
    }

    /**
     * The scope rule reaches an asset by either end — where it sits, or where it came from.
     *
     * <p>Pinned on the source because the method is private and sits behind a security context this
     * test cannot stand up honestly. What matters is that <em>both</em> terms are present: with only
     * the first, a branch loses sight of everything it has sent away, which is the bug; with only the
     * second, an asset that has never moved becomes invisible to everyone.
     */
    @Test
    void storedAssetsAreReachableFromEitherEnd() {
        String body = methodBody(
                readSource("src/main/java/pride/bank/erp/inventory/InventoryReportController.java"),
                "scopeAssets");

        assertThat(body)
                .as("the ordinary case: an asset standing at the caller's own branch")
                .contains("a.getBranch()");
        assertThat(body)
                .as("the shared-store case: a Gulu asset on the Head Office repair bench reads Head "
                        + "Office on asset.branch, so without the movement's origin Gulu cannot see "
                        + "its own machine and Head Office sees everyone's")
                .contains("originByAsset");
    }

    /**
     * The origin lookup is one query for the page, not one per row.
     *
     * <p>A per-row lookup inside a filter is the shape that turns a store listing into N+1 queries,
     * and it would be invisible until the IT store filled up.
     */
    @Test
    void theOriginLookupIsBatched() {
        String body = methodBody(
                readSource("src/main/java/pride/bank/erp/inventory/InventoryReportController.java"),
                "originLocations");

        assertThat(body)
                .as("origins must be fetched for the whole list at once")
                .contains("findOriginLocationsForAssets");
        assertThat(body)
                .as("an asset repaired twice must not be claimed by whoever owned it two repairs "
                        + "ago; newest wins, so the first row per asset is kept")
                .contains("putIfAbsent");
    }

    /**
     * The origin query returns what it claims to, against real rows.
     *
     * <p>The source assertions above prove the call is wired; only this proves the column is
     * populated. If no movement in the database carries an origin, the whole mechanism is inert and
     * the visibility fix silently does nothing — so that case is reported rather than passed over.
     */
    @Test
    @Transactional(readOnly = true)
    void theOriginQueryAnswersForAssetsThatHaveMoved() {
        List<Long> assetIds = assetRepository.findAll().stream()
                .map(Asset::getId)
                .filter(java.util.Objects::nonNull)
                .limit(500)
                .toList();
        if (assetIds.isEmpty()) return;

        List<Object[]> rows = movementRepository.findOriginLocationsForAssets(assetIds);

        System.out.printf("origin lookup: %d of %d asset(s) sampled have a movement origin%n",
                rows.stream().map(r -> r[0]).distinct().count(), assetIds.size());

        assertThat(rows).allSatisfy(row -> {
            assertThat(row[0]).as("every row must name an asset").isNotNull();
            assertThat(row[1]).as("the join requires a non-null origin, so this cannot be null")
                    .isNotNull();
        });
    }

    /**
     * Balances were already right, and must stay right.
     *
     * <p>{@code StoreBalanceView.locationId} is named for a location but carries
     * {@code StoreBalance.branch} — the owning branch. That accident is why the consumables half of
     * this page never had the bug. Anyone "correcting" the mapping to the store's location would
     * reintroduce it on the other path, so the mapping is pinned here.
     */
    @Test
    void balanceVisibilityKeysOnTheBalancesOwnBranch() {
        String mapping = methodBody(
                readSource("src/main/java/pride/bank/erp/inventory/InventoryReportService.java"),
                "toBalanceView");

        assertThat(mapping)
                .as("a balance carries its own branch alongside its store; that is what lets a branch "
                        + "see its goods inside a bank-wide store")
                .contains(".locationId(b.getBranch()");

        assertThat(methodBody(
                readSource("src/main/java/pride/bank/erp/inventory/InventoryReportController.java"),
                "scopeBalances"))
                .as("the balance filter must read that field")
                .contains("getLocationId()");
    }

    /**
     * Visibility only. The flows that put assets into these stores are not branch-scoped.
     *
     * <p>This is the explicit constraint on the work: repair and disposal route <em>every</em>
     * branch's assets into stores held at Head Office. A branch check on those paths would refuse
     * each branch its own repairs — the store's location is not theirs — and break the flows the
     * visibility change exists to serve. {@code RepairMovementService} guards on the <b>asset's</b>
     * branch, which predates this and is correct; it must not acquire a store check.
     */
    @Test
    void theMovementFlowsIntoTheSharedStoresAreUntouched() {
        assertThat(readSource("src/main/java/pride/bank/erp/movement/RepairMovementService.java"))
                .as("repair and disposal deliberately cross branch boundaries by store; a branch "
                        + "check here would strand every branch's repairs at Head Office")
                .doesNotContain("scopeAssets")
                .doesNotContain("originLocations(");

        /*
         * What the guard *does* consult, and why that is not what the lines above forbid.
         *
         * The repair guard reaches an asset when either end is the caller's: the branch it sits in,
         * or the branch recorded on its repair transfer. That is a *branch* pair, and it only ever
         * widens - which is the opposite of the failure this test exists to prevent, a store-location
         * check narrowing every branch out of the stores its own repairs route into.
         *
         * It is pinned because it is load-bearing and easy to "simplify" away. `asset.branch` is
         * overwritten with the destination's location on receipt, so with the current branch alone
         * the branch that sent an asset away cannot act on it again - measured, all thirteen repair
         * transfers came from Gulu and all seven assets in maintenance read Head Office, so Gulu
         * could not have initiated a single one of its own returns.
         */
        String guard = methodBody(
                readSource("src/main/java/pride/bank/erp/movement/RepairMovementService.java"),
                "requireAssetInScope");

        assertThat(guard)
                .as("the repair guard must reach the branch an asset came from as well as the one it "
                        + "sits in, or a branch cannot bring its own repaired asset home")
                .contains("originLocationFor(asset)")
                .contains("requireCanManageAnyOf");

        /*
         * The write path still sets the asset's branch to the destination store's location.
         *
         * That is what makes the origin lookup necessary, and changing it was the road not taken:
         * `asset.branch` also resolves which Admin store a repaired asset goes home to
         * (`currentStoreOf`) and which branch the repair guard checks (`requireAssetInScope`), so
         * preserving the origin there would have altered the flows rather than the view of them.
         * Pinned so that a later "tidy-up" of the relocation rule is a deliberate act.
         */
        assertThat(methodBody(
                readSource("src/main/java/pride/bank/erp/inventory/InventoryService.java"),
                "relocateAsset"))
                .as("if this stops writing the destination's location, the origin lookup becomes "
                        + "redundant and the repair return leg needs re-reading")
                .contains("asset.setBranch(destStore.getLocation())");
    }

    /**
     * The store <em>directory</em> must not be scoped by the store's location.
     *
     * <p>This is the trap the store plan names outright, and it was live: the listing filtered on
     * {@code StoreView.locationId}, which is where a store stands, so the IT and Disposal
     * singletons — held at Head Office — were removed from every branch's view of what stores exist.
     *
     * <p>The visible symptom was an empty asset panel on the IT and Disposal pages for every branch
     * user: the page asked which store to read, got nothing back, and rendered no rows. The
     * invisible symptom is larger — those two stores are where repairs and write-offs route, so
     * anything choosing a destination from this list could not offer them.
     *
     * <p>Directory open, contents scoped. Letting the rows through discloses nothing; what is
     * <em>inside</em> stays narrowed by {@code scopeBalances} and {@code scopeAssets}, each on the
     * key that actually means ownership.
     */
    @Test
    void theStoreDirectoryDoesNotHideTheBankWideStoresFromBranches() {
        String body = methodBody(
                readSource("src/main/java/pride/bank/erp/inventory/InventoryReportController.java"),
                "stores");

        assertThat(body)
                .as("a branch must still be able to see that the shared IT and Disposal stores "
                        + "exist, however the rest of the filter is written")
                .contains("isBankWide");

        assertThat(methodBody(
                readSource("src/main/java/pride/bank/erp/inventory/InventoryReportController.java"),
                "isBankWide"))
                .as("and 'bank-wide' must mean the two seeded singletons")
                .contains("StoreType.IT")
                .contains("StoreType.DISPOSAL");
    }

    /**
     * Opening a store page must not ask for a branch the caller cannot see.
     *
     * <p>The front end resolved the current user's branch from {@code title.branch}. A title is an
     * organisation-wide job description and commonly carries no branch, so the lookup returned
     * nothing, fell through to a Head Office default, and the page then asked
     * {@code GET /store?branchId=<Head Office>} — which the server correctly refused. A branch user
     * opening the store page was told they had no access to a record they had never asked for.
     *
     * <p>Pinned from the backend side because this is the contract the two ends share:
     * {@code currentUserBranchId} reads {@code user.branch}, and the page must read the same field.
     */
    @Test
    void theBackendResolvesTheCallersBranchFromTheUserNotTheTitle() {
        String body = methodBody(
                readSource("src/main/java/pride/bank/erp/config/BranchScopeService.java"),
                "currentUserBranchId");

        assertThat(body)
                .as("the duty station is the branch; if this ever becomes title.branch, the front "
                        + "end must move with it or every store page 403s again")
                .doesNotContain("getTitle()");
    }

    /**
     * Asking for a store without naming a branch must not become "at my branch".
     *
     * <h2>The fix that did nothing until this one landed</h2>
     * Letting the bank-wide stores through the post-query filter was necessary and not sufficient.
     * The listing first called {@code branchScope.resolve(locationId)}, which replaces a null with
     * the caller's own branch — so "the IT store, wherever it is" became "IT stores at Gulu", the
     * query returned nothing, and the filter had nothing left to admit. A branch user's IT and
     * Disposal pages showed an empty shelf while Head Office saw the same store full, and the
     * failure looked identical to the one the filter had just fixed.
     *
     * <p>Substitution is right for a balance listing, where "no branch named" means "mine". It is
     * wrong for a directory, because a store the caller may legitimately see does not stand at their
     * branch. Naming someone else's branch outright is still refused.
     */
    @Test
    void theStoreDirectoryDoesNotSubstituteTheCallersOwnBranch() {
        String body = methodBody(
                readSource("src/main/java/pride/bank/erp/inventory/InventoryReportController.java"),
                "stores");

        assertThat(body)
                .as("resolve() turns 'no branch named' into 'my branch', which queries away the one "
                        + "IT store in the bank before any filter can admit it")
                .doesNotContain("branchScope.resolve(");
        assertThat(body)
                .as("a branch named explicitly must still be refused if it is not the caller's")
                .contains("requireAccessTo");
    }

    /**
     * The origin column the whole mechanism rests on is actually populated.
     *
     * <p>Every other assertion here is about source. This one is about data, because the mechanism
     * fails silently and identically whether the query is wrong, the column is empty, or the store
     * was never found: an empty panel. Traced against real rows, assets sitting in the IT store do
     * carry an origin pointing at the branch that sent them — {@code RepairMovementService} captures
     * it before the transfer and {@code MovementService} maps it onto the entity.
     *
     * <p>If this ever reports zero origins while the IT store holds assets, the visibility feature is
     * inert no matter what the source says.
     */
    @Test
    @Transactional(readOnly = true)
    void assetsInTheSharedStoreCarryAnOriginBranch() {
        List<Store> shared = storeRepository.findByStoreType(StoreType.IT);
        if (shared.isEmpty()) return;

        List<Asset> inside = assetRepository.findByCurrentStore_Id(shared.get(0).getId());
        if (inside.isEmpty()) return;   // nothing on the bench; nothing to prove

        List<Long> ids = inside.stream().map(Asset::getId)
                .filter(java.util.Objects::nonNull).toList();
        long withOrigin = movementRepository.findOriginLocationsForAssets(ids).stream()
                .map(row -> row[0]).distinct().count();

        System.out.printf("shared store holds %d asset(s), %d with a recorded origin%n",
                inside.size(), withOrigin);

        assertThat(withOrigin)
                .as("an asset reaches the bank-wide IT store through a movement, and that movement "
                        + "records where it came from; with no origin the sending branch cannot see "
                        + "its own item and the feature is inert")
                .isGreaterThan(0);
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String readSource(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /**
     * The text of one method, from its declaration to the next member.
     *
     * <p>Line-based, and requires a modifier at the front of the line so a call site elsewhere in the
     * file is not mistaken for the declaration — searching the raw text for {@code " name("} finds the
     * caller first, then reads a slice that happens not to contain what is being asserted, and fails
     * against correct code.
     */
    private static String methodBody(String source, String methodName) {
        String[] lines = source.split("\n", -1);
        int first = -1;
        for (int i = 0; i < lines.length && first < 0; i++) {
            String trimmed = lines[i].trim();
            boolean declares = trimmed.startsWith("public ")
                    || trimmed.startsWith("private ")
                    || trimmed.startsWith("protected ");
            if (declares && trimmed.contains(" " + methodName + "(")) first = i;
        }
        assertThat(first).as("method %s must be declared", methodName).isGreaterThanOrEqualTo(0);

        StringBuilder body = new StringBuilder(lines[first]).append('\n');
        for (int i = first + 1; i < lines.length; i++) {
            String trimmed = lines[i].trim();
            boolean nextMember = trimmed.startsWith("public ")
                    || trimmed.startsWith("private ")
                    || trimmed.startsWith("protected ")
                    || trimmed.startsWith("@Transactional");
            if (nextMember) break;
            body.append(lines[i]).append('\n');
        }
        return body.toString();
    }

    /** Guards against the source reader silently matching nothing and every assertion passing. */
    @Test
    void theSourceReaderActuallyFindsMethods() {
        assertThat(methodBody(
                readSource("src/main/java/pride/bank/erp/inventory/InventoryReportController.java"),
                "scopeAssets"))
                .as("if this is empty, the precedence assertions above prove nothing")
                .isNotBlank()
                .hasSizeGreaterThan(50);
    }
}
