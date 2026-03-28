package com.freightflow.notificationservice.domain.model;

import java.util.List;
import java.util.Map;

/**
 * Sealed interface representing the delivery channel for a notification.
 *
 * <p>Using a sealed interface ensures that all channel types are explicitly defined
 * and can be exhaustively matched in switch expressions (Java 21 pattern matching).
 * Each channel carries the routing information needed by the corresponding sender.</p>
 *
 * <h3>Strategy Pattern</h3>
 * <p>The {@code NotificationChannel} serves as the strategy discriminator. The
 * {@link com.freightflow.notificationservice.domain.port.NotificationSender}
 * implementations select the appropriate sending logic based on the channel type.</p>
 *
 * @see EmailChannel
 * @see SmsChannel
 * @see WebhookChannel
 */
public sealed interface NotificationChannel
        permits NotificationChannel.EmailChannel,
                NotificationChannel.SmsChannel,
                NotificationChannel.WebhookChannel {

    /**
     * Returns the channel type name for serialization and routing.
     *
     * @return the channel type name
     */
    default String channelType() {
        return this.getClass().getSimpleName();
    }

    /**
     * Email delivery channel — sends notifications via SMTP.
     *
     * @param to  the primary recipient email address
     * @param cc  the carbon copy recipients (may be empty)
     * @param bcc the blind carbon copy recipients (may be empty)
     */
    record EmailChannel(
            String to,
            List<String> cc,
            List<String> bcc
    ) implements NotificationChannel {

        /**
         * Compact canonical constructor with validation.
         */
        public EmailChannel {
            if (to == null || to.isBlank()) {
                throw new IllegalArgumentException("Email 'to' address must not be blank");
            }
            if (cc == null) { cc = List.of(); }
            if (bcc == null) { bcc = List.of(); }
        }

        /**
         * Convenience constructor for a single recipient with no CC/BCC.
         *
         * @param to the recipient email address
         */
        public EmailChannel(String to) {
            this(to, List.of(), List.of());
        }
    }

    /**
     * SMS delivery channel — sends notifications via SMS gateway.
     *
     * @param phoneNumber the recipient phone number (E.164 format)
     */
    record SmsChannel(
            String phoneNumber
    ) implements NotificationChannel {

        /**
         * Compact canonical constructor with validation.
         */
        public SmsChannel {
            if (phoneNumber == null || phoneNumber.isBlank()) {
                throw new IllegalArgumentException("Phone number must not be blank");
            }
        }
    }

    /**
     * Webhook delivery channel — sends notifications via HTTP POST to an external URL.
     *
     * @param url     the webhook endpoint URL
     * @param headers custom HTTP headers to include in the webhook request
     */
    record WebhookChannel(
            String url,
            Map<String, String> headers
    ) implements NotificationChannel {

        /**
         * Compact canonical constructor with validation.
         */
        public WebhookChannel {
            if (url == null || url.isBlank()) {
                throw new IllegalArgumentException("Webhook URL must not be blank");
            }
            if (headers == null) { headers = Map.of(); }
        }

        /**
         * Convenience constructor for a webhook with no custom headers.
         *
         * @param url the webhook endpoint URL
         */
        public WebhookChannel(String url) {
            this(url, Map.of());
        }
    }
}
