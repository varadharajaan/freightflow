package com.freightflow.commons.domain;

import java.time.LocalDate;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Value Object representing an inclusive date range.
 *
 * <p>Follows the Value Object pattern from Domain-Driven Design — instances are immutable,
 * compared by value, and self-validating. A {@code DateRange} always satisfies the invariant
 * that {@code from} is not after {@code to}.</p>
 *
 * @param from the start date (inclusive, must not be after {@code to})
 * @param to   the end date (inclusive, must not be before {@code from})
 */
public record DateRange(LocalDate from, LocalDate to) {

    public DateRange {
        Objects.requireNonNull(from, "DateRange 'from' must not be null");
        Objects.requireNonNull(to, "DateRange 'to' must not be null");
        if (from.isAfter(to)) {
            throw new IllegalArgumentException(
                    "DateRange 'from' must not be after 'to', got: from=%s, to=%s".formatted(from, to));
        }
    }

    /**
     * Checks whether the given date falls within this range (inclusive on both ends).
     *
     * @param date the date to check
     * @return {@code true} if the date is within [{@code from}, {@code to}]
     * @throws IllegalArgumentException if the date is null
     */
    public boolean contains(LocalDate date) {
        Objects.requireNonNull(date, "Date to check must not be null");
        return !date.isBefore(from) && !date.isAfter(to);
    }

    /**
     * Returns the duration of this range in days.
     *
     * <p>A range where {@code from} equals {@code to} has a duration of {@code 0} days.</p>
     *
     * @return the number of days between {@code from} and {@code to}
     */
    public long durationInDays() {
        return ChronoUnit.DAYS.between(from, to);
    }

    @Override
    public String toString() {
        return "%s..%s".formatted(from, to);
    }
}
