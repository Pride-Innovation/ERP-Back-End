package pride.bank.erp.stocktake;

import org.junit.jupiter.api.Test;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Stock take is scoped by where the counting happens, not by whose items are counted.
 *
 * <h2>Two keys, deliberately</h2>
 * A stock take is a physical act at a physical place — somebody standing at the shelves writing down
 * what is on them. So it scopes on {@code count.store.location}: a Gulu officer counts Gulu's Admin
 * store, and only Head Office counts the bank-wide IT and Disposal stores, because nobody in Gulu can
 * walk into those rooms.
 *
 * <p>Balance <em>visibility</em> scopes on the other key — {@code StoreBalance.branch}, whose item it
 * is — so a Gulu asset awaiting repair inside the Head Office IT store is Gulu's to see. Two
 * different questions, two different answers. Reading them as an inconsistency and "fixing" one to
 * match the other is the mistake this note exists to prevent.
 *
 * <p><b>Accepted consequence:</b> a branch cannot formally count its own items inside a shared store.
 * Seeing them is a report, already answered by the branch-scoped balance listing; counting them is
 * not something a branch can physically do.
 */
class StockTakeScopeTest {

    /** Every way into a count checks the store first. */
    @Test
    void everyStockCountEntryPointIsGuarded() {
        String source = readSource("src/main/java/pride/bank/erp/stocktake/StockTakeService.java");

        for (String method : List.of("openCount", "saveCounts", "submit", "approve",
                "sendBack", "cancel", "post", "fetch")) {
            assertThat(bodyOf(source, method))
                    .as("%s reaches a count by id; without a scope check a Gulu officer can act on "
                            + "Kampala's", method)
                    .containsAnyOf("requireCountInScope", "requireStoreCountable");
        }
    }

    /**
     * Posting writes the counted quantities into live balances, so it is the sharpest action in the
     * module and the one that most needs the check.
     */
    @Test
    void postingIsGuardedBeforeItTouchesBalances() {
        String body = bodyOf(
                readSource("src/main/java/pride/bank/erp/stocktake/StockTakeService.java"), "post");

        int guard = body.indexOf("requireCountInScope");
        int status = body.indexOf("requireStatus");

        assertThat(guard).as("post must consult the scope check").isGreaterThanOrEqualTo(0);
        assertThat(guard)
                .as("the scope check must come before anything else post does")
                .isLessThan(status);
    }

    /** The listing must not answer with counts from stores the caller cannot reach. */
    @Test
    void theListingIsFiltered() {
        assertThat(bodyOf(
                readSource("src/main/java/pride/bank/erp/stocktake/StockTakeService.java"), "list"))
                .as("called with no store id this returns every count in the bank")
                .contains("countIsInScope");
    }

    /**
     * The movement flows must stay untouched.
     *
     * <p>Repair transfers, disposals and returns route assets into the shared IT and Disposal stores
     * by <em>store</em>, not by branch. Adding a branch check to those paths is how scoping this
     * module would break the flows it is meant to leave alone — the explicit constraint on this work.
     *
     * <p>Pinned by absence: {@code RepairMovementService} guards on the <b>asset's</b> branch, which
     * is correct and predates this, and must not acquire a store-location check.
     */
    @Test
    void theRepairAndDisposalFlowsAreNotStoreScoped() {
        String source = readSource(
                "src/main/java/pride/bank/erp/movement/RepairMovementService.java");

        assertThat(source)
                .as("repair and disposal route through the shared stores on purpose; scoping them by "
                        + "the store's location would refuse every branch its own repairs")
                .doesNotContain("requireStoreCountable")
                .doesNotContain("requireCountInScope");
    }

    /** Segregation of duties predates this work and must survive it. */
    @Test
    void aCountStillCannotBeApprovedByWhoeverCountedIt() {
        assertThat(bodyOf(
                readSource("src/main/java/pride/bank/erp/stocktake/StockTakeService.java"), "approve"))
                .as("branch-level approval is only safe because a second pair of eyes is required; "
                        + "that rule is what makes Head-Office-only approval unnecessary")
                .contains("cannot be approved by the person who counted it");
    }

