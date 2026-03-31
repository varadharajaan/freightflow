package com.freightflow.booking.infrastructure.adapter.out.messaging.schema;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.annotation.PostConstruct;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.time.LocalDate;
import java.util.Objects;
import java.util.UUID;

/**
 * Validates schema compatibility on application startup.
 *
 * <p>This component runs compatibility checks between schema versions to ensure
 * that producers and consumers can safely evolve independently. It validates three
 * compatibility modes as defined by the Confluent Schema Registry model:</p>
 *
 * <h3>Compatibility Modes</h3>
 * <ul>
 *   <li><b>Backward compatible</b>: A V2 consumer can read data produced by a V1 producer.
 *       New fields have defaults (null). Tested by deserializing V1 JSON as V2.</li>
 *   <li><b>Forward compatible</b>: A V1 consumer can read data produced by a V2 producer.
 *       Unknown fields are ignored. Tested by deserializing V2 JSON as V1 with
 *       {@code FAIL_ON_UNKNOWN_PROPERTIES = false}.</li>
 *   <li><b>Full compatible</b>: Both backward and forward compatible. This is the safest
 *       mode and is what we validate here — allowing producers and consumers to be
 *       upgraded in any order.</li>
 * </ul>
 *
 * <p>Validation failures are logged at WARN level but do not prevent application startup,
 * since schema incompatibility may be intentional during major version upgrades.</p>
 *
 * @see BookingEventSchemaV1
 * @see BookingEventSchemaV2
 * @see EventSchemaRegistry
 */
@Component
public class SchemaCompatibilityValidator {

    private static final Logger log = LoggerFactory.getLogger(SchemaCompatibilityValidator.class);

    private final ObjectMapper objectMapper;

    /**
     * Constructs the validator with the application's Jackson {@link ObjectMapper}.
     *
     * @param objectMapper the Jackson ObjectMapper for JSON processing
     */
    public SchemaCompatibilityValidator(ObjectMapper objectMapper) {
        this.objectMapper = Objects.requireNonNull(objectMapper, "ObjectMapper must not be null");
    }

    /**
     * Runs compatibility validation on startup.
     *
     * <p>Verifies that V1 and V2 schemas for BookingCreated are both backward and
     * forward compatible (full compatibility). Results are logged at INFO level.</p>
     */
    @PostConstruct
    void validateOnStartup() {
        log.info("Starting schema compatibility validation for BookingCreated event schemas");

        boolean backwardCompatible = validateBackwardCompatibility();
        boolean forwardCompatible = validateForwardCompatibility();

        if (backwardCompatible && forwardCompatible) {
            log.info("Schema compatibility validation PASSED: BookingCreated V1/V2 are FULLY compatible " +
                    "(backward + forward)");
        } else {
            log.warn("Schema compatibility validation completed with issues: " +
                    "backward={}, forward={}", backwardCompatible, forwardCompatible);
        }
    }

    /**
     * Validates backward compatibility: V2 consumer can read V1 data.
     *
     * <p>Creates a sample V1 event, serializes it, then attempts to deserialize it
     * as a V2 schema. If successful, V2 consumers can safely read V1 events.</p>
     *
     * @return {@code true} if V2 can read V1 data
     */
    private boolean validateBackwardCompatibility() {
        try {
            BookingEventSchemaV1 sampleV1 = createSampleV1();
            String v1Json = objectMapper.writeValueAsString(sampleV1);

            // V2 consumer reads V1 data — new fields should default to null
            ObjectMapper lenientMapper = objectMapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            BookingEventSchemaV2 deserialized = lenientMapper.readValue(v1Json, BookingEventSchemaV2.class);

            // Verify core fields are preserved
            boolean coreFieldsMatch = sampleV1.eventId().equals(deserialized.eventId())
                    && sampleV1.bookingId().equals(deserialized.bookingId())
                    && sampleV1.customerId().equals(deserialized.customerId())
                    && sampleV1.origin().equals(deserialized.origin())
                    && sampleV1.destination().equals(deserialized.destination())
                    && sampleV1.containerType().equals(deserialized.containerType())
                    && sampleV1.containerCount() == deserialized.containerCount();

            if (coreFieldsMatch) {
                log.info("Backward compatibility check PASSED: V2 can read V1 events " +
                        "(new fields weight={}, commodityCode={})",
                        deserialized.weight(), deserialized.commodityCode());
            } else {
                log.warn("Backward compatibility check FAILED: core fields do not match after V1->V2 read");
            }
            return coreFieldsMatch;

        } catch (JsonProcessingException ex) {
            log.warn("Backward compatibility check FAILED: V2 cannot read V1 data — {}", ex.getMessage(), ex);
            return false;
        }
    }

    /**
     * Validates forward compatibility: V1 consumer can read V2 data (ignoring unknown fields).
     *
     * <p>Creates a sample V2 event with the new fields populated, serializes it, then
     * attempts to deserialize it as a V1 schema with unknown field ignoring enabled.
     * If successful, V1 consumers can safely read V2 events.</p>
     *
     * @return {@code true} if V1 can read V2 data (ignoring unknown fields)
     */
    private boolean validateForwardCompatibility() {
        try {
            BookingEventSchemaV2 sampleV2 = createSampleV2();
            String v2Json = objectMapper.writeValueAsString(sampleV2);

            // V1 consumer reads V2 data — must ignore unknown fields
            ObjectMapper lenientMapper = objectMapper.copy()
                    .configure(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES, false);
            BookingEventSchemaV1 deserialized = lenientMapper.readValue(v2Json, BookingEventSchemaV1.class);

            // Verify V1 fields are preserved
            boolean coreFieldsMatch = sampleV2.eventId().equals(deserialized.eventId())
                    && sampleV2.bookingId().equals(deserialized.bookingId())
                    && sampleV2.customerId().equals(deserialized.customerId())
                    && sampleV2.origin().equals(deserialized.origin())
                    && sampleV2.destination().equals(deserialized.destination())
                    && sampleV2.containerType().equals(deserialized.containerType())
                    && sampleV2.containerCount() == deserialized.containerCount();

            if (coreFieldsMatch) {
                log.info("Forward compatibility check PASSED: V1 can read V2 events (unknown fields ignored)");
            } else {
                log.warn("Forward compatibility check FAILED: core fields do not match after V2->V1 read");
            }
            return coreFieldsMatch;

        } catch (JsonProcessingException ex) {
            log.warn("Forward compatibility check FAILED: V1 cannot read V2 data — {}", ex.getMessage(), ex);
            return false;
        }
    }

    private BookingEventSchemaV1 createSampleV1() {
        return new BookingEventSchemaV1(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "DEHAM",
                "CNSHA",
                "DRY_40",
                2,
                LocalDate.now().plusDays(30),
                Instant.now()
        );
    }

    private BookingEventSchemaV2 createSampleV2() {
        return new BookingEventSchemaV2(
                UUID.randomUUID(),
                UUID.randomUUID().toString(),
                UUID.randomUUID().toString(),
                "DEHAM",
                "CNSHA",
                "DRY_40",
                2,
                LocalDate.now().plusDays(30),
                Instant.now(),
                25000.0,
                "HS8471"
        );
    }
}
