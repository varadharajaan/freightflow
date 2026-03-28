package com.freightflow.notificationservice.domain.port;

import com.freightflow.notificationservice.domain.model.Notification;
import com.freightflow.notificationservice.domain.model.NotificationStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Outbound port for notification persistence.
 *
 * <p>This interface defines the contract that the domain layer expects from
 * the persistence layer (Dependency Inversion Principle). The domain does NOT
 * depend on JPA, Hibernate, or any infrastructure technology.</p>
 *
 * @see com.freightflow.notificationservice.infrastructure.adapter.out.persistence
 */
public interface NotificationRepository {

    /**
     * Persists a new or updated notification.
     *
     * @param notification the notification aggregate to save
     * @return the saved notification (with updated version)
     */
    Notification save(Notification notification);

    /**
     * Finds a notification by its ID.
     *
     * @param notificationId the notification identifier
     * @return the notification, or empty if not found
     */
    Optional<Notification> findById(UUID notificationId);

    /**
     * Finds all notifications for a recipient.
     *
     * @param recipientId the recipient identifier
     * @return list of notifications (may be empty)
     */
    List<Notification> findByRecipientId(UUID recipientId);

    /**
     * Finds all notifications in a given status.
     *
     * @param status the notification status to filter by
     * @return list of notifications (may be empty)
     */
    List<Notification> findByStatus(NotificationStatus status);

    /**
     * Finds notifications pending retry (status = RETRYING).
     *
     * @return list of notifications awaiting retry
     */
    List<Notification> findPendingRetries();
}
