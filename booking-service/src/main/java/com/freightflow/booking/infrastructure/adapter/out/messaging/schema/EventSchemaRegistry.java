package com.freightflow.booking.infrastructure.adapter.out.messaging.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.freightflow.booking.domain.event.BookingCreated;
import com.freightflow.booking.domain.event.BookingEvent;
import com.freightflow.booking.domain.model.ContainerType;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.PortCode;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Map;
import java.util.Objects;

/**
 * Registry that manages event schema versions and provides serialization/deserialization
 * with automatic version upcasting.
 *
 * <p>This component is the central point for event schema evolution. It maps event type names
 * and version numbers to their corresponding schema classes, and handles transparent upcasting
 * when a consumer expects a newer version than what was originally produced.</p>
 *
 * <h3>Upcasting Strategy</h3>
 * <p>When a consumer requests V2 but the stored event is V1, the registry:</p>
 * <ol>
 *   <li>Deserializes the JSON into the V1 schema class</li>
 *   <li>Calls the V2 upcaster ({@link BookingEventSchemaV2#fromV1}) to transform it</li>
 *   <li>Returns the V2 representation with new fields defaulted to null</li>
 * </ol>
 *
 * <h3>Supported Event Types and Versions</h3>
 * <ul>
 *   <li>{@code BookingCreated} — V1: {@link BookingEventSchemaV1}, V2: {@link BookingEventSchemaV2}</li>
 * </ul>
 *
 * @see BookingEventSchemaV1
 * @see BookingEventSchemaV2
 */
@Component
@Profiled("eventSchemaRegistry")
public class EventSchemaRegistry {

    private static final Logger log = LoggerFactory.getLogger(EventSchemaRegistry.class);

    private static final String BOOKING_CREATED = "BookingCreated";

    private final ObjectMapper objectMapper;

    /**
     * Schema class lookup: maps (eventType, version) to the corresponding schema class.
     */
    private final Map<String, Class<?>> schemaMap = Map.of(
            schemaKey(BOOKING_CREATED, 1), BookingEventSchemaV1.class,
            schemaKey(BOOKING_CREATED, 2), BookingEventSchemaV2.class
    );

    /**
     * Constructs the registry with the application's Jackson {@link ObjectMapper}.
     *
     * @param objectMapper the Jackson ObjectMapper for JSON processing
     */
    public EventSchemaRegistry(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
        log.info("EventSchemaRegistry initialized with {} schema mappings", schemaMap.size());
    }

    /**
     * Serializes a {@link BookingEvent} to JSON using the specified schema version.
     *
     * <p>The event is first converted to the appropriate schema record (which contains only
     * primitive/JDK types), then serialized to JSON via Jackson.</p>
     *
     * @param event   the domain event to serialize
     * @param version the schema version to use (1 or 2)
     * @return the JSON string representation
     * @throws IllegalArgumentException if the event type or version is not supported
     * @throws IllegalStateException    if JSON serialization fails
     */
    @Profiled(value = "schemaSerialize", slowThresholdMs = 50)
    public String serialize(BookingEvent event, int version) {
        Objects.requireNonNull(event, "Event must not be null");
        log.debug("Serializing event: type={}, version={}, eventId={}",
                event.eventType(), version, event.eventId());

        try {
            Object schema = toSchema(event, version);
            String json = objectMapper.writeValueAsString(schema);
            log.debug("Event serialized successfully: type={}, version={}, length={}",
                    event.eventType(), version, json.length());
            return json;
        } catch (JsonProcessingException ex) {
            log.error("Failed to serialize event: type={}, version={}, error={}",
                    event.eventType(), version, ex.getMessage(), ex);
            throw new IllegalStateException(
                    "Event serialization failed for %s v%d".formatted(event.eventType(), version), ex);
        }
    }

