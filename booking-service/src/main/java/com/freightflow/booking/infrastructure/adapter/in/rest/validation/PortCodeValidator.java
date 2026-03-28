package com.freightflow.booking.infrastructure.adapter.in.rest.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.regex.Pattern;

/**
 * Validator implementation for the {@link ValidPortCode} constraint.
 *
 * <p>Validates that a string conforms to the UN/LOCODE format: exactly 5 characters
 * consisting of uppercase ASCII letters and digits. The first 2 characters represent
 * the ISO 3166-1 alpha-2 country code, and the last 3 characters represent the
 * location identifier.</p>
 *
 * <h3>Custom Validator Pattern</h3>
 * <p>A custom validator implements {@link ConstraintValidator} with two type parameters:</p>
 * <ul>
 *   <li><b>A</b> — the constraint annotation type ({@link ValidPortCode})</li>
 *   <li><b>T</b> — the type being validated ({@link String})</li>
 * </ul>
 *
 * <p>The validator lifecycle:</p>
 * <ol>
 *   <li>{@link #initialize(ValidPortCode)} — called once when the validator is created,
 *       receives the annotation instance for reading any annotation attributes</li>
 *   <li>{@link #isValid(String, ConstraintValidatorContext)} — called for each validation,
 *       returns {@code true} if valid</li>
 * </ol>
 *
 * <p>Validators are CDI/Spring-managed beans, so you can inject dependencies
 * (e.g., a port reference data service) for more sophisticated validation.</p>
 *
 * @see ValidPortCode
 * @see ConstraintValidator
 */
public class PortCodeValidator implements ConstraintValidator<ValidPortCode, String> {

    private static final Logger log = LoggerFactory.getLogger(PortCodeValidator.class);

    /**
     * UN/LOCODE format: 2 uppercase letters (country) + 3 uppercase letters/digits (location).
     *
     * <p>Examples: USLAX, CNSHA, NLRTM, SGSIN, DE0HA</p>
     */
    private static final Pattern PORT_CODE_PATTERN = Pattern.compile("^[A-Z]{2}[A-Z0-9]{3}$");

    /**
     * Expected length of a valid UN/LOCODE port code.
     */
    private static final int PORT_CODE_LENGTH = 5;

    /**
     * Initializes the validator with the constraint annotation.
     *
     * <p>This method is called once per validator instance. Use it to read
     * annotation attributes (e.g., custom regex patterns, min/max lengths)
     * for configurable validators.</p>
     *
     * @param constraintAnnotation the {@link ValidPortCode} annotation instance
     */
    @Override
    public void initialize(ValidPortCode constraintAnnotation) {
        // No annotation attributes to read for this validator
    }

    /**
     * Validates the given port code string.
     *
     * <p>Validation rules:</p>
     * <ol>
     *   <li>Null values are considered valid (use {@code @NotNull} separately for nullability)</li>
     *   <li>Must not be blank (empty or whitespace-only)</li>
     *   <li>Must be exactly {@value PORT_CODE_LENGTH} characters long</li>
     *   <li>Must match the UN/LOCODE pattern: 2 uppercase letters + 3 uppercase alphanumeric</li>
     * </ol>
     *
     * <p><b>Design choice:</b> Null is valid per Bean Validation best practices —
     * nullability should be controlled by {@code @NotNull}, not by each individual
     * constraint. This allows composing {@code @NotNull @ValidPortCode} or using
     * just {@code @ValidPortCode} on optional fields.</p>
     *
     * @param value   the port code to validate (may be null)
     * @param context the constraint validator context (for custom error messages)
     * @return true if the value is null or a valid port code
     */
    @Override
    public boolean isValid(String value, ConstraintValidatorContext context) {
        if (value == null) {
            return true;
        }

        if (value.isBlank()) {
            log.trace("Port code validation failed: blank value");
            return false;
        }

        if (value.length() != PORT_CODE_LENGTH) {
            log.trace("Port code validation failed: expected {} characters, got {}", PORT_CODE_LENGTH, value.length());
            return false;
        }

        boolean matches = PORT_CODE_PATTERN.matcher(value).matches();
        if (!matches) {
            log.trace("Port code validation failed: '{}' does not match UN/LOCODE pattern", value);
        }
        return matches;
    }
}
