package com.freightflow.trackingservice.domain.model;

import java.time.Instant;
import java.util.Objects;

/**
 * Value object representing a significant logistics milestone in a container's journey.
 *
 * <p>Milestones mark key events in the container lifecycle — gate-in at origin,
 * loading onto vessel, departure, arrival, and gate-out at destination. Each milestone
 * is immutable and carries the port code, timestamp, and a human-readable description.</p>
 *
 * @param milestoneType the type of milestone event
 * @param port          the UN/LOCODE port code where the milestone occurred
 * @param timestamp     when the milestone occurred
 * @param description   human-readable description of the milestone
 */
public record Milestone(
        MilestoneType milestoneType,
        String port,
        Instant timestamp,
        String description
) {

    /**
     * Creates a validated Milestone.
     *
     * @throws NullPointerException if any parameter is null
     */
    public Milestone {
        Objects.requireNonNull(milestoneType, "Milestone type must not be null");
        Objects.requireNonNull(port, "Port must not be null");
        Objects.requireNonNull(timestamp, "Timestamp must not be null");
        Objects.requireNonNull(description, "Description must not be null");
    }

    /**
     * Enumeration of significant logistics milestone types in the container journey.
     */
    public enum MilestoneType {
        /** Container has entered the gate at the origin terminal. */
        GATE_IN,
        /** Container has been loaded onto the vessel. */
        LOADED,
        /** Vessel has departed from the port. */
        DEPARTED,
        /** Vessel has arrived at the port. */
        ARRIVED,
        /** Container has exited the gate at the destination terminal. */
        GATE_OUT
    }
}
