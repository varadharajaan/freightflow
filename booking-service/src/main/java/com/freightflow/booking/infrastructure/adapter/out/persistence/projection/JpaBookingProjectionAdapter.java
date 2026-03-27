package com.freightflow.booking.infrastructure.adapter.out.persistence.projection;

import com.freightflow.booking.application.query.BookingProjectionRepository;
import com.freightflow.booking.application.query.BookingView;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA-based adapter implementing the {@link BookingProjectionRepository} port.
 *
 * <p>Maps between the JPA {@link BookingProjectionEntity} (infrastructure concern) and
 * the {@link BookingView} record (application-layer query response). This adapter
 * isolates the application layer from JPA-specific details, following the
 * Dependency Inversion Principle.</p>
 *
 * @see BookingProjectionRepository
 * @see BookingProjectionEntity
 * @see SpringDataProjectionRepository
 */
@Component
public class JpaBookingProjectionAdapter implements BookingProjectionRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaBookingProjectionAdapter.class);

    private final SpringDataProjectionRepository repository;

    /**
     * Constructor injection of the Spring Data repository.
     *
     * @param repository the Spring Data JPA repository for projection entities
     */
    public JpaBookingProjectionAdapter(SpringDataProjectionRepository repository) {
        this.repository = Objects.requireNonNull(repository, "repository must not be null");
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public Optional<BookingView> findById(String bookingId) {
        log.debug("Finding booking projection by ID: bookingId={}", bookingId);

        UUID id = UUID.fromString(bookingId);
        Optional<BookingView> result = repository.findById(id).map(this::toBookingView);

        if (result.isEmpty()) {
            log.debug("No booking projection found: bookingId={}", bookingId);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     */
    @Override
    public List<BookingView> findByCustomerId(String customerId) {
        log.debug("Finding booking projections by customer: customerId={}", customerId);

        UUID id = UUID.fromString(customerId);
        List<BookingView> results = repository.findByCustomerIdOrderBySequenceNumberDesc(id)
                .stream()
                .map(this::toBookingView)
                .toList();

        log.debug("Found {} booking projection(s) for customer: customerId={}",
                results.size(), customerId);
        return results;
    }

    /**
     * Maps a {@link BookingProjectionEntity} to a {@link BookingView} record.
     *
     * <p>Converts UUIDs to strings and nullable fields are passed through as-is.
     * This mapping layer ensures the application layer never sees JPA entities.</p>
     *
     * @param entity the JPA projection entity
     * @return the application-layer booking view
     */
    private BookingView toBookingView(BookingProjectionEntity entity) {
        return new BookingView(
                entity.getBookingId().toString(),
                entity.getCustomerId().toString(),
                entity.getStatus(),
                entity.getOriginPort(),
                entity.getDestinationPort(),
                entity.getContainerType(),
                entity.getContainerCount(),
                entity.getVoyageId() != null ? entity.getVoyageId().toString() : null,
                entity.getCancellationReason(),
                entity.getRequestedDepartureDate(),
                entity.getCreatedAt(),
                entity.getUpdatedAt()
        );
    }
}
