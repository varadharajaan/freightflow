package com.freightflow.booking.infrastructure.adapter.out.external;

import com.freightflow.booking.application.port.NotificationPort;
import com.freightflow.commons.observability.profiling.Profiled;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter for notification dispatch.
 */
@Component
public class NotificationServiceClient implements NotificationPort {

    private static final Logger log = LoggerFactory.getLogger(NotificationServiceClient.class);

    @Override
    @CircuitBreaker(name = "notificationService", fallbackMethod = "sendBookingConfirmationFallback")
    @Retry(name = "notificationService")
    @Profiled(value = "sendBookingConfirmation", slowThresholdMs = 1500)
    public boolean sendBookingConfirmation(String bookingId, String idempotencyKey) {
        log.debug("Sending booking confirmation notification: bookingId={}, idempotencyKey={}",
                bookingId, idempotencyKey);

        // TODO: Replace with actual HTTP call to notification-service.
        return true;
    }

    private boolean sendBookingConfirmationFallback(String bookingId, String idempotencyKey, Throwable throwable) {
        log.warn("Notification fallback: bookingId={}, idempotencyKey={}, error={}",
                bookingId, idempotencyKey, throwable.getMessage());
        return false;
    }
}

