package com.freightflow.commons.exception;

import java.io.Serial;

/**
 * Thrown when a requested resource cannot be found.
 *
 * <p>Maps to HTTP 404 Not Found. Used when an aggregate, entity, or read model
 * cannot be located by its identifier.</p>
 *
 * <p>Usage example:</p>
 * <pre>{@code
 * throw ResourceNotFoundException.forBooking("BKG-2026-001234");
 * }</pre>
 */
public final class ResourceNotFoundException extends FreightFlowException {

    @Serial
    private static final long serialVersionUID = 1L;

    private final String resourceType;
    private final String resourceId;

    /**
     * Constructs a ResourceNotFoundException.
     *
     * @param errorCode    machine-readable error code
     * @param resourceType the type of resource (e.g., "Booking", "Customer")
     * @param resourceId   the identifier that was not found
     */
    public ResourceNotFoundException(String errorCode, String resourceType, String resourceId) {
        super(errorCode, "%s not found with ID '%s'".formatted(resourceType, resourceId));
        this.resourceType = resourceType;
        this.resourceId = resourceId;
    }

    /**
     * Factory method for booking not found.
     *
     * @param bookingId the booking identifier
     * @return a ResourceNotFoundException for a booking
     */
    public static ResourceNotFoundException forBooking(String bookingId) {
        return new ResourceNotFoundException("BOOKING_NOT_FOUND", "Booking", bookingId);
    }

    /**
     * Factory method for customer not found.
     *
     * @param customerId the customer identifier
     * @return a ResourceNotFoundException for a customer
     */
    public static ResourceNotFoundException forCustomer(String customerId) {
        return new ResourceNotFoundException("CUSTOMER_NOT_FOUND", "Customer", customerId);
    }

    /**
     * Factory method for any resource type.
     *
     * @param resourceType the type name
     * @param resourceId   the identifier
     * @return a ResourceNotFoundException
     */
    public static ResourceNotFoundException forResource(String resourceType, String resourceId) {
        return new ResourceNotFoundException(
                resourceType.toUpperCase() + "_NOT_FOUND", resourceType, resourceId);
    }

    public String getResourceType() {
        return resourceType;
    }

    public String getResourceId() {
        return resourceId;
    }
}
