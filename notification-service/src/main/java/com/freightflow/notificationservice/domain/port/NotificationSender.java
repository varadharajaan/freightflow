package com.freightflow.notificationservice.domain.port;

import com.freightflow.notificationservice.domain.model.Notification;
import com.freightflow.notificationservice.domain.model.NotificationChannel;

/**
 * Strategy interface for sending notifications through different channels.
 *
 * <p>Implementations of this interface handle the actual delivery of notifications
 * via their respective channels (email, SMS, webhook). The
 * {@link com.freightflow.notificationservice.application.command.NotificationCommandHandler}
 * selects the appropriate sender based on the notification's channel type using
 * Java 21 pattern matching.</p>
 *
 * <h3>Strategy Pattern</h3>
 * <p>Each implementation encapsulates the sending logic for a specific channel:</p>
 * <ul>
 *   <li>{@code EmailNotificationSender} — sends via SMTP using Spring Mail</li>
 *   <li>{@code SmsNotificationSender} — sends via SMS gateway API</li>
 *   <li>{@code WebhookNotificationSender} — sends via HTTP POST to external endpoint</li>
 * </ul>
 *
 * @see NotificationChannel
 * @see Notification
 */
public interface NotificationSender {

    /**
     * Sends a notification through the appropriate channel.
     *
     * <p>Implementations should throw a {@link NotificationSendException} if delivery fails.
     * The command handler will catch the exception and mark the notification for retry
     * or permanent failure based on the retry policy.</p>
     *
     * @param notification the notification to send
     * @throws NotificationSendException if delivery fails
     */
    void send(Notification notification);

    /**
     * Checks whether this sender supports the given channel type.
     *
     * @param channel the notification channel to check
     * @return true if this sender can handle the channel
     */
    boolean supports(NotificationChannel channel);

    /**
     * Exception thrown when notification delivery fails.
     *
     * <p>Wraps the underlying transport exception (SMTP error, HTTP error, etc.)
     * with contextual information about the notification.</p>
     */
    class NotificationSendException extends RuntimeException {

        /**
         * Creates a send exception with a message and root cause.
         *
         * @param message the contextual error message
         * @param cause   the original transport exception
         */
        public NotificationSendException(String message, Throwable cause) {
            super(message, cause);
        }

        /**
         * Creates a send exception with a message only.
         *
         * @param message the error message
         */
        public NotificationSendException(String message) {
            super(message);
        }
    }
}
