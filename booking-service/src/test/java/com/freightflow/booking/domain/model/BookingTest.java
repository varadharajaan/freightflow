package com.freightflow.booking.domain.model;

import com.freightflow.booking.domain.event.BookingCancelled;
import com.freightflow.booking.domain.event.BookingConfirmed;
import com.freightflow.booking.domain.event.BookingCreated;
import com.freightflow.booking.domain.event.BookingEvent;
import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.PortCode;
import com.freightflow.commons.domain.VoyageId;
import com.freightflow.commons.domain.Weight;
import com.freightflow.commons.exception.ConflictException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Unit tests for the {@link Booking} aggregate root.
 *
 * <p>Tests cover creation, state transitions, domain event emission, and invariant
 * enforcement. All tests follow BDD naming: {@code should_X_When_Y()} and use
 * AssertJ assertions for fluent, readable verification.</p>
 *
 * @see Booking
 * @see BookingStatus
 * @see BookingEvent
 */
@DisplayName("Booking Aggregate Root")
class BookingTest {

    // ==================== Test Fixtures ====================

    private static final CustomerId CUSTOMER_ID = CustomerId.generate();
    private static final VoyageId VOYAGE_ID = VoyageId.generate();
    private static final PortCode ORIGIN = PortCode.of("DEHAM");
    private static final PortCode DESTINATION = PortCode.of("CNSHA");
    private static final LocalDate FUTURE_DATE = LocalDate.now().plusDays(30);

    private static Cargo validCargo() {
        return new Cargo(
                "HS8471",
                "Electronic components",
                Weight.ofKilograms(new BigDecimal("25000")),
                ContainerType.DRY_40,
                2,
                ORIGIN,
                DESTINATION
        );
    }

    private static Booking createDraftBooking() {
        return Booking.create(CUSTOMER_ID, validCargo(), FUTURE_DATE);
    }

    // ==================== Creation Tests ====================

    @Nested
    @DisplayName("Creation")
    class Creation {

        @Test
        @DisplayName("should create booking in DRAFT status when valid input provided")
        void should_CreateBookingInDraftStatus_When_ValidInputProvided() {
            // When
            Booking booking = Booking.create(CUSTOMER_ID, validCargo(), FUTURE_DATE);

            // Then
            assertThat(booking.getId()).isNotNull();
            assertThat(booking.getCustomerId()).isEqualTo(CUSTOMER_ID);
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.DRAFT);
            assertThat(booking.getCargo()).isEqualTo(validCargo());
            assertThat(booking.getRequestedDepartureDate()).isEqualTo(FUTURE_DATE);
            assertThat(booking.getCreatedAt()).isNotNull();
            assertThat(booking.getUpdatedAt()).isNotNull();
            assertThat(booking.getVoyageId()).isEmpty();
            assertThat(booking.getCancellationReason()).isEmpty();
            assertThat(booking.getVersion()).isZero();
        }

        @Test
        @DisplayName("should throw exception when departure date is in the past")
        void should_ThrowException_When_DepartureDateInPast() {
            // Given
            LocalDate pastDate = LocalDate.now().minusDays(1);

            // When / Then
            assertThatThrownBy(() -> Booking.create(CUSTOMER_ID, validCargo(), pastDate))
                    .isInstanceOf(IllegalArgumentException.class)
                    .hasMessageContaining("Requested departure date must be in the future");
        }

        @Test
        @DisplayName("should emit BookingCreated event when created")
        void should_EmitBookingCreatedEvent_When_Created() {
            // When
            Booking booking = Booking.create(CUSTOMER_ID, validCargo(), FUTURE_DATE);

            // Then
            List<BookingEvent> events = booking.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(BookingCreated.class);

            BookingCreated created = (BookingCreated) events.get(0);
            assertThat(created.bookingId()).isEqualTo(booking.getId());
            assertThat(created.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(created.origin()).isEqualTo(ORIGIN);
            assertThat(created.destination()).isEqualTo(DESTINATION);
            assertThat(created.containerType()).isEqualTo(ContainerType.DRY_40);
            assertThat(created.containerCount()).isEqualTo(2);
            assertThat(created.requestedDepartureDate()).isEqualTo(FUTURE_DATE);
            assertThat(created.occurredAt()).isNotNull();
            assertThat(created.eventId()).isNotNull();
        }
    }

