package com.freightflow.notificationservice.domain.model;

import java.io.Serial;

/**
 * Exception thrown when notification delivery fails.
 *
 * <p>Wraps the underlying transport exception (SMTP error, HTTP error, etc.)
 * with contextual information about the notification.</p>
 *
 * @see com.freightflow.notificationservice.domain.port.NotificationSender
 */
public class NotificationSendException extends RuntimeException {

    @Serial
    private static final long serialVersionUID = 1L;

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
