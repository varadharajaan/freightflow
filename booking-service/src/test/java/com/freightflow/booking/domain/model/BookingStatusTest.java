package com.freightflow.booking.domain.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.stream.Stream;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Unit tests for the {@link BookingStatus} state machine.
 *
 * <p>Tests the full state transition matrix — every possible from→to combination
 * is verified to ensure the state machine is correctly implemented. This prevents
 * regressions when new states are added.</p>
 *
 * @see BookingStatus
 */
@DisplayName("BookingStatus State Machine")
class BookingStatusTest {

    // ==================== Valid Transitions ====================

    @Nested
    @DisplayName("Valid Transitions")
    class ValidTransitions {

        @Test
        @DisplayName("should allow transition from DRAFT to CONFIRMED")
        void should_AllowTransition_From_DRAFT_To_CONFIRMED() {
            assertThat(BookingStatus.DRAFT.canTransitionTo(BookingStatus.CONFIRMED)).isTrue();
        }

        @Test
        @DisplayName("should allow transition from DRAFT to CANCELLED")
        void should_AllowTransition_From_DRAFT_To_CANCELLED() {
            assertThat(BookingStatus.DRAFT.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("should allow transition from CONFIRMED to SHIPPED")
        void should_AllowTransition_From_CONFIRMED_To_SHIPPED() {
            assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.SHIPPED)).isTrue();
        }

