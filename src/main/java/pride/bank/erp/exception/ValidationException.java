/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.exception;

import java.io.Serial;

/**
 * Exception thrown when input validation fails.
 * Used to indicate invalid data, missing required fields, or format errors.
 */
public class ValidationException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * Constructs a new validation exception with null as its detail message.
     */
    public ValidationException() {
        super();
    }

    /**
     * Constructs a new validation exception with the specified detail message.
     *
     * @param message the detail message
     */
    public ValidationException(String message) {
        super(message);
    }

    /**
     * Constructs a new validation exception with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public ValidationException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new validation exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public ValidationException(String message, Throwable cause) {
        super(message, cause);
    }
}