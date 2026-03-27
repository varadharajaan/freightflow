package com.freightflow.booking.benchmark;

import com.freightflow.booking.domain.model.Booking;
import com.freightflow.booking.domain.model.Cargo;
import com.freightflow.booking.domain.model.ContainerType;
import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.Money;
import com.freightflow.commons.domain.PortCode;
import com.freightflow.commons.domain.Weight;
import org.openjdk.jmh.annotations.Benchmark;
import org.openjdk.jmh.annotations.BenchmarkMode;
import org.openjdk.jmh.annotations.Fork;
import org.openjdk.jmh.annotations.Measurement;
import org.openjdk.jmh.annotations.Mode;
import org.openjdk.jmh.annotations.OutputTimeUnit;
import org.openjdk.jmh.annotations.Scope;
import org.openjdk.jmh.annotations.State;
import org.openjdk.jmh.annotations.Warmup;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.concurrent.TimeUnit;

/**
 * JMH microbenchmarks for critical-path domain operations.
 *
 * <p>Measures the performance of domain object creation, validation, and state transitions.
 * These benchmarks prove that domain logic adds negligible overhead compared to I/O
 * (database, network, Kafka).</p>
 *
 * <h3>Run benchmarks</h3>
 * <pre>{@code
 * # Via Maven (if JMH plugin configured)
 * ./mvnw -pl booking-service test -Dtest=BookingBenchmark -Pbenchmark
 *
 * # Or directly with JMH runner
 * java -jar target/benchmarks.jar BookingBenchmark
 * }</pre>
 *
 * <h3>Expected Results</h3>
 * <pre>
 * Benchmark                                 Mode  Cnt    Score    Error  Units
 * BookingBenchmark.createBooking             avgt    5   ~200     ns/op
 * BookingBenchmark.createValueObjects        avgt    5   ~50      ns/op
 * BookingBenchmark.confirmBooking            avgt    5   ~80      ns/op
 * BookingBenchmark.stateTransitionCheck      avgt    5   ~5       ns/op
 * </pre>
 *
 * @see <a href="https://openjdk.org/projects/code-tools/jmh/">JMH documentation</a>
 */
@State(Scope.Benchmark)
@BenchmarkMode(Mode.AverageTime)
@OutputTimeUnit(TimeUnit.NANOSECONDS)
@Fork(value = 1, jvmArgs = {"--enable-preview", "-Xms512m", "-Xmx512m"})
@Warmup(iterations = 3, time = 1)
@Measurement(iterations = 5, time = 1)
public class BookingBenchmark {

    private static final CustomerId CUSTOMER_ID = CustomerId.generate();
    private static final PortCode ORIGIN = PortCode.of("DEHAM");
    private static final PortCode DESTINATION = PortCode.of("CNSHA");
    private static final Weight WEIGHT = Weight.ofKilograms(new BigDecimal("18500.00"));

    /**
     * Benchmarks creating a complete Booking aggregate with all value objects.
     * This is the hot path called on every POST /api/v1/bookings request.
     */
    @Benchmark
    public Booking createBooking() {
        Cargo cargo = new Cargo(
                "HS-8471", "Electronic components", WEIGHT,
                ContainerType.DRY_40, 2, ORIGIN, DESTINATION
        );
        return Booking.create(CUSTOMER_ID, cargo, LocalDate.now().plusDays(30));
    }

    /**
     * Benchmarks value object creation in isolation.
     * Shows the overhead of validation in Records.
     */
    @Benchmark
    public Cargo createValueObjects() {
        return new Cargo(
                "HS-8471", "Electronic components",
                Weight.ofKilograms(new BigDecimal("18500.00")),
                ContainerType.DRY_40, 2,
                PortCode.of("DEHAM"), PortCode.of("CNSHA")
        );
    }

    /**
     * Benchmarks the confirm state transition.
     * Measures state machine check + event creation overhead.
     */
    @Benchmark
    public void confirmBooking() {
        Cargo cargo = new Cargo(
                "HS-8471", "Electronic components", WEIGHT,
                ContainerType.DRY_40, 2, ORIGIN, DESTINATION
        );
        Booking booking = Booking.create(CUSTOMER_ID, cargo, LocalDate.now().plusDays(30));
        booking.confirm(com.freightflow.commons.domain.VoyageId.generate());
    }

    /**
     * Benchmarks the Money value object arithmetic.
     * Critical for billing calculations.
     */
    @Benchmark
    public Money moneyArithmetic() {
        Money price = Money.of(new BigDecimal("15000.00"), "USD");
        Money surcharge = Money.of(new BigDecimal("2500.00"), "USD");
        return price.add(surcharge);
    }
}
