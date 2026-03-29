package com.freightflow.booking.infrastructure.adapter.in.rest;

import com.freightflow.booking.application.BookingService;
import com.freightflow.booking.application.saga.BookingConfirmationSaga;
import com.freightflow.booking.application.saga.SagaExecution;
import com.freightflow.booking.domain.model.Booking;
import com.freightflow.booking.infrastructure.adapter.in.rest.dto.BookingResponse;
import com.freightflow.booking.infrastructure.adapter.in.rest.dto.CancelBookingRequest;
import com.freightflow.booking.infrastructure.adapter.in.rest.dto.ConfirmBookingRequest;
import com.freightflow.booking.infrastructure.adapter.in.rest.dto.CreateBookingRequest;
import com.freightflow.booking.infrastructure.adapter.in.rest.dto.SagaExecutionResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationToken;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;

/**
 * REST controller for managing freight bookings.
 *
 * <p>This is the primary inbound adapter in the Hexagonal Architecture, translating
 * HTTP requests into application-layer commands and queries. It delegates all business
 * logic to {@link BookingService} and maps domain objects to REST DTOs.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST   /api/v1/bookings}               — create a new booking</li>
 *   <li>{@code GET    /api/v1/bookings/{bookingId}}    — retrieve a booking</li>
 *   <li>{@code POST   /api/v1/bookings/{bookingId}/confirm} — confirm a booking</li>
 *   <li>{@code POST   /api/v1/bookings/{bookingId}/confirm-saga} — confirm via saga orchestration</li>
 *   <li>{@code DELETE  /api/v1/bookings/{bookingId}}   — cancel a booking</li>
 *   <li>{@code GET    /api/v1/bookings?customerId={id}} — list bookings by customer</li>
 * </ul>
 *
 * @see BookingService
 * @see BookingResponse
 */
@RestController
@RequestMapping("/api/v1/bookings")
public class BookingController {

    private static final Logger log = LoggerFactory.getLogger(BookingController.class);

    private final BookingService bookingService;
    private final BookingConfirmationSaga bookingConfirmationSaga;

    /**
     * Creates a new {@code BookingController} with the required application services.
     *
     * @param bookingService           the booking application service (must not be null)
     * @param bookingConfirmationSaga  the saga orchestrator for distributed booking confirmation (must not be null)
     */
    public BookingController(BookingService bookingService,
                             BookingConfirmationSaga bookingConfirmationSaga) {
        this.bookingService = Objects.requireNonNull(bookingService, "BookingService must not be null");
        this.bookingConfirmationSaga = Objects.requireNonNull(bookingConfirmationSaga,
                "BookingConfirmationSaga must not be null");
    }

