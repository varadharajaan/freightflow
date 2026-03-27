package com.freightflow.booking.infrastructure.adapter.out.persistence.eventstore;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.booking.application.query.BookingEventRecord;
import com.freightflow.booking.application.query.BookingQueryHandler;
import com.freightflow.booking.domain.event.BookingEvent;
import com.freightflow.booking.domain.port.EventStore;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.exception.ConflictException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Objects;

/**
 * JPA-based adapter implementing the {@link EventStore} domain port and the
 * {@link BookingQueryHandler.BookingEventStoreQueryPort} query port.
 *
 * <p>This adapter bridges the domain and application layers to the PostgreSQL event store.
 * It serializes domain events to JSON for storage and deserializes them back on load.
 * The {@code booking_events} table's {@code UNIQUE(aggregate_id, version)} constraint
 * provides optimistic concurrency control — concurrent appends at the same version
 * result in a {@link DataIntegrityViolationException} that is translated to a
 * {@link ConflictException}.</p>
 *
 * <h3>Serialization Strategy</h3>
 * <p>Events are serialized using Jackson {@link ObjectMapper} with type information
 * embedded in the JSON payload. The {@code event_type} column stores the simple class
 * name for routing and querying, while the full type hierarchy is preserved in the
 * JSON for deserialization.</p>
 *
 * @see EventStore
 * @see EventStoreEntity
 * @see SpringDataEventStoreRepository
 */
@Component
public class JpaEventStoreAdapter implements EventStore, BookingQueryHandler.BookingEventStoreQueryPort {

    private static final Logger log = LoggerFactory.getLogger(JpaEventStoreAdapter.class);
    private static final String AGGREGATE_TYPE = "Booking";

    private final SpringDataEventStoreRepository repository;
    private final ObjectMapper objectMapper;

