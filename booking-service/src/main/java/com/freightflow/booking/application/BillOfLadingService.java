package com.freightflow.booking.application;

import com.freightflow.booking.domain.model.BillOfLading;
import com.freightflow.booking.domain.model.Booking;
import com.freightflow.booking.domain.model.BookingStatus;
import com.freightflow.booking.domain.port.BookingRepository;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.exception.ResourceNotFoundException;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Application service for issuing and retrieving Bills of Lading.
 *
 * <p>A Bill of Lading (B/L) is the most critical document in international shipping,
 * serving as a receipt of goods, contract of carriage, and document of title.
 * This service orchestrates B/L issuance by validating that the associated booking
 * is in the correct state (SHIPPED) before generating the document.</p>
 *
 * <h3>Business Rules</h3>
 * <ul>
 *   <li>A B/L can only be issued for a booking in SHIPPED status</li>
 *   <li>Each booking can have at most one B/L</li>
 *   <li>The B/L captures the voyage and vessel information at the time of issuance</li>
 * </ul>
 *
 * @see BillOfLading
 * @see Booking
 */
@Service
@Profiled(value = "BillOfLadingService", slowThresholdMs = 1000)
public class BillOfLadingService {

    private static final Logger log = LoggerFactory.getLogger(BillOfLadingService.class);

    private final BookingRepository bookingRepository;

    /** In-memory B/L store indexed by bookingId — production would use a repository port. */
    private final ConcurrentHashMap<String, BillOfLading> bolStore = new ConcurrentHashMap<>();

    /**
     * Creates a new {@code BillOfLadingService} with the required dependencies.
     *
     * @param bookingRepository the booking repository for loading booking aggregates (must not be null)
     */
    public BillOfLadingService(BookingRepository bookingRepository) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository,
                "BookingRepository must not be null");
    }

    // ==================== Commands ====================

    /**
     * Issues a Bill of Lading for the specified booking.
     *
     * <p>The booking must be in {@link BookingStatus#SHIPPED} status. The B/L is generated
     * with vessel and voyage information extracted from the booking. If a B/L already
     * exists for this booking, the existing one is returned.</p>
     *
     * @param bookingId the booking identifier
     * @return the issued Bill of Lading
     * @throws ResourceNotFoundException if the booking does not exist
     * @throws IllegalArgumentException  if the booking is not in SHIPPED status
     */
    @Profiled(value = "issueBillOfLading", slowThresholdMs = 500)
    public BillOfLading issueBillOfLading(String bookingId) {
        Objects.requireNonNull(bookingId, "Booking ID must not be null");

        log.debug("Issuing Bill of Lading: bookingId={}", bookingId);

        // Check if B/L already exists for this booking
        BillOfLading existing = bolStore.get(bookingId);
        if (existing != null) {
            log.info("B/L already exists for booking: bookingId={}, bolNumber={}",
                    bookingId, existing.getBolNumber());
            return existing;
        }

        Booking booking = findBookingOrThrow(bookingId);

        if (booking.getStatus() != BookingStatus.SHIPPED) {
            throw new IllegalArgumentException(
                    "Cannot issue B/L for booking %s: status is %s, expected SHIPPED".formatted(
                            bookingId, booking.getStatus()));
        }

        // Extract voyage info — use voyage ID as voyage number if present
        String voyageNumber = booking.getVoyageId()
                .map(v -> v.asString())
                .orElse("UNKNOWN");

        BillOfLading bol = BillOfLading.issue(booking, "FreightFlow Vessel", voyageNumber);

        bolStore.put(bookingId, bol);

        log.info("Bill of Lading issued: bolId={}, bolNumber={}, bookingId={}, route={}→{}",
                bol.getBolId(), bol.getBolNumber(), bookingId,
                bol.getPortOfLoading(), bol.getPortOfDischarge());

        return bol;
    }

    // ==================== Queries ====================

    /**
     * Retrieves the Bill of Lading for a booking.
     *
     * @param bookingId the booking identifier
     * @return the Bill of Lading
     * @throws ResourceNotFoundException if no B/L exists for the booking
     */
    @Profiled(value = "getBillOfLading", slowThresholdMs = 200)
    public BillOfLading getBillOfLading(String bookingId) {
        Objects.requireNonNull(bookingId, "Booking ID must not be null");

        log.debug("Fetching Bill of Lading: bookingId={}", bookingId);

        BillOfLading bol = bolStore.get(bookingId);
        if (bol == null) {
            log.warn("Bill of Lading not found for booking: bookingId={}", bookingId);
            throw ResourceNotFoundException.forResource("BillOfLading", bookingId);
        }

        log.info("Bill of Lading retrieved: bolNumber={}, bookingId={}, status={}",
                bol.getBolNumber(), bookingId, bol.getStatus());

        return bol;
    }

    // ==================== Private Helpers ====================

    /**
     * Loads a booking or throws if not found.
     *
     * @param bookingId the booking identifier
     * @return the booking
     * @throws ResourceNotFoundException if not found
     */
    private Booking findBookingOrThrow(String bookingId) {
        return bookingRepository.findById(BookingId.fromString(bookingId))
                .orElseThrow(() -> {
                    log.warn("Booking not found: bookingId={}", bookingId);
                    return ResourceNotFoundException.forBooking(bookingId);
                });
    }
}
