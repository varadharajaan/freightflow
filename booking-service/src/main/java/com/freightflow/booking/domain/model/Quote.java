package com.freightflow.booking.domain.model;

import com.freightflow.commons.domain.Money;
import com.freightflow.commons.domain.PortCode;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.Currency;
import java.util.Objects;
import java.util.UUID;

/**
 * Domain entity representing a freight shipping quote.
 *
 * <p>A quote captures the estimated cost for shipping a specified number of containers
 * between two ports. Quotes have a limited validity period and can be converted into
 * a {@link Booking} before they expire.</p>
 *
 * <h3>Design Patterns Applied</h3>
 * <ul>
 *   <li><b>Domain Entity</b> (DDD) — identified by {@code quoteId}</li>
 *   <li><b>Factory Method</b> — {@link #generate} encapsulates creation logic</li>
 *   <li><b>Tell, Don't Ask</b> — state queries and transitions are internal</li>
 * </ul>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>A quote always has an origin, destination, container type, and price</li>
 *   <li>The validity date must be in the future at creation time</li>
 *   <li>Only an ACTIVE, non-expired quote can be converted to a booking</li>
 * </ul>
 *
 * @see QuoteStatus
 * @see Booking
 */
public class Quote {

    private final UUID quoteId;
    private final String customerId;
    private final PortCode origin;
    private final PortCode destination;
    private final ContainerType containerType;
    private final int containerCount;
    private final Money totalPrice;
    private final LocalDate validUntil;

    private QuoteStatus status;

    /**
     * Private constructor — use {@link #generate} factory method.
     */
    private Quote(UUID quoteId, String customerId, PortCode origin, PortCode destination,
                  ContainerType containerType, int containerCount, Money totalPrice,
                  LocalDate validUntil) {
        this.quoteId = Objects.requireNonNull(quoteId, "Quote ID must not be null");
        this.customerId = Objects.requireNonNull(customerId, "Customer ID must not be null");
        this.origin = Objects.requireNonNull(origin, "Origin must not be null");
        this.destination = Objects.requireNonNull(destination, "Destination must not be null");
        this.containerType = Objects.requireNonNull(containerType, "Container type must not be null");
        this.totalPrice = Objects.requireNonNull(totalPrice, "Total price must not be null");
        this.validUntil = Objects.requireNonNull(validUntil, "Valid until must not be null");

        if (containerCount <= 0) {
            throw new IllegalArgumentException("Container count must be positive, got: " + containerCount);
        }

        this.containerCount = containerCount;
        this.status = QuoteStatus.ACTIVE;
    }

    // ==================== Factory Method ====================

    /**
     * Generates a new freight quote with the given parameters.
     *
     * <p>The quote is created in {@link QuoteStatus#ACTIVE} status and is valid until
     * the specified date. The total price represents the estimated shipping cost.</p>
     *
     * @param customerId    the customer requesting the quote
     * @param origin        the origin port code
     * @param destination   the destination port code
     * @param containerType the type of container required
     * @param containerCount the number of containers
     * @param totalPrice    the calculated total price
     * @param validUntil    the date until which the quote is valid
     * @return a new Quote in ACTIVE status
     * @throws IllegalArgumentException if validUntil is not in the future or containerCount is not positive
     */
    public static Quote generate(String customerId, PortCode origin, PortCode destination,
                                  ContainerType containerType, int containerCount,
                                  Money totalPrice, LocalDate validUntil) {
        if (!validUntil.isAfter(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Quote validity date must be in the future, got: " + validUntil);
        }

        return new Quote(UUID.randomUUID(), customerId, origin, destination,
                containerType, containerCount, totalPrice, validUntil);
    }

    // ==================== Domain Logic ====================

    /**
     * Checks whether this quote has expired based on the current date.
     *
     * <p>A quote is considered expired if the current date is after the {@code validUntil} date,
     * or if the status has already been set to {@link QuoteStatus#EXPIRED}.</p>
     *
     * @return {@code true} if the quote has expired
     */
    public boolean isExpired() {
        return status == QuoteStatus.EXPIRED || LocalDate.now().isAfter(validUntil);
    }

    /**
     * Converts this quote to a booking by marking it as {@link QuoteStatus#CONVERTED}.
     *
     * <p>This method validates that the quote is still active and has not expired.
     * After conversion, the quote cannot be used again.</p>
     *
     * @throws IllegalStateException if the quote is not in ACTIVE status or has expired
     */
    public void convertToBooking() {
        if (status != QuoteStatus.ACTIVE) {
            throw new IllegalStateException(
                    "Cannot convert quote %s: status is %s, expected ACTIVE".formatted(quoteId, status));
        }
        if (isExpired()) {
            this.status = QuoteStatus.EXPIRED;
            throw new IllegalStateException(
                    "Cannot convert quote %s: quote has expired (validUntil=%s)".formatted(quoteId, validUntil));
        }

        this.status = QuoteStatus.CONVERTED;
    }

    // ==================== Reconstitution ====================

    /**
     * Reconstitutes a Quote from persisted state.
     *
     * <p>Used only by the persistence adapter when loading a quote from the database.
     * Bypasses validation since data has already been validated at creation time.</p>
     *
     * @return a Quote in its persisted state
     */
    public static Quote reconstitute(UUID quoteId, String customerId, PortCode origin,
                                      PortCode destination, ContainerType containerType,
                                      int containerCount, Money totalPrice,
                                      LocalDate validUntil, QuoteStatus status) {
        var quote = new Quote(quoteId, customerId, origin, destination,
                containerType, containerCount, totalPrice, validUntil);
        quote.status = status;
        return quote;
    }

    // ==================== Accessors ====================

    public UUID getQuoteId() {
        return quoteId;
    }

    public String getCustomerId() {
        return customerId;
    }

    public PortCode getOrigin() {
        return origin;
    }

    public PortCode getDestination() {
        return destination;
    }

    public ContainerType getContainerType() {
        return containerType;
    }

    public int getContainerCount() {
        return containerCount;
    }

    public Money getTotalPrice() {
        return totalPrice;
    }

    public LocalDate getValidUntil() {
        return validUntil;
    }

    public QuoteStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "Quote[id=%s, customer=%s, route=%s→%s, containers=%dx%s, price=%s, status=%s]".formatted(
                quoteId, customerId, origin.value(), destination.value(),
                containerCount, containerType, totalPrice, status);
    }
}
