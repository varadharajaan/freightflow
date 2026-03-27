package com.freightflow.commons.observability.profiling;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.binder.jvm.ClassLoaderMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmGcMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmMemoryMetrics;
import io.micrometer.core.instrument.binder.jvm.JvmThreadMetrics;
import io.micrometer.core.instrument.binder.system.ProcessorMetrics;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * Configures JVM profiling metrics and custom business metrics via Micrometer.
 *
 * <h3>JVM Metrics (auto-registered)</h3>
 * <pre>
 * jvm_memory_used_bytes{area="heap|nonheap"}
 * jvm_memory_max_bytes{area="heap|nonheap"}
 * jvm_gc_pause_seconds{action="end of major|minor GC"}
 * jvm_threads_live_threads (includes Virtual Threads count)
 * jvm_threads_daemon_threads
 * jvm_classes_loaded_classes
 * system_cpu_usage
 * process_cpu_usage
 * </pre>
 *
 * <h3>Custom Business Metrics</h3>
 * <pre>
 * freightflow_bookings_created_total{status="success|failure"}
 * freightflow_bookings_confirmed_total
 * freightflow_bookings_cancelled_total
 * freightflow_events_published_total{event_type="BookingCreated|..."}
 * </pre>
 *
 * <p>All metrics are scraped by Prometheus and visualized in Grafana dashboards.</p>
 */
@Configuration
public class MetricsConfig {

    private static final Logger log = LoggerFactory.getLogger(MetricsConfig.class);

    /**
     * Registers JVM memory metrics (heap, non-heap, buffer pools).
     */
    @Bean
    public JvmMemoryMetrics jvmMemoryMetrics() {
        return new JvmMemoryMetrics();
    }

    /**
     * Registers JVM garbage collection metrics (pause times, collection counts).
     */
    @Bean
    public JvmGcMetrics jvmGcMetrics() {
        return new JvmGcMetrics();
    }

    /**
     * Registers JVM thread metrics (live threads, daemon threads, peak, Virtual Threads).
     */
    @Bean
    public JvmThreadMetrics jvmThreadMetrics() {
        return new JvmThreadMetrics();
    }

    /**
     * Registers class loader metrics (loaded/unloaded classes).
     */
    @Bean
    public ClassLoaderMetrics classLoaderMetrics() {
        return new ClassLoaderMetrics();
    }

    /**
     * Registers CPU and processor metrics (system CPU, process CPU).
     */
    @Bean
    public ProcessorMetrics processorMetrics() {
        return new ProcessorMetrics();
    }

    // ==================== Custom Business Metrics ====================

    /**
     * Counter for bookings created (success/failure tracking).
     */
    @Bean
    public Counter bookingsCreatedCounter(MeterRegistry registry) {
        return Counter.builder("freightflow.bookings.created")
                .description("Total bookings created")
                .tag("status", "success")
                .register(registry);
    }

    /**
     * Counter for bookings confirmed.
     */
    @Bean
    public Counter bookingsConfirmedCounter(MeterRegistry registry) {
        return Counter.builder("freightflow.bookings.confirmed")
                .description("Total bookings confirmed")
                .register(registry);
    }

    /**
     * Counter for bookings cancelled.
     */
    @Bean
    public Counter bookingsCancelledCounter(MeterRegistry registry) {
        return Counter.builder("freightflow.bookings.cancelled")
                .description("Total bookings cancelled")
                .register(registry);
    }
}
