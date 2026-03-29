package com.freightflow.booking.application.saga;

import java.util.Arrays;
import java.util.Comparator;
import java.util.List;

/**
 * Defines the individual steps in the Booking Confirmation Saga.
 *
 * <p>Each step has an execution order, a flag indicating whether it supports compensation
 * (rollback), and a human-readable description. Steps are executed in ascending order
 * and compensated in descending order on failure.</p>
 *
 * <pre>
 * Execution order:  CONFIRM_BOOKING(1) → RESERVE_CAPACITY(2) → GENERATE_INVOICE(3) → SEND_NOTIFICATION(4)
 * Compensation order: GENERATE_INVOICE(3) → RESERVE_CAPACITY(2) → CONFIRM_BOOKING(1)
 *                     (SEND_NOTIFICATION is fire-and-forget — no compensation)
 * </pre>
 *
 * @see SagaExecution
 * @see BookingConfirmationSaga
 */
public enum SagaStep {

    /**
     * Step 1: Confirm the booking in the booking service.
     * Compensation: Cancel the booking.
     */
    CONFIRM_BOOKING(1, true, "Confirm booking and transition to CONFIRMED status"),

    /**
     * Step 2: Reserve vessel capacity in the vessel schedule service.
     * Compensation: Release the reserved capacity.
     */
    RESERVE_CAPACITY(2, true, "Reserve vessel capacity for the booked TEU"),

    /**
     * Step 3: Generate an invoice in the billing service.
     * Compensation: Cancel the generated invoice.
     */
    GENERATE_INVOICE(3, true, "Generate invoice for the confirmed booking"),

    /**
     * Step 4: Send a confirmation notification.
     * No compensation needed — this is a fire-and-forget operation.
     */
    SEND_NOTIFICATION(4, false, "Send booking confirmation notification");

    private final int order;
    private final boolean compensatable;
    private final String description;

    SagaStep(int order, boolean compensatable, String description) {
        this.order = order;
        this.compensatable = compensatable;
        this.description = description;
    }

    /**
     * Returns the execution order of this step.
     *
     * <p>Steps are executed in ascending order: 1, 2, 3, 4.</p>
     *
     * @return the step's execution order
     */
    public int order() {
        return order;
    }

    /**
     * Indicates whether this step supports compensating transactions.
     *
     * <p>Steps that are not compensatable (e.g., fire-and-forget notifications)
     * are skipped during the compensation phase.</p>
     *
     * @return {@code true} if the step can be rolled back
     */
    public boolean isCompensatable() {
        return compensatable;
    }

    /**
     * Returns a human-readable description of this step.
     *
     * @return the step description
     */
    public String description() {
        return description;
    }

    /**
     * Returns the compensation order for this step (reverse of execution order).
     *
     * <p>During compensation, steps are rolled back in reverse execution order.
     * This method returns a value suitable for sorting: higher execution-order
     * steps are compensated first.</p>
     *
     * @return the compensation priority (negative of execution order for natural reverse sorting)
     */
    public int compensationOrder() {
        return -order;
    }

    /**
     * Returns all compensatable steps sorted in compensation order (reverse execution order).
     *
     * <p>Used by the saga orchestrator to determine which steps to roll back
     * when a failure occurs.</p>
     *
     * @return list of compensatable steps in compensation (reverse) order
     */
    public static List<SagaStep> compensatableStepsInReverseOrder() {
        return Arrays.stream(values())
                .filter(SagaStep::isCompensatable)
                .sorted(Comparator.comparingInt(SagaStep::compensationOrder))
                .toList();
    }
}
