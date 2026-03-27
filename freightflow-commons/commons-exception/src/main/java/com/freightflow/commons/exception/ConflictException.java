package com.freightflow.commons.exception;

import java.io.Serial;

/**
 * Thrown when an operation conflicts with the current state of a resource.
 *
 * <p>Maps to HTTP 409 Conflict. Typical scenarios:</p>
 * <ul>
 *   <li>Invalid state transition (e.g., cancelling an already shipped booking)</li>
 *   <li>Optimistic locking failure (concurrent modification)</li>
 *   <li>Duplicate resource creation (idempotency violation)</li>
 * </ul>
 */
public final class ConflictException extends FreightFlowException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String currentState;

    public ConflictException(String errorCode, String message, String currentState) {
        super(errorCode, message);
        this.currentState = currentState;
    }

    /**
     * Factory for invalid state transition.
     *
     * @param resourceType the resource type
     * @param resourceId   the resource ID
     * @param currentState the current state
     * @param targetState  the attempted target state
     * @return a ConflictException
     */
    public static ConflictException invalidStateTransition(
            String resourceType, String resourceId, String currentState, String targetState) {
        return new ConflictException(
                "INVALID_STATE_TRANSITION",
                "Cannot transition %s '%s' from %s to %s".formatted(
                        resourceType, resourceId, currentState, targetState),
                currentState);
    }

    /**
     * Factory for optimistic locking conflict.
     *
     * @param resourceType the resource type
     * @param resourceId   the resource ID
     * @return a ConflictException
     */
    public static ConflictException optimisticLock(String resourceType, String resourceId) {
        return new ConflictException(
                "OPTIMISTIC_LOCK_CONFLICT",
                "%s '%s' was modified by another transaction. Please retry.".formatted(
                        resourceType, resourceId),
                null);
    }

    public String getCurrentState() {
        return currentState;
    }
}
