package pride.bank.erp.repair;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpMethod;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.RequestMappingInfo;
import org.springframework.web.servlet.mvc.method.annotation.RequestMappingHandlerMapping;

import pride.bank.erp.asset.Asset;
import pride.bank.erp.asset.AssetRepository;
import pride.bank.erp.config.seed.SeederStateRepository;
import pride.bank.erp.movement.RepairMovementController;

import java.lang.reflect.Method;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * One repair, one journey, one record.
 *
 * <h2>What was wrong</h2>
 * The bank had two repair flows that shared a word and nothing else.
 *
 * <ul>
 *   <li>The assets page's <b>Repair</b> row option wrote a {@link Repair} row and flipped a status.
 *       No movement, so the asset never moved; no custody hand-over, so the trail still said its
 *       user held it; no approval; and no branch scope, so any holder of {@code REPAIR_ASSET} could
 *       book any asset in the bank by id. Both status ids it used were hardcoded and both were
 *       wrong — 13 is {@code sentToStore} and 12 is {@code stockCompleted}, a <em>stock-order</em>
 *       status, while the real {@code inMaintenance} is 7.</li>
 *   <li>The movements page's <b>Repair &amp; Disposal</b> modal moved the asset properly and wrote
 *       <em>no</em> maintenance record at all.</li>
 * </ul>
 *
 * <p>Measured before this change: <b>24 repair movements and 0 repair rows.</b> So the flow people
 * actually used produced nothing the maintenance report or the asset's Repair History tab could
 * show, and the flow that produced those had never been used once.
 */
@SpringBootTest
class RepairFlowUnificationTest {

    @Autowired RepairService repairService;
    @Autowired RepairRepository repairRepository;
    @Autowired AssetRepository assetRepository;
    @Autowired SeederStateRepository seederStateRepository;
    @Autowired RequestMappingHandlerMapping handlerMapping;

    private static final String SERVICE =
            "src/main/java/pride/bank/erp/repair/RepairService.java";
    private static final String MOVEMENT_SERVICE =
            "src/main/java/pride/bank/erp/movement/RepairMovementService.java";

    // ── The record the journey now carries ───────────────────────────────────

    /**
     * Opening and closing a maintenance record, end to end.
     *
     * <p>Rolled back, so it proves the behaviour without leaving a repair against a real asset. The
     * round trip is what matters: the return leg finds the record the transfer opened by matching on
     * {@code "Pending"}, and a typo in either half would leave every record open forever while
     * nothing failed.
     */
    @Test
    @Transactional
    void aTransferOpensARecordAndAReturnClosesIt() {
        Asset asset = assetRepository.findAll().stream()
                .filter(a -> !Boolean.TRUE.equals(a.getDeleted()))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no asset to open a repair against"));

        Repair opened = repairService.openForTransfer(
                asset, "Screen flickers under load", "HO IT Bench", List.of("dispatch-note.pdf"));

        assertThat(opened.getId()).isNotNull();
        assertThat(opened.getStatus()).isEqualTo("Pending");
        assertThat(opened.getRepairReason()).isEqualTo("Screen flickers under load");
        assertThat(opened.getRepairStartDate()).isNotNull();
        assertThat(opened.getRepairEndDate())
                .as("a repair that has only just been raised has not finished")
                .isNull();

        Repair closed = repairService.closeForReturn(asset, "Replaced the panel", List.of("grn.pdf"));

        assertThat(closed)
                .as("the return leg must find the record the transfer opened, or every maintenance "
                        + "record stays Pending for the life of the asset")
                .isNotNull();
        assertThat(closed.getId()).isEqualTo(opened.getId());
        assertThat(closed.getStatus()).isEqualTo("Completed");
        assertThat(closed.getRepairEndDate()).isNotNull();
        assertThat(closed.getCompletionNotes()).isEqualTo("Replaced the panel");
        assertThat(closed.getCompletionDocuments()).containsExactly("grn.pdf");
    }

    /**
     * A record with no fault on it is not a maintenance record.
     *
     * <p>The whole reason the movement flow needed one is that it had nowhere to say what failed —
     * only {@code remarks}, which describes the journey. Accepting a blank here would reproduce that
     * gap while looking like it had been fixed.
     */
    @Test
    void openingARecordRequiresSayingWhatIsWrong() {
        Asset asset = assetRepository.findAll().get(0);

        assertThatThrownBy(() -> repairService.openForTransfer(asset, "   ", "Bench", List.of()))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("what is wrong");
    }

