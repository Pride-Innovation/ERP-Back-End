package pride.bank.erp.request;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * The request module's access rules, pinned where they are easiest to erode.
 *
 * <h2>What was open</h2>
 * {@code AccessScopeService} appeared exactly once in {@code RequestService} — inside
 * {@code requestersList()}, which feeds only the listing. Everything reached <b>by id</b> — the
 * single read, the update, the delete — asked for a permission and then nothing further, so a
 * request id was enough to read, edit or delete another branch's request. That is the same gap
 * assets had before {@code requireAssetInScope}.
 *
 * <p>Worse, the listing's own scope could be switched off from the client: {@code RequestSearchDao}
 * skips the requester restriction whenever {@code currentApproverId} is set — correct in itself,
 * since an approver often sits above the requester — and that parameter arrived straight from the
 * request. Naming a colleague returned their whole inbox, from every branch.
 *
 * <p>Pinned on the source rather than by standing up two users and a workflow: what these rules are
 * worth is that they are <em>called at all</em>, and a behavioural test with one principal would
 * pass against the broken version. The behaviour that can be tested honestly — that the resolver
 * narrows a foreign id — is covered below without a fixture.
 */
class RequestScopeTest {

    private static final String SERVICE = "src/main/java/pride/bank/erp/request/RequestService.java";

    /** Every by-id entry point must consult the scope before it acts. */
    @Test
    void readUpdateAndDeleteAreAllGuarded() {
        String source = read(SERVICE);

        assertThat(methodBody(source, "fetchSingleRequest"))
                .as("a request id was enough to read any branch's request")
                .contains("requireRequestReadable");

        assertThat(methodBody(source, "updateRequest"))
                .as("a request id was enough to edit any branch's request")
                .contains("requireRequestChangeable");

        assertThat(methodBody(source, "deleteRequest"))
                .as("a request id was enough to delete any branch's request")
                .contains("requireRequestChangeable");
    }

    /**
     * Reading is wider than changing, and deliberately so.
     *
     * <p>An approval step routinely routes a branch request to somebody at Head Office or in a unit.
     * A plain branch check on the read would refuse the very people the workflow just asked to act,
     * and the inbox would list rows that will not open. Changing gets no such exception: being asked
     * to approve something is not a licence to edit or delete it.
     */
    @Test
    void theReadGuardAdmitsARoutedApproverAndTheWriteGuardDoesNot() {
        String source = read(SERVICE);

        assertThat(methodBody(source, "requireRequestReadable"))
                .as("an approver at another branch must still be able to open what was routed to them")
                .contains("isRoutedToCaller");

        assertThat(methodBody(source, "requireRequestChangeable"))
                .as("routing must not widen a write; approving is guarded separately, by the engine")
                .doesNotContain("isRoutedToCaller");
    }

    /**
     * Approving a request must not cost you the right to read it.
     *
     * <h2>The bug this pins</h2>
     * {@code currentApprover} names whoever must act <b>now</b>, and the engine moves it to the next
     * tier as part of approving. So the "routed to me" limb — which is what let an approver open the
     * record in the first place — stopped being true at the instant they approved, and the detail
     * page, which refetches as the modal closes, answered "You do not have access to this record".
     *
     * <p>A supervisor is SELF-scoped by default, and at SELF the scope check keys on the requester,
     * who is somebody else; so every other limb failed too. The request could meanwhile stay in their
     * listing, since a subordinate's requests are in reach there — a row in your list that will not
     * open.
     *
     * <p>Being routed a request is durable, not momentary: the fix is a limb that asks whether the
     * workflow ever involved you, which the step log records permanently.
     */
    @Test
    void anApproverKeepsAccessToWhatTheyApproved() {
        String source = read(SERVICE);

        assertThat(methodBody(source, "requireRequestReadable"))
                .as("without this, approving is the act that takes the record away from you")
                .contains("hasActedOn");

        assertThat(methodBody(source, "hasActedOn"))
                .as("the step log is what remembers participation after the routing has moved on")
                .contains("hasActedOnRequest");

        assertThat(methodBody(
                read("src/main/java/pride/bank/erp/workflowinstance/WorkflowEngineService.java"),
                "hasActedOnRequest"))
                .as("answered from the step log, which survives the routing advancing")
                .contains("existsByRequestIdAndActorId");
    }

    /**
     * Participation widens the read only — never the write.
     *
     * <p>Having approved something is not a licence to edit or delete it afterwards; that stays with
     * the requester and their branch, exactly as being asked to approve does.
     */
    @Test
    void havingApprovedSomethingDoesNotLetYouChangeIt() {
        assertThat(methodBody(read(SERVICE), "requireRequestChangeable"))
                .as("the write guard must consult none of the read's widening limbs")
                .doesNotContain("hasActedOn")
                .doesNotContain("isRoutedToCaller")
                .doesNotContain("mayIssue");
    }

