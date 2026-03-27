package com.freightflow.booking.infrastructure.scheduler;

import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;

/**
 * Scheduled tasks for the booking service using Spring's {@code @Scheduled}.
 *
 * <h3>Spring Advanced Feature: @Scheduled with Virtual Threads</h3>
 * <p>With {@code spring.threads.virtual.enabled=true} (Spring Boot 3.2+),
 * scheduled tasks automatically run on Virtual Threads instead of platform threads.
 * This means thousands of concurrent scheduled tasks can run without
 * exhausting the thread pool.</p>
 *
 * <h3>Important: Distributed Scheduling</h3>
 * <p>In a multi-pod deployment, {@code @Scheduled} runs on ALL pods simultaneously.
 * To prevent duplicate execution, we will integrate Shedlock in Topic T21.
 * For now, tasks are designed to be <b>idempotent</b> — running them multiple
 * times produces the same result.</p>
 *
 * @see org.springframework.scheduling.annotation.Scheduled
 */
@Component
@EnableScheduling
public class BookingScheduledTasks {

    private static final Logger log = LoggerFactory.getLogger(BookingScheduledTasks.class);

    /**
     * Cleans up stale DRAFT bookings that haven't been confirmed within 72 hours.
     *
     * <p>Runs every hour. Idempotent — safe to run on multiple pods simultaneously.</p>
     * <p>Uses {@code fixedDelayString} for externalized interval configuration.</p>
     */
    @Scheduled(fixedDelayString = "${freightflow.scheduler.stale-booking-cleanup-ms:3600000}")
    @Profiled(value = "staleBookingCleanup", slowThresholdMs = 5000)
    public void cleanupStaleDraftBookings() {
        log.info("Starting stale DRAFT booking cleanup job at {}", Instant.now());

        // TODO: Implement query for DRAFT bookings older than 72 hours and cancel them
        // Will be fully implemented when JPA Specifications are integrated (T4 enhancement)
        // For now, logs execution to demonstrate @Scheduled + @Profiled integration

        log.info("Stale DRAFT booking cleanup completed at {}", Instant.now());
    }

    /**
     * Emits booking statistics metrics every 5 minutes.
     *
     * <p>Uses {@code cron} expression for precise scheduling.
     * On Virtual Threads, this is extremely lightweight.</p>
     */
    @Scheduled(cron = "${freightflow.scheduler.metrics-cron:0 */5 * * * *}")
    @Profiled(value = "emitBookingMetrics", slowThresholdMs = 2000)
    public void emitBookingStatistics() {
        log.debug("Emitting booking statistics metrics");

        // TODO: Query booking counts by status and push to Micrometer gauges
        // Will be enhanced when business metrics are fully integrated

        log.debug("Booking statistics metrics emitted");
    }
}
