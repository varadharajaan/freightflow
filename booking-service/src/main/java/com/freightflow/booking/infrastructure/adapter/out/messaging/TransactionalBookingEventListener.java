package com.freightflow.booking.infrastructure.adapter.out.messaging;

import com.freightflow.booking.domain.event.BookingCancelled;
import com.freightflow.booking.domain.event.BookingConfirmed;
import com.freightflow.booking.domain.event.BookingCreated;
import com.freightflow.booking.domain.event.BookingEvent;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * Listens for {@link BookingEvent}s using Spring's {@code @TransactionalEventListener}
 * to ensure events are only processed <b>after</b> the originating transaction commits.
 *
 * <h3>{@code @TransactionalEventListener} vs {@code @EventListener}</h3>
 *
 * <table>
 *   <tr><th>Aspect</th><th>{@code @EventListener}</th><th>{@code @TransactionalEventListener}</th></tr>
 *   <tr>
 *     <td>Timing</td>
 *     <td>Fires <b>during</b> the transaction (synchronously within the same tx)</td>
 *     <td>Fires <b>after</b> the transaction commits (by default) or at other phases</td>
 *   </tr>
 *   <tr>
 *     <td>Rollback behavior</td>
 *     <td>If the listener throws, the entire transaction rolls back</td>
 *     <td>If the listener throws, only the listener fails — the original tx is already committed</td>
 *   </tr>
 *   <tr>
 *     <td>Side effects</td>
 *     <td>Dangerous for external calls (Kafka, email) — may fire even if tx rolls back</td>
 *     <td>Safe — guaranteed the data is persisted before the listener runs</td>
 *   </tr>
 *   <tr>
 *     <td>Use case</td>
 *     <td>In-memory cache invalidation, synchronous validations</td>
 *     <td>Sending Kafka messages, emails, webhooks, audit logging</td>
 *   </tr>
 * </table>
 *
 * <h3>Transaction Phases</h3>
 * <ul>
 *   <li>{@link TransactionPhase#AFTER_COMMIT} (default) — fires only if the transaction commits</li>
 *   <li>{@link TransactionPhase#AFTER_ROLLBACK} — fires only if the transaction rolls back</li>
 *   <li>{@link TransactionPhase#AFTER_COMPLETION} — fires after commit or rollback</li>
 *   <li>{@link TransactionPhase#BEFORE_COMMIT} — fires before commit, within the transaction</li>
 * </ul>
 *
 * <h3>Java 21 Pattern Matching</h3>
 * <p>This listener uses sealed interface pattern matching ({@code switch} on
 * {@link BookingEvent} subtypes) to exhaustively handle all event types with
 * compile-time safety. The sealed interface guarantees that all permitted subtypes
 * are covered — the compiler will warn if a new subtype is added to the sealed
 * hierarchy but not handled here.</p>
 *
 * @see BookingEvent
 * @see BookingCreated
 * @see BookingConfirmed
 * @see BookingCancelled
 */
@Component
public class TransactionalBookingEventListener {

    private static final Logger log = LoggerFactory.getLogger(TransactionalBookingEventListener.class);

    /**
     * Handles booking events after the originating transaction commits successfully.
     *
     * <p>This method is invoked by the Spring event infrastructure only when the
     * transaction that published the event via
     * {@link org.springframework.context.ApplicationEventPublisher#publishEvent(Object)}
     * has committed. If the transaction rolls back, this listener is never called.</p>
     *
     * <p>Uses Java 21 pattern matching switch on the sealed {@link BookingEvent}
     * interface to dispatch to type-specific handling logic. The compiler enforces
     * exhaustiveness — all permitted subtypes must be handled.</p>
     *
     * @param event the booking domain event (post-commit)
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    public void onBookingEvent(BookingEvent event) {
        log.info("Received post-commit booking event: type={}, bookingId={}, eventId={}",
                event.eventType(), event.bookingId().value(), event.eventId());

        switch (event) {
            case BookingCreated created -> handleBookingCreated(created);
            case BookingConfirmed confirmed -> handleBookingConfirmed(confirmed);
            case BookingCancelled cancelled -> handleBookingCancelled(cancelled);
        }
    }

    /**
     * Handles a newly created booking event.
     *
     * <p>Post-commit actions for booking creation: log for auditing, potentially
     * trigger downstream notifications or analytics updates.</p>
     *
     * @param event the booking created event
     */
    private void handleBookingCreated(BookingCreated event) {
        log.info("Booking created (post-commit): bookingId={}, customerId={}, route={}->{},"
                        + " containers={}, departureDate={}",
                event.bookingId().value(),
                event.customerId().value(),
                event.origin().value(),
                event.destination().value(),
                event.containerCount(),
                event.requestedDepartureDate());
    }

    /**
     * Handles a confirmed booking event.
     *
     * <p>Post-commit actions for booking confirmation: log for auditing, potentially
     * trigger voyage capacity updates or billing notifications.</p>
     *
     * @param event the booking confirmed event
     */
    private void handleBookingConfirmed(BookingConfirmed event) {
        log.info("Booking confirmed (post-commit): bookingId={}, customerId={}, voyageId={}",
                event.bookingId().value(),
                event.customerId().value(),
                event.voyageId().value());
    }

    /**
     * Handles a cancelled booking event.
     *
     * <p>Post-commit actions for booking cancellation: log for auditing, potentially
     * trigger refund processing or capacity release.</p>
     *
     * @param event the booking cancelled event
     */
    private void handleBookingCancelled(BookingCancelled event) {
        log.info("Booking cancelled (post-commit): bookingId={}, customerId={}, "
                        + "previousStatus={}, reason={}",
                event.bookingId().value(),
                event.customerId().value(),
                event.previousStatus(),
                event.reason());
    }
}
