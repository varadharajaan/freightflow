package com.freightflow.booking.domain.model;

/**
 * Represents the lifecycle states of a booking aggregate.
 *
 * <p>State transitions follow a strict state machine pattern:</p>
 * <pre>
 *   DRAFT ──→ CONFIRMED ──→ SHIPPED ──→ DELIVERED
 *     │           │
 *     ▼           ▼
 *  CANCELLED   CANCELLED
 * </pre>
 *
 * <p>Invalid transitions throw {@link com.freightflow.commons.exception.ConflictException}
 * with the current state and attempted target state.</p>
 *
 * @see Booking
 */
public enum BookingStatus {

    /** Initial state — booking has been created but not yet confirmed. */
    DRAFT,

    /** Booking has been confirmed — containers allocated, voyage assigned. */
    CONFIRMED,

    /** Cargo has been loaded onto the vessel and is in transit. */
    SHIPPED,

    /** Cargo has been delivered to the destination port. Terminal state. */
    DELIVERED,

    /** Booking has been cancelled. Terminal state. */
    CANCELLED;

    /**
     * Checks whether a transition from this state to the target state is allowed.
     *
     * @param target the desired target state
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(BookingStatus target) {
        return switch (this) {
            case DRAFT -> target == CONFIRMED || target == CANCELLED;
            case CONFIRMED -> target == SHIPPED || target == CANCELLED;
            case SHIPPED -> target == DELIVERED;
            case DELIVERED, CANCELLED -> false;
        };
    }
}
