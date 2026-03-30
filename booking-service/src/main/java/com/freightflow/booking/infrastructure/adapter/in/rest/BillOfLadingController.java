package com.freightflow.booking.infrastructure.adapter.in.rest;

import com.freightflow.booking.application.BillOfLadingService;
import com.freightflow.booking.domain.model.BillOfLading;
import com.freightflow.booking.infrastructure.adapter.in.rest.dto.BillOfLadingResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Objects;

/**
 * REST controller for issuing and retrieving Bills of Lading.
 *
 * <p>This is an inbound adapter in the Hexagonal Architecture, translating
 * HTTP requests into application-layer calls. All business logic — including
 * validation that the booking is in SHIPPED status — is delegated to
 * {@link BillOfLadingService}.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST /api/v1/bookings/{bookingId}/bill-of-lading} — issue a B/L for a booking</li>
 *   <li>{@code GET  /api/v1/bookings/{bookingId}/bill-of-lading} — retrieve the B/L for a booking</li>
 * </ul>
 *
 * <h3>Security</h3>
 * <p>All endpoints require ADMIN or OPERATOR role — B/L issuance is an operational
 * action that should not be performed by customers directly.</p>
 *
 * @see BillOfLadingService
 * @see BillOfLadingResponse
 */
@RestController
@RequestMapping("/api/v1/bookings/{bookingId}/bill-of-lading")
public class BillOfLadingController {

    private static final Logger log = LoggerFactory.getLogger(BillOfLadingController.class);

    private final BillOfLadingService billOfLadingService;

    /**
     * Creates a new {@code BillOfLadingController} with the required application service.
     *
     * @param billOfLadingService the B/L application service (must not be null)
     */
    public BillOfLadingController(BillOfLadingService billOfLadingService) {
        this.billOfLadingService = Objects.requireNonNull(billOfLadingService,
                "BillOfLadingService must not be null");
    }

    /**
     * Issues a Bill of Lading for the specified booking.
     *
     * <p>The booking must be in SHIPPED status. If a B/L already exists for the booking,
     * the existing B/L is returned (idempotent operation).</p>
     *
     * @param bookingId the booking UUID (path variable)
     * @return 201 Created with the issued Bill of Lading
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @PostMapping
    public ResponseEntity<BillOfLadingResponse> issueBillOfLading(@PathVariable String bookingId) {
        log.debug("POST /api/v1/bookings/{}/bill-of-lading — issuing B/L", bookingId);

        BillOfLading bol = billOfLadingService.issueBillOfLading(bookingId);
        BillOfLadingResponse response = BillOfLadingResponse.from(bol);

        log.info("B/L issued: bolNumber={}, bookingId={}", response.bolNumber(), bookingId);

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves the Bill of Lading for the specified booking.
     *
     * @param bookingId the booking UUID (path variable)
     * @return 200 OK with the Bill of Lading details
     */
    @PreAuthorize("hasAnyRole('ADMIN', 'OPERATOR')")
    @GetMapping
    public ResponseEntity<BillOfLadingResponse> getBillOfLading(@PathVariable String bookingId) {
        log.debug("GET /api/v1/bookings/{}/bill-of-lading — fetching B/L", bookingId);

        BillOfLading bol = billOfLadingService.getBillOfLading(bookingId);
        BillOfLadingResponse response = BillOfLadingResponse.from(bol);

        log.info("B/L retrieved: bolNumber={}, bookingId={}, status={}",
                response.bolNumber(), bookingId, response.status());

        return ResponseEntity.ok(response);
    }
}
