package com.freightflow.notificationservice.domain.event;

import java.time.Instant;
import java.util.UUID;

/**
 * Domain event emitted when a notification is successfully delivered.
 *
 * <p>Consumers: monitoring dashboards, delivery audit logs.</p>
 *
 * @param eventId         unique event identifier
 * @param notificationId  the delivered notification
 * @param recipientId     the recipient who received the notification
 * @param channelType     the delivery channel used (EMAIL, SMS, WEBHOOK)
 * @param subject         the notification subject
 * @param attempts        the number of attempts before success
 * @param occurredAt      when the delivery was confirmed
 */
public record NotificationSent(
        UUID eventId,
        UUID notificationId,
        UUID recipientId,
        String channelType,
        String subject,
        int attempts,
        Instant occurredAt
) implements NotificationEvent {

    /**
     * Constructor that auto-generates the event ID.
     */
    public NotificationSent(UUID notificationId, UUID recipientId, String channelType,
                             String subject, int attempts, Instant occurredAt) {
        this(UUID.randomUUID(), notificationId, recipientId, channelType,
                subject, attempts, occurredAt);
    }
}
