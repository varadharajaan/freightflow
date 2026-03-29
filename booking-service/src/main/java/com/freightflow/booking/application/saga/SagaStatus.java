package com.freightflow.booking.application.saga;

/**
 * Represents the lifecycle status of a saga execution.
 *
 * <p>The saga progresses through a linear state machine during normal execution,
 * and transitions to compensating states on failure:</p>
 *
 * <pre>
 * ┌─────────┐    ┌────────────────────┐    ┌─────────────────────┐    ┌──────────────────────┐    ┌───────────────────────┐    ┌───────────┐
 * │ STARTED │───→│ CONFIRMING_BOOKING │───→│ RESERVING_CAPACITY  │───→│ GENERATING_INVOICE   │───→│ SENDING_NOTIFICATION  │───→│ COMPLETED │
 * └─────────┘    └────────┬───────────┘    └──────────┬──────────┘    └──────────┬───────────┘    └───────────────────────┘    └───────────┘
 *                         │                           │                          │
 *                         ▼                           ▼                          ▼
 *                    ┌────────┐                 ┌──────────────┐           ┌──────────────┐
 *                    │ FAILED │◄────────────────│ COMPENSATING │◄──────────│ COMPENSATING │
 *                    └────────┘                 └──────────────┘           └──────────────┘
 * </pre>
 *
 * <p>Terminal states ({@link #COMPLETED} and {@link #FAILED}) indicate the saga
 * has finished execution — no further transitions are permitted.</p>
 *
 * @see SagaExecution
 * @see BookingConfirmationSaga
 */
public enum SagaStatus {

    /** Saga has been created but no steps have been executed yet. */
    STARTED,

    /** Step 1: Confirming the booking in the booking service. */
    CONFIRMING_BOOKING,

    /** Step 2: Reserving vessel capacity in the vessel schedule service. */
    RESERVING_CAPACITY,

    /** Step 3: Generating an invoice in the billing service. */
    GENERATING_INVOICE,

    /** Step 4: Sending a confirmation notification (fire-and-forget). */
    SENDING_NOTIFICATION,

    /** All steps completed successfully — saga is done. */
    COMPLETED,

    /** One or more steps failed — compensating transactions are being executed. */
    COMPENSATING,

    /** Saga failed and all compensation has been attempted. Terminal state. */
    FAILED;

    /**
     * Checks whether this status represents a terminal state.
     *
     * <p>A saga in a terminal state cannot transition to any other state.
     * Both {@link #COMPLETED} and {@link #FAILED} are terminal.</p>
     *
     * @return {@code true} if the saga has finished execution
     */
    public boolean isTerminal() {
        return this == COMPLETED || this == FAILED;
    }
}
