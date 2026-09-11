package pride.bank.erp.store;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Store administration answers to the store, not only to the permission.
 *
 * <h2>What was open</h2>
 * {@code PUT /stores/{id}/active} and {@code PUT /stores/{id}/name} both reach a store <b>by id</b>
 * and were gated on {@code UPDATE_STORE} alone. Nothing afterwards asked whose store it was, so a
 * branch officer could rename or deactivate another branch's department store, and rename the
 * bank-wide IT store, by knowing its id. That is the same hole assets had before
 * {@code requireAssetInScope} — the endpoint admitted the caller and the record was never consulted.
 *
 * <p>One half was already covered by accident: {@code setActive} refuses to deactivate a store with
 * no department, which happens to include the ADMIN, IT and Disposal stores. Renaming had nothing at
 * all, and neither had any notion of <em>branch</em>.
 *
 * <h2>Two rules, because there are two kinds of store</h2>
 * An ordinary store answers to its branch — a Gulu department store is Gulu's. The IT and Disposal
 * stores answer to nobody below ALL: they are one each for the whole bank, every branch's repairs and
 * write-offs route through them, and they stand at Head Office. Guarding those by location alone
 * would hand them to any Head Office officer with branch reach, and one branch renaming a store the
 * other twenty depend on is not a branch-sized decision.
 */
@SpringBootTest
class StoreAdminScopeTest {

    @Autowired
    private StoreRepository storeRepository;

    private static final String SERVICE =
            "src/main/java/pride/bank/erp/store/StoreAdminService.java";

    /** Both entry points consult the store before changing it. */
    @Test
    void bothAdminEntryPointsAreGuarded() {
        String source = readSource(SERVICE);

        for (String method : List.of("setActive", "rename")) {
            assertThat(methodBody(source, method))
                    .as("%s reaches a store by id; without this check the caller's own branch is "
                            + "never consulted and any store in the bank is reachable", method)
                    .contains("requireStoreChangeable");
        }
    }

    /**
     * The guard runs before the work, not after it.
     *
     * <p>{@code setActive} has its own business rules — a core store cannot be deactivated, and a
     * store still holding stock cannot either. Those must not stand in for the scope check: they
     * refuse for different reasons, and a caller who trips one of them learns nothing about whether
     * they were entitled to the store in the first place.
     */
    @Test
    void theScopeCheckPrecedesTheBusinessRules() {
        String body = methodBody(readSource(SERVICE), "setActive");

        int guard = body.indexOf("requireStoreChangeable");
        int businessRule = body.indexOf("if (!active)");

        assertThat(guard).as("setActive must consult the scope check").isGreaterThanOrEqualTo(0);
        assertThat(guard)
                .as("the scope check must come before the deactivation rules")
                .isLessThan(businessRule);
    }

    /**
     * The bank-wide singletons need the explicit cross-branch grant, not merely their location.
     *
     * <p>Pinned on the branch of the guard that distinguishes them, because the whole point of the
     * rule is that these two stores are treated differently from every other. Collapsing it to a
     * plain location check would look like a simplification and would quietly re-open them to any
     * Head Office officer.
     */
    @Test
    void theSharedStoresNeedCrossBranchAuthority() {
        String body = methodBody(readSource(SERVICE), "requireStoreChangeable");

        assertThat(body)
                .as("the IT and Disposal singletons must be recognised as a distinct case")
                .contains("StoreType.IT")
                .contains("StoreType.DISPOSAL");
        assertThat(body)
                .as("and must demand ALL scope — they stand at Head Office, so a location check "
                        + "alone would grant them to any Head Office officer with branch reach")
                .contains("DashboardScope.ALL");
        assertThat(body)
                .as("every other store answers to its own branch")
                .contains("store.getLocation()");
    }

    /**
     * The singletons the rule depends on genuinely are singletons.
     *
     * <p>{@code StoreSeeder} asserts this on startup; checked here too because the guard above treats
     * IT and Disposal as one-per-bank. If a second IT store ever appeared, "the shared store" stops
     * being a well-defined thing and the rule needs rethinking rather than patching.
     */
    @Test
    @Transactional(readOnly = true)
    void theSharedStoresAreStillOnePerBank() {
        for (StoreType type : List.of(StoreType.IT, StoreType.DISPOSAL)) {
            assertThat(storeRepository.findByStoreType(type))
                    .as("%s must be a single bank-wide store; the admin guard assumes it", type)
                    .hasSizeLessThanOrEqualTo(1);
        }
    }

    // ── the stationery reports ───────────────────────────────────────────────

