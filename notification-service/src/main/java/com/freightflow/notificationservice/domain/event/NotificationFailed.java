package com.freightflow.notificationservice.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a notification permanently fails delivery.
 *
 * <p>Emitted after all retry attempts are exhausted. Consumers: alerting systems,
 * operations dashboards, dead letter processing.</p>
 *
 * @param eventId         unique event identifier
 * @param notificationId  the failed notification
 * @param recipientId     the intended recipient
 * @param channelType     the delivery channel that failed (EMAIL, SMS, WEBHOOK)
 * @param subject         the notification subject
 * @param attempts        the total number of delivery attempts
 * @param errorMessage    the final error message
 * @param occurredAt      when the permanent failure was determined
 */
public record NotificationFailed(
        UUID eventId,
        UUID notificationId,
        UUID recipientId,
        String channelType,
        String subject,
        int attempts,
        String errorMessage,
        Instant occurredAt
) implements NotificationEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public NotificationFailed(UUID notificationId, UUID recipientId, String channelType,
                               String subject, int attempts, String errorMessage,
                               Instant occurredAt) {
        this(UUID.randomUUID(), notificationId, recipientId, channelType,
                subject, attempts, errorMessage, occurredAt);
    }
}
