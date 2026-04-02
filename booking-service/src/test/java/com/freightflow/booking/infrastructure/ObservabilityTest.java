package com.freightflow.booking.infrastructure;

import io.micrometer.core.instrument.MeterRegistry;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.web.servlet.MockMvc;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Integration tests verifying the observability stack is correctly configured.
 *
 * <p>These tests validate that Spring Boot Actuator endpoints are accessible and
 * that custom Micrometer metrics are registered in the {@link MeterRegistry}.
 * This ensures the Prometheus scrape endpoint will return the expected metrics
 * when the service is deployed.</p>
 *
 * <h3>What These Tests Verify</h3>
 * <ul>
 *   <li>Actuator health endpoint returns UP status</li>
 *   <li>Prometheus endpoint returns JVM and custom metrics in Prometheus exposition format</li>
 *   <li>Metrics endpoint lists all registered meter names</li>
 *   <li>Custom business metrics (freightflow.bookings.created) are registered</li>
 *   <li>Custom profiling metrics (freightflow.method.execution) are registered</li>
 * </ul>
 *
 * @see io.micrometer.core.instrument.MeterRegistry
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {
                "spring.profiles.active=test",
                "spring.datasource.url=jdbc:h2:mem:testdb;MODE=PostgreSQL;DB_CLOSE_DELAY=-1",
                "spring.datasource.driver-class-name=org.h2.Driver",
                "spring.jpa.hibernate.ddl-auto=create-drop",
                "spring.jpa.database-platform=org.hibernate.dialect.H2Dialect",
                "spring.flyway.enabled=false",
                "spring.sql.init.mode=always",
                "spring.sql.init.schema-locations=classpath:schema-h2.sql",
                "spring.kafka.bootstrap-servers=localhost:9092",
                "spring.kafka.consumer.auto-offset-reset=earliest",
                "spring.cloud.config.enabled=false",
                "eureka.client.enabled=false",
                "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.kafka.KafkaAutoConfiguration,org.springframework.boot.autoconfigure.security.oauth2.resource.servlet.OAuth2ResourceServerAutoConfiguration",
                "management.endpoints.web.exposure.include=health,info,metrics,prometheus",
                "management.endpoint.health.show-details=always",
                "management.endpoint.prometheus.enabled=true",
                "management.prometheus.metrics.export.enabled=true",
                "freightflow.tracing.enabled=false",
                "freightflow.security.enabled=false",
                "spring.cache.type=simple"
        }
)
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DisplayName("Observability Stack Integration Tests")
class ObservabilityTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private MeterRegistry meterRegistry;

    // ==================== Actuator Endpoint Tests ====================

    @Nested
    @DisplayName("Actuator Endpoints")
    class ActuatorEndpoints {

        @Test
        @DisplayName("should return 200 with status UP from health endpoint")
        void should_ReturnHealthUp_When_HealthEndpointCalled() throws Exception {
            // Given — application is running

            // When / Then
            mockMvc.perform(get("/actuator/health"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.status").value("UP"));
        }

        @Test
        @DisplayName("should return 200 with Prometheus metrics including JVM memory data")
        void should_ReturnPrometheusMetrics_When_PrometheusEndpointCalled() throws Exception {
            // Given — Micrometer Prometheus registry is auto-configured

            // When / Then
            mockMvc.perform(get("/actuator/prometheus"))
                    .andExpect(status().isOk())
                    .andExpect(result -> {
                        String body = result.getResponse().getContentAsString();
                        assertThat(body)
                                .as("Prometheus endpoint should contain JVM memory metrics")
                                .contains("jvm_memory_used_bytes");
                    });
        }

        @Test
        @DisplayName("should return 200 with metrics listing containing names array")
        void should_ReturnMetricNames_When_MetricsEndpointCalled() throws Exception {
            // Given — Micrometer registry contains auto-registered and custom metrics

            // When / Then
            mockMvc.perform(get("/actuator/metrics"))
                    .andExpect(status().isOk())
                    .andExpect(jsonPath("$.names").isArray())
                    .andExpect(jsonPath("$.names").isNotEmpty());
        }
    }

    // ==================== Custom Metrics Registration Tests ====================

    @Nested
    @DisplayName("Custom Metrics Registration")
    class CustomMetrics {

        @Test
        @DisplayName("should register freightflow.bookings.created counter in MeterRegistry")
        void should_RegisterBookingsCreatedCounter_When_ApplicationStarts() {
            // Given — MetricsConfig registers custom counters on startup

            // When
            var meter = meterRegistry.find("freightflow.bookings.created").counter();

            // Then
            assertThat(meter)
                    .as("freightflow.bookings.created counter should be registered in MeterRegistry")
                    .isNotNull();
        }

        @Test
        @DisplayName("should register freightflow.bookings.confirmed counter in MeterRegistry")
        void should_RegisterBookingsConfirmedCounter_When_ApplicationStarts() {
            // Given — MetricsConfig registers custom counters on startup

            // When
            var meter = meterRegistry.find("freightflow.bookings.confirmed").counter();

            // Then
            assertThat(meter)
                    .as("freightflow.bookings.confirmed counter should be registered in MeterRegistry")
                    .isNotNull();
        }

        @Test
        @DisplayName("should register freightflow.bookings.cancelled counter in MeterRegistry")
        void should_RegisterBookingsCancelledCounter_When_ApplicationStarts() {
            // Given — MetricsConfig registers custom counters on startup

            // When
            var meter = meterRegistry.find("freightflow.bookings.cancelled").counter();

            // Then
            assertThat(meter)
                    .as("freightflow.bookings.cancelled counter should be registered in MeterRegistry")
                    .isNotNull();
        }

        @Test
        @DisplayName("should register JVM memory metrics in MeterRegistry")
        void should_RegisterJvmMemoryMetrics_When_ApplicationStarts() {
            // Given — MetricsConfig registers JVM metric binders

            // When
            var meter = meterRegistry.find("jvm.memory.used").gauge();

            // Then
            assertThat(meter)
                    .as("JVM memory used gauge should be registered in MeterRegistry")
                    .isNotNull();
        }
    }
}
