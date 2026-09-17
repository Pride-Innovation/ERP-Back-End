package pride.bank.erp.workflowinstance;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * An action must belong to the stage the request is actually at.
 *
 * <h2>What went wrong</h2>
 * A request sitting at the <b>ISSUANCE</b> step still carries an approval-flavoured status —
 * {@code unitAcknowledged} lives in the workflow-approval codes — so the detail page went on offering
 * Approve and Reject beside Issue Items, and the user could not tell which of the three was the right
 * thing to press.
 *
 * <p>That was not only confusing. {@link WorkflowEngineService#processStepAction} resolves whatever
 * step is <em>current</em> and applies the action it is handed, without asking whether the two suit
 * each other. Pressing Approve there would have marked the <b>issuance</b> step approved and advanced
 * the workflow — recording the request as issued when nothing had been issued.
 *
 * <h2>Why the engine cannot catch it alone</h2>
 * {@code APPROVED} is legitimate at a {@code REQUEST_APPROVAL} step <b>and</b> at an {@code ISSUANCE}
 * one, because approving an issuance sends the same word. The action vocabulary cannot distinguish
 * "approve the request" from "approve the issuance", so the assertion has to sit with the caller,
 * which knows which act it is performing.
 *
 * <p>Pinned on the source: what these guards are worth is that they are <em>called at all</em>, and
 * standing up a workflow instance at a chosen step would pin the fixture far more than the rule.
 */
class StepStageGuardTest {

    private static final String ENGINE =
            "src/main/java/pride/bank/erp/workflowinstance/WorkflowEngineService.java";

    /**
     * Every service that advances a step says which stage it is for.
     *
     * <p>Issuance approval is not listed: it already reads {@code StepType.ISSUANCE} for its unit
     * check, and it loads an existing {@code Issuance}, so it cannot run before something was issued.
     */
    @Test
    void eachServiceAssertsTheStageItBelongsTo() {
        record Caller(String path, String step, String what) { }

        List<Caller> callers = List.of(
                new Caller("src/main/java/pride/bank/erp/requestReport/RequestReportService.java",
                        "REQUEST_APPROVAL", "approving a request"),
                new Caller("src/main/java/pride/bank/erp/acknowledgeRequestReceipt/"
                        + "AcknowledgeRequestReceiptService.java",
                        "ACKNOWLEDGE_REQUEST", "the Infra/Admin handshake"),
                new Caller("src/main/java/pride/bank/erp/acknowledgeIssuanceReceipt/"
                        + "AcknowledgeIssuanceReceiptService.java",
                        "ACKNOWLEDGE_RECEIPT", "the requester's receipt"));

        for (Caller caller : callers) {
            String source = read(caller.path());
            assertThat(source)
                    .as("%s must refuse to run at another stage; the engine applies whatever action "
                            + "it is handed to whatever step is current", caller.what())
                    .contains("requireCurrentStepIs");
            assertThat(source)
                    .as("%s must name its own step type", caller.what())
                    .contains(caller.step());
        }
    }

    /**
     * The guard is permissive exactly where the engine is.
     *
     * <p>A request with no active instance predates the workflow engine, and {@code processStepAction}
     * already returns quietly for those. Refusing would strand work nobody could then clear — the
     * failure mode every other guard in this class is careful to avoid, and the reason this assertion
     * is here rather than left to a reviewer to remember.
     */
    @Test
    void aRequestWithNoWorkflowIsNotStranded() {
        String body = methodBody(read(ENGINE), "requireCurrentStepIs");

        assertThat(body)
                .as("no instance, or one already finished, must pass through rather than refuse")
                .contains("isEmpty()")
                .contains("IN_PROGRESS");
        assertThat(body)
                .as("and it must return rather than throw on that path")
                .contains("return;");
    }

    /** A refusal should say which stage the request is at, not just that it was refused. */
    @Test
    void theRefusalNamesTheStage() {
        String source = read(ENGINE);

        assertThat(methodBody(source, "requireCurrentStepIs"))
                .as("'cannot be approved now' without saying why sends the user back to guessing")
                .contains("describe(")
                .contains("BusinessRuleException");

        assertThat(methodBody(source, "describe"))
                .as("every step type needs a word, or a new one prints as an enum constant")
                .contains("REQUEST_APPROVAL")
                .contains("ACKNOWLEDGE_REQUEST")
                .contains("ISSUANCE")
                .contains("ACKNOWLEDGE_RECEIPT");
    }

    /**
     * The issuance sign-off is a stage of its own, and nothing may treat it as a plain approval.
     *
     * <h2>What this is protecting</h2>
     * It is stored as a {@code REQUEST_APPROVAL} step, differing only by
     * {@code approverSubject = ISSUER}: it approves the <em>issuer's</em> issuance rather than the
     * requester's request, and {@code resolveStatusCode} already maps it to {@code issuanceApproved}
     * rather than to an approval status. So it passes every check for "is this an approval", and
     * {@code POST /approvals} would advance it exactly like a ladder tier — recording the request
     * {@code issuanceApproved} with <b>nothing sent</b>, because the fulfilment movement is created
     * by {@code POST /approve-issuance} and by nothing else.
     *
     * <p>Found by measuring rather than by reading: on live data every issued request had already
     * moved past the {@code ISSUANCE} step, so the rule that looked for it there matched nothing and
     * <em>Approve Issuance</em> was offered nowhere at all, while the step beside it read as an
     * ordinary approval.
     */
    @Test
    void theIssuanceSignOffIsNotTreatedAsAPlainApproval() {
        String approvals =
                read("src/main/java/pride/bank/erp/requestReport/RequestReportService.java");
        assertThat(approvals)
                .as("approving through /approvals at the issuance sign-off advances the step "
                        + "without ever creating the fulfilment movement")
                .contains("requireNotIssuanceApprovalStage");
        assertThat(approvals)
                .as("rejection must stay open — handleRejection is the only way to refuse an "
                        + "issuance, and there is no reject endpoint beside /approve-issuance")
                .contains("!\"REJECTED\".equals(workflowAction)");

        String guard = methodBody(read(ENGINE), "requireNotIssuanceApprovalStage");
        assertThat(guard)
                .as("the subject is what distinguishes the stage; the step type cannot")
                .contains("ApproverSubject.ISSUER");
        assertThat(guard)
                .as("a request with no live workflow must pass through, as every other guard does")
                .contains("IN_PROGRESS")
                .contains("return;");
    }

    /**
     * And a listing must report that stage under its own name.
     *
     * <p>The front end decides which button to offer from this one field. Reported as
     * {@code REQUEST_APPROVAL} it would offer <em>Approve Request</em> on a request already issued —
     * the endpoint refuses it now, but the menu would still be promising it, which is the failure
     * the mirroring exists to prevent.
     */
    @Test
    void theListingNamesThatStageSeparately() {
        assertThat(read("src/main/java/pride/bank/erp/workflowinstance/"
                + "RequestWorkflowInstanceRepository.java"))
                .as("the type alone does not identify the stage — the subject has to come back too")
                .contains("s.approverSubject");

        assertThat(read("src/main/java/pride/bank/erp/request/RequestService.java"))
                .as("and the stage needs a name the front end can key on")
                .contains("ISSUANCE_APPROVAL")
                .contains("ApproverSubject.ISSUER");
    }

    // ── helpers ─────────────────────────────────────────────────────────────

    private static String read(String path) {
        try {
            return java.nio.file.Files.readString(java.nio.file.Path.of(path));
        } catch (Exception e) {
            throw new AssertionError("could not read " + path, e);
        }
    }

    /** The text of one method, from its declaration to the next member. */
    private static String methodBody(String source, String methodName) {
        String[] lines = source.split("\n", -1);
        int first = -1;
        for (int i = 0; i < lines.length && first < 0; i++) {
            String trimmed = lines[i].trim();
            boolean declares = trimmed.startsWith("public ")
                    || trimmed.startsWith("private ")
                    || trimmed.startsWith("protected ")
                    || trimmed.startsWith("private static ");
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

    /** Guards against the reader silently matching nothing and every assertion passing. */
    @Test
    void theSourceReaderActuallyFindsMethods() {
        assertThat(methodBody(read(ENGINE), "requireCurrentStepIs"))
                .as("if this is empty, the assertions above prove nothing")
                .isNotBlank()
                .hasSizeGreaterThan(50);
    }
}
