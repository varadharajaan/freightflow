package com.freightflow.booking.infrastructure.adapter.out.external;

import com.freightflow.booking.application.port.BillingPort;
import com.freightflow.commons.observability.profiling.Profiled;
import io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker;
import io.github.resilience4j.retry.annotation.Retry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Infrastructure adapter for billing interactions.
 */
@Component
public class BillingServiceClient implements BillingPort {

    private static final Logger log = LoggerFactory.getLogger(BillingServiceClient.class);

    @Override
    @CircuitBreaker(name = "billingService", fallbackMethod = "generateInvoiceFallback")
    @Retry(name = "billingService")
    @Profiled(value = "generateInvoice", slowThresholdMs = 1500)
    public boolean generateInvoice(String bookingId, String idempotencyKey) {
        log.debug("Generating invoice: bookingId={}, idempotencyKey={}", bookingId, idempotencyKey);

        // TODO: Replace with actual HTTP call to billing-service.
        return true;
    }

    @Override
    @CircuitBreaker(name = "billingService", fallbackMethod = "cancelInvoiceFallback")
    @Retry(name = "billingService")
    @Profiled(value = "cancelInvoice", slowThresholdMs = 1500)
    public boolean cancelInvoice(String bookingId, String idempotencyKey) {
        log.debug("Cancelling invoice: bookingId={}, idempotencyKey={}", bookingId, idempotencyKey);

        // TODO: Replace with actual HTTP call to billing-service.
        return true;
    }

    private boolean generateInvoiceFallback(String bookingId, String idempotencyKey, Throwable throwable) {
        log.warn("Billing generateInvoice fallback: bookingId={}, idempotencyKey={}, error={}",
                bookingId, idempotencyKey, throwable.getMessage());
        return false;
    }

    private boolean cancelInvoiceFallback(String bookingId, String idempotencyKey, Throwable throwable) {
        log.warn("Billing cancelInvoice fallback: bookingId={}, idempotencyKey={}, error={}",
                bookingId, idempotencyKey, throwable.getMessage());
        return false;
    }
}

