package com.freightflow.trackingservice.domain.model;

import java.time.Instant;

/**
 * Value object representing a geospatial position of a container at a point in time.
 *
 * <p>Immutable record following DDD value object semantics — equality is based on
 * all fields. Positions are sourced from AIS transponders, GPS devices, or manual entry.</p>
 *
 * @param latitude  the latitude coordinate in decimal degrees (-90 to 90)
 * @param longitude the longitude coordinate in decimal degrees (-180 to 180)
 * @param timestamp when this position was recorded
 * @param source    the data source that provided this position
 */
public record Position(
        double latitude,
        double longitude,
        Instant timestamp,
        PositionSource source
) {

    /**
     * Creates a validated Position.
     *
     * @throws IllegalArgumentException if coordinates are out of valid range
     */
    public Position {
        if (latitude < -90.0 || latitude > 90.0) {
            throw new IllegalArgumentException("Latitude must be between -90 and 90, got: " + latitude);
        }
        if (longitude < -180.0 || longitude > 180.0) {
            throw new IllegalArgumentException("Longitude must be between -180 and 180, got: " + longitude);
        }
        if (timestamp == null) {
            throw new IllegalArgumentException("Position timestamp must not be null");
        }
        if (source == null) {
            throw new IllegalArgumentException("Position source must not be null");
        }
    }

    /**
     * The data source that provided the position reading.
     */
    public enum PositionSource {
        /** Automatic Identification System — vessel transponder. */
        AIS,
        /** Global Positioning System — container GPS tracker. */
        GPS,
        /** Manual entry by port operator or logistics staff. */
        MANUAL
    }
}
