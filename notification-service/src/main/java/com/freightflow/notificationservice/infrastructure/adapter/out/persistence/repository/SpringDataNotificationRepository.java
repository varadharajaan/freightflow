package com.freightflow.notificationservice.infrastructure.adapter.out.persistence.repository;

import com.freightflow.notificationservice.infrastructure.adapter.out.persistence.entity.NotificationJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.JpaSpecificationExecutor;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for notification entities — provides derived queries,
 * JPQL, native SQL, and pagination for notification management.
 *
 * <p>This is a <b>technology-specific</b> interface. The domain port is
 * {@link com.freightflow.notificationservice.domain.port.NotificationRepository}.</p>
 *
 * <h3>Features Demonstrated</h3>
 * <ol>
 *   <li>Derived query methods (method name parsing)</li>
 *   <li>JPQL queries via {@code @Query}</li>
 *   <li>Native SQL queries for analytics</li>
 *   <li>Bulk {@code @Modifying} operations</li>
 *   <li>Pagination support</li>
 * </ol>
 *
 * @see com.freightflow.notificationservice.domain.port.NotificationRepository
 */
@Repository
public interface SpringDataNotificationRepository
        extends JpaRepository<NotificationJpaEntity, UUID>,
                JpaSpecificationExecutor<NotificationJpaEntity> {

    // ==================== Derived Query Methods ====================

    /**
     * Finds all notifications for a recipient, ordered by creation date descending.
     *
     * <p><b>JPA Feature:</b> Derived query with ordering.</p>
     *
     * @param recipientId the recipient UUID
     * @return notifications for the recipient, most recent first
     */
    List<NotificationJpaEntity> findByRecipientIdOrderByCreatedAtDesc(UUID recipientId);

    /**
     * Finds all notifications with a given status.
     *
     * <p><b>JPA Feature:</b> Simple derived query on status field.</p>
     *
     * @param status the notification status
     * @return notifications in the given status
     */
    List<NotificationJpaEntity> findByStatus(String status);

    /**
     * Finds notifications with a given status and channel type.
     *
     * <p><b>JPA Feature:</b> Derived query with {@code And} keyword.</p>
     *
     * @param status      the notification status
     * @param channelType the channel type (EMAIL, SMS, WEBHOOK)
     * @return matching notifications
     */
    List<NotificationJpaEntity> findByStatusAndChannelType(String status, String channelType);

    /**
     * Finds notifications pending retry (RETRYING status), ordered by oldest first.
     *
     * <p><b>JPA Feature:</b> Derived query with specific status filter for retry processing.</p>
     *
     * @return notifications awaiting retry, oldest first
     */
    List<NotificationJpaEntity> findByStatusOrderByUpdatedAtAsc(String status);

    /**
     * Counts notifications in a specific status.
     *
     * <p><b>JPA Feature:</b> Count derived query.</p>
     *
     * @param status the notification status
     * @return the count of matching notifications
     */
    long countByStatus(String status);

    /**
     * Checks whether any notification exists for a recipient with a given status.
     *
     * <p><b>JPA Feature:</b> Existence-check derived query.</p>
     *
     * @param recipientId the recipient UUID
     * @param status      the notification status
     * @return true if at least one matching notification exists
     */
    boolean existsByRecipientIdAndStatus(UUID recipientId, String status);

    /**
     * Finds the most recent notifications of a given channel type.
     *
     * <p><b>JPA Feature:</b> Top limiting with derived query.</p>
     *
     * @param channelType the channel type
     * @return up to 20 most recent notifications of the channel type
     */
    List<NotificationJpaEntity> findTop20ByChannelTypeOrderByCreatedAtDesc(String channelType);

    // ==================== JPQL Queries ====================

    /**
     * Finds failed notifications that might be eligible for retry.
     *
     * <p><b>JPA Feature:</b> JPQL with arithmetic — filters notifications that have not
     * exhausted their retry attempts.</p>
     *
     * @param maxAttempts the maximum number of attempts allowed
     * @return failed notifications eligible for retry
     */
    @Query("SELECT n FROM NotificationJpaEntity n WHERE n.status = 'RETRYING' AND n.attempts < :maxAttempts ORDER BY n.updatedAt ASC")
    List<NotificationJpaEntity> findRetryableNotifications(@Param("maxAttempts") int maxAttempts);

    /**
     * Finds notifications by status with pagination support.
     *
     * <p><b>JPA Feature:</b> JPQL combined with Pageable.</p>
     *
     * @param status   the notification status
     * @param pageable pagination parameters
     * @return a page of matching notifications
     */
    @Query("SELECT n FROM NotificationJpaEntity n WHERE n.status = :status")
    Page<NotificationJpaEntity> findByStatusPaged(@Param("status") String status, Pageable pageable);

    // ==================== Native SQL Queries ====================

    /**
     * Gets delivery statistics per channel type using native SQL.
     *
     * <p><b>JPA Feature:</b> Native SQL with GROUP BY and multiple aggregates.</p>
     *
     * @return raw result arrays of [channel_type, total, sent, failed, avg_attempts]
     */
    @Query(value = """
            SELECT n.channel_type,
                   COUNT(*) as total,
                   COUNT(*) FILTER (WHERE n.status = 'SENT') as sent_count,
                   COUNT(*) FILTER (WHERE n.status = 'FAILED') as failed_count,
                   AVG(n.attempts) as avg_attempts
            FROM notifications n
            GROUP BY n.channel_type
            ORDER BY total DESC
            """, nativeQuery = true)
    List<Object[]> getDeliveryStatsByChannel();

    /**
     * Finds the hourly notification volume over the past 24 hours.
     *
     * <p><b>JPA Feature:</b> Native SQL with PostgreSQL date_trunc for time-series analysis.</p>
     *
     * @return raw result arrays of [hour, notification_count]
     */
    @Query(value = """
            SELECT date_trunc('hour', n.created_at) as hour,
                   COUNT(*) as notification_count
            FROM notifications n
            WHERE n.created_at >= NOW() - INTERVAL '24 hours'
            GROUP BY date_trunc('hour', n.created_at)
            ORDER BY hour DESC
            """, nativeQuery = true)
    List<Object[]> getHourlyVolumeLastDay();

    // ==================== @Modifying Bulk Operations ====================

    /**
     * Bulk-updates the status of old failed notifications for archival.
     *
     * <p><b>JPA Feature:</b> {@code @Modifying} JPQL UPDATE for batch operations.</p>
     *
     * @param cutoff notifications updated before this timestamp are affected
     * @return the number of rows updated
     */
    @Modifying
    @Query("DELETE FROM NotificationJpaEntity n WHERE n.status = 'FAILED' AND n.updatedAt < :cutoff")
    int purgeOldFailedNotifications(@Param("cutoff") Instant cutoff);

    /**
     * Bulk marks stale RETRYING notifications as FAILED.
     *
     * <p><b>JPA Feature:</b> {@code @Modifying} JPQL UPDATE — transitions stuck
     * notifications to a terminal state for cleanup.</p>
     *
     * @param cutoff notifications not updated since this timestamp are marked as FAILED
     * @return the number of rows updated
     */
    @Modifying
    @Query("UPDATE NotificationJpaEntity n SET n.status = 'FAILED', n.error = 'Retry timeout exceeded', n.updatedAt = CURRENT_TIMESTAMP WHERE n.status = 'RETRYING' AND n.updatedAt < :cutoff")
    int markStaleRetriesAsFailed(@Param("cutoff") Instant cutoff);
}
