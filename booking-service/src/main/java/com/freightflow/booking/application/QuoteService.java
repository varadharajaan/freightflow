package com.freightflow.booking.application;

import com.freightflow.booking.domain.model.ContainerType;
import com.freightflow.booking.domain.model.Quote;
import com.freightflow.commons.domain.Money;
import com.freightflow.commons.domain.PortCode;
import com.freightflow.commons.exception.ResourceNotFoundException;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.LocalDate;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service for generating and retrieving freight shipping quotes.
 *
 * <p>This service calculates estimated shipping costs based on a simplified pricing model
 * that considers the base rate per TEU and a distance factor derived from the origin
 * and destination ports. Quotes are valid for a configurable period (default 30 days).</p>
 *
 * <h3>Pricing Model</h3>
 * <p>The total price is calculated as:</p>
 * <pre>
 *   totalPrice = baseRatePerTeu × teuFactor × containerCount × distanceFactor
 * </pre>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li>Quotes are stored in-memory for simplicity — a production system would persist to a database</li>
 *   <li>The pricing model is intentionally simplified for demonstration purposes</li>
 *   <li>Thread-safe via {@link ConcurrentHashMap}</li>
 * </ul>
 *
 * @see Quote
 */
@Service
@Profiled(value = "QuoteService", slowThresholdMs = 1000)
public class QuoteService {

    private static final Logger log = LoggerFactory.getLogger(QuoteService.class);

    /** Base rate per TEU in USD — simplified pricing baseline. */
    private static final BigDecimal BASE_RATE_PER_TEU = new BigDecimal("1500.00");

    /** Default quote validity period in days. */
    private static final int QUOTE_VALIDITY_DAYS = 30;

    /** Default currency for all quotes. */
    private static final String DEFAULT_CURRENCY = "USD";

    /**
     * Simplified distance factors for common trade lanes.
     * In production, this would be backed by a rate management system.
     */
    private static final Map<String, BigDecimal> DISTANCE_FACTORS = Map.of(
            "CNSHA-USLAX", new BigDecimal("2.50"),
            "CNSHA-DEHAM", new BigDecimal("2.20"),
            "DEHAM-USLAX", new BigDecimal("1.80"),
            "USLAX-CNSHA", new BigDecimal("2.50"),
            "DEHAM-CNSHA", new BigDecimal("2.20"),
            "USLAX-DEHAM", new BigDecimal("1.80"),
            "SGSIN-DEHAM", new BigDecimal("2.10"),
            "GBFXT-USLAX", new BigDecimal("1.50")
    );

    /** Default distance factor for unknown trade lanes. */
    private static final BigDecimal DEFAULT_DISTANCE_FACTOR = new BigDecimal("2.00");

    /** In-memory quote store — production would use a repository port. */
    private final ConcurrentHashMap<UUID, Quote> quoteStore = new ConcurrentHashMap<>();

    /**
     * Creates a new {@code QuoteService}.
     *
     * <p>Constructor injection with no external dependencies beyond configuration.
     * In a production system, this would accept a {@code QuoteRepository} port.</p>
     */
    public QuoteService() {
        // No external dependencies required for simplified implementation
    }

    // ==================== Commands ====================

    /**
     * Generates a freight shipping quote based on the provided parameters.
     *
     * <p>Calculates the estimated cost using the simplified pricing model and stores
     * the quote for later retrieval or conversion to a booking.</p>
     *
     * @param origin         the origin port code (UN/LOCODE)
     * @param destination    the destination port code (UN/LOCODE)
     * @param containerType  the type of container required
     * @param containerCount the number of containers needed
     * @param customerId     the ID of the customer requesting the quote
     * @return the generated quote with calculated pricing
     * @throws IllegalArgumentException if any parameter is invalid
     */
    @Profiled(value = "generateQuote", slowThresholdMs = 500)
    public Quote generateQuote(String origin, String destination, ContainerType containerType,
                                int containerCount, String customerId) {
        Objects.requireNonNull(origin, "Origin must not be null");
        Objects.requireNonNull(destination, "Destination must not be null");
        Objects.requireNonNull(containerType, "Container type must not be null");
        Objects.requireNonNull(customerId, "Customer ID must not be null");

        log.debug("Generating quote: customerId={}, route={}→{}, containers={}x{}",
                customerId, origin, destination, containerCount, containerType);

        PortCode originPort = PortCode.of(origin);
        PortCode destinationPort = PortCode.of(destination);

        Money totalPrice = calculatePrice(originPort, destinationPort, containerType, containerCount);
        LocalDate validUntil = LocalDate.now().plusDays(QUOTE_VALIDITY_DAYS);

        Quote quote = Quote.generate(customerId, originPort, destinationPort,
                containerType, containerCount, totalPrice, validUntil);

        quoteStore.put(quote.getQuoteId(), quote);

        log.info("Quote generated: quoteId={}, customerId={}, route={}→{}, price={}, validUntil={}",
                quote.getQuoteId(), customerId, origin, destination, totalPrice, validUntil);

        return quote;
    }

    // ==================== Queries ====================

    /**
     * Retrieves a quote by its unique identifier.
     *
     * @param quoteId the quote UUID
     * @return the quote
     * @throws ResourceNotFoundException if the quote does not exist
     */
    @Profiled(value = "getQuote", slowThresholdMs = 200)
    public Quote getQuote(UUID quoteId) {
        Objects.requireNonNull(quoteId, "Quote ID must not be null");

        log.debug("Fetching quote: quoteId={}", quoteId);

        Quote quote = quoteStore.get(quoteId);
        if (quote == null) {
            log.warn("Quote not found: quoteId={}", quoteId);
            throw ResourceNotFoundException.forResource("Quote", quoteId.toString());
        }

        log.info("Quote retrieved: quoteId={}, status={}, expired={}",
                quoteId, quote.getStatus(), quote.isExpired());

        return quote;
    }

    // ==================== Private Helpers ====================

    /**
     * Calculates the total shipping price using the simplified pricing model.
     *
     * <p>Formula: baseRatePerTeu × teuFactor × containerCount × distanceFactor</p>
     *
     * @param origin         the origin port
     * @param destination    the destination port
     * @param containerType  the container type (determines TEU factor)
     * @param containerCount the number of containers
     * @return the calculated total price
     */
    private Money calculatePrice(PortCode origin, PortCode destination,
                                  ContainerType containerType, int containerCount) {
        String tradeLane = origin.value() + "-" + destination.value();
        BigDecimal distanceFactor = DISTANCE_FACTORS.getOrDefault(tradeLane, DEFAULT_DISTANCE_FACTOR);
        BigDecimal teuFactor = BigDecimal.valueOf(containerType.teuFactor());

        BigDecimal totalAmount = BASE_RATE_PER_TEU
                .multiply(teuFactor)
                .multiply(BigDecimal.valueOf(containerCount))
                .multiply(distanceFactor)
                .setScale(2, RoundingMode.HALF_UP);

        log.debug("Price calculated: tradeLane={}, distanceFactor={}, teuFactor={}, total={}",
                tradeLane, distanceFactor, teuFactor, totalAmount);

        return Money.of(totalAmount, DEFAULT_CURRENCY);
    }
}