    // ==================== Confirm Transition Tests ====================

    @Nested
    @DisplayName("Confirm Transition")
    class ConfirmTransition {

        @Test
        @DisplayName("should transition to CONFIRMED when in DRAFT status")
        void should_TransitionToConfirmed_When_InDraftStatus() {
            // Given
            Booking booking = createDraftBooking();
            booking.pullDomainEvents(); // clear creation event

            // When
            booking.confirm(VOYAGE_ID);

            // Then
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CONFIRMED);
            assertThat(booking.getVoyageId()).contains(VOYAGE_ID);
        }

        @Test
        @DisplayName("should emit BookingConfirmed event when confirmed")
        void should_EmitBookingConfirmedEvent_When_Confirmed() {
            // Given
            Booking booking = createDraftBooking();
            booking.pullDomainEvents(); // clear creation event

            // When
            booking.confirm(VOYAGE_ID);

            // Then
            List<BookingEvent> events = booking.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(BookingConfirmed.class);

            BookingConfirmed confirmed = (BookingConfirmed) events.get(0);
            assertThat(confirmed.bookingId()).isEqualTo(booking.getId());
            assertThat(confirmed.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(confirmed.voyageId()).isEqualTo(VOYAGE_ID);
            assertThat(confirmed.occurredAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw ConflictException when confirming cancelled booking")
        void should_ThrowConflictException_When_ConfirmingCancelledBooking() {
            // Given
            Booking booking = createDraftBooking();
            booking.cancel("Customer request");

            // When / Then
            assertThatThrownBy(() -> booking.confirm(VOYAGE_ID))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("CANCELLED")
                    .hasMessageContaining("CONFIRMED");
        }
    }

    // ==================== Cancel Transition Tests ====================

    @Nested
    @DisplayName("Cancel Transition")
    class CancelTransition {

        @Test
        @DisplayName("should transition to CANCELLED when in DRAFT status")
        void should_TransitionToCancelled_When_InDraftStatus() {
            // Given
            Booking booking = createDraftBooking();

            // When
            booking.cancel("Changed plans");

            // Then
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(booking.getCancellationReason()).contains("Changed plans");
        }

        @Test
        @DisplayName("should transition to CANCELLED when in CONFIRMED status")
        void should_TransitionToCancelled_When_InConfirmedStatus() {
            // Given
            Booking booking = createDraftBooking();
            booking.confirm(VOYAGE_ID);

            // When
            booking.cancel("Cargo no longer needed");

            // Then
            assertThat(booking.getStatus()).isEqualTo(BookingStatus.CANCELLED);
            assertThat(booking.getCancellationReason()).contains("Cargo no longer needed");
        }

        @Test
        @DisplayName("should emit BookingCancelled event when cancelled")
        void should_EmitBookingCancelledEvent_When_Cancelled() {
            // Given
            Booking booking = createDraftBooking();
            booking.pullDomainEvents(); // clear creation event

            // When
            booking.cancel("No longer needed");

            // Then
            List<BookingEvent> events = booking.pullDomainEvents();
            assertThat(events).hasSize(1);
            assertThat(events.get(0)).isInstanceOf(BookingCancelled.class);

            BookingCancelled cancelled = (BookingCancelled) events.get(0);
            assertThat(cancelled.bookingId()).isEqualTo(booking.getId());
            assertThat(cancelled.customerId()).isEqualTo(CUSTOMER_ID);
            assertThat(cancelled.previousStatus()).isEqualTo(BookingStatus.DRAFT);
            assertThat(cancelled.reason()).isEqualTo("No longer needed");
            assertThat(cancelled.occurredAt()).isNotNull();
        }

        @Test
        @DisplayName("should throw ConflictException when cancelling delivered booking")
        void should_ThrowConflictException_When_CancellingDeliveredBooking() {
            // Given — use reconstitute to create a DELIVERED booking
            Booking booking = Booking.reconstitute(
                    com.freightflow.commons.domain.BookingId.generate(),
                    CUSTOMER_ID,
                    validCargo(),
                    FUTURE_DATE,
                    BookingStatus.DELIVERED,
                    VOYAGE_ID,
                    null,
                    java.time.Instant.now(),
                    java.time.Instant.now(),
                    3
            );

            // When / Then
            assertThatThrownBy(() -> booking.cancel("Too late"))
                    .isInstanceOf(ConflictException.class)
                    .hasMessageContaining("DELIVERED")
                    .hasMessageContaining("CANCELLED");
        }
    }

    // ==================== Domain Event Management Tests ====================

    @Nested
    @DisplayName("Domain Event Management")
    class DomainEventManagement {

        @Test
        @DisplayName("should clear domain events when pullDomainEvents is called")
        void should_ClearDomainEvents_When_PullDomainEventsCalled() {
            // Given
            Booking booking = createDraftBooking();
            assertThat(booking.pullDomainEvents()).hasSize(1);

            // When — pull again
            List<BookingEvent> secondPull = booking.pullDomainEvents();

            // Then — should be empty
            assertThat(secondPull).isEmpty();
        }

        @Test
        @DisplayName("should accumulate multiple events across state transitions")
        void should_AccumulateMultipleEvents_When_MultipleTransitionsOccur() {
            // Given
            Booking booking = createDraftBooking();
            booking.confirm(VOYAGE_ID);
            booking.cancel("Changed mind");

            // When — pull all events at once
            List<BookingEvent> events = booking.pullDomainEvents();

            // Then
            assertThat(events).hasSize(3);
            assertThat(events.get(0)).isInstanceOf(BookingCreated.class);
            assertThat(events.get(1)).isInstanceOf(BookingConfirmed.class);
            assertThat(events.get(2)).isInstanceOf(BookingCancelled.class);
        }
    }

    // ==================== TEU Calculation Tests ====================

    @Nested
    @DisplayName("TEU Calculation")
    class TeuCalculation {

        @Test
        @DisplayName("should calculate correct TEU when cargo created with DRY_20")
        void should_CalculateCorrectTeu_When_CargoCreatedWith20ft() {
            // Given
            Cargo cargo = new Cargo(
                    "HS8471",
                    "Small items",
                    Weight.ofKilograms(new BigDecimal("5000")),
                    ContainerType.DRY_20,
                    3,
                    ORIGIN,
                    DESTINATION
            );

            // When / Then — DRY_20 has TEU factor 1.0
            assertThat(cargo.totalTeu()).isEqualTo(3.0);
        }

        @Test
        @DisplayName("should calculate correct TEU when cargo created with DRY_40")
        void should_CalculateCorrectTeu_When_CargoCreatedWith40ft() {
            // Given
            Cargo cargo = new Cargo(
                    "HS8471",
                    "Large items",
                    Weight.ofKilograms(new BigDecimal("20000")),
                    ContainerType.DRY_40,
                    2,
                    ORIGIN,
                    DESTINATION
            );

            // When / Then — DRY_40 has TEU factor 2.0
            assertThat(cargo.totalTeu()).isEqualTo(4.0);
        }

        @Test
        @DisplayName("should calculate correct TEU when cargo created with REEFER_40")
        void should_CalculateCorrectTeu_When_CargoCreatedWithReefer40() {
            // Given
            Cargo cargo = new Cargo(
                    "HS0201",
                    "Frozen meat",
                    Weight.ofKilograms(new BigDecimal("18000")),
                    ContainerType.REEFER_40,
                    5,
                    ORIGIN,
                    DESTINATION
            );

            // When / Then — REEFER_40 has TEU factor 2.0
            assertThat(cargo.totalTeu()).isEqualTo(10.0);
        }
    }
}
