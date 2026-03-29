package com.freightflow.booking.application.port;

/**
 * Outbound application port for notification operations.
 */
public interface NotificationPort {

    /**
     * Sends booking confirmation notification.
     *
     * @param bookingId booking identifier
     * @param idempotencyKey idempotency key for downstream deduplication
     * @return {@code true} when notification dispatch is accepted/successful
     */
    boolean sendBookingConfirmation(String bookingId, String idempotencyKey);
}

