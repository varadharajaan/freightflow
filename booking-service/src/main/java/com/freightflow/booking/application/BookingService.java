package com.freightflow.booking.application;

import com.freightflow.booking.domain.model.Booking;
import com.freightflow.booking.domain.model.Cargo;
import com.freightflow.booking.domain.model.ContainerType;
import com.freightflow.booking.domain.port.BookingEventPublisher;
import com.freightflow.booking.domain.port.BookingRepository;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.PortCode;
import com.freightflow.commons.domain.VoyageId;
import com.freightflow.commons.domain.Weight;
import com.freightflow.commons.exception.ResourceNotFoundException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;

/**
 * Application service orchestrating booking use cases.
 *
 * <p>This is the application layer in Hexagonal Architecture — it coordinates
 * between the domain model and the infrastructure adapters. It does NOT contain
 * business logic (that belongs in the domain), but it does handle:</p>
 * <ul>
 *   <li>Transaction management ({@code @Transactional})</li>
 *   <li>Domain event publishing (after persistence)</li>
 *   <li>Input transformation (commands → domain objects)</li>
 *   <li>Logging at business-significant boundaries</li>
 * </ul>
 *
 * <p>Follows Single Responsibility Principle: this service is the ONLY entry point
 * for booking operations. Controllers and Kafka consumers call this, not the
 * repository directly.</p>
 */
@Service
public class BookingService {

    private static final Logger log = LoggerFactory.getLogger(BookingService.class);

    private final BookingRepository bookingRepository;
    private final BookingEventPublisher eventPublisher;

    /**
     * Constructor injection — no field injection, all dependencies are final.
     * Follows Dependency Inversion Principle: depends on port interfaces, not implementations.
     */
    public BookingService(BookingRepository bookingRepository,
                          BookingEventPublisher eventPublisher) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository, "BookingRepository must not be null");
        this.eventPublisher = Objects.requireNonNull(eventPublisher, "BookingEventPublisher must not be null");
    }

    // ==================== Commands (Write Operations) ====================

    /**
     * Creates a new booking in DRAFT status.
     *
     * @param command the creation command
     * @return the created booking
     */
    @Transactional
    public Booking createBooking(CreateBookingCommand command) {
        log.debug("Creating booking: customerId={}, route={}→{}, containers={}x{}",
                command.customerId(), command.origin(), command.destination(),
                command.containerCount(), command.containerType());

        Cargo cargo = new Cargo(
                command.commodityCode(),
                command.description(),
                Weight.ofKilograms(command.weightKg()),
                command.containerType(),
                command.containerCount(),
                PortCode.of(command.origin()),
                PortCode.of(command.destination())
        );

        Booking booking = Booking.create(
                CustomerId.fromString(command.customerId()),
                cargo,
                command.requestedDepartureDate()
        );

        Booking saved = bookingRepository.save(booking);
        eventPublisher.publishAll(saved.pullDomainEvents());

        log.info("Booking created: bookingId={}, customerId={}, route={}→{}, status={}",
                saved.getId().asString(), command.customerId(),
                command.origin(), command.destination(), saved.getStatus());

        return saved;
    }

    /**
     * Confirms a booking and assigns it to a voyage.
     *
     * @param bookingId the booking to confirm
     * @param voyageId  the assigned voyage
     * @return the confirmed booking
     * @throws ResourceNotFoundException if the booking does not exist
     */
    @Transactional
    public Booking confirmBooking(String bookingId, String voyageId) {
        log.debug("Confirming booking: bookingId={}, voyageId={}", bookingId, voyageId);

        Booking booking = findBookingOrThrow(bookingId);
        booking.confirm(VoyageId.fromString(voyageId));

        Booking saved = bookingRepository.save(booking);
        eventPublisher.publishAll(saved.pullDomainEvents());

        log.info("Booking confirmed: bookingId={}, voyageId={}, status={}",
                bookingId, voyageId, saved.getStatus());

        return saved;
    }

    /**
     * Cancels a booking with a reason.
     *
     * @param bookingId the booking to cancel
     * @param reason    the cancellation reason
     * @return the cancelled booking
     * @throws ResourceNotFoundException if the booking does not exist
     */
    @Transactional
    public Booking cancelBooking(String bookingId, String reason) {
        log.debug("Cancelling booking: bookingId={}, reason={}", bookingId, reason);

        Booking booking = findBookingOrThrow(bookingId);
        booking.cancel(reason);

        Booking saved = bookingRepository.save(booking);
        eventPublisher.publishAll(saved.pullDomainEvents());

        log.info("Booking cancelled: bookingId={}, reason={}, previousStatus={}",
                bookingId, reason, saved.getStatus());

        return saved;
    }

    // ==================== Queries (Read Operations) ====================

    /**
     * Retrieves a booking by its ID.
     *
     * @param bookingId the booking identifier
     * @return the booking
     * @throws ResourceNotFoundException if not found
     */
    @Transactional(readOnly = true)
    public Booking getBooking(String bookingId) {
        log.debug("Fetching booking: bookingId={}", bookingId);
        return findBookingOrThrow(bookingId);
    }

    /**
     * Lists all bookings for a customer.
     *
     * @param customerId the customer identifier
     * @return list of bookings
     */
    @Transactional(readOnly = true)
    public List<Booking> getBookingsByCustomer(String customerId) {
        log.debug("Fetching bookings for customer: customerId={}", customerId);
        return bookingRepository.findByCustomerId(CustomerId.fromString(customerId));
    }

    // ==================== Private Helpers ====================

    private Booking findBookingOrThrow(String bookingId) {
        return bookingRepository.findById(BookingId.fromString(bookingId))
                .orElseThrow(() -> {
                    log.warn("Booking not found: bookingId={}", bookingId);
                    return ResourceNotFoundException.forBooking(bookingId);
                });
    }

    // ==================== Command Records ====================

    /**
     * Command to create a new booking. Validated at the REST layer via Bean Validation.
     */
    public record CreateBookingCommand(
            String customerId,
            String origin,
            String destination,
            String commodityCode,
            String description,
            BigDecimal weightKg,
            ContainerType containerType,
            int containerCount,
            LocalDate requestedDepartureDate
    ) {}
}
