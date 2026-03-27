package com.freightflow.booking.application.command;

import com.freightflow.booking.domain.event.BookingEvent;
import com.freightflow.booking.domain.model.Booking;
import com.freightflow.booking.domain.model.Cargo;
import com.freightflow.booking.domain.port.BookingEventPublisher;
import com.freightflow.booking.domain.port.BookingRepository;
import com.freightflow.booking.domain.port.EventStore;
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

import java.util.List;
import java.util.Objects;

/**
 * CQRS Command Handler — handles all write operations for the booking aggregate.
 *
 * <p>This is the <b>write side</b> of CQRS. It:</p>
 * <ol>
 *   <li>Receives a command (sealed type)</li>
 *   <li>Loads the aggregate (or creates a new one)</li>
 *   <li>Executes domain logic on the aggregate</li>
 *   <li>Persists the aggregate state to the bookings table</li>
 *   <li>Appends domain events to the event store</li>
 *   <li>Publishes events for downstream consumers</li>
 * </ol>
 *
 * <p>Uses Java 21 pattern matching with sealed command types for exhaustive handling.
 * Each command is dispatched via {@link #handle(BookingCommand)}.</p>
 *
 * <h3>Design Patterns</h3>
 * <ul>
 *   <li><b>Command Pattern</b> — commands are objects representing intentions</li>
 *   <li><b>CQRS</b> — write operations separated from read operations</li>
 *   <li><b>Event Sourcing</b> — events appended to event store as source of truth</li>
 * </ul>
 *
 * @see BookingCommand
 * @see com.freightflow.booking.application.query.BookingQueryHandler
 */
@Service
public class BookingCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(BookingCommandHandler.class);

    private final BookingRepository bookingRepository;
    private final EventStore eventStore;
    private final BookingEventPublisher eventPublisher;

    /**
     * Constructor injection — all dependencies are final (Dependency Inversion Principle).
     */
    public BookingCommandHandler(BookingRepository bookingRepository,
                                  EventStore eventStore,
                                  BookingEventPublisher eventPublisher) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository);
        this.eventStore = Objects.requireNonNull(eventStore);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
    }

    /**
     * Dispatches a command to the appropriate handler using Java 21 pattern matching.
     *
     * <p>The sealed {@link BookingCommand} hierarchy guarantees exhaustive matching —
     * the compiler enforces that all command types are handled.</p>
     *
     * @param command the booking command to handle
     * @return the affected booking
     */
    @Transactional
    public Booking handle(BookingCommand command) {
        log.debug("Handling command: type={}", command.getClass().getSimpleName());

        return switch (command) {
            case CreateBookingCommand cmd -> handleCreate(cmd);
            case ConfirmBookingCommand cmd -> handleConfirm(cmd);
            case CancelBookingCommand cmd -> handleCancel(cmd);
        };
    }

    /**
     * Handles booking creation: validates input, creates aggregate, persists, publishes events.
     */
    private Booking handleCreate(CreateBookingCommand cmd) {
        log.debug("Creating booking: customerId={}, route={}→{}, containers={}x{}",
                cmd.customerId(), cmd.origin(), cmd.destination(),
                cmd.containerCount(), cmd.containerType());

        Cargo cargo = new Cargo(
                cmd.commodityCode(),
                cmd.description(),
                Weight.ofKilograms(cmd.weightKg()),
                cmd.containerType(),
                cmd.containerCount(),
                PortCode.of(cmd.origin()),
                PortCode.of(cmd.destination())
        );

        Booking booking = Booking.create(
                CustomerId.fromString(cmd.customerId()),
                cargo,
                cmd.requestedDepartureDate()
        );

        // Persist aggregate state (write model)
        Booking saved = bookingRepository.save(booking);

        // Append events to event store (source of truth for Event Sourcing)
        List<BookingEvent> events = saved.pullDomainEvents();
        eventStore.append(saved.getId(), events, 0);

        // Publish events for downstream services and read model projections
        eventPublisher.publishAll(events);

        log.info("Booking created: bookingId={}, customerId={}, status={}",
                saved.getId().asString(), cmd.customerId(), saved.getStatus());

        return saved;
    }

    /**
     * Handles booking confirmation: loads aggregate, transitions state, persists, publishes.
     */
    private Booking handleConfirm(ConfirmBookingCommand cmd) {
        log.debug("Confirming booking: bookingId={}, voyageId={}", cmd.bookingId(), cmd.voyageId());

        Booking booking = loadBookingOrThrow(cmd.bookingId());
        long currentVersion = booking.getVersion();

        booking.confirm(VoyageId.fromString(cmd.voyageId()));

        Booking saved = bookingRepository.save(booking);
        List<BookingEvent> events = saved.pullDomainEvents();
        eventStore.append(saved.getId(), events, currentVersion);
        eventPublisher.publishAll(events);

        log.info("Booking confirmed: bookingId={}, voyageId={}", cmd.bookingId(), cmd.voyageId());

        return saved;
    }

    /**
     * Handles booking cancellation: loads aggregate, cancels, persists, publishes.
     */
    private Booking handleCancel(CancelBookingCommand cmd) {
        log.debug("Cancelling booking: bookingId={}, reason={}", cmd.bookingId(), cmd.reason());

        Booking booking = loadBookingOrThrow(cmd.bookingId());
        long currentVersion = booking.getVersion();

        booking.cancel(cmd.reason());

        Booking saved = bookingRepository.save(booking);
        List<BookingEvent> events = saved.pullDomainEvents();
        eventStore.append(saved.getId(), events, currentVersion);
        eventPublisher.publishAll(events);

        log.info("Booking cancelled: bookingId={}, reason={}", cmd.bookingId(), cmd.reason());

        return saved;
    }

    private Booking loadBookingOrThrow(String bookingId) {
        return bookingRepository.findById(BookingId.fromString(bookingId))
                .orElseThrow(() -> {
                    log.warn("Booking not found: bookingId={}", bookingId);
                    return ResourceNotFoundException.forBooking(bookingId);
                });
    }
}