    /**
     * Returning an asset that has no open record must still work.
     *
     * <p>Seven assets were returned from repair before the record was opened by the transfer. If
     * this threw, those would be stranded at Head Office — refusing a real movement to close a
     * reporting gap, which is the wrong way round.
     */
    @Test
    @Transactional
    void aReturnWithNoOpenRecordIsToleratedRatherThanRefused() {
        Asset asset = assetRepository.findAll().stream()
                .filter(a -> repairRepository
                        .findFirstByAsset_IdAndStatusOrderByIdDesc(a.getId(), "Pending").isEmpty())
                .findFirst()
                .orElseThrow(() -> new AssertionError("every asset has an open repair"));

        assertThat(repairService.closeForReturn(asset, "n/a", List.of()))
                .as("nothing to close is a fact, not a fault")
                .isNull();
    }

    // ── The flow that had no record, and the flow that had no movement ───────

    /** The journey opens the record, so the maintenance report reflects repairs that really happened. */
    @Test
    void theRepairTransferOpensTheMaintenanceRecord() {
        String source = read(MOVEMENT_SERVICE);

        assertThat(source)
                .as("13 repair transfers had produced 0 repair rows; the transfer must write one")
                .contains("repairService.openForTransfer(")
                .contains("movement.setRepair(repair)");

        assertThat(source)
                .as("and the return leg must close it, or every record stays Pending")
                .contains("repairService.closeForReturn(");
    }

    /**
     * The standalone "book a repair" endpoint is gone.
     *
     * <p>Checked against the live handler mapping rather than the source, because a route can be
     * registered from more than one place and what matters is whether anything answers. The reads
     * and the PUT stay — reading a maintenance history and closing a repair on the bench are real
     * and separate duties.
     */
    @Test
    void thereIsNoLongerASecondWayToBookARepair() {
        Set<String> repairRoutes = handlerMapping.getHandlerMethods().entrySet().stream()
                .filter(e -> patternsOf(e.getKey()).stream().anyMatch(p -> p.contains("/assets/repairs")))
                .flatMap(e -> methodsOf(e.getKey()).stream()
                        .map(m -> m + " " + String.join(",", patternsOf(e.getKey()))))
                .collect(java.util.stream.Collectors.toSet());

        assertThat(repairRoutes)
                .as("POST /assets/repairs/{assetId} set a status and wrote a record, moved nothing, "
                        + "and asked nobody whose asset it was")
                .noneMatch(r -> r.startsWith("POST"));

        assertThat(repairRoutes)
                .as("the maintenance history and the bench's completion must survive")
                .anyMatch(r -> r.startsWith("GET"))
                .anyMatch(r -> r.startsWith("PUT"));
    }

    // ── Trap #4: never hardcode a status id ──────────────────────────────────

    /**
     * Both literals in this service named a status nobody meant.
     *
     * <p>{@code 13L} was called "the repair status" and is {@code sentToStore}; {@code 12L} was
     * called "in store" and is {@code stockCompleted}, which belongs to stock orders and has no
     * meaning on an asset. Measured: 0 assets read either, because the flow that set them had never
     * been used — so this was latent rather than live, and would have started producing wrong
     * statuses the day anyone used it.
     */
    @Test
    void repairResolvesStatusesByCodeAndNotById() {
        /*
         * Comments stripped first, and that is not a convenience.
         *
         * The javadoc on completeRepair names both removed literals, because the next person to read
         * that method needs to know which statuses they actually were and why they were wrong. An
         * assertion about *code* that reads the whole file would fail on its own explanation - and
         * the obvious repair for that is to delete the explanation, which is the wrong trade.
         */
        String code = codeOnly(read(SERVICE));

        assertThat(code)
                .as("trap #4: ids depend on seed order, and these two named the wrong statuses in "
                        + "this very file - 13 is sentToStore, 12 is a stock-order status")
                .doesNotContain("13L")
                .doesNotContain("12L")
                .doesNotContain("findOneStatus(");

        assertThat(code)
                .as("resolved by code instead")
                .contains("findByStatusCode(CODE_SENT_TO_STORE)");
    }

