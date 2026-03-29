package com.freightflow.booking.application.port;

/**
 * Outbound application port for vessel capacity reservation and compensation.
 */
public interface VesselCapacityPort {

    /**
     * Reserves vessel capacity.
     *
     * @param voyageId voyage identifier
     * @param teu required TEU to reserve
     * @param idempotencyKey idempotency key for downstream deduplication
     * @return {@code true} when capacity was reserved
     */
    boolean reserveCapacity(String voyageId, double teu, String idempotencyKey);

    /**
     * Releases previously reserved vessel capacity.
     *
     * @param voyageId voyage identifier
     * @param teu TEU to release
     * @param idempotencyKey idempotency key for downstream deduplication
     * @return {@code true} when capacity was released
     */
    boolean releaseCapacity(String voyageId, double teu, String idempotencyKey);
}

