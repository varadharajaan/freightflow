package com.freightflow.notificationservice.infrastructure.adapter.out.persistence;

import com.freightflow.notificationservice.domain.model.Notification;
import com.freightflow.notificationservice.domain.model.NotificationChannel;
import com.freightflow.notificationservice.domain.model.NotificationChannel.EmailChannel;
import com.freightflow.notificationservice.domain.model.NotificationChannel.SmsChannel;
import com.freightflow.notificationservice.domain.model.NotificationChannel.WebhookChannel;
import com.freightflow.notificationservice.domain.model.NotificationStatus;
import com.freightflow.notificationservice.infrastructure.adapter.out.persistence.entity.NotificationJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Maps between the JPA entity ({@link NotificationJpaEntity}) and the domain model
 * ({@link Notification}).
 *
 * <p>This mapper is the translation layer between the persistence world (JPA annotations,
 * flat columns) and the domain world (aggregates, sealed interfaces, value objects).
 * It ensures the domain model stays completely free of persistence concerns
 * (Dependency Inversion Principle).</p>
 *
 * <p>The {@link NotificationChannel} sealed hierarchy (Email, SMS, Webhook) is serialized
 * as a channel type discriminator plus a JSON-like channel data string. This mapper handles
 * the flattening and reconstruction of these sealed types.</p>
 *
 * @see NotificationJpaEntity
 * @see Notification
 * @see NotificationChannel
 */
@Component
public class NotificationEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(NotificationEntityMapper.class);

    /**
     * Converts a domain {@link Notification} aggregate to a {@link NotificationJpaEntity}
     * for persistence.
     *
     * @param notification the domain notification aggregate
     * @return the JPA entity ready for persistence
     */
    public NotificationJpaEntity toEntity(Notification notification) {
        log.trace("Mapping domain Notification to JPA entity: notificationId={}",
                notification.getNotificationId());

        var entity = new NotificationJpaEntity();
        entity.setId(notification.getNotificationId());
        entity.setRecipientId(notification.getRecipientId());

        // Channel fields (sealed interface flattened to type + data)
        NotificationChannel channel = notification.getChannel();
        entity.setChannelType(channel.channelType());
        entity.setChannelData(serializeChannelData(channel));

        // Content fields
        entity.setSubject(notification.getSubject());
        entity.setBody(notification.getBody());

        // Status and delivery fields
        entity.setStatus(notification.getStatus().name());
        entity.setAttempts(notification.getAttempts());
        entity.setSentAt(notification.getSentAt());
        entity.setError(notification.getError());

        // Audit fields
        entity.setCreatedAt(notification.getCreatedAt());
        entity.setUpdatedAt(notification.getUpdatedAt());
        entity.setVersion(notification.getVersion());

        return entity;
    }

    /**
     * Reconstructs a domain {@link Notification} aggregate from a {@link NotificationJpaEntity}.
     *
     * <p>Uses {@link Notification#reconstitute} to rebuild the aggregate from persisted state
     * without triggering domain events. The sealed {@link NotificationChannel} is reconstructed
     * from the channel type and channel data stored in the entity.</p>
     *
     * @param entity the JPA entity loaded from the database
     * @return the domain notification aggregate in its persisted state
     */
    public Notification toDomain(NotificationJpaEntity entity) {
        log.trace("Mapping JPA entity to domain Notification: notificationId={}", entity.getId());

        // Reconstruct the sealed NotificationChannel from persisted type + data
        NotificationChannel channel = deserializeChannel(
                entity.getChannelType(), entity.getChannelData());

        return Notification.reconstitute(
                entity.getId(),
                entity.getRecipientId(),
                channel,
                entity.getSubject(),
                entity.getBody(),
                NotificationStatus.valueOf(entity.getStatus()),
                entity.getAttempts(),
                entity.getSentAt(),
                entity.getError(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }

    /**
     * Serializes the channel-specific routing data to a string for persistence.
     *
     * <p>Each channel type produces a simple string representation that captures
     * the essential routing information.</p>
     *
     * @param channel the notification channel to serialize
     * @return the serialized channel data string
     */
    private String serializeChannelData(NotificationChannel channel) {
        return switch (channel) {
            case EmailChannel email -> email.to();
            case SmsChannel sms -> sms.phoneNumber();
            case WebhookChannel webhook -> webhook.url();
        };
    }

    /**
     * Deserializes a {@link NotificationChannel} from the persisted type and data fields.
     *
     * @param channelType the channel type discriminator (e.g., "EmailChannel", "SmsChannel")
     * @param channelData the serialized routing data
     * @return the reconstructed channel instance
     * @throws IllegalStateException if the channel type is not recognized
     */
    private NotificationChannel deserializeChannel(String channelType, String channelData) {
        return switch (channelType) {
            case "EmailChannel" -> new EmailChannel(channelData);
            case "SmsChannel" -> new SmsChannel(channelData);
            case "WebhookChannel" -> new WebhookChannel(channelData);
            default -> throw new IllegalStateException(
                    "Unknown notification channel type: " + channelType);
        };
    }
}
