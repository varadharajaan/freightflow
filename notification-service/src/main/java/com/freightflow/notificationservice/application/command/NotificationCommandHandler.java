package com.freightflow.notificationservice.application.command;

import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.notificationservice.domain.model.Notification;
import com.freightflow.notificationservice.domain.model.NotificationChannel;
import com.freightflow.notificationservice.domain.model.NotificationChannel.EmailChannel;
import com.freightflow.notificationservice.domain.model.NotificationChannel.SmsChannel;
import com.freightflow.notificationservice.domain.model.NotificationChannel.WebhookChannel;
import com.freightflow.notificationservice.domain.port.NotificationRepository;
import com.freightflow.notificationservice.domain.port.NotificationSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Command Handler — handles all write operations for the notification aggregate.
 *
 * <p>This is the <b>write side</b> of the application. It:</p>
 * <ol>
 *   <li>Creates a new notification with the specified channel</li>
 *   <li>Selects the appropriate sender using the Strategy pattern</li>
 *   <li>Attempts delivery and handles success/failure/retry</li>
 *   <li>Persists the notification state and domain events</li>
 * </ol>
 *
 * <h3>Strategy Pattern for Channel Selection</h3>
 * <p>Uses Java 21 pattern matching on the sealed {@link NotificationChannel} hierarchy
 * to select the appropriate {@link NotificationSender} implementation. This is resolved
 * at runtime based on the channel type of each notification.</p>
 *
 * <h3>Design Patterns</h3>
 * <ul>
 *   <li><b>Strategy</b> — channel-specific sending behavior via {@link NotificationSender}</li>
 *   <li><b>Command Handler</b> — centralized write operations</li>
 *   <li><b>Retry Pattern</b> — automatic retry with exponential backoff via aggregate</li>
 * </ul>
 *
 * @see NotificationSender
 * @see Notification
 * @see NotificationChannel
 */
@Service
public class NotificationCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationCommandHandler.class);

    private final NotificationRepository notificationRepository;
    private final List<NotificationSender> senders;

    /**
     * Constructor injection — depends on the repository and all available senders.
     *
     * <p>Spring automatically injects all {@link NotificationSender} implementations
     * as a list, enabling the Strategy pattern for channel selection.</p>
     *
     * @param notificationRepository the notification persistence port
     * @param senders                all available notification sender implementations
     */
    public NotificationCommandHandler(NotificationRepository notificationRepository,
                                       List<NotificationSender> senders) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository,
                "NotificationRepository must not be null");
        this.senders = Objects.requireNonNull(senders, "Senders list must not be null");

        log.info("NotificationCommandHandler initialized with {} sender(s): {}",
                senders.size(),
                senders.stream().map(s -> s.getClass().getSimpleName()).toList());
    }

    /**
     * Creates and sends a notification through the specified channel.
     *
     * <p>The notification is persisted first in PENDING status, then the appropriate
     * sender is selected based on the channel type. On success, the notification is
     * marked as SENT. On failure, it is marked for retry or permanent failure.</p>
     *
     * @param recipientId the recipient customer/user ID
     * @param channel     the delivery channel
     * @param subject     the notification subject
     * @param body        the notification body
     * @return the notification (with final status)
     */
    @Transactional
    @Profiled(value = "sendNotification", slowThresholdMs = 2000)
    public Notification sendNotification(UUID recipientId, NotificationChannel channel,
                                          String subject, String body) {
        log.debug("Creating notification: recipientId={}, channel={}, subject='{}'",
                recipientId, channel.channelType(), subject);

        Notification notification = Notification.create(recipientId, channel, subject, body);
        notification = notificationRepository.save(notification);

        log.info("Notification created: notificationId={}, channel={}, status={}",
                notification.getNotificationId(), channel.channelType(), notification.getStatus());

        return attemptDelivery(notification);
    }

    /**
     * Retries sending a previously failed or retrying notification.
     *
     * @param notificationId the notification to retry
     * @return the notification with updated status
     */
    @Transactional
    @Profiled(value = "retryNotification", slowThresholdMs = 2000)
    public Notification retryNotification(UUID notificationId) {
        log.debug("Retrying notification: notificationId={}", notificationId);

        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Notification not found: " + notificationId));

        return attemptDelivery(notification);
    }

    /**
     * Attempts to deliver a notification by selecting the appropriate sender.
     *
     * <p>Uses Java 21 pattern matching on the sealed {@link NotificationChannel}
     * to select the correct sender strategy. On success, marks as SENT; on failure,
     * marks for retry or permanent failure based on the retry policy.</p>
     *
     * @param notification the notification to deliver
     * @return the notification with updated status
     */
    @Profiled(value = "attemptDelivery", slowThresholdMs = 1000)
    private Notification attemptDelivery(Notification notification) {
        String channelType = resolveChannelType(notification.getChannel());

        log.debug("Attempting delivery: notificationId={}, channel={}, attempt={}",
                notification.getNotificationId(), channelType, notification.getAttempts() + 1);

        NotificationSender sender = findSender(notification.getChannel());

        try {
            sender.send(notification);
            notification.markSent();

            log.info("Notification sent successfully: notificationId={}, channel={}, attempts={}",
                    notification.getNotificationId(), channelType, notification.getAttempts());

        } catch (NotificationSender.NotificationSendException ex) {
            log.warn("Notification delivery failed: notificationId={}, channel={}, error={}",
                    notification.getNotificationId(), channelType, ex.getMessage());

            notification.markRetryOrFail(ex.getMessage());

            log.info("Notification status after failure: notificationId={}, status={}, attempts={}",
                    notification.getNotificationId(), notification.getStatus(),
                    notification.getAttempts());

        } catch (Exception ex) {
            log.error("Unexpected error during notification delivery: notificationId={}, error={}",
                    notification.getNotificationId(), ex.getMessage(), ex);

            notification.markRetryOrFail("Unexpected error: " + ex.getMessage());
        }

        return notificationRepository.save(notification);
    }

    /**
     * Finds the appropriate sender for the given channel using the Strategy pattern.
     *
     * @param channel the notification channel
     * @return the matching sender
     * @throws IllegalStateException if no sender supports the channel
     */
    private NotificationSender findSender(NotificationChannel channel) {
        return senders.stream()
                .filter(sender -> sender.supports(channel))
                .findFirst()
                .orElseThrow(() -> {
                    log.error("No sender found for channel: {}", channel.channelType());
                    return new IllegalStateException(
                            "No NotificationSender found for channel: " + channel.channelType());
                });
    }

    /**
     * Resolves the channel type name using Java 21 pattern matching on the sealed interface.
     *
     * @param channel the notification channel
     * @return the channel type name
     */
    private String resolveChannelType(NotificationChannel channel) {
        return switch (channel) {
            case EmailChannel email -> "EMAIL (to=%s)".formatted(email.to());
            case SmsChannel sms -> "SMS (phone=%s)".formatted(sms.phoneNumber());
            case WebhookChannel webhook -> "WEBHOOK (url=%s)".formatted(webhook.url());
        };
    }
}