    /**
     * An issuer can open the request they are issuing against.
     *
     * <p>Issuance is cross-branch by design — Head Office fulfils a branch's request — and neither of
     * the other two limbs reaches: the requester's branch is not the issuer's, and the "routed to me"
     * exception has already lapsed, because the engine nulls {@code currentApprover} the moment the
     * last step completes, which is exactly when issuing begins.
     *
     * <p>Measured when this was added: of the ten accounts holding {@code ISSUE_ITEMS}, five had no
     * cross-branch request reach and would have lost the job the permission names.
     *
     * <p>Read only — the write guard must not consult it.
     */
    @Test
    void anIssuerMayReadTheRequestTheyAreIssuingAgainstButNotChangeIt() {
        String source = read(SERVICE);

        assertThat(methodBody(source, "requireRequestReadable"))
                .as("without this limb, five of ten issuers cannot open a branch request to fulfil it")
                .contains("mayIssue");

        assertThat(methodBody(source, "requireRequestChangeable"))
                .as("issuing against a request is not a licence to edit or delete it")
                .doesNotContain("mayIssue");
    }

    /**
     * The approval-inbox parameter is the caller's own unless they may act on any branch.
     *
     * <p>This is the same fault the movements inbox had, where the approver came from the path. The
     * override is the explicit cross-branch grant, because that is exactly what it means.
     */
    @Test
    void theApproverInboxCannotBeReadForSomebodyElse() {
        String body = methodBody(read(SERVICE), "resolveApproverInbox");

        assertThat(body)
                .as("the caller's own id is the default answer")
                .contains("requested.equals(own)");
        assertThat(body)
                .as("only the explicit cross-branch grant may read another approver's inbox")
                .contains("MANAGE");
        assertThat(body)
                .as("and using the override should be visible afterwards")
                .contains("log.info");
    }

    /**
     * A SELF-scoped user's subordinates are their own branch's, not the whole bank's.
     *
     * <h2>Why this leaked</h2>
     * A title is organisation-wide — "Teller" is the same title at every branch — so resolving
     * subordinates through {@code findUserIdsByTitleIds} answered with everyone in the bank holding a
     * subordinate title. Measured on live data: the Gulu Head Cashier is a SUPERVISOR, therefore
     * SELF-scoped, therefore took this path, and their subordinates spanned Gulu <em>and Mbarara</em>.
     * They saw Mbarara's requests on the All Requests tab.
     *
     * <p>Branch Managers and BOMs were never affected — those roles are BRANCH-scoped and take
     * {@code findUserIdsByBranchId}, which was always filtered. The exposure was SELF-scoped users who
     * happen to have subordinates.
     */
    @Test
    void subordinatesAreConfinedToTheCallersOwnBranch() {
        String body = methodBody(read(SERVICE), "getManagerAndSubordinateUserIds");

        assertThat(body)
                .as("a reporting line is a property of the title, which is organisation-wide; the "
                        + "branch has to be applied separately")
                .contains("findUserIdsByTitleIdsAndBranchId");
        assertThat(body)
                .as("the unfiltered lookup answers with the whole bank and must not be used here")
                .doesNotContain("findUserIdsByTitleIds(");
    }

    /**
     * An empty requester list is the <em>widest</em> answer, so no path may return one.
     *
     * <p>{@code RequestSearchDao} applies the requester predicate only when the list is non-empty, so
     * returning nothing means "no restriction". This method used to return {@code emptyList()} for a
     * user with no title — handing them every request in the bank, which is the opposite of what a
     * missing title should mean. Every path now ends in at least the caller's own id.
     */
    @Test
    void aConfigurationGapNarrowsRatherThanWidens() {
        String body = methodBody(read(SERVICE), "getManagerAndSubordinateUserIds");

        assertThat(body)
                .as("empty means unrestricted downstream; a missing title must not grant the bank")
                .doesNotContain("Collections.emptyList()");
        assertThat(body)
                .as("an unidentifiable caller has nothing to narrow to, so it must refuse instead")
                .contains("AccessDeniedException");
    }

    /**
     * The listing must not turn a refusal into an empty page.
     *
     * <p>{@code fetchAllRequests} ends in a blanket {@code catch (Exception)} that returns
     * {@code Page.empty()}, so a fault and "there are no requests" were the same screen. With the
     * guards above in place that would have been worse: a boundary would have rendered as an
     * ordinary empty list, hiding the refusal from the user and from us.
     */
    @Test
    void aRefusalIsNotSwallowedIntoAnEmptyPage() {
        assertThat(methodBody(read(SERVICE), "fetchAllRequests"))
                .as("AccessDeniedException must escape the blanket catch and reach the client as 403")
                .contains("catch (org.springframework.security.access.AccessDeniedException e)");
    }