    /**
     * Constructor injection of infrastructure dependencies.
     *
     * @param repository   the Spring Data JPA repository for event entities
     * @param objectMapper the Jackson object mapper for JSON serialization
     */
    public JpaEventStoreAdapter(SpringDataEventStoreRepository repository,
                                ObjectMapper objectMapper) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
        this.objectMapper = Objects.requireNonNull(objectMapper, "objectMapper must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Serializes each domain event to JSON, wraps it in an {@link EventStoreEntity},
     * and persists all entities atomically. If a version conflict is detected (duplicate
     * {@code aggregate_id + version}), a {@link ConflictException} is thrown.</p>
     */
    @Override
    public void append(BookingId aggregateId, List<BookingEvent> events, long expectedVersion) {
        log.debug("Appending {} event(s) to event store: aggregateId={}, expectedVersion={}",
                events.size(), aggregateId.asString(), expectedVersion);

        long version = expectedVersion;
        for (BookingEvent event : events) {
            version++;
            String eventData = serializeEvent(event);

            EventStoreEntity entity = new EventStoreEntity(
                    event.eventId(),
                    aggregateId.value(),
                    AGGREGATE_TYPE,
                    event.eventType(),
                    eventData,
                    null,
                    version,
                    event.occurredAt(),
                    Instant.now(),
                    null
            );

            try {
                repository.save(entity);
            } catch (DataIntegrityViolationException ex) {
                log.error("Optimistic concurrency conflict: aggregateId={}, version={}",
                        aggregateId.asString(), version, ex);
                throw ConflictException.optimisticLock("Booking", aggregateId.asString());
            }
        }

        log.info("Appended {} event(s) to event store: aggregateId={}, versions=[{}..{}]",
                events.size(), aggregateId.asString(), expectedVersion + 1, version);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Loads all event entities for the aggregate in version order and deserializes
     * each JSON payload back to the corresponding {@link BookingEvent} subtype.</p>
     */
    @Override
    public List<BookingEvent> loadEvents(BookingId aggregateId) {
        log.debug("Loading all events from event store: aggregateId={}", aggregateId.asString());

        List<EventStoreEntity> entities =
                repository.findByAggregateIdOrderByVersionAsc(aggregateId.value());

        List<BookingEvent> events = entities.stream()
                .map(this::deserializeEvent)
                .toList();

        log.debug("Loaded {} event(s) from event store: aggregateId={}",
                events.size(), aggregateId.asString());
        return events;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Loads event entities starting from the specified version (inclusive) and
     * deserializes them. Used in combination with snapshots for partial replay.</p>
     */
    @Override
    public List<BookingEvent> loadEventsFromVersion(BookingId aggregateId, long fromVersion) {
        log.debug("Loading events from version: aggregateId={}, fromVersion={}",
                aggregateId.asString(), fromVersion);

        List<EventStoreEntity> entities =
                repository.findByAggregateIdAndVersionGreaterThanEqualOrderByVersionAsc(
                        aggregateId.value(), fromVersion);

        List<BookingEvent> events = entities.stream()
                .map(this::deserializeEvent)
                .toList();

        log.debug("Loaded {} event(s) from version {}: aggregateId={}",
                events.size(), fromVersion, aggregateId.asString());
        return events;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Loads all events for the booking and maps them to displayable
     * {@link BookingEventRecord} instances with generic key-value event data.</p>
     */
    @Override
    public List<BookingEventRecord> loadEventHistory(String bookingId) {
        log.debug("Loading event history for display: bookingId={}", bookingId);

        BookingId id = BookingId.fromString(bookingId);
        List<EventStoreEntity> entities =
                repository.findByAggregateIdOrderByVersionAsc(id.value());

        return entities.stream()
                .map(this::toEventRecord)
                .toList();
    }

    /**
     * Serializes a domain event to a JSON string.
     *
     * @param event the domain event to serialize
     * @return the JSON string representation
     * @throws IllegalStateException if serialization fails
     */
    private String serializeEvent(BookingEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize event: eventType={}, eventId={}",
                    event.eventType(), event.eventId(), ex);
            throw new IllegalStateException(
                    "Failed to serialize event '%s'".formatted(event.eventType()), ex);
        }
    }

    /**
     * Deserializes an event entity's JSON payload back to a {@link BookingEvent}.
     *
     * @param entity the event store entity
     * @return the deserialized domain event
     * @throws IllegalStateException if deserialization fails
     */
    private BookingEvent deserializeEvent(EventStoreEntity entity) {
        try {
            Class<? extends BookingEvent> eventClass = resolveEventClass(entity.getEventType());
            return objectMapper.readValue(entity.getEventData(), eventClass);
        } catch (JsonProcessingException ex) {
            log.error("Failed to deserialize event: eventType={}, eventId={}",
                    entity.getEventType(), entity.getEventId(), ex);
            throw new IllegalStateException(
                    "Failed to deserialize event '%s' with ID '%s'"
                            .formatted(entity.getEventType(), entity.getEventId()), ex);
        }
    }

    /**
     * Resolves the concrete {@link BookingEvent} class from the event type name.
     *
     * <p>Uses Java 21 pattern matching on string values to map event type names to
     * their corresponding record classes within the sealed hierarchy.</p>
     *
     * @param eventType the event type name
     * @return the corresponding class
     * @throws IllegalArgumentException if the event type is unknown
     */
    private Class<? extends BookingEvent> resolveEventClass(String eventType) {
        return switch (eventType) {
            case "BookingCreated" ->
                    com.freightflow.booking.domain.event.BookingCreated.class;
            case "BookingConfirmed" ->
                    com.freightflow.booking.domain.event.BookingConfirmed.class;
            case "BookingCancelled" ->
                    com.freightflow.booking.domain.event.BookingCancelled.class;
            default -> {
                log.error("Unknown event type encountered: {}", eventType);
                throw new IllegalArgumentException("Unknown event type: " + eventType);
            }
        };
    }

    /**
     * Maps an {@link EventStoreEntity} to a displayable {@link BookingEventRecord}.
     *
     * @param entity the event store entity
     * @return the event record for display
     */
    private BookingEventRecord toEventRecord(EventStoreEntity entity) {
        Map<String, Object> eventData;
        try {
            eventData = objectMapper.readValue(
                    entity.getEventData(), new TypeReference<Map<String, Object>>() {});
        } catch (JsonProcessingException ex) {
            log.error("Failed to parse event data as map: eventId={}", entity.getEventId(), ex);
            eventData = Map.of("_raw", entity.getEventData());
        }

        return new BookingEventRecord(
                entity.getEventId().toString(),
                entity.getEventType(),
                entity.getOccurredAt(),
                entity.getVersion(),
                eventData
        );
    }
}
