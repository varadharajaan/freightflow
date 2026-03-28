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
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
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
    private final CacheManager cacheManager;

    /**
     * Constructor injection — all dependencies are final (Dependency Inversion Principle).
     *
     * @param bookingRepository the booking aggregate repository
     * @param eventStore        the event store for event sourcing
     * @param eventPublisher    the outbox-based event publisher
     * @param cacheManager      the Spring cache manager for programmatic cache eviction
     */
    public BookingCommandHandler(BookingRepository bookingRepository,
                                  EventStore eventStore,
                                  BookingEventPublisher eventPublisher,
                                  CacheManager cacheManager) {
        this.bookingRepository = Objects.requireNonNull(bookingRepository);
        this.eventStore = Objects.requireNonNull(eventStore);
        this.eventPublisher = Objects.requireNonNull(eventPublisher);
        this.cacheManager = Objects.requireNonNull(cacheManager, "CacheManager must not be null");
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
    @Profiled(value = "handleBookingCommand", slowThresholdMs = 500)
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
     *
     * <p><b>Cache contract:</b> Evicts the "customerBookings" cache region (all entries)
     * because a new booking changes the customer's booking list. The "bookings" cache
     * does not need eviction since this is a new entry not previously cached.</p>
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

        // Evict customerBookings cache — new booking changes the customer's list
        evictCustomerBookingsCache();

        log.info("Booking created: bookingId={}, customerId={}, status={}",
                saved.getId().asString(), cmd.customerId(), saved.getStatus());

        return saved;
    }

    /**
     * Handles booking confirmation: loads aggregate, transitions state, persists, publishes.
     *
     * <p><b>Cache contract:</b> Evicts the specific booking from the "bookings" cache
     * (keyed by bookingId) because the booking status has changed. Also evicts
     * "customerBookings" (all entries) because the customer's booking list view
     * includes status information.</p>
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

        // Evict caches — booking state changed
        evictBookingCache(cmd.bookingId());
        evictCustomerBookingsCache();

        log.info("Booking confirmed: bookingId={}, voyageId={}", cmd.bookingId(), cmd.voyageId());

        return saved;
    }

    /**
     * Handles booking cancellation: loads aggregate, cancels, persists, publishes.
     *
     * <p><b>Cache contract:</b> Evicts the specific booking from the "bookings" cache
     * (keyed by bookingId) because the booking status has changed to CANCELLED.
     * Also evicts "customerBookings" (all entries) because the customer's booking
     * list view includes status information.</p>
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

        // Evict caches — booking state changed
        evictBookingCache(cmd.bookingId());
        evictCustomerBookingsCache();

        log.info("Booking cancelled: bookingId={}, reason={}", cmd.bookingId(), cmd.reason());

        return saved;
    }

    // ==================== Cache Eviction Helpers ====================

    /**
     * Evicts a specific booking entry from the "bookings" cache.
     *
     * <p>Programmatic eviction is used instead of {@code @CacheEvict} annotations because
     * the command handler dispatches to private methods via {@link #handle(BookingCommand)},
     * and Spring AOP proxies do not intercept self-invocations.</p>
     *
     * @param bookingId the booking ID whose cache entry should be evicted
     */
    private void evictBookingCache(String bookingId) {
        var cache = cacheManager.getCache("bookings");
        if (cache != null) {
            cache.evict(bookingId);
            log.debug("Evicted cache: region=bookings, key={}", bookingId);
        }
    }

    /**
     * Evicts all entries from the "customerBookings" cache.
     *
     * <p>A full region eviction is necessary because booking mutations (create, confirm,
     * cancel) affect the customer's aggregated booking list. Fine-grained per-customer
     * eviction would require resolving the customer ID from the booking, which adds
     * coupling for marginal benefit.</p>
     */
    private void evictCustomerBookingsCache() {
        var cache = cacheManager.getCache("customerBookings");
        if (cache != null) {
            cache.clear();
            log.debug("Evicted cache: region=customerBookings, allEntries=true");
        }
    }

    private Booking loadBookingOrThrow(String bookingId) {
        return bookingRepository.findById(BookingId.fromString(bookingId))
                .orElseThrow(() -> {
                    log.warn("Booking not found: bookingId={}", bookingId);
                    return ResourceNotFoundException.forBooking(bookingId);
                });
    }
}
