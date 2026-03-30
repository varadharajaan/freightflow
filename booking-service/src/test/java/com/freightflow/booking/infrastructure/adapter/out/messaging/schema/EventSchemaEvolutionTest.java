package com.freightflow.booking.infrastructure.adapter.out.messaging.schema;

import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.freightflow.booking.domain.event.BookingCreated;
import com.freightflow.booking.domain.event.BookingEvent;
import com.freightflow.booking.domain.model.ContainerType;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.PortCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.time.LocalDate;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Tests for event schema evolution — verifying round-trip serialization,
 * backward compatibility (V1→V2 upcasting), and forward compatibility
 * (V2 read as V1 ignoring unknown fields).
 *
 * @see BookingEventSchemaV1
 * @see BookingEventSchemaV2
 * @see EventSchemaRegistry
 */
@DisplayName("Event Schema Evolution")
class EventSchemaEvolutionTest {

    private EventSchemaRegistry registry;
    private ObjectMapper objectMapper;

    private static final BookingId BOOKING_ID = BookingId.generate();
    private static final CustomerId CUSTOMER_ID = CustomerId.generate();
    private static final PortCode ORIGIN = PortCode.of("DEHAM");
    private static final PortCode DESTINATION = PortCode.of("CNSHA");
    private static final LocalDate DEPARTURE_DATE = LocalDate.now().plusDays(30);
    private static final Instant OCCURRED_AT = Instant.now();
    private static final UUID EVENT_ID = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS)
                .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
        registry = new EventSchemaRegistry(objectMapper);
    }

    private BookingCreated createSampleEvent() {
        return new BookingCreated(
                EVENT_ID,
                BOOKING_ID,
                CUSTOMER_ID,
                ORIGIN,
                DESTINATION,
                ContainerType.DRY_40,
                2,
                DEPARTURE_DATE,
                OCCURRED_AT
        );
    }

    @Test
    @DisplayName("should serialize V1 and deserialize V1 (round trip)")
    void should_SerializeV1_And_DeserializeV1() {
        // Given
        BookingCreated original = createSampleEvent();

        // When
        String json = registry.serialize(original, 1);
        BookingEvent deserialized = registry.deserialize(json, "BookingCreated", 1);

        // Then
        assertThat(deserialized).isInstanceOf(BookingCreated.class);
        BookingCreated result = (BookingCreated) deserialized;
        assertThat(result.eventId()).isEqualTo(original.eventId());
        assertThat(result.bookingId()).isEqualTo(original.bookingId());
        assertThat(result.customerId()).isEqualTo(original.customerId());
        assertThat(result.origin()).isEqualTo(original.origin());
        assertThat(result.destination()).isEqualTo(original.destination());
        assertThat(result.containerType()).isEqualTo(original.containerType());
        assertThat(result.containerCount()).isEqualTo(original.containerCount());
        assertThat(result.requestedDepartureDate()).isEqualTo(original.requestedDepartureDate());
    }

    @Test
    @DisplayName("should serialize V2 and deserialize V2 (round trip)")
    void should_SerializeV2_And_DeserializeV2() {
        // Given
        BookingCreated original = createSampleEvent();

        // When
        String json = registry.serialize(original, 2);
        BookingEvent deserialized = registry.deserialize(json, "BookingCreated", 2);

        // Then
        assertThat(deserialized).isInstanceOf(BookingCreated.class);
        BookingCreated result = (BookingCreated) deserialized;
        assertThat(result.eventId()).isEqualTo(original.eventId());
        assertThat(result.bookingId()).isEqualTo(original.bookingId());
        assertThat(result.customerId()).isEqualTo(original.customerId());
        assertThat(result.origin()).isEqualTo(original.origin());
        assertThat(result.destination()).isEqualTo(original.destination());
        assertThat(result.containerType()).isEqualTo(original.containerType());
        assertThat(result.containerCount()).isEqualTo(original.containerCount());
        assertThat(result.requestedDepartureDate()).isEqualTo(original.requestedDepartureDate());
    }

    @Test
    @DisplayName("should upcast V1 to V2 (backward compatibility)")
    void should_UpcastV1ToV2() {
        // Given — serialize as V1
        BookingCreated original = createSampleEvent();
        String v1Json = registry.serialize(original, 1);

        // When — deserialize as V2 (should upcast automatically)
        BookingEvent deserialized = registry.deserialize(v1Json, "BookingCreated", 2);

        // Then — core fields preserved, event successfully deserialized
        assertThat(deserialized).isInstanceOf(BookingCreated.class);
        BookingCreated result = (BookingCreated) deserialized;
        assertThat(result.eventId()).isEqualTo(original.eventId());
        assertThat(result.bookingId()).isEqualTo(original.bookingId());
        assertThat(result.customerId()).isEqualTo(original.customerId());
        assertThat(result.origin()).isEqualTo(original.origin());
        assertThat(result.destination()).isEqualTo(original.destination());
        assertThat(result.containerType()).isEqualTo(original.containerType());
        assertThat(result.containerCount()).isEqualTo(original.containerCount());
        assertThat(result.requestedDepartureDate()).isEqualTo(original.requestedDepartureDate());
    }

    @Test
    @DisplayName("should deserialize V2 as V1 ignoring unknown fields (forward compatibility)")
    void should_DeserializeV2AsV1_IgnoringUnknownFields() {
        // Given — create a V2 schema with extra fields populated
        BookingEventSchemaV2 v2Schema = new BookingEventSchemaV2(
                EVENT_ID,
                BOOKING_ID.asString(),
                CUSTOMER_ID.asString(),
                ORIGIN.value(),
                DESTINATION.value(),
                ContainerType.DRY_40.name(),
                2,
                DEPARTURE_DATE,
                OCCURRED_AT,
                25000.0,
                "HS8471"
        );
        String v2Json;
        try {
            v2Json = objectMapper.writeValueAsString(v2Schema);
        } catch (Exception ex) {
            throw new RuntimeException("Failed to serialize V2 schema for test", ex);
        }

        // When — deserialize as V1 (should ignore weight and commodityCode)
        BookingEvent deserialized = registry.deserialize(v2Json, "BookingCreated", 1);

        // Then — core fields preserved, unknown V2 fields silently ignored
        assertThat(deserialized).isInstanceOf(BookingCreated.class);
        BookingCreated result = (BookingCreated) deserialized;
        assertThat(result.eventId()).isEqualTo(EVENT_ID);
        assertThat(result.bookingId()).isEqualTo(BOOKING_ID);
        assertThat(result.customerId()).isEqualTo(CUSTOMER_ID);
        assertThat(result.origin()).isEqualTo(ORIGIN);
        assertThat(result.destination()).isEqualTo(DESTINATION);
        assertThat(result.containerType()).isEqualTo(ContainerType.DRY_40);
        assertThat(result.containerCount()).isEqualTo(2);
        assertThat(result.requestedDepartureDate()).isEqualTo(DEPARTURE_DATE);
    }
}