        @Test
        @DisplayName("should allow transition from CONFIRMED to CANCELLED")
        void should_AllowTransition_From_CONFIRMED_To_CANCELLED() {
            assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CANCELLED)).isTrue();
        }

        @Test
        @DisplayName("should allow transition from SHIPPED to DELIVERED")
        void should_AllowTransition_From_SHIPPED_To_DELIVERED() {
            assertThat(BookingStatus.SHIPPED.canTransitionTo(BookingStatus.DELIVERED)).isTrue();
        }
    }

    // ==================== Invalid Transitions ====================

    @Nested
    @DisplayName("Invalid Transitions")
    class InvalidTransitions {

        @ParameterizedTest(name = "CANCELLED → {0} should be denied")
        @EnumSource(BookingStatus.class)
        @DisplayName("should deny all transitions from CANCELLED")
        void should_DenyTransition_From_CANCELLED_To_Any(BookingStatus target) {
            assertThat(BookingStatus.CANCELLED.canTransitionTo(target)).isFalse();
        }

        @ParameterizedTest(name = "DELIVERED → {0} should be denied")
        @EnumSource(BookingStatus.class)
        @DisplayName("should deny all transitions from DELIVERED")
        void should_DenyTransition_From_DELIVERED_To_Any(BookingStatus target) {
            assertThat(BookingStatus.DELIVERED.canTransitionTo(target)).isFalse();
        }

        @Test
        @DisplayName("should deny transition from DRAFT to SHIPPED")
        void should_DenyTransition_From_DRAFT_To_SHIPPED() {
            assertThat(BookingStatus.DRAFT.canTransitionTo(BookingStatus.SHIPPED)).isFalse();
        }

        @Test
        @DisplayName("should deny transition from DRAFT to DELIVERED")
        void should_DenyTransition_From_DRAFT_To_DELIVERED() {
            assertThat(BookingStatus.DRAFT.canTransitionTo(BookingStatus.DELIVERED)).isFalse();
        }

        @Test
        @DisplayName("should deny transition from DRAFT to DRAFT (self-transition)")
        void should_DenyTransition_From_DRAFT_To_DRAFT() {
            assertThat(BookingStatus.DRAFT.canTransitionTo(BookingStatus.DRAFT)).isFalse();
        }

        @Test
        @DisplayName("should deny transition from CONFIRMED to CONFIRMED (self-transition)")
        void should_DenyTransition_From_CONFIRMED_To_CONFIRMED() {
            assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.CONFIRMED)).isFalse();
        }

        @Test
        @DisplayName("should deny transition from CONFIRMED to DRAFT (backward)")
        void should_DenyTransition_From_CONFIRMED_To_DRAFT() {
            assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.DRAFT)).isFalse();
        }

        @Test
        @DisplayName("should deny transition from CONFIRMED to DELIVERED (skipping SHIPPED)")
        void should_DenyTransition_From_CONFIRMED_To_DELIVERED() {
            assertThat(BookingStatus.CONFIRMED.canTransitionTo(BookingStatus.DELIVERED)).isFalse();
        }

        @Test
        @DisplayName("should deny transition from SHIPPED to DRAFT (backward)")
        void should_DenyTransition_From_SHIPPED_To_DRAFT() {
            assertThat(BookingStatus.SHIPPED.canTransitionTo(BookingStatus.DRAFT)).isFalse();
        }

        @Test
        @DisplayName("should deny transition from SHIPPED to CONFIRMED (backward)")
        void should_DenyTransition_From_SHIPPED_To_CONFIRMED() {
            assertThat(BookingStatus.SHIPPED.canTransitionTo(BookingStatus.CONFIRMED)).isFalse();
        }

        @Test
        @DisplayName("should deny transition from SHIPPED to CANCELLED")
        void should_DenyTransition_From_SHIPPED_To_CANCELLED() {
            assertThat(BookingStatus.SHIPPED.canTransitionTo(BookingStatus.CANCELLED)).isFalse();
        }

        @Test
        @DisplayName("should deny transition from SHIPPED to SHIPPED (self-transition)")
        void should_DenyTransition_From_SHIPPED_To_SHIPPED() {
            assertThat(BookingStatus.SHIPPED.canTransitionTo(BookingStatus.SHIPPED)).isFalse();
        }
    }

    // ==================== Full Transition Matrix ====================

    @Nested
    @DisplayName("Full Transition Matrix")
    class FullTransitionMatrix {

        /**
         * Provides every possible (from, to, expectedResult) combination.
         *
         * <p>This exhaustive parameterised test guarantees that the complete state
         * machine truth table is verified — 5 states x 5 states = 25 combinations.</p>
         */
        static Stream<Arguments> transitionMatrix() {
            return Stream.of(
                    // DRAFT transitions
                    Arguments.of(BookingStatus.DRAFT, BookingStatus.DRAFT, false),
                    Arguments.of(BookingStatus.DRAFT, BookingStatus.CONFIRMED, true),
                    Arguments.of(BookingStatus.DRAFT, BookingStatus.SHIPPED, false),
                    Arguments.of(BookingStatus.DRAFT, BookingStatus.DELIVERED, false),
                    Arguments.of(BookingStatus.DRAFT, BookingStatus.CANCELLED, true),

                    // CONFIRMED transitions
                    Arguments.of(BookingStatus.CONFIRMED, BookingStatus.DRAFT, false),
                    Arguments.of(BookingStatus.CONFIRMED, BookingStatus.CONFIRMED, false),
                    Arguments.of(BookingStatus.CONFIRMED, BookingStatus.SHIPPED, true),
                    Arguments.of(BookingStatus.CONFIRMED, BookingStatus.DELIVERED, false),
                    Arguments.of(BookingStatus.CONFIRMED, BookingStatus.CANCELLED, true),

                    // SHIPPED transitions
                    Arguments.of(BookingStatus.SHIPPED, BookingStatus.DRAFT, false),
                    Arguments.of(BookingStatus.SHIPPED, BookingStatus.CONFIRMED, false),
                    Arguments.of(BookingStatus.SHIPPED, BookingStatus.SHIPPED, false),
                    Arguments.of(BookingStatus.SHIPPED, BookingStatus.DELIVERED, true),
                    Arguments.of(BookingStatus.SHIPPED, BookingStatus.CANCELLED, false),

                    // DELIVERED transitions (terminal state)
                    Arguments.of(BookingStatus.DELIVERED, BookingStatus.DRAFT, false),
                    Arguments.of(BookingStatus.DELIVERED, BookingStatus.CONFIRMED, false),
                    Arguments.of(BookingStatus.DELIVERED, BookingStatus.SHIPPED, false),
                    Arguments.of(BookingStatus.DELIVERED, BookingStatus.DELIVERED, false),
                    Arguments.of(BookingStatus.DELIVERED, BookingStatus.CANCELLED, false),

                    // CANCELLED transitions (terminal state)
                    Arguments.of(BookingStatus.CANCELLED, BookingStatus.DRAFT, false),
                    Arguments.of(BookingStatus.CANCELLED, BookingStatus.CONFIRMED, false),
                    Arguments.of(BookingStatus.CANCELLED, BookingStatus.SHIPPED, false),
                    Arguments.of(BookingStatus.CANCELLED, BookingStatus.DELIVERED, false),
                    Arguments.of(BookingStatus.CANCELLED, BookingStatus.CANCELLED, false)
            );
        }

        @ParameterizedTest(name = "{0} → {1} = {2}")
        @MethodSource("transitionMatrix")
        @DisplayName("should correctly evaluate transition")
        void should_CorrectlyEvaluateTransition_When_GivenFromAndToStates(
                BookingStatus from, BookingStatus to, boolean expected) {
            assertThat(from.canTransitionTo(to))
                    .as("Transition from %s to %s should be %s", from, to, expected ? "allowed" : "denied")
                    .isEqualTo(expected);
        }
    }
}
