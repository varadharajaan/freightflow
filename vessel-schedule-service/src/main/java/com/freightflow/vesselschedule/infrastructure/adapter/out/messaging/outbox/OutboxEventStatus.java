package com.freightflow.vesselschedule.infrastructure.adapter.out.messaging.outbox;

/**
 * Lifecycle state for outbox events.
 */
public enum OutboxEventStatus {
    PENDING,
    PUBLISHED,
    FAILED
}
