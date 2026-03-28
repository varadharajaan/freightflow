package com.freightflow.notificationservice.application.query;

import com.freightflow.commons.exception.ResourceNotFoundException;
import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.notificationservice.domain.model.Notification;
import com.freightflow.notificationservice.domain.model.NotificationStatus;
import com.freightflow.notificationservice.domain.port.NotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * Query Handler — handles all <b>read</b> operations for the notification domain.
 *
 * <p>This is the <b>read side</b> of the application. It provides optimized
 * query methods for retrieving notifications by ID, recipient, or status.
 * Results are cached where appropriate to reduce database load.</p>
 *
 * @see com.freightflow.notificationservice.application.command.NotificationCommandHandler
 * @see NotificationRepository
 */
@Service
public class NotificationQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(NotificationQueryHandler.class);

    private final NotificationRepository notificationRepository;

    /**
     * Constructor injection — all dependencies are final (Dependency Inversion Principle).
     *
     * @param notificationRepository the notification persistence port
     */
    public NotificationQueryHandler(NotificationRepository notificationRepository) {
        this.notificationRepository = Objects.requireNonNull(notificationRepository,
                "NotificationRepository must not be null");
    }

    /**
     * Retrieves a single notification by its ID.
     *
     * <h3>Spring Advanced Feature: @Cacheable</h3>
     * <p>Results are cached in the "notifications" cache region.</p>
     *
     * @param notificationId the notification UUID
     * @return the notification
     * @throws ResourceNotFoundException if no notification exists for the given ID
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "notifications", key = "#notificationId", unless = "#result == null")
    @Profiled(value = "getNotificationQuery", slowThresholdMs = 200)
    public Notification getNotification(String notificationId) {
        log.debug("Querying notification: notificationId={}", notificationId);

        return notificationRepository.findById(UUID.fromString(notificationId))
                .orElseThrow(() -> {
                    log.warn("Notification not found: notificationId={}", notificationId);
                    return ResourceNotFoundException.forResource("Notification", notificationId);
                });
    }

    /**
     * Retrieves all notifications for a recipient.
     *
     * @param recipientId the recipient UUID
     * @return list of notifications (may be empty, never null)
     */
    @Transactional(readOnly = true)
    @Profiled(value = "getNotificationsByRecipient", slowThresholdMs = 300)
    public List<Notification> getNotificationsByRecipient(String recipientId) {
        log.debug("Querying notifications by recipient: recipientId={}", recipientId);

        List<Notification> results = notificationRepository.findByRecipientId(
                UUID.fromString(recipientId));

        log.debug("Found {} notification(s) for recipientId={}", results.size(), recipientId);
        return results;
    }

    /**
     * Retrieves all notifications in a given status.
     *
     * @param status the notification status to filter by
     * @return list of notifications (may be empty, never null)
     */
    @Transactional(readOnly = true)
    @Profiled(value = "getNotificationsByStatus", slowThresholdMs = 300)
    public List<Notification> getNotificationsByStatus(NotificationStatus status) {
        log.debug("Querying notifications by status: status={}", status);

        List<Notification> results = notificationRepository.findByStatus(status);

        log.debug("Found {} notification(s) with status={}", results.size(), status);
        return results;
    }

    /**
     * Retrieves all notifications pending retry.
     *
     * @return list of notifications awaiting retry (may be empty, never null)
     */
    @Transactional(readOnly = true)
    @Profiled(value = "getPendingRetries", slowThresholdMs = 300)
    public List<Notification> getPendingRetries() {
        log.debug("Querying notifications pending retry");

        List<Notification> results = notificationRepository.findPendingRetries();

        log.debug("Found {} notification(s) pending retry", results.size());
        return results;
    }
}
