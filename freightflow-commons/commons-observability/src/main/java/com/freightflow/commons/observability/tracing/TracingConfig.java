package com.freightflow.commons.observability.tracing;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import io.micrometer.observation.ObservationRegistry;
import io.micrometer.observation.aop.ObservedAspect;

/**
 * Configures distributed tracing for FreightFlow microservices using Micrometer Tracing
 * with the OpenTelemetry bridge.
 *
 * <h3>Architecture</h3>
 * <p>In Spring Boot 3.x, <b>Micrometer Tracing</b> replaces the deprecated Spring Cloud Sleuth
 * as the tracing abstraction layer. Micrometer Tracing provides a vendor-neutral API that
 * bridges to concrete tracing implementations (OpenTelemetry, Brave/Zipkin).</p>
 *
 * <h3>How Traces Propagate</h3>
 * <p>FreightFlow uses <b>OpenTelemetry</b> as the tracing backend, which is the CNCF standard
 * for observability. Traces propagate across service boundaries via <b>W3C Trace Context</b>
 * headers ({@code traceparent}, {@code tracestate}). This is the default propagation format
 * for OpenTelemetry and is supported by all modern observability platforms.</p>
 *
 * <h3>Trace Flow</h3>
 * <pre>
 * Client → API Gateway → Booking Service → Kafka → Tracking Service
 *   │         │              │                          │
 *   └─ traceparent header propagated across all hops ──┘
 * </pre>
 *
 * <h3>Configuration</h3>
 * <p>Tracing is enabled by default and can be disabled via:
 * {@code freightflow.tracing.enabled=false}</p>
 *
 * <p>The OTLP exporter sends traces to Jaeger (or any OTLP-compatible collector) configured
 * via {@code management.otlp.tracing.endpoint} in application.yml.</p>
 *
 * <h3>Spring Boot Auto-Configuration</h3>
 * <p>Spring Boot 3.x auto-configures the following when the required dependencies are on
 * the classpath:</p>
 * <ul>
 *   <li>{@code micrometer-tracing-bridge-otel} — bridges Micrometer Tracing to OpenTelemetry</li>
 *   <li>{@code opentelemetry-exporter-otlp} — exports traces via OTLP protocol</li>
 *   <li>W3C Trace Context propagation (default, no configuration needed)</li>
 *   <li>Automatic instrumentation for RestTemplate, WebClient, JDBC, Kafka</li>
 * </ul>
 *
 * @see io.micrometer.observation.ObservationRegistry
 * @see io.micrometer.observation.aop.ObservedAspect
 */
@Configuration
@ConditionalOnProperty(name = "freightflow.tracing.enabled", havingValue = "true", matchIfMissing = true)
public class TracingConfig {

    private static final Logger log = LoggerFactory.getLogger(TracingConfig.class);

    private final String serviceName;

    /**
     * Constructs the tracing configuration with the service name resolved from
     * {@code spring.application.name}.
     *
     * @param serviceName the Spring application name, used as the OpenTelemetry service name
     */
    public TracingConfig(@Value("${spring.application.name:freightflow}") String serviceName) {
        this.serviceName = serviceName;
        log.info("Distributed tracing initialized for service='{}'. "
                + "Propagation=W3C Trace Context, Exporter=OTLP", serviceName);
    }

    /**
     * Registers the {@link ObservedAspect} bean to enable {@code @Observed} annotation support.
     *
     * <p>Methods annotated with {@code @Observed} will automatically create observation spans,
     * which are recorded as both metrics (via Micrometer) and traces (via the OpenTelemetry bridge).
     * This provides unified observability without manual instrumentation.</p>
     *
     * @param observationRegistry the auto-configured observation registry
     * @return the observed aspect for AOP-based observation
     */
    @Bean
    public ObservedAspect observedAspect(ObservationRegistry observationRegistry) {
        log.debug("Registering ObservedAspect for @Observed annotation support on service='{}'",
                serviceName);
        return new ObservedAspect(observationRegistry);
    }
}
