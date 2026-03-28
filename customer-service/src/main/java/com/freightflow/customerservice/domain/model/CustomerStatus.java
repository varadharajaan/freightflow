package com.freightflow.customerservice.domain.model;

/**
 * Represents the lifecycle states of a customer aggregate.
 *
 * <p>State transitions follow a strict state machine pattern:</p>
 * <pre>
 *   ACTIVE ──→ SUSPENDED ──→ ACTIVE
 *     │            │
 *     ▼            ▼
 *   CLOSED       CLOSED
 * </pre>
 *
 * <p>Invalid transitions throw {@link com.freightflow.commons.exception.ConflictException}
 * with the current state and attempted target state.</p>
 *
 * @see Customer
 */
public enum CustomerStatus {

    /** Customer is active and can place bookings and use credit. */
    ACTIVE,

    /** Customer is temporarily suspended — bookings and credit operations are blocked. */
    SUSPENDED,

    /** Customer account is permanently closed. Terminal state. */
    CLOSED;

    /**
     * Checks whether a transition from this state to the target state is allowed.
     *
     * @param target the desired target state
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(CustomerStatus target) {
        return switch (this) {
            case ACTIVE -> target == SUSPENDED || target == CLOSED;
            case SUSPENDED -> target == ACTIVE || target == CLOSED;
            case CLOSED -> false;
        };
    }
}
