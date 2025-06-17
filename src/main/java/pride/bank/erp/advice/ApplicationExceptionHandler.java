/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.advice;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ProblemDetail;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.InsufficientAuthenticationException;
import org.springframework.security.core.userdetails.UsernameNotFoundException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.MissingServletRequestParameterException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
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

    @ResponseStatus(HttpStatus.BAD_REQUEST)
    @ExceptionHandler(IllegalArgumentException.class)
    public ProblemDetail handleIllegalArgument(IllegalArgumentException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.BAD_REQUEST, ex.getMessage());
        errorDetail.setProperty("request_failure", "Bad Request");
        return errorDetail;
    }

    @ResponseStatus(HttpStatus.INTERNAL_SERVER_ERROR)
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
        errorDetail.setProperty("access_denied_reason", "Invalid Email or Password");
        return errorDetail;
    }

    @ExceptionHandler(DisabledException.class)
    @ResponseStatus(HttpStatus.UNAUTHORIZED)
    public ProblemDetail handleDisabledException(DisabledException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.UNAUTHORIZED, "User account is disabled");
        errorDetail.setProperty("access_denied_reason", "Account Disabled");
        return errorDetail;
    }

    @ExceptionHandler(AccessDeniedException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ProblemDetail handleAccessDeniedException(AccessDeniedException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        errorDetail.setProperty("access_denied_reason", "Not Authorized");
        return errorDetail;
    }

    @ExceptionHandler(InsufficientAuthenticationException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ProblemDetail handleInsufficientAuthenticationException(InsufficientAuthenticationException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
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

    @ExceptionHandler(MissingServletRequestParameterException.class)
    @ResponseStatus(HttpStatus.FORBIDDEN)
    public ProblemDetail handleMissingServletRequestParameterException(MissingServletRequestParameterException ex) {
        ProblemDetail errorDetail = ProblemDetail
                .forStatusAndDetail(HttpStatus.FORBIDDEN, ex.getMessage());
        errorDetail.setProperty("message", ex.getMessage());
        return errorDetail;
    }

}
