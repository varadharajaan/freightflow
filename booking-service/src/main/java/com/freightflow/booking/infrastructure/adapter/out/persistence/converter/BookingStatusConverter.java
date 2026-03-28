package com.freightflow.booking.infrastructure.adapter.out.persistence.converter;

import com.freightflow.booking.domain.model.BookingStatus;
import jakarta.persistence.AttributeConverter;
import jakarta.persistence.Converter;

/**
 * JPA {@link AttributeConverter} that maps between the {@link BookingStatus} domain enum
 * and its {@code VARCHAR} database representation.
 *
 * <h3>{@code @Converter} vs {@code @Enumerated}</h3>
 *
 * <p><b>{@code @Enumerated(EnumType.STRING)}</b> — the standard JPA approach for
 * persisting enums. It stores the {@code enum.name()} value directly. Limitations:</p>
 * <ul>
 *   <li>No control over the persisted string (always uses {@code name()})</li>
 *   <li>Renaming an enum constant breaks existing data</li>
 *   <li>Cannot map to a custom database value (e.g., abbreviations)</li>
 *   <li>{@code @Enumerated(EnumType.ORDINAL)} is fragile — reordering breaks data</li>
 * </ul>
 *
 * <p><b>{@code @Converter} / {@code AttributeConverter}</b> (this approach) — provides
 * full control over the conversion logic:</p>
 * <ul>
 *   <li>Can map to custom database values (e.g., "DRF" instead of "DRAFT")</li>
 *   <li>Decouples Java enum names from database values — safe to rename enums</li>
 *   <li>Can add validation, logging, or fallback logic during conversion</li>
 *   <li>{@code autoApply = true} applies to all entities; {@code false} requires
 *       explicit {@code @Convert(converter = ...)} on each field</li>
 * </ul>
 *
 * <p>This converter uses {@code autoApply = false} so it must be explicitly referenced
 * on entity fields that need it. This is intentional — the {@link BookingJpaEntity}
 * currently stores status as a {@code String} and converts manually. This converter
 * demonstrates the pattern for future use where the entity field type changes to
 * {@link BookingStatus} directly.</p>
 *
 * <h3>Usage on an entity field</h3>
 * <pre>{@code
 * @Convert(converter = BookingStatusConverter.class)
 * @Column(name = "status", nullable = false, length = 20)
 * private BookingStatus status;
 * }</pre>
 *
 * @see BookingStatus
 * @see jakarta.persistence.AttributeConverter
 * @see jakarta.persistence.Convert
 */
@Converter(autoApply = false)
public class BookingStatusConverter implements AttributeConverter<BookingStatus, String> {

    /**
     * Converts the {@link BookingStatus} enum to its database string representation.
     *
     * <p>Uses {@link BookingStatus#name()} which matches the enum constant name.
     * For custom mappings (e.g., abbreviations), replace this with a switch expression.</p>
     *
     * @param attribute the domain enum value (may be null)
     * @return the database string, or null if the attribute is null
     */
    @Override
    public String convertToDatabaseColumn(BookingStatus attribute) {
        if (attribute == null) {
            return null;
        }
        return attribute.name();
    }

    /**
     * Converts the database string back to a {@link BookingStatus} enum value.
     *
     * <p>Uses {@link BookingStatus#valueOf(String)} for exact matching. Throws
     * {@link IllegalArgumentException} if the database contains an unrecognized status
     * — this indicates a data integrity issue that should be investigated.</p>
     *
     * @param dbData the database string value (may be null)
     * @return the domain enum, or null if the database value is null
     * @throws IllegalArgumentException if the database value does not match any enum constant
     */
    @Override
    public BookingStatus convertToEntityAttribute(String dbData) {
        if (dbData == null) {
            return null;
        }
        return BookingStatus.valueOf(dbData);
    }
}
