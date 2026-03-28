package com.freightflow.booking.infrastructure.adapter.out.persistence.repository;

import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * Immutable search criteria for dynamic booking queries.
 *
 * <p>This record encapsulates all optional filters that can be applied when searching
 * for bookings via the {@link BookingCustomRepository#searchBookings(BookingSearchCriteria)}
 * method. Each field is wrapped in {@link Optional} to clearly indicate that every
 * filter is independently optional — only non-empty values are applied.</p>
 *
 * <p>Uses the Builder pattern (via a static nested builder) for ergonomic construction:</p>
 * <pre>{@code
 * BookingSearchCriteria criteria = BookingSearchCriteria.builder()
 *     .customerId(customerId)
 *     .status("CONFIRMED")
 *     .originPort("USLAX")
 *     .minContainers(5)
 *     .build();
 * }</pre>
 *
 * <p>Design decisions:</p>
 * <ul>
 *   <li>Java 21 record for immutability and concise syntax</li>
 *   <li>Optional fields instead of null checks for type-safe absence modeling</li>
 *   <li>Builder pattern to avoid telescoping constructors</li>
 *   <li>Date range represented as separate from/to for open-ended ranges</li>
 * </ul>
 *
 * @param customerId    optional customer UUID to filter by
 * @param status        optional booking status string to filter by
 * @param originPort    optional origin port code to filter by
 * @param destinationPort optional destination port code to filter by
 * @param fromDate      optional start date (inclusive) for departure date range
 * @param toDate        optional end date (inclusive) for departure date range
 * @param minContainers optional minimum container count
 * @param containerType optional container type to filter by
 *
 * @see BookingCustomRepository
 * @see BookingCustomRepositoryImpl
 */
public record BookingSearchCriteria(
        Optional<UUID> customerId,
        Optional<String> status,
        Optional<String> originPort,
        Optional<String> destinationPort,
        Optional<LocalDate> fromDate,
        Optional<LocalDate> toDate,
        Optional<Integer> minContainers,
        Optional<String> containerType
) {

    /**
     * Creates a new criteria instance with all empty optionals.
     */
    public BookingSearchCriteria() {
        this(
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty(),
                Optional.empty(), Optional.empty(), Optional.empty(), Optional.empty()
        );
    }

    /**
     * Returns a new {@link Builder} for fluent construction.
     *
     * @return a fresh builder instance
     */
    public static Builder builder() {
        return new Builder();
    }

    /**
     * Fluent builder for constructing {@link BookingSearchCriteria} instances.
     *
     * <p>All setter methods are chainable and wrap values in {@link Optional}.
     * Unset fields default to {@link Optional#empty()}.</p>
     */
    public static final class Builder {

        private Optional<UUID> customerId = Optional.empty();
        private Optional<String> status = Optional.empty();
        private Optional<String> originPort = Optional.empty();
        private Optional<String> destinationPort = Optional.empty();
        private Optional<LocalDate> fromDate = Optional.empty();
        private Optional<LocalDate> toDate = Optional.empty();
        private Optional<Integer> minContainers = Optional.empty();
        private Optional<String> containerType = Optional.empty();

        private Builder() {
            // use BookingSearchCriteria.builder()
        }

        /**
         * Filters bookings by customer.
         *
         * @param customerId the customer UUID
         * @return this builder
         */
        public Builder customerId(UUID customerId) {
            this.customerId = Optional.ofNullable(customerId);
            return this;
        }

        /**
         * Filters bookings by status.
         *
         * @param status the booking status name
         * @return this builder
         */
        public Builder status(String status) {
            this.status = Optional.ofNullable(status);
            return this;
        }

        /**
         * Filters bookings by origin port.
         *
         * @param originPort the UN/LOCODE origin port code
         * @return this builder
         */
        public Builder originPort(String originPort) {
            this.originPort = Optional.ofNullable(originPort);
            return this;
        }

        /**
         * Filters bookings by destination port.
         *
         * @param destinationPort the UN/LOCODE destination port code
         * @return this builder
         */
        public Builder destinationPort(String destinationPort) {
            this.destinationPort = Optional.ofNullable(destinationPort);
            return this;
        }

        /**
         * Filters bookings with departure date on or after this date.
         *
         * @param fromDate the inclusive start date
         * @return this builder
         */
        public Builder fromDate(LocalDate fromDate) {
            this.fromDate = Optional.ofNullable(fromDate);
            return this;
        }

        /**
         * Filters bookings with departure date on or before this date.
         *
         * @param toDate the inclusive end date
         * @return this builder
         */
        public Builder toDate(LocalDate toDate) {
            this.toDate = Optional.ofNullable(toDate);
            return this;
        }

        /**
         * Filters bookings with at least this many containers.
         *
         * @param minContainers the minimum container count
         * @return this builder
         */
        public Builder minContainers(int minContainers) {
            this.minContainers = Optional.of(minContainers);
            return this;
        }

        /**
         * Filters bookings by container type.
         *
         * @param containerType the container type name
         * @return this builder
         */
        public Builder containerType(String containerType) {
            this.containerType = Optional.ofNullable(containerType);
            return this;
        }

        /**
         * Builds the immutable criteria instance.
         *
         * @return the constructed {@link BookingSearchCriteria}
         */
        public BookingSearchCriteria build() {
            return new BookingSearchCriteria(
                    customerId, status, originPort, destinationPort,
                    fromDate, toDate, minContainers, containerType
            );
        }
    }
}
