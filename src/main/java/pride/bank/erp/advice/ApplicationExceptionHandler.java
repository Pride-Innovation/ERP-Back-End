/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.advice;

import org.springframework.dao.DataIntegrityViolationException;
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
     * A token that is absent, malformed or expired where one was required.
     *
     * <p>Moved off 403 with its neighbour, and deliberately <b>not</b> onto 401: the front end reads
     * 401 as "the session expired", tries a refresh and then signs the user out. This exception is
     * also raised for a token that is simply wrong for the route, and logging somebody out for that
     * would be a worse answer than telling them.
     */
    @ExceptionHandler(InsufficientAuthenticationException.class)
    public ProblemDetail handleInsufficientAuthenticationException(InsufficientAuthenticationException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(AccessDenied.STATUS, ex.getMessage());
        errorDetail.setProperty("errorCode", AccessDenied.ERROR_CODE);
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
}
