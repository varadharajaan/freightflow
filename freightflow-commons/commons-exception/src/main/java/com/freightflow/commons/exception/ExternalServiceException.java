package com.freightflow.commons.exception;

import java.io.Serial;

/**
 * Thrown when an external service call fails.
 *
 * <p>Maps to HTTP 502 Bad Gateway or 503 Service Unavailable.
 * Used when downstream services (payment gateways, port terminal systems,
 * vessel tracking APIs) are unreachable or return errors.</p>
 *
 * <p>This exception should be thrown AFTER resilience mechanisms (circuit breaker,
 * retry) have been exhausted.</p>
 */
public final class ExternalServiceException extends FreightFlowException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String serviceName;
    private final int httpStatus;

    public ExternalServiceException(String errorCode, String message,
                                     String serviceName, int httpStatus, Throwable cause) {
        super(errorCode, message, cause);
        this.serviceName = serviceName;
        this.httpStatus = httpStatus;
    }

    /**
     * Factory for external service timeout.
     *
     * @param serviceName the name of the external service
     * @return an ExternalServiceException
     */
    public static ExternalServiceException timeout(String serviceName) {
        return new ExternalServiceException(
                "EXTERNAL_SERVICE_TIMEOUT",
                "External service '%s' timed out".formatted(serviceName),
                serviceName, 504, null);
    }

    /**
     * Factory for external service unavailable.
     *
     * @param serviceName the name of the external service
     * @param cause       the underlying exception
     * @return an ExternalServiceException
     */
    public static ExternalServiceException unavailable(String serviceName, Throwable cause) {
        return new ExternalServiceException(
                "EXTERNAL_SERVICE_UNAVAILABLE",
                "External service '%s' is unavailable".formatted(serviceName),
                serviceName, 503, cause);
    }

    public String getServiceName() {
        return serviceName;
    }

    public int getHttpStatus() {
        return httpStatus;
    }
}