    /**
     * The adjustment history is narrowed by the same rule as the count listing — and so are its
     * totals. It answered with every store in the bank, and its headline aggregate was a separate
     * query that would have gone on disclosing bank-wide figures even after the list was narrowed.
     */
    @Test
    void theAdjustmentHistoryAndItsTotalsAreScoped() {
        String service = readSource("src/main/java/pride/bank/erp/stocktake/StockTakeService.java");
        String body = bodyOf(service, "varianceReport");

        assertThat(body).contains("storeIsInScope").contains("requireStoreCountable");
        assertThat(service)
                .as("a separate aggregate totals stores the scoped list withholds")
                .doesNotContain("summariseByReason");
    }

    /**
     * Cancel admits the counter as well as an approver — but only for their own count, and only
     * while it is still being counted. Without the counter limb an abandoned draft held its store's
     * one open slot until an approver noticed; without the narrowing, PERFORM would let any counter
     * cancel anyone's submitted sheet.
     */
    @Test
    void aCounterMayCancelOnlyTheirOwnUnsubmittedCount() {
        String body = bodyOf(
                readSource("src/main/java/pride/bank/erp/stocktake/StockTakeService.java"), "cancel");

        assertThat(body)
                .contains("APPROVE_STOCK_TAKE")
                .contains("getCountedBy()")
                .contains("StockCountStatus.DRAFT")
                .contains("StockCountStatus.IN_PROGRESS")
                .contains("AccessDeniedException")
                .as("a reason is required, as it is for sending back")
                .contains("isBlank(request.getRemarks())");

        String controller = readSource("src/main/java/pride/bank/erp/stocktake/StockTakeController.java");
        int cancel = controller.indexOf("\"/stock-counts/{countId}/cancel\"");
        String annotation = controller.substring(controller.lastIndexOf("@PreAuthorize", cancel), cancel);
        assertThat(annotation).contains("PERFORM_STOCK_TAKE").contains("APPROVE_STOCK_TAKE");
    }

    /** Every lifecycle step leaves a row in the audit trail — the ledger records stock, not people. */
    @Test
    void everyLifecycleStepIsAudited() {
        String service = readSource("src/main/java/pride/bank/erp/stocktake/StockTakeService.java");
        for (String method : List.of("openCount", "submit", "approve", "sendBack", "cancel")) {
            assertThat(bodyOf(service, method)).as("%s must be audited", method).contains("audit(");
        }
        assertThat(readSource("src/main/java/pride/bank/erp/stocktake/StockTakePostingService.java"))
                .as("posting is audited inside the transaction that changes the balances")
                .contains("STOCK_COUNT_POSTED");
    }

    /** A courier holds stock on the road; there is no shelf to count. */
    @Test
    void aCourierStoreCannotBeCounted() {
        assertThat(bodyOf(
                readSource("src/main/java/pride/bank/erp/stocktake/StockTakeService.java"), "openCount"))
                .contains("StoreType.COURIER");
    }

    /** Opening snapshots a count and all its lines; they must land together or not at all. */
    @Test
    void openingACountIsTransactional() throws Exception {
        Method open = StockTakeService.class.getMethod("openCount", StockTakeDTOs.OpenCountRequest.class);
        assertThat(open.isAnnotationPresent(org.springframework.transaction.annotation.Transactional.class))
                .as("the annotation once sat on a private helper, where Spring ignores it")
                .isTrue();
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
     * The text of one method, from its signature to the start of the next.
     *
     * <p>Crude, and deliberately so: the alternative is standing up two branches, two users, a store
     * and a populated count to assert a refusal, which would pin the fixture far more than the rule.
     * What matters is that the call is present and reached before the work — and that is exactly what
     * would be lost if someone simplified these methods later.
     */
    private static String bodyOf(String source, String methodName) {
        /*
         * Finds the declaration, not a call site.
         *
         * Searching the raw text for " name(" finds `return fetch(countId);` before it finds
         * `public CountView fetch(...)`, then reads a slice that happens not to contain the guard —
         * so the assertion fails against correct code. Line-based and requiring a modifier at the
         * front of the line separates a declaration from a call without regex escaping to get wrong.
         */
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

    /** Guards against the helper silently matching nothing and every assertion passing vacuously. */
    @Test
    void theSourceReaderActuallyFindsMethods() {
        Method[] methods = StockTakeService.class.getDeclaredMethods();
        assertThat(methods).as("StockTakeService must exist for the rest of this to mean anything")
                .isNotEmpty();

        assertThat(bodyOf(
                readSource("src/main/java/pride/bank/erp/stocktake/StockTakeService.java"), "post"))
                .as("if this is empty, every assertion above passes by reading nothing")
                .isNotBlank()
                .hasSizeGreaterThan(50);
    }
}
