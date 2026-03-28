package com.freightflow.trackingservice.domain.model;

/**
 * Represents the lifecycle states of a container in the tracking domain.
 *
 * <p>State transitions follow a strict state machine pattern:</p>
 * <pre>
 *   EMPTY ──→ LOADED ──→ IN_TRANSIT ──→ AT_PORT ──→ DELIVERED
 *                              │           │
 *                              ▼           ▼
 *                           AT_PORT    IN_TRANSIT (re-entry for transshipment)
 * </pre>
 *
 * <p>Invalid transitions throw {@link com.freightflow.commons.exception.ConflictException}
 * with the current state and attempted target state.</p>
 *
 * @see Container
 */
public enum ContainerStatus {

    /** Container is empty and awaiting cargo loading. */
    EMPTY,

    /** Cargo has been loaded into the container. */
    LOADED,

    /** Container is in transit on a vessel. */
    IN_TRANSIT,

    /** Container has arrived at a port (may be intermediate or final). */
    AT_PORT,

    /** Container has been delivered to the consignee. Terminal state. */
    DELIVERED;

    /**
     * Checks whether a transition from this state to the target state is allowed.
     *
     * @param target the desired target state
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(ContainerStatus target) {
        return switch (this) {
            case EMPTY -> target == LOADED;
            case LOADED -> target == IN_TRANSIT;
            case IN_TRANSIT -> target == AT_PORT;
            case AT_PORT -> target == IN_TRANSIT || target == DELIVERED;
            case DELIVERED -> false;
        };
    }
}
