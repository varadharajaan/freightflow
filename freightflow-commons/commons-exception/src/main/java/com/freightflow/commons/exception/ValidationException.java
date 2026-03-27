package com.freightflow.commons.exception;

import java.io.Serial;
import java.util.Collections;
import java.util.List;

/**
 * Thrown when input validation fails.
 *
 * <p>Maps to HTTP 422 Unprocessable Entity. Carries a list of field-level
 * validation errors for structured error reporting via RFC 7807.</p>
 */
public final class ValidationException extends FreightFlowException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final List<FieldError> fieldErrors;

    public ValidationException(String errorCode, String message, List<FieldError> fieldErrors) {
        super(errorCode, message);
        this.fieldErrors = fieldErrors != null ? List.copyOf(fieldErrors) : List.of();
    }

    public ValidationException(String message, List<FieldError> fieldErrors) {
        this("VALIDATION_ERROR", message, fieldErrors);
    }

    /**
     * Returns an unmodifiable list of field-level validation errors.
     *
     * @return the field errors
     */
    public List<FieldError> getFieldErrors() {
        return fieldErrors;
    }

    /**
     * Represents a single field validation error.
     *
     * @param field         the field name (e.g., "cargo.weight.value")
     * @param message       the validation message
     * @param rejectedValue the value that was rejected
     */
    public record FieldError(String field, String message, Object rejectedValue) {}
}