    /**
     * Both stationery reports were readable by anyone with a login.
     *
     * <p>Neither carried a {@code @PreAuthorize}, so both fell through to
     * {@code .anyRequest().authenticated()}. The bank-wide one returned the organisation's whole
     * stationery position to any signed-in user.
     */
    @Test
    void bothStationeryReportsAreGated() {
        String controller = readSource("src/main/java/pride/bank/erp/store/StoreController.java");

        int bankWide = controller.indexOf("@GetMapping(\"/stationery-report\")");
        int perBranch = controller.indexOf("@GetMapping(\"/stationery-report/branch\")");

        assertThat(bankWide).as("the bank-wide report must exist").isGreaterThanOrEqualTo(0);
        assertThat(perBranch).as("the branch report must exist").isGreaterThanOrEqualTo(0);

        assertThat(preAuthorizeAbove(controller, bankWide))
                .as("the bank-wide stationery report must name a permission")
                .contains("READ_STORE");
        assertThat(preAuthorizeAbove(controller, perBranch))
                .as("the per-branch stationery report must name a permission")
                .contains("READ_STORE");
    }

    /**
     * Gating it was not enough — the bank-wide figure is also narrowed.
     *
     * <p>A branch officer holding {@code READ_STORE} passes the gate, and without this would still
     * read the whole bank's total. Narrowed rather than refused: asking what stationery is in stock
     * is a reasonable question, just about one's own branch.
     */
    @Test
    void theBankWideStationeryFigureIsScoped() {
        String body = methodBody(
                readSource("src/main/java/pride/bank/erp/store/StoreService.java"),
                "findCommoditiesInStockByAssetType");

        assertThat(body)
                .as("ALL scope keeps the bank-wide figure")
                .contains("canViewAllBranches");
        assertThat(body)
                .as("everyone else gets their own branch's figure, not the bank's")
                .contains("findAssetTypeSummaryForBranch");
    }

    // ── trap #1, found while reading these queries ───────────────────────────

    /**
     * The courier exclusion must not silently drop stock that is in no store at all.
     *
     * <p>Five queries wrote {@code s.store IS NULL OR s.store.storeType <> COURIER}. The dotted path
     * compiles to an INNER JOIN in the FROM clause, which removes every balance holding no store
     * <em>before</em> the {@code IS NULL} disjunct is evaluated — so that disjunct was unreachable and
     * the queries did not do what they plainly said. The author's intent is visible in the SQL
     * sibling beside them, which correctly uses a LEFT JOIN.
     *
     * <p><b>Defensive, not a repair.</b> Measured at the time of the fix, there were no storeless
     * balances at all, so no result changed. Recorded here so nobody reads the dead branch as
     * evidence that rows were being lost.
     *
     * <p><b>Do not extend this to {@code branch}.</b> {@code summariseByBranch} groups on
     * {@code s.branch.id}, also nullable, and that inner join is load-bearing: every branchless
     * balance is in-transit courier stock, which the design says must not be counted as any branch's.
     * A LEFT JOIN there would invent a phantom no-branch group in the branch overview.
     *
     * <p>Pinned by absence, because the broken form reads as obviously correct.
     */
    @Test
    void theCourierExclusionUsesExplicitLeftJoins() {
        String repository = readSource(
                "src/main/java/pride/bank/erp/store/StoreBalanceRepository.java");

        assertThat(repository)
                .as("a dotted path through the nullable `store` inner-joins and makes the `IS NULL` "
                        + "half of its own OR unreachable — trap #1")
                .doesNotContain("s.store IS NULL OR s.store.storeType");

        /*
         * Two dotted paths through `store` survive, and correctly.
         *
         * `findOnHandByLocation` filters on the store's own location and `findCourierHeld` asks for
         * couriers: both are meaningless for a balance holding no store, so an inner join is the
         * right join and rewriting them would be a change for its own sake. Trap #1 is not "never
         * write a dotted path" — it is "never write one through an association the query is trying
         * to treat as optional".
         */
        assertThat(repository)
                .as("the legitimate uses must not be rewritten in the name of the rule")
                .contains("s.store.location.id = :locationId");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String readSource(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** The annotations immediately above a mapping, back to the previous member. */
    private static String preAuthorizeAbove(String source, int mappingIndex) {
        int from = Math.max(0, source.lastIndexOf("    }", mappingIndex));
        return source.substring(from, mappingIndex);
    }

    /**
     * The text of one method, from its declaration to the next member.
     *
     * <p>Line-based, and requires a modifier at the front of the line so a call site elsewhere in the
     * file is not mistaken for the declaration — searching the raw text for {@code " name("} finds
     * the caller first, reads a slice that happens not to contain the guard, and fails against
     * correct code.
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
        assertThat(methodBody(readSource(SERVICE), "requireStoreChangeable"))
                .as("if this is empty, the guard assertions above prove nothing")
                .isNotBlank()
                .hasSizeGreaterThan(50);
    }
}
