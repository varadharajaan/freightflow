package com.freightflow.notificationservice.domain.model;

import com.freightflow.commons.exception.ConflictException;
import com.freightflow.notificationservice.domain.event.NotificationEvent;
import com.freightflow.notificationservice.domain.event.NotificationFailed;
import com.freightflow.notificationservice.domain.event.NotificationSent;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Notification Aggregate Root — the central domain entity managing notification lifecycle.
 *
 * <p>This class encapsulates all business rules and invariants for sending notifications
 * across multiple channels. State transitions are enforced via the {@link NotificationStatus}
 * state machine, and retry logic is built into the aggregate.</p>
 *
 * <h3>Design Patterns Applied</h3>
 * <ul>
 *   <li><b>Aggregate Root</b> (DDD) — single entry point for all notification mutations</li>
 *   <li><b>State Pattern</b> — transitions governed by {@link NotificationStatus#canTransitionTo}</li>
 *   <li><b>Strategy Pattern</b> — channel selection determines sending behavior</li>
 *   <li><b>Domain Events</b> — state changes emit events for audit and monitoring</li>
 *   <li><b>Factory Method</b> — {@link #create} encapsulates creation logic</li>
 * </ul>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>A notification always has an ID, recipient, channel, subject, and body</li>
 *   <li>State transitions follow the state machine (see {@link NotificationStatus})</li>
 *   <li>Retry attempts cannot exceed the maximum defined in {@link NotificationStatus}</li>
 *   <li>A sent or permanently failed notification cannot be modified</li>
 * </ul>
 *
 * @see NotificationStatus
 * @see NotificationChannel
 * @see NotificationEvent
 */
public class Notification {

    private final UUID notificationId;
    private final UUID recipientId;
    private final NotificationChannel channel;
    private final String subject;
    private final String body;
    private NotificationStatus status;
    private int attempts;
    private Instant sentAt;
    private String error;
    private Instant createdAt;
    private Instant updatedAt;
    private long version;

    /** Uncommitted domain events — cleared after publishing. */
    private final List<NotificationEvent> domainEvents = new ArrayList<>();

    /**
     * Private constructor — use {@link #create} factory method.
     */
    private Notification(UUID notificationId, UUID recipientId, NotificationChannel channel,
                          String subject, String body) {
        this.notificationId = Objects.requireNonNull(notificationId, "Notification ID must not be null");
        this.recipientId = Objects.requireNonNull(recipientId, "Recipient ID must not be null");
        this.channel = Objects.requireNonNull(channel, "Channel must not be null");
        this.subject = Objects.requireNonNull(subject, "Subject must not be null");
        this.body = Objects.requireNonNull(body, "Body must not be null");
        this.status = NotificationStatus.PENDING;
        this.attempts = 0;
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.version = 0;
    }

    // ==================== Factory Method ====================

    /**
     * Factory method to create a new notification in PENDING status.
     *
     * <p>The notification is not persisted until the calling use case saves it.</p>
     *
     * @param recipientId the recipient customer/user ID
     * @param channel     the delivery channel (email, SMS, webhook)
     * @param subject     the notification subject
     * @param body        the notification body content
     * @return a new Notification in PENDING status
     */
    public static Notification create(UUID recipientId, NotificationChannel channel,
                                       String subject, String body) {
        return new Notification(UUID.randomUUID(), recipientId, channel, subject, body);
    }

    // ==================== State Transitions ====================

    /**
     * Marks the notification as successfully sent.
     *
     * <p>Transition: PENDING/RETRYING → SENT</p>
     *
     * @throws ConflictException if the notification cannot transition to SENT
     */
    public void markSent() {
        assertTransition(NotificationStatus.SENT);

        this.status = NotificationStatus.SENT;
        this.sentAt = Instant.now();
        this.attempts++;
        this.updatedAt = Instant.now();

        registerEvent(new NotificationSent(
                this.notificationId,
                this.recipientId,
                this.channel.channelType(),
                this.subject,
                this.attempts,
                this.sentAt
        ));
    }

    /**
     * Marks the notification for retry after a failed delivery attempt.
     *
     * <p>If the maximum number of retries has been reached, the notification
     * is marked as permanently FAILED instead.</p>
     *
     * @param errorMessage the error message from the failed attempt
     */
    public void markRetryOrFail(String errorMessage) {
        Objects.requireNonNull(errorMessage, "Error message must not be null");

        this.attempts++;
        this.error = errorMessage;
        this.updatedAt = Instant.now();

        if (NotificationStatus.shouldRetry(this.attempts)) {
            assertTransition(NotificationStatus.RETRYING);
            this.status = NotificationStatus.RETRYING;
        } else {
            markFailed(errorMessage);
        }
    }

    /**
     * Marks the notification as permanently failed.
     *
     * <p>Transition: PENDING/RETRYING → FAILED</p>
     *
     * @param errorMessage the final error message
     */
    public void markFailed(String errorMessage) {
        assertTransition(NotificationStatus.FAILED);

        this.status = NotificationStatus.FAILED;
        this.error = errorMessage;
        this.updatedAt = Instant.now();

        registerEvent(new NotificationFailed(
                this.notificationId,
                this.recipientId,
                this.channel.channelType(),
                this.subject,
                this.attempts,
                errorMessage,
                this.updatedAt
        ));
    }

    // ==================== Domain Event Management ====================

    /**
     * Reconstitutes a Notification aggregate from persisted state.
     *
     * <p>This method is used ONLY by the persistence adapter when loading a notification
     * from the database. No domain events are emitted.</p>
     *
     * @return a Notification aggregate in its persisted state
     */
    public static Notification reconstitute(UUID notificationId, UUID recipientId,
                                              NotificationChannel channel, String subject,
                                              String body, NotificationStatus status,
                                              int attempts, Instant sentAt, String error,
                                              Instant createdAt, Instant updatedAt, long version) {
        var notification = new Notification(notificationId, recipientId, channel, subject, body);
        notification.status = status;
        notification.attempts = attempts;
        notification.sentAt = sentAt;
        notification.error = error;
        notification.createdAt = createdAt;
        notification.updatedAt = updatedAt;
        notification.version = version;
        return notification;
    }

    /**
     * Returns and clears all uncommitted domain events.
     *
     * @return an unmodifiable list of domain events
     */
    public List<NotificationEvent> pullDomainEvents() {
        List<NotificationEvent> events = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return events;
    }

    private void registerEvent(NotificationEvent event) {
        domainEvents.add(event);
    }

    // ==================== State Transition Guard ====================

    /**
     * Validates that a state transition is allowed; throws ConflictException if not.
     *
     * @param target the desired target state
     * @throws ConflictException if the transition is not valid
     */
    private void assertTransition(NotificationStatus target) {
        if (!status.canTransitionTo(target)) {
            throw ConflictException.invalidStateTransition(
                    "Notification", notificationId.toString(), status.name(), target.name());
        }
    }

    // ==================== Accessors ====================

    public UUID getNotificationId() { return notificationId; }
    public UUID getRecipientId() { return recipientId; }
    public NotificationChannel getChannel() { return channel; }
    public String getSubject() { return subject; }
    public String getBody() { return body; }
    public NotificationStatus getStatus() { return status; }
    public int getAttempts() { return attempts; }
    public Instant getSentAt() { return sentAt; }
    public String getError() { return error; }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    @Override
    public String toString() {
        return "Notification[id=%s, channel=%s, status=%s, attempts=%d, recipient=%s]".formatted(
                notificationId, channel.channelType(), status, attempts, recipientId);
    }
}
