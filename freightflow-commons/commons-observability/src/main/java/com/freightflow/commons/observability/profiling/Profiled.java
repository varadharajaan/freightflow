package com.freightflow.commons.observability.profiling;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a method or class for performance profiling via AOP.
 *
 * <p>When applied, the {@link PerformanceProfilingAspect} will:</p>
 * <ul>
 *   <li>Log method entry at DEBUG level (method name, parameters)</li>
 *   <li>Log method exit at DEBUG level (method name, return type, execution time)</li>
 *   <li>Log WARN if execution exceeds {@link #slowThresholdMs()}</li>
 *   <li>Record execution time as a Micrometer timer metric</li>
 * </ul>
 *
 * <p>Usage:</p>
 * <pre>{@code
 * @Profiled(value = "createBooking", slowThresholdMs = 500)
 * public Booking createBooking(CreateBookingCommand command) { ... }
 * }</pre>
 *
 * <p>Can be applied at class level (profiles all public methods) or method level.</p>
 *
 * @see PerformanceProfilingAspect
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.RUNTIME)
public @interface Profiled {

    /**
     * Logical name for the profiled operation. Used in metrics and logs.
     * Defaults to the method name if empty.
     *
     * @return the operation name
     */
    String value() default "";

    /**
     * Threshold in milliseconds above which a WARN log is emitted.
     * Helps identify slow operations in production.
     *
     * @return the slow threshold in milliseconds
     */
    long slowThresholdMs() default 1000;

    /**
     * Whether to include method parameters in the log output.
     * Set to false for methods with sensitive parameters (passwords, tokens).
     *
     * @return true to log parameters
     */
    boolean logParameters() default true;
}
