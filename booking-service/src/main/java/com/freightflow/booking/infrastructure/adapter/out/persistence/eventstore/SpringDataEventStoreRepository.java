package com.freightflow.booking.infrastructure.adapter.out.persistence.eventstore;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for {@link EventStoreEntity}.
 *
 * <p>Provides data access for the {@code booking_events} table. Methods follow
 * Spring Data naming conventions to auto-generate queries for the most common
 * event store access patterns.</p>
 *
 * <h3>Access Patterns</h3>
 * <ul>
 *   <li>Load all events for an aggregate (replay from beginning)</li>
 *   <li>Load events from a specific version (replay after snapshot)</li>
 * </ul>
 *
 * @see EventStoreEntity
 * @see JpaEventStoreAdapter
 */
public interface SpringDataEventStoreRepository extends JpaRepository<EventStoreEntity, UUID> {

    /**
     * Loads all events for an aggregate in version order.
     *
     * <p>Primary access pattern: reconstruct aggregate state by replaying all events.</p>
     *
     * @param aggregateId the aggregate ID
     * @return ordered list of event entities (ascending by version)
     */
    List<EventStoreEntity> findByAggregateIdOrderByVersionAsc(UUID aggregateId);

    /**
     * Loads events for an aggregate starting from a specific version (inclusive).
     *
     * <p>Used in combination with snapshots — load snapshot, then replay events
     * from the snapshot version onward.</p>
     *
     * @param aggregateId the aggregate ID
     * @param fromVersion the minimum version (inclusive)
     * @return ordered list of event entities from the specified version
     */
    List<EventStoreEntity> findByAggregateIdAndVersionGreaterThanEqualOrderByVersionAsc(
            UUID aggregateId, long fromVersion);
}
