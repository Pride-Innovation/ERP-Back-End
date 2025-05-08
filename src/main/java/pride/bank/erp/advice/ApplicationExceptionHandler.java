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
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import pride.bank.erp.exception.NotFoundException;

import java.util.HashMap;
import java.util.Map;

@RestControllerAdvice
public class ApplicationExceptionHandler {

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
            return "Duplicate Entry";
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

}
