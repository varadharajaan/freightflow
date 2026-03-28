package com.freightflow.billingservice.infrastructure.adapter.out.persistence;

import com.freightflow.billingservice.domain.model.Invoice;
import com.freightflow.billingservice.domain.port.InvoiceRepository;
import com.freightflow.billingservice.infrastructure.adapter.out.persistence.entity.InvoiceJpaEntity;
import com.freightflow.billingservice.infrastructure.adapter.out.persistence.repository.SpringDataInvoiceRepository;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * JPA persistence adapter implementing the domain's {@link InvoiceRepository} port.
 *
 * <p>This is the <b>outbound adapter</b> in Hexagonal Architecture. It translates
 * between the domain model ({@link Invoice}) and the JPA entity ({@link InvoiceJpaEntity})
 * using the {@link InvoiceEntityMapper}.</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>Adapter Pattern</b> — adapts Spring Data JPA to the domain port interface</li>
 *   <li><b>Dependency Inversion</b> — domain defines the port, infrastructure implements it</li>
 *   <li><b>Mapper isolation</b> — JPA entities never leak into the domain layer</li>
 *   <li><b>Logging</b> — DEBUG for operations, WARN for edge cases</li>
 * </ul>
 *
 * @see InvoiceRepository
 * @see SpringDataInvoiceRepository
 * @see InvoiceEntityMapper
 */
@Component
public class JpaInvoicePersistenceAdapter implements InvoiceRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaInvoicePersistenceAdapter.class);

    private final SpringDataInvoiceRepository jpaRepository;
    private final InvoiceEntityMapper mapper;

    /**
     * Constructor injection — depends on Spring Data repository and entity mapper.
     *
     * @param jpaRepository the Spring Data JPA repository for invoice entities
     * @param mapper        the entity mapper for domain-to-JPA translation
     */
    public JpaInvoicePersistenceAdapter(SpringDataInvoiceRepository jpaRepository,
                                         InvoiceEntityMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "JPA repository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Mapper must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Converts the domain invoice to a JPA entity, persists it via Spring Data,
     * and returns the reconstituted domain model with updated version.</p>
     */
    @Override
    @Profiled(value = "invoiceRepository.save", slowThresholdMs = 500)
    public Invoice save(Invoice invoice) {
        log.debug("Persisting invoice: invoiceId={}, status={}",
                invoice.getInvoiceId(), invoice.getStatus());

        InvoiceJpaEntity entity = mapper.toEntity(invoice);
        InvoiceJpaEntity saved = jpaRepository.save(entity);

        log.debug("Invoice persisted: invoiceId={}, version={}",
                saved.getId(), saved.getVersion());

        return mapper.toDomain(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Looks up the invoice by its UUID and maps the result back to the domain
     * model if found.</p>
     */
    @Override
    public Optional<Invoice> findById(UUID invoiceId) {
        log.debug("Finding invoice: invoiceId={}", invoiceId);

        Optional<Invoice> result = jpaRepository.findById(invoiceId)
                .map(entity -> {
                    log.debug("Invoice found: invoiceId={}, status={}",
                            entity.getId(), entity.getStatus());
                    return mapper.toDomain(entity);
                });

        if (result.isEmpty()) {
            log.warn("Invoice not found: invoiceId={}", invoiceId);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retrieves all invoices associated with the given booking identifier,
     * ordered by creation date descending.</p>
     */
    @Override
    public List<Invoice> findByBookingId(UUID bookingId) {
        log.debug("Finding invoices for booking: bookingId={}", bookingId);

        List<InvoiceJpaEntity> entities = jpaRepository
                .findByBookingIdOrderByCreatedAtDesc(bookingId);

        log.debug("Found {} invoices for booking: bookingId={}",
                entities.size(), bookingId);

        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retrieves all invoices associated with the given customer identifier,
     * ordered by creation date descending.</p>
     */
    @Override
    public List<Invoice> findByCustomerId(UUID customerId) {
        log.debug("Finding invoices for customer: customerId={}", customerId);

        List<InvoiceJpaEntity> entities = jpaRepository
                .findByCustomerIdOrderByCreatedAtDesc(customerId);

        log.debug("Found {} invoices for customer: customerId={}",
                entities.size(), customerId);

        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the Spring Data repository's existence check by invoice ID.</p>
     */
    @Override
    public boolean existsById(UUID invoiceId) {
        boolean exists = jpaRepository.existsById(invoiceId);
        log.debug("Invoice exists check: invoiceId={}, exists={}", invoiceId, exists);
        return exists;
    }
}
