/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.advice;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.exc.UnrecognizedPropertyException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatusCode;
import org.springframework.http.ResponseEntity;
import org.springframework.web.ErrorResponse;
import org.springframework.web.HttpMediaTypeNotAcceptableException;
import org.springframework.web.HttpMediaTypeNotSupportedException;
import org.springframework.web.HttpRequestMethodNotSupportedException;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pride.bank.erp.exception.AccountBlockedException;
import pride.bank.erp.exception.BadRequestException;
import pride.bank.erp.exception.NoApproverException;
import pride.bank.erp.exception.NotFoundException;

import java.io.IOException;
import java.io.InputStream;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

@RestControllerAdvice
public class ApplicationExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(ApplicationExceptionHandler.class);

    private static final Map<String, String> CONSTRAINT_MAP = loadConstraintMap();

    private static String extractKeyword(String errorMessage) {
        if (errorMessage == null || errorMessage.isEmpty()) {
            return "Unknown error";
        }

        String duplicateEntryPattern = "Duplicate entry";
        String foreignKeyPattern = "foreign key constraint fails";
        String checkConstraintPattern = "CHECK constraint";
        String notNullPattern = "cannot be null";
        String dataTruncationPattern = "Data truncation";

        if (errorMessage.contains(duplicateEntryPattern)) {
            String value = extractBetween(errorMessage, "Duplicate entry '");
            String constraint = extractBetween(errorMessage, "for key '");

            String column = mapConstraintToColumn(constraint);

            if (column != null) {
                return String.format("%s '%s' has already been taken", capitalize(column), value);
            } else {
                return String.format("Duplicate entry: '%s'", value);
            }
        } else if (errorMessage.contains(foreignKeyPattern)) {
            return "Foreign Key Constraint Violation";
        } else if (errorMessage.contains(checkConstraintPattern)) {
            return "Check Constraint Violation";
        } else if (errorMessage.contains(notNullPattern)) {
            return "Not Null Constraint Violation";
        } else if (errorMessage.contains(dataTruncationPattern)) {
            return "Data Truncation";
        }

        return "Unknown Database Constraint Violation";
    }

    private static String extractBetween(String text, String start) {
        int startIndex = text.indexOf(start);
        if (startIndex == -1) return null;
        startIndex += start.length();
        int endIndex = text.indexOf("'", startIndex);
        if (endIndex == -1) return null;
        return text.substring(startIndex, endIndex);
    }

    private static String mapConstraintToColumn(String constraint) {
        return CONSTRAINT_MAP.getOrDefault(constraint, null);
    }

    private static Map<String, String> loadConstraintMap() {
        Properties props = new Properties();

        try (InputStream input = ApplicationExceptionHandler.class.getClassLoader().getResourceAsStream("constraints.properties")) {
            if (input == null) {
                System.err.println("constraints.properties not found in classpath.");
                return Collections.emptyMap();
            }
            props.load(input);
        } catch (IOException ex) {
            System.err.println("Could not load constraints.properties: " + ex.getMessage());
            return Collections.emptyMap();
        }

        Map<String, String> map = new HashMap<>();
        for (String key : props.stringPropertyNames()) {
            map.put(key, props.getProperty(key));
        }
        return map;
    }

    private static String capitalize(String str) {
        if (str == null || str.isEmpty()) return str;
        return str.substring(0, 1).toUpperCase() + str.substring(1);
    }


    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ProblemDetail handleInvalidArgument(MethodArgumentNotValidException ex) {

        Map<String, Object> errors = new HashMap<>();

        ex.getBindingResult()
                .getFieldErrors()
                .forEach(error -> errors.put(error.getField(), error.getDefaultMessage()));

        ProblemDetail errorDetail =
                ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, "Validation error");

        errorDetail.setProperty("properties", errors);
        return errorDetail;
    }

    /**
     * A request the server understood but will not act on as written.
     *
     * <p>Was unhandled, so it fell through to Spring's default error path and surfaced as a bare 500
     * — the message never reached the client, turning actionable advice into "Internal Server Error".
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(BadRequestException.class)
    public ProblemDetail handleBadRequest(BadRequestException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        errorDetail.setProperty("request_failure", "Bad Request");
        errorDetail.setProperty("message", ex.getMessage());
        return errorDetail;
    }

    /**
     * No approver could be resolved for a movement that needs one.
     *
     * <p>Carries {@code errorCode} so the client can recognize this specific case and offer to
     * proceed without approval, rather than parsing the message text.
     */
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(NoApproverException.class)
    public ProblemDetail handleNoApprover(NoApproverException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        errorDetail.setProperty("errorCode", "NO_APPROVER");
        errorDetail.setProperty("message", ex.getMessage());
        return errorDetail;
    }

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        errorDetail.setProperty("request_failure", "Bad Request");
        return errorDetail;
    }

    /**
     * Two people changed the same stock line or store balance at once and one write lost the race.
     * Nothing is wrong with the request itself — it just needs to be re-read and re-submitted, so
     * say that in plain language rather than surfacing a Hibernate stack trace.
     */
    @ResponseStatus(HttpStatus.CONFLICT)
    @ExceptionHandler(ObjectOptimisticLockingFailureException.class)
    public ProblemDetail handleOptimisticLock(ObjectOptimisticLockingFailureException ex) {
        ProblemDetail errorDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.CONFLICT,
                "Someone else updated this record while you were working on it. "
                        + "Refresh the page to pick up their change, then try again.");
        errorDetail.setProperty("request_failure", "Conflict");
        return errorDetail;
    }

    @ResponseStatus(HttpStatus.NOT_FOUND)
    @ExceptionHandler(NotFoundException.class)
    public Map<String, String> handleException(NotFoundException ex) {
        Map<String, String> errors = new HashMap<>();
        errors.put("message", ex.getMessage());
        return errors;
    }

    @ExceptionHandler({BadCredentialsException.class, UsernameNotFoundException.class})
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail handleBadCredentialsException(Exception ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        errorDetail.setProperty("errorCode", "INVALID_CREDENTIALS");
        errorDetail.setProperty("access_denied_reason", "Invalid Email or Password");
        return errorDetail;
    }

    @ExceptionHandler(AccountBlockedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail handleAccountBlockedException(AccountBlockedException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        errorDetail.setProperty("errorCode", "ACCOUNT_BLOCKED");
        errorDetail.setProperty("access_denied_reason", ex.getMessage());
        return errorDetail;
    }

    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail handleDisabledException(DisabledException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.UNAUTHORIZED, "Your account has been disabled. Please contact your admin.");
        errorDetail.setProperty("errorCode", "ACCOUNT_DISABLED");
        errorDetail.setProperty("access_denied_reason", "Account Disabled. Please contact Admin");
        return errorDetail;
    }

    @ExceptionHandler(LockedException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail handleLockedException(LockedException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        errorDetail.setProperty("errorCode", "ACCOUNT_LOCKED");
        errorDetail.setProperty("access_denied_reason", "Account Locked. Please verify your email.");
        return errorDetail;
    }

    /**
     * Every authorization refusal in the application arrives here.
     *
     * <p>Both kinds: a rule in {@code SecurityConfiguration} or a {@code @PreAuthorize} refusing at
     * the filter chain — routed in by {@code CustomBearerTokenAccessDeniedHandler} — and a scope
     * check inside a service refusing a record. That single funnel is why moving off 403 is a change
     * to two methods rather than to ninety call sites.
     *
     * <p>The status comes from {@link AccessDenied#STATUS} because the firewall in front of this
     * service blocks 403 and replaces it with its own page, so a refusal sent that way never reaches
     * the caller. The status is transport; {@code errorCode} is the meaning. See {@link AccessDenied}.
     */
    @ExceptionHandler(AccessDeniedException.class)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(AccessDenied.STATUS, ex.getMessage());
        errorDetail.setProperty("errorCode", AccessDenied.ERROR_CODE);
        errorDetail.setProperty("access_denied_reason", "Not Authorized");
        return errorDetail;
    }

    /**
     * A token that is absent, malformed or expired where one was required — <b>401</b>.
     *
     * <h2>This was 422 for a week, and it broke session expiry</h2>
     * When refusals moved off 403, this exception moved with its neighbor, on the reasoning that
     * "the front end reads 401 as the session expired, tries a refresh and then signs the user out,
     * and this is also raised for a token that is simply wrong for the route". That reasoning was
     * wrong, and it was wrong about the case that matters most.
     *
     * <p>Spring Security raises this through the {@code authenticationEntryPoint} when the request is
     * <b>anonymous</b> — which is precisely what an expired token produces here, because
     * {@code JwtService.extractUserEmail} returns null for one and {@code JwtAuthenticationFilter}
     * then sets no authentication. So every expiry arrived as a 422, the interceptor's refresh path
     * (which keys on 401) never fired, nobody was signed out, and the error surfaced in the browser
     * as an uncaught {@code AxiosError: Full authentication is required to access this resource}.
     *
     * <p>Measured before the fix: a request with no header, one with a garbage token and one with a
     * correctly-signed expired token <b>all returned 422</b>.
     *
     * <h2>The distinction that actually matters</h2>
     * <ul>
     *   <li><b>401</b> — I do not know who you are. Re-authenticate: refresh, or sign in again.</li>
     *   <li><b>{@link AccessDenied#STATUS}</b> — I know who you are and the answer is no. Refreshing
     *       changes nothing, and signing the user out for it would be wrong.</li>
     * </ul>
     *
     * <p>Splitting them on the exception is what makes that legible, and it is what the front end
     * needs in order to do two different things. 401 is passed by the firewall — only 403 is blocked —
     * so there is nothing forcing them together.
     */
    @ExceptionHandler(InsufficientAuthenticationException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail handleInsufficientAuthenticationException(InsufficientAuthenticationException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.UNAUTHORIZED, ex.getMessage());
        errorDetail.setProperty("errorCode", "SESSION_EXPIRED");
        errorDetail.setProperty("access_denied_reason", "Invalid or Expired Token");
        return errorDetail;
    }

    @ExceptionHandler(DataIntegrityViolationException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleDataIntegrityViolationException(DataIntegrityViolationException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.CONFLICT, extractKeyword(ex.getMessage()));
        errorDetail.setProperty("message", extractKeyword(ex.getMessage()));
        return errorDetail;
    }

    /**
     * A required query parameter was omitted — a malformed request, not a refused one.
     *
     * <p>This answered 403 Forbidden, which sends anyone debugging straight into permissions and
     * roles when the actual fault is a missing parameter named in the message. The client cannot
     * act on "Forbidden" here; it can act on "Bad Request".
     */
    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        errorDetail.setProperty("request_failure", "Bad Request");
        errorDetail.setProperty("message", ex.getMessage());
        return errorDetail;
    }

    /**
     * The record is not in a state where this action makes sense — a conflict, not a refusal.
     *
     * <p>Every lifecycle guard in the movement and consignment flows raises this: "Only a dispatched
     * consignment can be marked in transit", "this movement travels on a consignment and is
     * dispatched with it", "the goods have to arrive before this can be handed over". Answering 403
     * dressed all of them up as permission problems, when each one is telling the caller to do
     * something first — advice a user can act on, and only if the status does not contradict it.
     *
     * <p>409 matches how the optimistic-locking handler above already reports a state clash.
     */
    @ExceptionHandler(IllegalStateException.class)
    @ResponseStatus(HttpStatus.CONFLICT)
    public ProblemDetail handleIllegalStateExceptionException(IllegalStateException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.CONFLICT, ex.getMessage());
        errorDetail.setProperty("request_failure", "Conflict");
        errorDetail.setProperty("message", ex.getMessage());
        return errorDetail;
    }

    // ── Statuses the firewall will not carry ─────────────────────────────────
    //
    // Everything below exists because a blocked status does not reach the caller as an error: the
    // firewall replaces it with its own HTML page served as HTTP 200. See WafStatus.

    /**
     * An upload larger than the configured limit. Spring answers 413, which the firewall blocks.
     *
     * <p>400 rather than 440, because this genuinely is the caller's doing, and they can act on it.
     * The limit is {@code spring.servlet.multipart.max-file-size}, currently 2MB, and seven
     * controllers accept uploads - asset images, GRN documents, repair and movement attachments,
     * request files - so this is reachable by anyone attaching a photograph from a phone.
     */
    @ExceptionHandler(MaxUploadSizeExceededException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleMaxUploadSizeExceeded(MaxUploadSizeExceededException ex) {
        ProblemDetail errorDetail = ProblemDetail.forStatusAndDetail(
                HttpStatus.BAD_REQUEST, "That file is too large. The maximum upload size is 2MB.");
        errorDetail.setProperty("errorCode", "FILE_TOO_LARGE");
        errorDetail.setProperty("message", "That file is too large. The maximum upload size is 2MB.");
        return errorDetail;
    }

    /**
     * The verb does not match the route. Spring answers 405, which the firewall blocks.
     *
     * <p>Worth keeping in mind while reading this: {@code HttpMethodOverrideFilter} rewrites POST
     * into PUT or DELETE before routing, so a mismatch here can mean the override header disagreed
     * with the route rather than that the caller used the wrong verb.
     */
    @ExceptionHandler(HttpRequestMethodNotSupportedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleMethodNotSupported(HttpRequestMethodNotSupportedException ex) {
        ProblemDetail errorDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        errorDetail.setProperty("errorCode", "METHOD_NOT_ALLOWED");
        errorDetail.setProperty("message", ex.getMessage());
        return errorDetail;
    }

    /** Wrong {@code Content-Type}. Spring answers 415, which the firewall blocks. */
    @ExceptionHandler(HttpMediaTypeNotSupportedException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleMediaTypeNotSupported(HttpMediaTypeNotSupportedException ex) {
        ProblemDetail errorDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        errorDetail.setProperty("errorCode", "UNSUPPORTED_MEDIA_TYPE");
        errorDetail.setProperty("message", ex.getMessage());
        return errorDetail;
    }

    /** Unsatisfiable {@code Accept}. Spring answers 406, which the firewall blocks. */
    @ExceptionHandler(HttpMediaTypeNotAcceptableException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleMediaTypeNotAcceptable(HttpMediaTypeNotAcceptableException ex) {
        ProblemDetail errorDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        errorDetail.setProperty("errorCode", "NOT_ACCEPTABLE");
        errorDetail.setProperty("message", ex.getMessage());
        return errorDetail;
    }

    /**
     * A payload Jackson could not map. The caller sent the wrong shape - a client error, not a fault.
     *
     * <h2>Why this needs its own handler</h2>
     * Spring turns a malformed <b>request body</b> into {@code HttpMessageNotReadableException} and
     * reports 400 without help from us. But {@code UserController.bulkInsertUsers} takes the rows as
     * a {@code @RequestParam} string and calls {@code objectMapper.readValue} itself, so Jackson's
     * exception escapes as an ordinary unhandled one. It reached the catch-all below and was
     * reported as a server fault saying "Something went wrong".
     *
     * <p>That is what a real import failure looked like: the file had been parsed from the wrong
     * worksheet and every column was unrecognized, and the only thing the user was told was that
     * something had gone wrong. The field name was in the server log the whole time.
     *
     * <p>So this names the offending field and lists what was expected. The caller can act on that;
     * they cannot act on "something went wrong".
     */
    @ExceptionHandler(JsonProcessingException.class)
    @ResponseStatus(HttpStatus.BAD_REQUEST)
    public ProblemDetail handleJsonProcessingException(JsonProcessingException ex) {
        String detail = "The data sent could not be read. Please check the file and try again.";

        if (ex instanceof UnrecognizedPropertyException unrecognised) {
            String known = unrecognised.getKnownPropertyIds() == null ? "" :
                    unrecognised.getKnownPropertyIds().stream()
                            .map(Object::toString).sorted().reduce((a, b) -> a + ", " + b).orElse("");
            detail = "The column \"%s\" is not one this import understands. Expected: %s."
                    .formatted(unrecognised.getPropertyName(), known);
        }

        ProblemDetail errorDetail = ProblemDetail.forStatusAndDetail(HttpStatus.BAD_REQUEST, detail);
        errorDetail.setProperty("errorCode", "MALFORMED_PAYLOAD");
        errorDetail.setProperty("message", detail);
        return errorDetail;
    }

    /**
     * The floor. Anything not handled above leaves here, never as a 5xx.
     *
     * <h2>Why this had to exist</h2>
     * There was no catch-all, so every unhandled exception - a null dereference, a driver timeout, a
     * mapping fault - became Spring's default <b>500</b>. The firewall blocks the whole 5xx range,
     * so none of those reached the caller: they arrived as the firewall's rejection page under
     * HTTP 200, indistinguishable from any other block. Fixing the seven handwritten 500s would
     * have left that untouched, because the problem was the absence of a floor rather than those
     * seven lines.
     *
     * <h2>Spring's own exceptions keep their status</h2>
     * {@link ErrorResponse} is implemented by the exceptions that carry an intended status -
     * {@code ResponseStatusException}, {@code NoResourceFoundException} and others. Without the
     * check below this handler would sit in front of them and flatten every one into a server
     * fault: the 400s and 409s raised across the asset-type, courier and consultant services, and a
     * 404 from an unmatched route, would all have started reporting as "something went wrong".
     * Their status is honored wherever the firewall admits it.
     *
     * <p>The exception is logged in full here because the response deliberately does not carry it -
     * a stack trace is not something to hand a caller, and the firewall would block it anyway.
     */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<ProblemDetail> handleUnexpectedException(Exception ex) {
        HttpStatusCode status = WafStatus.SERVER_FAULT;
        String errorCode = WafStatus.SERVER_FAULT_CODE;
        String detail = "Something went wrong. Please try again, or contact support if it continues.";

        if (ex instanceof ErrorResponse errorResponse
                && WafStatus.ALLOWED_ERROR_STATUSES.contains(errorResponse.getStatusCode().value())) {
            status = errorResponse.getStatusCode();
            errorCode = null;
            detail = ex.getMessage();
        } else {
            log.error("Unhandled exception, reported as {}", WafStatus.SERVER_FAULT.value(), ex);
        }

        ProblemDetail errorDetail = ProblemDetail.forStatusAndDetail(status, detail);
        if (errorCode != null) {
            errorDetail.setProperty("errorCode", errorCode);
        }
        errorDetail.setProperty("message", detail);
        return ResponseEntity.status(status).body(errorDetail);
    }
}
