package com.freightflow.billingservice.domain.model;

/**
 * Represents the lifecycle states of an invoice aggregate.
 *
 * <p>State transitions follow a strict state machine pattern:</p>
 * <pre>
 *   DRAFT ──→ ISSUED ──→ PAID
 *     │          │
 *     ▼          ▼
 *  CANCELLED   OVERDUE ──→ PAID
 *                 │
 *                 ▼
 *             CANCELLED
 * </pre>
 *
 * <p>Invalid transitions throw {@link com.freightflow.commons.exception.ConflictException}
 * with the current state and attempted target state.</p>
 *
 * @see Invoice
 */
public enum InvoiceStatus {

    /** Initial state — invoice has been created but not yet finalized. */
    DRAFT,

    /** Invoice has been issued to the customer. */
    ISSUED,

    /** Payment has been received in full. Terminal state. */
    PAID,

    /** Invoice has passed its due date without payment. */
    OVERDUE,

    /** Invoice has been cancelled. Terminal state. */
    CANCELLED;

    /**
     * Checks whether a transition from this state to the target state is allowed.
     *
     * @param target the desired target state
     * @return true if the transition is valid
     */
    public boolean canTransitionTo(InvoiceStatus target) {
        return switch (this) {
            case DRAFT -> target == ISSUED || target == CANCELLED;
            case ISSUED -> target == PAID || target == OVERDUE || target == CANCELLED;
            case OVERDUE -> target == PAID || target == CANCELLED;
            case PAID, CANCELLED -> false;
        };
    }
}