    /**
     * Deserializes a JSON string back to a {@link BookingEvent} domain object.
     *
     * <p>If the JSON was produced with V1 but the consumer requests V2, the registry
     * automatically upcasts by deserializing as V1 first, then applying the V1→V2
     * transformation.</p>
     *
     * @param json      the JSON string to deserialize
     * @param eventType the event type name (e.g., "BookingCreated")
     * @param version   the target schema version the consumer expects
     * @return the reconstructed domain event
     * @throws IllegalArgumentException if the event type or version is not supported
     * @throws IllegalStateException    if JSON deserialization fails
     */
    @Profiled(value = "schemaDeserialize", slowThresholdMs = 50)
    public BookingEvent deserialize(String json, String eventType, int version) {
        Objects.requireNonNull(json, "JSON must not be null");
        Objects.requireNonNull(eventType, "Event type must not be null");
        log.debug("Deserializing event: type={}, version={}", eventType, version);

        try {
            return fromSchema(json, eventType, version);
        } catch (JsonProcessingException ex) {
            log.error("Failed to deserialize event: type={}, version={}, error={}",
                    eventType, version, ex.getMessage(), ex);
            throw new IllegalStateException(
                    "Event deserialization failed for %s v%d".formatted(eventType, version), ex);
        }
    }

    // ==================== Internal Conversion ====================

    private Object toSchema(BookingEvent event, int version) {
        if (event instanceof BookingCreated created) {
            return switch (version) {
                case 1 -> BookingEventSchemaV1.from(created);
                case 2 -> BookingEventSchemaV2.from(created);
                default -> throw new IllegalArgumentException(
                        "Unsupported schema version %d for %s".formatted(version, BOOKING_CREATED));
            };
        }
        throw new IllegalArgumentException("Unsupported event type for serialization: " + event.eventType());
    }

    private BookingEvent fromSchema(String json, String eventType, int version) throws JsonProcessingException {
        if (BOOKING_CREATED.equals(eventType)) {
            return deserializeBookingCreated(json, version);
        }
        throw new IllegalArgumentException("Unsupported event type for deserialization: " + eventType);
    }

    private BookingEvent deserializeBookingCreated(String json, int version) throws JsonProcessingException {
        // Configure a lenient reader that ignores unknown fields for forward compatibility
        ObjectMapper lenientMapper = objectMapper.copy()
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);

        return switch (version) {
            case 1 -> {
                BookingEventSchemaV1 v1 = lenientMapper.readValue(json, BookingEventSchemaV1.class);
                yield toDomainEvent(v1);
            }
            case 2 -> {
                // Try to deserialize as V2 first
                BookingEventSchemaV2 v2;
                try {
                    v2 = lenientMapper.readValue(json, BookingEventSchemaV2.class);
                } catch (JsonProcessingException ex) {
                    // If it fails, it might be a V1 event — upcast
                    log.info("Upcasting V1 event to V2 for BookingCreated");
                    BookingEventSchemaV1 v1 = lenientMapper.readValue(json, BookingEventSchemaV1.class);
                    v2 = BookingEventSchemaV2.fromV1(v1);
                }
                // If weight/commodityCode are null, this might be a V1 event — check and upcast
                if (v2.weight() == null && v2.commodityCode() == null) {
                    log.debug("V2 event has null extended fields — possibly upcasted from V1");
                }
                yield toDomainEvent(v2);
            }
            default -> throw new IllegalArgumentException(
                    "Unsupported schema version %d for BookingCreated".formatted(version));
        };
    }

    /**
     * Reconstructs a {@link BookingCreated} domain event from a V1 schema record.
     */
    private BookingCreated toDomainEvent(BookingEventSchemaV1 schema) {
        return new BookingCreated(
                schema.eventId(),
                BookingId.fromString(schema.bookingId()),
                CustomerId.fromString(schema.customerId()),
                PortCode.of(schema.origin()),
                PortCode.of(schema.destination()),
                ContainerType.valueOf(schema.containerType()),
                schema.containerCount(),
                schema.requestedDepartureDate(),
                schema.occurredAt()
        );
    }

    /**
     * Reconstructs a {@link BookingCreated} domain event from a V2 schema record.
     */
    private BookingCreated toDomainEvent(BookingEventSchemaV2 schema) {
        return new BookingCreated(
                schema.eventId(),
                BookingId.fromString(schema.bookingId()),
                CustomerId.fromString(schema.customerId()),
                PortCode.of(schema.origin()),
                PortCode.of(schema.destination()),
                ContainerType.valueOf(schema.containerType()),
                schema.containerCount(),
                schema.requestedDepartureDate(),
                schema.occurredAt()
        );
    }

    private static String schemaKey(String eventType, int version) {
        return eventType + ":v" + version;
    }
}
