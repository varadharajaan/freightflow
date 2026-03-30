package com.freightflow.booking.infrastructure.adapter.in.rest;

import com.freightflow.booking.application.QuoteService;
import com.freightflow.booking.domain.model.Quote;
import com.freightflow.booking.infrastructure.adapter.in.rest.dto.QuoteRequest;
import com.freightflow.booking.infrastructure.adapter.in.rest.dto.QuoteResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;
import java.util.UUID;

/**
 * REST controller for generating and retrieving freight shipping quotes.
 *
 * <p>This is an inbound adapter in the Hexagonal Architecture, translating
 * HTTP requests into application-layer calls and mapping domain objects to REST DTOs.
 * All business logic is delegated to {@link QuoteService}.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/v1/quotes}           — generate a new freight quote</li>
 *   <li>{@code GET  /api/v1/quotes/{quoteId}} — retrieve a quote by ID</li>
 * </ul>
 *
 * @see QuoteService
 * @see QuoteResponse
 */
@RestController
@RequestMapping("/api/v1/quotes")
public class QuoteController {

    private static final Logger log = LoggerFactory.getLogger(QuoteController.class);

    private final QuoteService quoteService;

    /**
     * Creates a new {@code QuoteController} with the required application service.
     *
     * @param quoteService the quote application service (must not be null)
     */
    public QuoteController(QuoteService quoteService) {
        this.quoteService = Objects.requireNonNull(quoteService, "QuoteService must not be null");
    }

    /**
     * Generates a new freight shipping quote.
     *
     * <p>The quote is generated asynchronously — the response is {@code 202 Accepted}
     * to indicate the request has been received and processed. The returned
     * {@link QuoteResponse} contains the calculated price and validity period.</p>
     *
     * @param request the quote generation request (validated via Bean Validation)
     * @return 202 Accepted with the generated quote
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'CUSTOMER')")
    @PostMapping
    public ResponseEntity<QuoteResponse> generateQuote(@Valid @RequestBody QuoteRequest request) {
        log.debug("POST /api/v1/quotes — generating quote: customerId={}, route={}→{}, containers={}x{}",
                request.customerId(), request.origin(), request.destination(),
                request.containerCount(), request.containerType());

        Quote quote = quoteService.generateQuote(
                request.origin(),
                request.destination(),
                request.containerType(),
                request.containerCount(),
                request.customerId()
        );

        QuoteResponse response = QuoteResponse.from(quote);

        log.info("Quote generated successfully: quoteId={}, customerId={}, price={} {}",
                response.quoteId(), response.customerId(), response.totalAmount(), response.currency());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Retrieves a quote by its unique identifier.
     *
     * @param quoteId the quote UUID (path variable)
     * @return 200 OK with the quote details
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'CUSTOMER')")
    @GetMapping("/{quoteId}")
    public ResponseEntity<QuoteResponse> getQuote(@PathVariable UUID quoteId) {
        log.debug("GET /api/v1/quotes/{} — fetching quote", quoteId);

        Quote quote = quoteService.getQuote(quoteId);
        QuoteResponse response = QuoteResponse.from(quote);

        log.info("Quote retrieved: quoteId={}, status={}", response.quoteId(), response.status());

        return ResponseEntity.ok(response);
    }
}
