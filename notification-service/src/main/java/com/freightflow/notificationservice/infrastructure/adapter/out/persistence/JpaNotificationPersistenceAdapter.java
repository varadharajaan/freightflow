package com.freightflow.notificationservice.infrastructure.adapter.out.persistence;

import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.notificationservice.domain.model.Notification;
import com.freightflow.notificationservice.domain.model.NotificationStatus;
import com.freightflow.notificationservice.domain.port.NotificationRepository;
import com.freightflow.notificationservice.infrastructure.adapter.out.persistence.entity.NotificationJpaEntity;
import com.freightflow.notificationservice.infrastructure.adapter.out.persistence.repository.SpringDataNotificationRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA persistence adapter implementing the domain's {@link NotificationRepository} port.
 *
 * <p>This is the <b>outbound adapter</b> in Hexagonal Architecture. It translates
 * between the domain model ({@link Notification}) and the JPA entity
 * ({@link NotificationJpaEntity}) using the {@link NotificationEntityMapper}.</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>Adapter Pattern</b> — adapts Spring Data JPA to the domain port interface</li>
 *   <li><b>Dependency Inversion</b> — domain defines the port, infrastructure implements it</li>
 *   <li><b>Mapper isolation</b> — JPA entities never leak into the domain layer</li>
 *   <li><b>Logging</b> — DEBUG for operations, WARN for edge cases</li>
 * </ul>
 *
 * @see NotificationRepository
 * @see SpringDataNotificationRepository
 * @see NotificationEntityMapper
 */
@Component
public class JpaNotificationPersistenceAdapter implements NotificationRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaNotificationPersistenceAdapter.class);

    private final SpringDataNotificationRepository jpaRepository;
    private final NotificationEntityMapper mapper;

    /**
     * Constructor injection — depends on Spring Data repository and entity mapper.
     *
     * @param jpaRepository the Spring Data JPA repository for notification entities
     * @param mapper        the entity mapper for domain-to-JPA translation
     */
    public JpaNotificationPersistenceAdapter(SpringDataNotificationRepository jpaRepository,
                                              NotificationEntityMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "JPA repository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Mapper must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Converts the domain notification to a JPA entity, persists it via Spring Data,
     * and returns the reconstituted domain model with updated version.</p>
     */
    @Override
    @Profiled(value = "notificationRepository.save", slowThresholdMs = 500)
    public Notification save(Notification notification) {
        log.debug("Persisting notification: notificationId={}, channel={}, status={}",
                notification.getNotificationId(), notification.getChannel().channelType(),
                notification.getStatus());

        NotificationJpaEntity entity = mapper.toEntity(notification);
        NotificationJpaEntity saved = jpaRepository.save(entity);

        log.debug("Notification persisted: notificationId={}, version={}",
                saved.getId(), saved.getVersion());

        return mapper.toDomain(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Looks up the notification by its UUID and maps the result back to the
     * domain model if found.</p>
     */
    @Override
    public Optional<Notification> findById(UUID notificationId) {
        log.debug("Finding notification: notificationId={}", notificationId);

        Optional<Notification> result = jpaRepository.findById(notificationId)
                .map(entity -> {
                    log.debug("Notification found: notificationId={}, channel={}, status={}",
                            entity.getId(), entity.getChannelType(), entity.getStatus());
                    return mapper.toDomain(entity);
                });

        if (result.isEmpty()) {
            log.warn("Notification not found: notificationId={}", notificationId);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retrieves all notifications for a given recipient, ordered by creation
     * date descending (most recent first).</p>
     */
    @Override
    public List<Notification> findByRecipientId(UUID recipientId) {
        log.debug("Finding notifications for recipient: recipientId={}", recipientId);

        List<NotificationJpaEntity> entities = jpaRepository
                .findByRecipientIdOrderByCreatedAtDesc(recipientId);

        log.debug("Found {} notifications for recipient: recipientId={}",
                entities.size(), recipientId);

        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retrieves all notifications in the given status.</p>
     */
    @Override
    public List<Notification> findByStatus(NotificationStatus status) {
        log.debug("Finding notifications by status: status={}", status);

        List<NotificationJpaEntity> entities = jpaRepository
                .findByStatus(status.name());

        log.debug("Found {} notifications with status: status={}",
                entities.size(), status);

        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retrieves all notifications in RETRYING status, ordered by last update
     * ascending (oldest retries first for fair processing).</p>
     */
    @Override
    public List<Notification> findPendingRetries() {
        log.debug("Finding notifications pending retry");

        List<NotificationJpaEntity> entities = jpaRepository
                .findByStatusOrderByUpdatedAtAsc(NotificationStatus.RETRYING.name());

        log.debug("Found {} notifications pending retry", entities.size());

        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }
}
