/*
13.9 Pride's Standard Copyright Notice:
Copyright ©20XX. Management of Pride Bank Limited (PBL). All Rights Reserved. Permission to use, copy, modify,
and distribute this software and its documentation for any purpose is prohibited unless authorized in writing by the
Managing Director
*/

package pride.bank.erp.exception;

import lombok.Getter;

import java.io.Serial;

/**
 * Exception thrown when business rules are violated.
 * Used to indicate violations of domain-specific rules such as:
 * - Branch users mixing stationery with non-stationery items
 * - Insufficient inventory for requested quantities
 * - Invalid workflow state transitions
 */
@Getter
public class BusinessRuleException extends RuntimeException {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * -- GETTER --
     *  Gets the rule code associated with this exception.
     *
     */
    // Optional: can be used to categorize different types of business rule violations
    private String ruleCode;

    /**
     * Constructs a new business rule exception with null as its detail message.
     */
    public BusinessRuleException() {
        super();
    }

    /**
     * Constructs a new business rule exception with the specified detail message.
     *
     * @param message the detail message
     */
    public BusinessRuleException(String message) {
        super(message);
    }

    /**
     * Constructs a new business rule exception with the specified cause.
     *
     * @param cause the cause of the exception
     */
    public BusinessRuleException(Throwable cause) {
        super(cause);
    }

    /**
     * Constructs a new business rule exception with the specified detail message and cause.
     *
     * @param message the detail message
     * @param cause the cause of the exception
     */
    public BusinessRuleException(String message, Throwable cause) {
        super(message, cause);
    }

    /**
     * Constructs a new business rule exception with the specified detail message and rule code.
     *
     * @param message the detail message
     * @param ruleCode a code identifying the specific business rule that was violated
     */
    public BusinessRuleException(String message, String ruleCode) {
        super(message);
        this.ruleCode = ruleCode;
    }

}