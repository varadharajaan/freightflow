package com.freightflow.booking.infrastructure.adapter.in.rest.dto;

import com.freightflow.booking.domain.model.ContainerType;
import com.freightflow.booking.domain.model.Quote;
import com.freightflow.booking.domain.model.QuoteStatus;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

/**
 * Outbound REST DTO representing a freight quote in API responses.
 *
 * <p>This record maps from the {@link Quote} domain entity to a flat JSON-serializable
 * structure. It intentionally hides internal domain details from API consumers,
 * following the Interface Segregation Principle.</p>
 *
 * @param quoteId        unique quote identifier
 * @param customerId     UUID of the requesting customer
 * @param origin         origin port code (UN/LOCODE)
 * @param destination    destination port code (UN/LOCODE)
 * @param containerType  type of container quoted
 * @param containerCount number of containers
 * @param totalAmount    the total quoted price amount
 * @param currency       the currency code (e.g. "USD")
 * @param validUntil     the date the quote expires
 * @param status         current quote lifecycle status
 * @param expired        whether the quote has expired
 */
public record QuoteResponse(
        UUID quoteId,
        String customerId,
        String origin,
        String destination,
        ContainerType containerType,
        int containerCount,
        BigDecimal totalAmount,
        String currency,
        LocalDate validUntil,
        QuoteStatus status,
        boolean expired
) {

    /**
     * Factory method that maps a domain {@link Quote} to an API response DTO.
     *
     * <p>Extracts nested value objects (PortCode, Money) into flat fields
     * suitable for JSON serialization.</p>
     *
     * @param quote the domain quote entity
     * @return a new {@code QuoteResponse} DTO
     */
    public static QuoteResponse from(Quote quote) {
        return new QuoteResponse(
                quote.getQuoteId(),
                quote.getCustomerId(),
                quote.getOrigin().value(),
                quote.getDestination().value(),
                quote.getContainerType(),
                quote.getContainerCount(),
                quote.getTotalPrice().amount(),
                quote.getTotalPrice().currency().getCurrencyCode(),
                quote.getValidUntil(),
                quote.getStatus(),
                quote.isExpired()
        );
    }
}
