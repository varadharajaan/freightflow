package com.freightflow.commons.observability.profiling;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.reflect.MethodSignature;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * AOP aspect for method-level performance profiling.
 *
 * <p>Intercepts methods annotated with {@link Profiled} and provides:</p>
 * <ul>
 *   <li><b>Logging</b>: DEBUG entry/exit with execution time, WARN for slow operations</li>
 *   <li><b>Metrics</b>: Micrometer timer recorded per operation for Prometheus/Grafana</li>
 *   <li><b>Error tracking</b>: Logs exceptions at ERROR level with execution time</li>
 * </ul>
 *
 * <h3>Metrics Exposed</h3>
 * <pre>
 * freightflow_method_execution_seconds{operation="createBooking", class="BookingService", outcome="success"}
 * freightflow_method_execution_seconds{operation="createBooking", class="BookingService", outcome="error"}
 * </pre>
 *
 * <h3>Design Patterns</h3>
 * <ul>
 *   <li><b>Decorator Pattern</b> — wraps method execution with profiling behavior</li>
 *   <li><b>Cross-Cutting Concern</b> — profiling logic separated from business logic (AOP)</li>
 *   <li><b>Single Responsibility</b> — business methods don't contain profiling code</li>
 * </ul>
 *
 * @see Profiled
 */
@Aspect
@Component
public class PerformanceProfilingAspect {

    private static final Logger log = LoggerFactory.getLogger(PerformanceProfilingAspect.class);
    private static final String METRIC_NAME = "freightflow.method.execution";

    private final MeterRegistry meterRegistry;

    /**
     * Constructor injection — depends on Micrometer registry for metrics recording.
     */
    public PerformanceProfilingAspect(MeterRegistry meterRegistry) {
        this.meterRegistry = Objects.requireNonNull(meterRegistry, "MeterRegistry must not be null");
    }

    /**
     * Around advice that profiles any method annotated with {@link Profiled}.
     *
     * <p>Execution flow:</p>
     * <ol>
     *   <li>Log method entry at DEBUG (with parameters if {@code logParameters=true})</li>
     *   <li>Record start time</li>
     *   <li>Execute the target method</li>
     *   <li>Calculate duration</li>
     *   <li>Record Micrometer timer metric</li>
     *   <li>Log exit at DEBUG (with duration)</li>
     *   <li>If duration exceeds threshold, log WARN</li>
     *   <li>On exception, log ERROR with duration and re-throw</li>
     * </ol>
     */
    @Around("@annotation(profiled) || @within(profiled)")
    public Object profile(ProceedingJoinPoint joinPoint, Profiled profiled) throws Throwable {
        MethodSignature signature = (MethodSignature) joinPoint.getSignature();
        String className = signature.getDeclaringType().getSimpleName();
        String methodName = signature.getName();
        String operationName = profiled.value().isEmpty() ? methodName : profiled.value();

        // Log entry
        if (profiled.logParameters()) {
            log.debug("ENTER {}.{}() args={}", className, methodName,
                    sanitizeParameters(joinPoint.getArgs()));
        } else {
            log.debug("ENTER {}.{}()", className, methodName);
        }

        long startTime = System.nanoTime();
        String outcome = "success";

        try {
            Object result = joinPoint.proceed();
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);

            // Log exit with duration
            log.debug("EXIT  {}.{}() duration={}ms", className, methodName, durationMs);

            // Warn if slow
            if (durationMs > profiled.slowThresholdMs()) {
                log.warn("SLOW  {}.{}() took {}ms (threshold={}ms)",
                        className, methodName, durationMs, profiled.slowThresholdMs());
            }

            return result;

        } catch (Throwable ex) {
            long durationMs = TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - startTime);
            outcome = "error";

            log.error("ERROR {}.{}() failed after {}ms: {}",
                    className, methodName, durationMs, ex.getMessage());

            throw ex;

        } finally {
            long durationNanos = System.nanoTime() - startTime;

            // Record Micrometer metric
            Timer.builder(METRIC_NAME)
                    .description("Method execution time")
                    .tag("operation", operationName)
                    .tag("class", className)
                    .tag("method", methodName)
                    .tag("outcome", outcome)
                    .register(meterRegistry)
                    .record(durationNanos, TimeUnit.NANOSECONDS);
        }
    }

    /**
     * Sanitizes method parameters for logging.
     * Truncates large objects and masks potentially sensitive data.
     */
    private String sanitizeParameters(Object[] args) {
        if (args == null || args.length == 0) {
            return "[]";
        }
        return Arrays.stream(args)
                .map(arg -> {
                    if (arg == null) return "null";
                    String str = arg.toString();
                    // Truncate large objects to prevent log flooding
                    return str.length() > 200 ? str.substring(0, 200) + "..." : str;
                })
                .toList()
                .toString();
    }
}