    /**
     * Creates a new booking in DRAFT status.
     *
     * <p>The booking is created asynchronously — the response is {@code 202 Accepted}
     * to indicate the request has been received and will be processed. The returned
     * {@link BookingResponse} contains the generated booking ID and initial state.</p>
     *
     * @param request the booking creation request (validated via Bean Validation)
     * @return 202 Accepted with the created booking
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'CUSTOMER')")
    @PostMapping
    public ResponseEntity<BookingResponse> createBooking(
            @Valid @RequestBody CreateBookingRequest request,
            Authentication authentication) {
        log.debug("POST /api/v1/bookings — creating booking: customerId={}, route={}→{}",
                request.customerId(), request.origin(), request.destination());
        enforceCustomerSelfScope(authentication, request.customerId(), "create booking");

        Booking booking = bookingService.createBooking(request.toCommand());
        BookingResponse response = BookingResponse.from(booking);

        log.info("Booking created successfully: bookingId={}, customerId={}",
                response.bookingId(), response.customerId());

        return ResponseEntity.status(HttpStatus.ACCEPTED).body(response);
    }

    /**
     * Retrieves a booking by its unique identifier.
     *
     * @param bookingId the booking UUID (path variable)
     * @return 200 OK with the booking details
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'CUSTOMER')")
    @GetMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> getBooking(
            @PathVariable String bookingId,
            Authentication authentication) {
        log.debug("GET /api/v1/bookings/{} — fetching booking", bookingId);

        Booking booking = bookingService.getBooking(bookingId);
        enforceCustomerSelfScope(authentication, booking.getCustomerId().asString(), "retrieve booking");
        BookingResponse response = BookingResponse.from(booking);

        log.info("Booking retrieved: bookingId={}, status={}", response.bookingId(), response.status());

        return ResponseEntity.ok(response);
    }

    /**
     * Confirms a booking and assigns it to a voyage.
     *
     * <p>Transitions the booking from DRAFT to CONFIRMED status. A voyage ID
     * must be provided to indicate which sailing the booking is allocated to.</p>
     *
     * @param bookingId the booking UUID (path variable)
     * @param request   the confirmation request containing the voyage ID
     * @return 200 OK with the confirmed booking
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PostMapping("/{bookingId}/confirm")
    public ResponseEntity<BookingResponse> confirmBooking(
            @PathVariable String bookingId,
            @Valid @RequestBody ConfirmBookingRequest request) {

        log.debug("POST /api/v1/bookings/{}/confirm — confirming with voyageId={}",
                bookingId, request.voyageId());

        Booking booking = bookingService.confirmBooking(bookingId, request.voyageId());
        BookingResponse response = BookingResponse.from(booking);

        log.info("Booking confirmed: bookingId={}, voyageId={}", response.bookingId(), response.voyageId());

        return ResponseEntity.ok(response);
    }

    /**
     * Cancels a booking with a reason.
     *
     * <p>Transitions the booking to CANCELLED status. Bookings in DRAFT or CONFIRMED
     * status can be cancelled. A cancellation reason must be provided for audit purposes.</p>
     *
     * @param bookingId the booking UUID (path variable)
     * @param request   the cancellation request containing the reason
     * @return 200 OK with the cancelled booking
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'CUSTOMER')")
    @DeleteMapping("/{bookingId}")
    public ResponseEntity<BookingResponse> cancelBooking(
            @PathVariable String bookingId,
            @Valid @RequestBody CancelBookingRequest request,
            Authentication authentication) {

        log.debug("DELETE /api/v1/bookings/{} — cancelling with reason='{}'",
                bookingId, request.reason());

        Booking existing = bookingService.getBooking(bookingId);
        enforceCustomerSelfScope(authentication, existing.getCustomerId().asString(), "cancel booking");

        Booking booking = bookingService.cancelBooking(bookingId, request.reason());
        BookingResponse response = BookingResponse.from(booking);

        log.info("Booking cancelled: bookingId={}, reason='{}'", response.bookingId(), request.reason());

        return ResponseEntity.ok(response);
    }

    /**
     * Lists all bookings for a given customer.
     *
     * @param customerId the customer UUID (query parameter)
     * @return 200 OK with a list of bookings (may be empty)
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR', 'CUSTOMER')")
    @GetMapping
    public ResponseEntity<List<BookingResponse>> getBookingsByCustomer(
            @RequestParam String customerId,
            Authentication authentication) {

        log.debug("GET /api/v1/bookings?customerId={} — listing customer bookings", customerId);
        enforceCustomerSelfScope(authentication, customerId, "list bookings");

        List<BookingResponse> responses = bookingService.getBookingsByCustomer(customerId)
                .stream()
                .map(BookingResponse::from)
                .toList();

        log.info("Retrieved {} bookings for customerId={}", responses.size(), customerId);

        return ResponseEntity.ok(responses);
    }

    /**
     * Confirms a booking via the Saga Orchestration pattern.
     *
     * <p>Executes a distributed transaction spanning multiple microservices:
     * booking confirmation, vessel capacity reservation, invoice generation,
     * and notification. If any step fails, compensating transactions are
     * executed to maintain data consistency across services.</p>
     *
     * <p>This endpoint is idempotent — the same {@code Idempotency-Key} header
     * will return the same result without re-executing the saga.</p>
     *
     * @param bookingId      the booking UUID (path variable)
     * @param request        the confirmation request containing the voyage ID
     * @param idempotencyKey the caller-provided idempotency key (required header)
     * @param authentication the current security context
     * @return 200 OK with the saga execution result
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PostMapping("/{bookingId}/confirm-saga")
    public ResponseEntity<SagaExecutionResponse> confirmBookingViaSaga(
            @PathVariable String bookingId,
            @Valid @RequestBody ConfirmBookingRequest request,
            @RequestHeader(name = "Idempotency-Key") String idempotencyKey,
            Authentication authentication) {

        log.debug("POST /api/v1/bookings/{}/confirm-saga — saga confirmation with voyageId={}, idempotencyKey={}",
                bookingId, request.voyageId(), idempotencyKey);

        SagaExecution saga = bookingConfirmationSaga.execute(bookingId, request.voyageId(), idempotencyKey);
        SagaExecutionResponse response = SagaExecutionResponse.from(saga);

        log.info("Saga execution result: sagaId={}, bookingId={}, status={}",
                response.sagaId(), response.bookingId(), response.status());

        return ResponseEntity.ok(response);
    }

    private void enforceCustomerSelfScope(
            Authentication authentication,
            String targetCustomerId,
            String action) {
        if (authentication == null || isAdminOrOperator(authentication)) {
            return;
        }
        if (!hasRole(authentication, "CUSTOMER")) {
            return;
        }

        String principalCustomerId = resolvePrincipalCustomerId(authentication);
        if (!targetCustomerId.equals(principalCustomerId)) {
            log.warn("Access denied for action='{}': principal='{}' attempted customerId='{}'",
                    action, principalCustomerId, targetCustomerId);
            throw new AccessDeniedException("Customers can only access their own bookings");
        }
    }

    private boolean isAdminOrOperator(Authentication authentication) {
        return hasRole(authentication, "ADMIN") || hasRole(authentication, "OPERATOR");
    }

    private boolean hasRole(Authentication authentication, String role) {
        String authority = "ROLE_" + role;
        return authentication.getAuthorities().stream()
                .anyMatch(granted -> authority.equals(granted.getAuthority()));
    }

    private String resolvePrincipalCustomerId(Authentication authentication) {
        if (authentication instanceof JwtAuthenticationToken jwtAuthenticationToken) {
            String subject = jwtAuthenticationToken.getToken().getSubject();
            if (subject != null && !subject.isBlank()) {
                return subject;
            }
        }
        return authentication.getName();
    }
}
