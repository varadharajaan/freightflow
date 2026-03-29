package com.freightflow.booking.application.port;

/**
 * Outbound application port for billing interactions used by the booking saga.
 */
public interface BillingPort {

    /**
     * Generates an invoice for a confirmed booking.
     *
     * @param bookingId booking identifier
     * @param idempotencyKey idempotency key for downstream deduplication
     * @return {@code true} when invoice generation is accepted/successful
     */
    boolean generateInvoice(String bookingId, String idempotencyKey);

    /**
     * Cancels a previously generated invoice during compensation.
     *
     * @param bookingId booking identifier
     * @param idempotencyKey idempotency key that correlates with original request
     * @return {@code true} when cancellation is accepted/successful
     */
    boolean cancelInvoice(String bookingId, String idempotencyKey);
}

