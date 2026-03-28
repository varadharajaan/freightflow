package com.freightflow.trackingservice.infrastructure.adapter.in.rest.dto;

import com.freightflow.trackingservice.domain.model.Position;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotNull;

import java.time.Instant;

/**
 * REST request DTO for updating a container's position.
 *
 * @param latitude  the latitude coordinate (-90 to 90)
 * @param longitude the longitude coordinate (-180 to 180)
 * @param timestamp when this position was recorded
 * @param source    the data source (AIS, GPS, MANUAL)
 */
public record UpdatePositionRequest(
        @NotNull @DecimalMin("-90.0") @DecimalMax("90.0") Double latitude,
        @NotNull @DecimalMin("-180.0") @DecimalMax("180.0") Double longitude,
        @NotNull Instant timestamp,
        @NotNull String source
) {

    /**
     * Converts this request DTO to a domain Position value object.
     *
     * @return the domain Position
     */
    public Position toPosition() {
        return new Position(
                latitude,
                longitude,
                timestamp,
                Position.PositionSource.valueOf(source)
        );
    }
}
