package com.freightflow.commons.domain;

/**
 * Centralized constants used across all FreightFlow services.
 *
 * <p>All magic strings, header names, metric names, cache names, and configuration
 * keys are defined here in one place. This follows the DRY principle and ensures
 * consistency across the entire platform.</p>
 *
 * <p><b>Usage:</b> Import statically where needed:</p>
 * <pre>{@code
 * import static com.freightflow.commons.domain.FreightFlowConstants.*;
 * }</pre>
 *
 * <p>This class is intentionally non-instantiable (private constructor).</p>
 */
public final class FreightFlowConstants {

    private FreightFlowConstants() {
        throw new AssertionError("Constants class — do not instantiate");
    }

    // ==================== HTTP Headers ====================
    /** Correlation ID header for distributed tracing across services. */
    public static final String HEADER_CORRELATION_ID = "X-Correlation-ID";
    /** Idempotency key header for safe POST/PATCH retries. */
    public static final String HEADER_IDEMPOTENCY_KEY = "Idempotency-Key";
    /** Tenant ID header for multi-tenancy routing. */
    public static final String HEADER_TENANT_ID = "X-Tenant-ID";
    /** Rate limit remaining header. */
    public static final String HEADER_RATE_LIMIT_REMAINING = "X-RateLimit-Remaining";
    /** Rate limit total header. */
    public static final String HEADER_RATE_LIMIT_LIMIT = "X-RateLimit-Limit";
    /** Rate limit reset header (epoch seconds). */
    public static final String HEADER_RATE_LIMIT_RESET = "X-RateLimit-Reset";

    // ==================== MDC Keys ====================
    /** MDC key for correlation ID (set by CorrelationIdFilter). */
    public static final String MDC_CORRELATION_ID = "correlationId";
    /** MDC key for tenant ID. */
    public static final String MDC_TENANT_ID = "tenantId";
    /** MDC key for authenticated user ID. */
    public static final String MDC_USER_ID = "userId";
    /** MDC key for the current service name. */
    public static final String MDC_SERVICE_NAME = "serviceName";
    /** MDC key for HTTP request method. */
    public static final String MDC_REQUEST_METHOD = "requestMethod";
    /** MDC key for HTTP request URI. */
    public static final String MDC_REQUEST_URI = "requestUri";

    // ==================== Kafka Topics ====================
    /** Main booking events topic (domain events). */
    public static final String TOPIC_BOOKING_EVENTS = "booking.events";
    /** Booking commands topic (async command processing). */
    public static final String TOPIC_BOOKING_COMMANDS = "booking.commands";
    /** Booking events dead letter queue. */
    public static final String TOPIC_BOOKING_DLQ = "booking.events.dlq";
    /** Billing events topic. */
    public static final String TOPIC_BILLING_EVENTS = "billing.events";
    /** Tracking events topic. */
    public static final String TOPIC_TRACKING_EVENTS = "tracking.events";
    /** Vessel events topic. */
    public static final String TOPIC_VESSEL_EVENTS = "vessel.events";
    /** Notification commands topic. */
    public static final String TOPIC_NOTIFICATION_COMMANDS = "notification.commands";

    // ==================== Kafka Consumer Groups ====================
    public static final String CONSUMER_GROUP_BOOKING = "booking-service-group";
    public static final String CONSUMER_GROUP_BILLING = "billing-service-group";
    public static final String CONSUMER_GROUP_TRACKING = "tracking-service-group";
    public static final String CONSUMER_GROUP_NOTIFICATION = "notification-service-group";

    // ==================== Kafka Headers ====================
    public static final String KAFKA_HEADER_EVENT_TYPE = "event-type";
    public static final String KAFKA_HEADER_AGGREGATE_TYPE = "aggregate-type";
    public static final String KAFKA_HEADER_CORRELATION_ID = "X-Correlation-ID";

    // ==================== Cache Names ====================
    /** Cache for individual booking projections. */
    public static final String CACHE_BOOKINGS = "bookings";
    /** Cache for customer booking lists. */
    public static final String CACHE_CUSTOMER_BOOKINGS = "customerBookings";
    /** Cache for vessel schedules. */
    public static final String CACHE_VESSEL_SCHEDULES = "vesselSchedules";
    /** Cache for port reference data. */
    public static final String CACHE_PORTS = "ports";
    /** Cache for customer profiles. */
    public static final String CACHE_CUSTOMERS = "customers";

    // ==================== Metric Names ====================
    public static final String METRIC_BOOKINGS_CREATED = "freightflow.bookings.created";
    public static final String METRIC_BOOKINGS_CONFIRMED = "freightflow.bookings.confirmed";
    public static final String METRIC_BOOKINGS_CANCELLED = "freightflow.bookings.cancelled";
    public static final String METRIC_EVENTS_PUBLISHED = "freightflow.events.published";
    public static final String METRIC_EVENTS_CONSUMED = "freightflow.events.consumed";
    public static final String METRIC_METHOD_EXECUTION = "freightflow.method.execution";

    // ==================== API Paths ====================
    public static final String API_V1 = "/api/v1";
    public static final String API_V1_BOOKINGS = API_V1 + "/bookings";
    public static final String API_V1_TRACKING = API_V1 + "/tracking";
    public static final String API_V1_BILLING = API_V1 + "/billing";
    public static final String API_V1_CUSTOMERS = API_V1 + "/customers";
    public static final String API_V1_VESSELS = API_V1 + "/vessels";

    // ==================== Problem Detail URIs (RFC 7807) ====================
    public static final String PROBLEM_BASE_URI = "https://api.freightflow.com/problems/";
    public static final String PROBLEM_NOT_FOUND = PROBLEM_BASE_URI + "resource-not-found";
    public static final String PROBLEM_VALIDATION = PROBLEM_BASE_URI + "validation-error";
    public static final String PROBLEM_CONFLICT = PROBLEM_BASE_URI + "conflict";
    public static final String PROBLEM_BUSINESS_RULE = PROBLEM_BASE_URI + "business-rule-violation";
    public static final String PROBLEM_EXTERNAL_SERVICE = PROBLEM_BASE_URI + "external-service-failure";
    public static final String PROBLEM_INTERNAL = PROBLEM_BASE_URI + "internal-error";
    public static final String PROBLEM_RATE_LIMIT = PROBLEM_BASE_URI + "rate-limit-exceeded";

    // ==================== Aggregate Types ====================
    public static final String AGGREGATE_BOOKING = "Booking";
    public static final String AGGREGATE_CUSTOMER = "Customer";
    public static final String AGGREGATE_VESSEL = "Vessel";
    public static final String AGGREGATE_INVOICE = "Invoice";

    // ==================== Date/Time Defaults ====================
    /** Minimum days before departure for a new booking. */
    public static final int DEFAULT_MIN_DEPARTURE_DAYS = 7;
    /** Maximum containers allowed per booking. */
    public static final int DEFAULT_MAX_CONTAINERS = 50;
    /** Default booking draft expiry in hours. */
    public static final int DEFAULT_DRAFT_EXPIRY_HOURS = 72;
}