    /**
     * A satellite of a request answers to the request's own rule.
     *
     * <p>The approval trail and the request report sit outside {@code /requests/**}, so the route
     * matchers that guard the module never covered them and any signed-in user could read them by
     * id. The trail was the sharpest: it names who approved what, and when.
     */
    @Test
    void theSatellitesAreGatedAndScopedThroughTheirRequest() {
        record Satellite(String path, String what) { }

        List<Satellite> satellites = List.of(
                new Satellite("src/main/java/pride/bank/erp/workflowinstance/WorkflowInstanceController.java",
                        "the approval trail"),
                new Satellite("src/main/java/pride/bank/erp/requestReport/RequestReportController.java",
                        "the request report"));

        for (Satellite satellite : satellites) {
            String source = read(satellite.path());
            assertThat(source)
                    .as("%s must require a permission", satellite.what())
                    .contains("@PreAuthorize");
            assertThat(source)
                    .as("%s must be scoped through the request it belongs to, not just gated",
                            satellite.what())
                    .contains("requireReadableById");
        }
    }

    /**
     * The issuance record is gated but deliberately <em>not</em> scoped to the requester's branch.
     *
     * <p>It was open to any signed-in user, because {@code REQUEST_ISSUANCE} is matched for POST
     * only. Gating it was right; scoping it was not, and the first attempt did both.
     *
     * <p>Issuance is cross-branch by design — Head Office fulfilling a branch's request is the
     * ordinary case, the same reason {@code createMovement} carries no scope check. Narrowing this by
     * the requester's branch would refuse the issuer their own work, and would do so only sometimes,
     * since the "routed to me" exception lapses the moment the last approval step completes.
     * {@code ISSUE_ITEMS} is the control.
     */
    @Test
    void theIssuanceRecordIsGatedButNotBranchScoped() {
        String source = read("src/main/java/pride/bank/erp/issuance/IssuanceController.java");

        assertThat(source)
                .as("it was reachable by any signed-in user: the matcher covers POST only")
                .contains("@PreAuthorize");
        assertThat(source)
                .as("scoping this by the requester's branch refuses issuers their own work")
                .doesNotContain("requireReadableById");
        assertThat(source)
                .as("ISSUE_ITEMS is what admits the people who do this job")
                .contains("ISSUE_ITEMS");
    }

    /**
     * Approve and reject are one gate, not two rules where the second never runs.
     *
     * <p>Two matchers for the same method and path meant the first won and the second was dead, so a
     * role granted only {@code REJECT_REQUEST} was refused at the filter chain — it could not reject.
     * Latent while the seeded manager holds both, live the moment a reject-only role is configured.
     */
    @Test
    void rejectOnlyRolesCanStillReachTheApprovalEndpoint() {
        String source = read("src/main/java/pride/bank/erp/config/SecurityConfiguration.java");
        int gate = source.indexOf("REQUEST_APPROVAL");
        assertThat(gate).isGreaterThanOrEqualTo(0);

        String rule = source.substring(gate, Math.min(source.length(), gate + 400));
        assertThat(rule)
                .as("approve and reject must share one hasAnyAuthority; a second matcher for the "
                        + "same path is unreachable")
                .contains("hasAnyAuthority");
    }

    /** Movements read through a request are narrowed per row, like every other movement read. */
    @Test
    void movementsReadThroughARequestAreScoped() {
        assertThat(methodBody(
                read("src/main/java/pride/bank/erp/movement/MovementService.java"), "findByRequest"))
                .as("a request id was enough to read another branch's fulfilment movements")
                .contains("canViewAny");
    }

    /**
     * The multi-branch guard and its boolean twin must stay one rule.
     *
     * <p>A listing narrows where a guard refuses, so both forms are needed — and if they drift, a row
     * appears that will not open, or one is hidden that would have. {@code requireAny} therefore
     * delegates to {@code anyInReach} rather than repeating it.
     */
    @Test
    void theRefusingAndFilteringFormsShareOneImplementation() {
        assertThat(methodBody(
                read("src/main/java/pride/bank/erp/access/AccessScopeService.java"), "requireAny"))
                .as("requireAny must not carry its own copy of the reach rule")
                .contains("anyInReach");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String read(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /**
     * The text of one method, from its declaration to the next member.
     *
     * <p>Requires a modifier at the front of the line so a call site is not mistaken for the
     * declaration — searching the raw text for {@code " name("} finds the caller first and then reads
     * a slice that happens not to contain what is being asserted, failing against correct code.
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
        assertThat(methodBody(read(SERVICE), "resolveApproverInbox"))
                .as("if this is empty, the assertions above prove nothing")
                .isNotBlank()
                .hasSizeGreaterThan(50);
    }
}