    /** Closing a repair changes the asset, so it must ask whose asset it is. */
    @Test
    void closingARepairIsBranchScoped() {
        assertThat(read(SERVICE))
                .as("the endpoint checked REPAIR_ASSET and nothing afterwards asked whose asset it "
                        + "was — the same gap requireAssetInScope closed on the movement side")
                .contains("requireAssetInScope(asset)")
                .contains("requireCanManage(");
    }

    // ── The permission the repair flows answer to ────────────────────────────

    /**
     * Repair is an asset action, as disposal already was.
     *
     * <p>These three endpoints asked for {@code CREATE_MOVEMENT} because they were written as
     * movement endpoints. Measured: <b>four accounts at Gulu and Mbarara held {@code CREATE_MOVEMENT}
     * without {@code REPAIR_ASSET}</b> — so they could send an asset for repair through the modal and
     * not through the assets page, and the permission named for repair was walked around by the
     * route people actually use.
     */
    @Test
    void theRepairMovementFlowsAnswerToRepairAsset() {
        assertThat(preAuthorizeOn("transferForRepair")).contains("REPAIR_ASSET");
        assertThat(preAuthorizeOn("issueTemporaryReplacement")).contains("REPAIR_ASSET");
        assertThat(preAuthorizeOn("returnAfterRepair")).contains("REPAIR_ASSET");

        assertThat(preAuthorizeOn("transferForRepair"))
                .as("and not by also still accepting the movement permission, or the tightening "
                        + "would be decorative")
                .doesNotContain("CREATE_MOVEMENT");
    }

    /**
     * The previews take a disjunction, deliberately.
     *
     * <p>A preview says where an asset would go. Gating it on {@code READ_MOVEMENT} alone would show
     * the repair form to somebody entitled to perform the repair and then refuse to tell them the
     * destination — the "renders a control whose endpoint then refuses it" failure, one screen
     * further in.
     */
    @Test
    void thePreviewsAdmitWhoeverMayPerformTheAction() {
        for (String method : List.of("previewRepairTransfer", "previewReturnAfterRepair",
                "previewTempReplacement")) {
            assertThat(preAuthorizeOn(method))
                    .as("%s must admit a repairer as well as a movement reader", method)
                    .contains("hasAnyAuthority")
                    .contains("REPAIR_ASSET")
                    .contains("READ_MOVEMENT");
        }
    }

    /**
     * The continuity backfill is pinned by its <b>marker</b>, never by its grants.
     *
     * <p>CLAUDE.md records why: asserting the effect against live data turns an administrator
     * revoking the permission in Settings — the feature working — into a build failure. What must
     * hold is that the step ran once and cannot run again, because re-running it would silently
     * restore a permission somebody deliberately took away.
     */
    @Test
    void theRepairPermissionBackfillRanExactlyOnce() {
        assertThat(seederStateRepository.existsById("REPAIR_ASSET_ON_MOVEMENT_REPAIR_FLOWS"))
                .as("the one-time grant must be recorded, or it re-runs on every restart and "
                        + "reverses whatever Settings has since decided")
                .isTrue();
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private String preAuthorizeOn(String methodName) {
        Method method = java.util.Arrays.stream(RepairMovementController.class.getDeclaredMethods())
                .filter(m -> m.getName().equals(methodName))
                .findFirst()
                .orElseThrow(() -> new AssertionError("no such handler: " + methodName));
        PreAuthorize annotation = method.getAnnotation(PreAuthorize.class);
        assertThat(annotation)
                .as("%s must carry a rule of its own — nothing under /movements/** matches a "
                        + "SecurityConfiguration rule except disposal", methodName)
                .isNotNull();
        return annotation.value();
    }

    private static Set<String> patternsOf(RequestMappingInfo info) {
        return info.getPathPatternsCondition() != null
                ? info.getPathPatternsCondition().getPatternValues()
                : Set.of();
    }

    private static Set<String> methodsOf(RequestMappingInfo info) {
        Set<HttpMethod> methods = info.getMethodsCondition().getMethods().stream()
                .map(m -> HttpMethod.valueOf(m.name()))
                .collect(java.util.stream.Collectors.toSet());
        return methods.stream().map(HttpMethod::name).collect(java.util.stream.Collectors.toSet());
    }

    /** Java source with block and line comments removed, so an assertion about code sees only code. */
    private static String codeOnly(String source) {
        return source
                .replaceAll("(?s)/\\*.*?\\*/", "")
                .replaceAll("(?m)//.*$", "");
    }

    private static String read(String path) {
        try {
            return Files.readString(Path.of(path));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }
}
