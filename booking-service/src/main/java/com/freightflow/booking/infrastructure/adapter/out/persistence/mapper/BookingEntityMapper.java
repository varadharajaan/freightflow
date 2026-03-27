package com.freightflow.booking.infrastructure.adapter.out.persistence.mapper;

import com.freightflow.booking.domain.model.Booking;
import com.freightflow.booking.domain.model.BookingStatus;
import com.freightflow.booking.domain.model.Cargo;
import com.freightflow.booking.domain.model.ContainerType;
import com.freightflow.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.PortCode;
import com.freightflow.commons.domain.VoyageId;
import com.freightflow.commons.domain.Weight;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.math.BigDecimal;
import java.util.UUID;

/**
 * Maps between JPA entity ({@link BookingJpaEntity}) and domain model ({@link Booking}).
 *
 * <p>This mapper is the translation layer between the persistence world (JPA annotations,
 * flat columns) and the domain world (aggregates, value objects, sealed types).
 * It ensures the domain model stays completely free of persistence concerns
 * (Dependency Inversion Principle).</p>
 *
 * <p>We use a hand-written mapper instead of MapStruct here because the Booking
 * aggregate has a private constructor and domain events — MapStruct cannot handle
 * this complex mapping. For simpler DTOs, MapStruct is preferred.</p>
 *
 * @see BookingJpaEntity
 * @see Booking
 */
@Component
public class BookingEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(BookingEntityMapper.class);

    /**
     * Converts a domain Booking to a JPA entity for persistence.
     *
     * @param booking the domain booking aggregate
     * @return the JPA entity
     */
    public BookingJpaEntity toEntity(Booking booking) {
        log.trace("Mapping domain Booking to JPA entity: bookingId={}", booking.getId().asString());

        var entity = new BookingJpaEntity();
        entity.setId(booking.getId().value());
        entity.setCustomerId(booking.getCustomerId().value());
        entity.setStatus(booking.getStatus().name());

        // Cargo fields (flattened from value objects)
        Cargo cargo = booking.getCargo();
        entity.setCommodityCode(cargo.commodityCode());
        entity.setDescription(cargo.description());
        entity.setWeightValue(cargo.weight().value());
        entity.setWeightUnit(cargo.weight().unit().name());
        entity.setContainerType(cargo.containerType().name());
        entity.setContainerCount(cargo.containerCount());
        entity.setOriginPort(cargo.origin().value());
        entity.setDestinationPort(cargo.destination().value());

        // Optional fields
        booking.getVoyageId().ifPresent(v -> entity.setVoyageId(v.value()));
        booking.getCancellationReason().ifPresent(entity::setCancellationReason);

        entity.setRequestedDepartureDate(booking.getRequestedDepartureDate());
        entity.setCreatedAt(booking.getCreatedAt());
        entity.setUpdatedAt(booking.getUpdatedAt());
        entity.setVersion(booking.getVersion());

        return entity;
    }

    /**
     * Reconstructs a domain Booking from a JPA entity.
     *
     * <p>Since the Booking aggregate has a private constructor (enforcing factory method usage),
     * we use a package-private reconstruction method. This is the ONLY place where a Booking
     * is created without going through {@link Booking#create} — it's restoring persisted state,
     * not creating a new booking.</p>
     *
     * @param entity the JPA entity
     * @return the domain booking aggregate
     */
    public Booking toDomain(BookingJpaEntity entity) {
        log.trace("Mapping JPA entity to domain Booking: bookingId={}", entity.getId());

        Weight weight = switch (entity.getWeightUnit()) {
            case "KG" -> Weight.ofKilograms(entity.getWeightValue());
            case "LBS" -> Weight.ofPounds(entity.getWeightValue());
            default -> throw new IllegalStateException(
                    "Unknown weight unit: " + entity.getWeightUnit());
        };

        Cargo cargo = new Cargo(
                entity.getCommodityCode(),
                entity.getDescription(),
                weight,
                ContainerType.valueOf(entity.getContainerType()),
                entity.getContainerCount(),
                PortCode.of(entity.getOriginPort()),
                PortCode.of(entity.getDestinationPort())
        );

        VoyageId voyageId = entity.getVoyageId() != null
                ? new VoyageId(entity.getVoyageId())
                : null;

        return Booking.reconstitute(
                new BookingId(entity.getId()),
                new CustomerId(entity.getCustomerId()),
                cargo,
                entity.getRequestedDepartureDate(),
                BookingStatus.valueOf(entity.getStatus()),
                voyageId,
                entity.getCancellationReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
