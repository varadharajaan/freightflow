package com.freightflow.booking.infrastructure.adapter.in.rest.validation;

import jakarta.validation.Constraint;
import jakarta.validation.Payload;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Custom Bean Validation constraint that validates a UN/LOCODE port code.
 *
 * <p>A valid port code must conform to the UN/LOCODE standard format:</p>
 * <ul>
 *   <li>Exactly 5 characters long</li>
 *   <li>First 2 characters: ISO 3166-1 alpha-2 country code (uppercase letters)</li>
 *   <li>Last 3 characters: location code (uppercase letters and digits)</li>
 *   <li>Examples: {@code USLAX} (Los Angeles), {@code CNSHA} (Shanghai), {@code NLRTM} (Rotterdam)</li>
 * </ul>
 *
 * <h3>Custom Constraint Pattern</h3>
 * <p>Creating a custom Bean Validation constraint requires three components:</p>
 * <ol>
 *   <li><b>Constraint annotation</b> (this annotation) — defines the constraint metadata,
 *       error message, groups, and payload. Must be annotated with
 *       {@code @Constraint(validatedBy = ...)} pointing to the validator class.</li>
 *   <li><b>Validator class</b> ({@link PortCodeValidator}) — implements
 *       {@link jakarta.validation.ConstraintValidator} with the actual validation logic.</li>
 *   <li><b>Usage</b> — annotate fields, method parameters, or return values with
 *       {@code @ValidPortCode}.</li>
 * </ol>
 *
 * <h3>Required annotation elements</h3>
 * <ul>
 *   <li>{@code message()} — the default error message (supports i18n via message interpolation)</li>
 *   <li>{@code groups()} — allows grouping constraints for partial validation</li>
 *   <li>{@code payload()} — metadata for constraint clients (e.g., severity levels)</li>
 * </ul>
 *
 * <h3>Usage example</h3>
 * <pre>{@code
 * public record CreateBookingRequest(
 *     @ValidPortCode String originPort,
 *     @ValidPortCode String destinationPort,
 *     ...
 * ) {}
 * }</pre>
 *
 * @see PortCodeValidator
 * @see jakarta.validation.Constraint
 */
@Documented
@Constraint(validatedBy = PortCodeValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPortCode {

    /**
     * The error message returned when validation fails.
     *
     * <p>Supports Bean Validation message interpolation. Can reference
     * {@code ValidationMessages.properties} for internationalization.</p>
     *
     * @return the constraint violation message
     */
    String message() default "Must be a valid 5-character UN/LOCODE port code";

    /**
     * Validation groups for partial validation.
     *
     * <p>Allows constraints to be selectively applied. For example, you might
     * validate port codes only during creation ({@code OnCreate.class}) but
     * not during status updates.</p>
     *
     * @return the validation groups
     */
    Class<?>[] groups() default {};

    /**
     * Constraint payload for metadata.
     *
     * <p>Can be used to attach severity levels or other metadata to the constraint.
     * Typically unused in simple applications.</p>
     *
     * @return the payload classes
     */
    Class<? extends Payload>[] payload() default {};
}
