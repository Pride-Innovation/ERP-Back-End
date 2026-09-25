package pride.bank.erp.advice;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.http.ResponseEntity;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * No response may carry a status the firewall will not pass.
 *
 * <h2>Why this is a build failure rather than a code review note</h2>
 * The WAF admits 2xx and 3xx, blocks <b>every 5xx</b>, and allows only seven 4xx codes:
 * {@code 400, 401, 404, 407, 409, 417, 440}. A response outside that set does not reach the caller
 * as an error - the firewall replaces it with its own rejection page, served as <b>HTTP 200</b> with
 * an HTML body. So the failure mode is not "the user sees the wrong status": it is that a successful
 * -looking response arrives carrying a page the client cannot parse, while the server log records a
 * perfectly ordinary 500.
 *
 * <p>That is invisible from either end on its own, which is exactly why it went unnoticed: refusals
 * were moved off 403 to <b>422</b> when the block on 403 was found, and 422 is not on the list
 * either. Every permission refusal in the application was being blocked from that change until
 * {@link WafStatus} existed. A constraint that lives in one person's memory is one nobody checks new
 * code against.
 *
 * <h2>What it reads</h2>
 * The source, with comments stripped - a javadoc explaining why 500 is forbidden must not itself
 * fail the build, and deleting the explanation to satisfy a test is the wrong trade.
 */
class WafStatusCoverageTest {

    private static final Path MAIN = Path.of("src/main/java");

    /** {@code HttpStatus.SOME_NAME} - resolved through the enum, so the mapping cannot drift. */
    private static final Pattern NAMED = Pattern.compile("HttpStatus\\.([A-Z][A-Z_]+)");

    /** {@code HttpStatusCode.valueOf(404)} and {@code .status(404)}. */
    private static final Pattern NUMERIC =
            Pattern.compile("(?:HttpStatusCode\\.valueOf|\\.status)\\(\\s*(\\d{3})\\s*\\)");

    @Test
    void noResponseCarriesAStatusTheFirewallBlocks() throws IOException {
        List<String> offences = new ArrayList<>();

        for (Path file : javaFiles()) {
            String code = codeOnly(Files.readString(file));
            String name = MAIN.relativize(file).toString();

            Matcher named = NAMED.matcher(code);
            while (named.find()) {
                Integer value = statusValue(named.group(1));
                if (value != null && isBlocked(value)) {
                    offences.add("%s uses HttpStatus.%s (%d)".formatted(name, named.group(1), value));
                }
            }

            Matcher numeric = NUMERIC.matcher(code);
            while (numeric.find()) {
                int value = Integer.parseInt(numeric.group(1));
                if (isBlocked(value)) {
                    offences.add("%s uses the literal status %d".formatted(name, value));
                }
            }
        }

        assertThat(offences)
                .as("the firewall replaces these with its own page under HTTP 200, so the caller "
                        + "receives neither the status nor the message - see WafStatus")
                .isEmpty();
    }

    /**
     * The floor must stay in place.
     *
     * <p>Fixing the individual hand-written 500s achieved nothing on its own: the problem was that
     * an unhandled exception had nowhere to land except Spring's default 500. Remove this handler
     * and every unexpected fault silently becomes un-deliverable again.
     */
    @Test
    void thereIsACatchAllSoNoExceptionCanFallThroughToA500() throws IOException {
        String handler = Files.readString(
                MAIN.resolve("pride/bank/erp/advice/ApplicationExceptionHandler.java"));

        assertThat(codeOnly(handler))
                .as("without @ExceptionHandler(Exception.class) any unmapped exception becomes a 500, "
                        + "which the firewall blocks")
                .contains("@ExceptionHandler(Exception.class)")
                .contains("WafStatus.SERVER_FAULT");
    }

    /**
     * And the error dispatch too, which the handler above cannot reach.
     *
     * <p>An exception thrown in a servlet filter never reaches {@code @ControllerAdvice}; the
     * container re-dispatches to {@code /error}. Without our own {@link org.springframework.boot.web.servlet.error.ErrorController}
     * Spring Boot answers that with the original status, which for a filter failure is a 500.
     */
    @Test
    void theErrorDispatchIsCoveredAsWell() {
        assertThat(MAIN.resolve("pride/bank/erp/advice/WafSafeErrorController.java"))
                .as("filter-level failures reach /error, not the controller advice")
                .exists();
    }

