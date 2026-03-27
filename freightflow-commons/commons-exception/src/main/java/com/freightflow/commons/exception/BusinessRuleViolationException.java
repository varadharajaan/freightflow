package com.freightflow.commons.exception;

import java.io.Serial;

/**
 * Thrown when a business rule is violated.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity. Unlike {@link ValidationException} which
 * handles input format validation, this exception handles domain-level business rules.</p>
 *
 * <p>Examples: insufficient vessel capacity, booking deadline passed,
 * customer credit limit exceeded.</p>
 */
public final class BusinessRuleViolationException extends FreightFlowException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String ruleName;

    public BusinessRuleViolationException(String errorCode, String message, String ruleName) {
        super(errorCode, message);
        this.ruleName = ruleName;
    }

    public BusinessRuleViolationException(String message, String ruleName) {
        this("BUSINESS_RULE_VIOLATION", message, ruleName);
    }

    public String getRuleName() {
        return ruleName;
    }
}
