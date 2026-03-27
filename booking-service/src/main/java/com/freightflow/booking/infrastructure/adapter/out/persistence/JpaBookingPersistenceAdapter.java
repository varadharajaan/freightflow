package com.freightflow.booking.infrastructure.adapter.out.persistence;

import com.freightflow.booking.domain.model.Booking;
import com.freightflow.booking.domain.port.BookingRepository;
import com.freightflow.booking.infrastructure.adapter.out.persistence.entity.BookingJpaEntity;
import com.freightflow.booking.infrastructure.adapter.out.persistence.mapper.BookingEntityMapper;
import com.freightflow.booking.infrastructure.adapter.out.persistence.repository.SpringDataBookingRepository;
import com.freightflow.commons.domain.BookingId;
import com.freightflow.commons.domain.CustomerId;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JPA persistence adapter implementing the domain's {@link BookingRepository} port.
 *
 * <p>This is the <b>outbound adapter</b> in Hexagonal Architecture. It translates
 * between the domain model ({@link Booking}) and the JPA entity ({@link BookingJpaEntity})
 * using the {@link BookingEntityMapper}.</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>Adapter Pattern</b> — adapts Spring Data JPA to the domain port interface</li>
 *   <li><b>Dependency Inversion</b> — domain defines the port, infrastructure implements it</li>
 *   <li><b>Mapper isolation</b> — JPA entities never leak into the domain layer</li>
 *   <li><b>Logging</b> — DEBUG for operations, WARN for edge cases</li>
 * </ul>
 *
 * @see BookingRepository
 * @see SpringDataBookingRepository
 * @see BookingEntityMapper
 */
@Component
public class JpaBookingPersistenceAdapter implements BookingRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaBookingPersistenceAdapter.class);

    private final SpringDataBookingRepository jpaRepository;
    private final BookingEntityMapper mapper;

    /**
     * Constructor injection — depends on Spring Data repository and mapper.
     */
    public JpaBookingPersistenceAdapter(SpringDataBookingRepository jpaRepository,
                                         BookingEntityMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "JPA repository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Mapper must not be null");
    }

    @Override
    public Booking save(Booking booking) {
        log.debug("Persisting booking: bookingId={}, status={}",
                booking.getId().asString(), booking.getStatus());

        BookingJpaEntity entity = mapper.toEntity(booking);
        BookingJpaEntity saved = jpaRepository.save(entity);

        log.debug("Booking persisted: bookingId={}, version={}",
                saved.getId(), saved.getVersion());

        return mapper.toDomain(saved);
    }

    @Override
    public Optional<Booking> findById(BookingId bookingId) {
        log.debug("Finding booking: bookingId={}", bookingId.asString());

        return jpaRepository.findById(bookingId.value())
                .map(entity -> {
                    log.debug("Booking found: bookingId={}, status={}",
                            entity.getId(), entity.getStatus());
                    return mapper.toDomain(entity);
                });
    }

    @Override
    public List<Booking> findByCustomerId(CustomerId customerId) {
        log.debug("Finding bookings for customer: customerId={}", customerId.asString());

        List<BookingJpaEntity> entities = jpaRepository
                .findByCustomerIdOrderByCreatedAtDesc(customerId.value());

        log.debug("Found {} bookings for customer: customerId={}",
                entities.size(), customerId.asString());

        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    @Override
    public boolean existsById(BookingId bookingId) {
        boolean exists = jpaRepository.existsById(bookingId.value());
        log.debug("Booking exists check: bookingId={}, exists={}", bookingId.asString(), exists);
        return exists;
    }
}