    /** The two carriers must themselves be on the list they exist to satisfy. */
    @Test
    void theCarriersAreStatusesTheFirewallAdmits() {
        assertThat(WafStatus.ALLOWED_ERROR_STATUSES).contains(
                AccessDenied.STATUS.value(),
                WafStatus.SERVER_FAULT.value(),
                WafStatus.ACCESS_DENIED.value());

        assertThat(AccessDenied.STATUS.value())
                .as("a refusal must not travel as 401 - the front end reads that as an expired "
                        + "session and signs the user out")
                .isNotEqualTo(401);
    }

    // ── what the handlers actually produce ───────────────────────────────────
    //
    // The checks above read the source; these run the code. A status constant can be correct while
    // the response still carries something else, and only one of the two is worth trusting.

    /** An unexpected fault leaves as an admitted status, and says what it was. */
    @Test
    void theCatchAllProducesAnAdmittedStatus() {
        ResponseEntity<ProblemDetail> response =
                new ApplicationExceptionHandler().handleUnexpectedException(new RuntimeException("boom"));

        assertThat(response.getStatusCode().value()).isEqualTo(WafStatus.SERVER_FAULT.value());
        assertThat(WafStatus.ALLOWED_ERROR_STATUSES).contains(response.getStatusCode().value());
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().getProperties())
                .as("the front end reports this one itself; without the code it cannot tell a server "
                        + "fault from a refusal")
                .containsEntry("errorCode", WafStatus.SERVER_FAULT_CODE);
    }

    /**
     * The catch-all must not flatten Spring's own status-carrying exceptions.
     *
     * <p>{@code @ExceptionHandler(Exception.class)} sits in front of the resolver that would
     * normally honour a {@code ResponseStatusException}. Without the {@code ErrorResponse} check
     * inside it, every 400 and 409 raised across the asset-type, courier and consultant services -
     * and a 404 from an unmatched route - would start reporting as "something went wrong".
     */
    @Test
    void aStatusSpringAlreadyChoseSurvivesTheCatchAll() {
        ApplicationExceptionHandler handler = new ApplicationExceptionHandler();

        assertThat(handler.handleUnexpectedException(
                new ResponseStatusException(HttpStatus.CONFLICT, "already exists"))
                .getStatusCode().value()).isEqualTo(409);

        assertThat(handler.handleUnexpectedException(
                new ResponseStatusException(HttpStatus.BAD_REQUEST, "name is required"))
                .getStatusCode().value()).isEqualTo(400);
    }

    /** A status Spring chose that the firewall will not carry is still rewritten. */
    @Test
    void aStatusTheFirewallBlocksIsRewrittenEvenWhenSpringChoseIt() {
        ResponseEntity<ProblemDetail> response = new ApplicationExceptionHandler()
                .handleUnexpectedException(new ResponseStatusException(HttpStatus.NOT_IMPLEMENTED, "nope"));

        assertThat(response.getStatusCode().value())
                .as("501 is in the blocked range, so honouring it would defeat the point")
                .isEqualTo(WafStatus.SERVER_FAULT.value());
    }

    // ── helpers ──────────────────────────────────────────────────────────────

    private static boolean isBlocked(int status) {
        return status >= 400 && !WafStatus.ALLOWED_ERROR_STATUSES.contains(status);
    }

    /** Null for a {@code HttpStatus.} reference that is not a status constant, such as a nested type. */
    private static Integer statusValue(String constantName) {
        try {
            return HttpStatus.valueOf(constantName).value();
        } catch (IllegalArgumentException notAStatus) {
            return null;
        }
    }

    private static String codeOnly(String source) {
        return source.replaceAll("(?s)/\\*.*?\\*/", "").replaceAll("//[^\n]*", "");
    }

    private static List<Path> javaFiles() throws IOException {
        try (Stream<Path> files = Files.walk(MAIN)) {
            return files.filter(p -> p.toString().endsWith(".java")).toList();
        }
    }
}
