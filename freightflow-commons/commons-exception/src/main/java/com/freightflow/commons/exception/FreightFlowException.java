package com.freightflow.commons.exception;

import java.io.Serial;

/**
 * Base exception for all FreightFlow application errors.
 *
 * <p>This sealed class hierarchy ensures that all domain exceptions are explicitly
 * defined and handled. Using sealed classes enforces the Liskov Substitution Principle
 * — every permitted subtype is a valid substitution for {@code FreightFlowException}
 * in catch blocks and exception handlers.</p>
 *
 * <p>Exception hierarchy:</p>
 * <pre>
 * FreightFlowException (sealed)
 * ├── ResourceNotFoundException      → 404 Not Found
 * ├── ValidationException            → 422 Unprocessable Entity
 * ├── ConflictException              → 409 Conflict
 * ├── BusinessRuleViolationException → 422 Unprocessable Entity
 * └── ExternalServiceException       → 502/503 Bad Gateway / Service Unavailable
 * </pre>
 *
 * @see ResourceNotFoundException
 * @see ValidationException
 * @see ConflictException
 * @see BusinessRuleViolationException
 * @see ExternalServiceException
 */
public sealed class FreightFlowException extends RuntimeException
        permits ResourceNotFoundException,
                ValidationException,
                ConflictException,
                BusinessRuleViolationException,
                ExternalServiceException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String errorCode;

    /**
     * Constructs a new FreightFlowException.
     *
     * @param errorCode a machine-readable error code (e.g., "BOOKING_NOT_FOUND")
     * @param message   a human-readable error message
     */
    protected FreightFlowException(String errorCode, String message) {
        super(message);
        this.errorCode = errorCode;
    }

    /**
     * Constructs a new FreightFlowException with a cause.
     *
     * @param errorCode a machine-readable error code
     * @param message   a human-readable error message
     * @param cause     the underlying cause
     */
    protected FreightFlowException(String errorCode, String message, Throwable cause) {
        super(message, cause);
        this.errorCode = errorCode;
    }

    /**
     * Returns the machine-readable error code.
     *
     * @return the error code
     */
    public String getErrorCode() {
        return errorCode;
    }
}
