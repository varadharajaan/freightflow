package com.freightflow.booking.domain.model;

/**
 * Represents the lifecycle states of a freight quote.
 *
 * <p>A quote transitions through the following states:</p>
 * <pre>
 *   ACTIVE ──→ EXPIRED     (time-based, when validUntil date passes)
 *   ACTIVE ──→ CONVERTED   (customer accepts quote and it becomes a booking)
 * </pre>
 *
 * <p>Both {@code EXPIRED} and {@code CONVERTED} are terminal states.</p>
 *
 * @see Quote
 */
public enum QuoteStatus {

    /** Quote is active and can be converted to a booking. */
    ACTIVE,

    /** Quote has passed its validity date and can no longer be used. */
    EXPIRED,

    /** Quote has been accepted and converted into a booking. */
    CONVERTED
}
