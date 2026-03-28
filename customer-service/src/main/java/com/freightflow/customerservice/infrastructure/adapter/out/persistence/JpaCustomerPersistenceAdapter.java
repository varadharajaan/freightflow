package com.freightflow.customerservice.infrastructure.adapter.out.persistence;

import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.customerservice.domain.model.Contract;
import com.freightflow.customerservice.domain.model.Customer;
import com.freightflow.customerservice.domain.model.CustomerStatus;
import com.freightflow.customerservice.domain.port.CustomerRepository;
import com.freightflow.customerservice.infrastructure.adapter.out.persistence.entity.CustomerJpaEntity;
import com.freightflow.customerservice.infrastructure.adapter.out.persistence.repository.SpringDataCustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * JPA persistence adapter implementing the domain's {@link CustomerRepository} port.
 *
 * <p>This is the <b>outbound adapter</b> in Hexagonal Architecture. It translates
 * between the domain model ({@link Customer}) and the JPA entity ({@link CustomerJpaEntity})
 * using the {@link CustomerEntityMapper}.</p>
 *
 * <h3>Design Decisions</h3>
 * <ul>
 *   <li><b>Adapter Pattern</b> — adapts Spring Data JPA to the domain port interface</li>
 *   <li><b>Dependency Inversion</b> — domain defines the port, infrastructure implements it</li>
 *   <li><b>Mapper isolation</b> — JPA entities never leak into the domain layer</li>
 *   <li><b>Logging</b> — DEBUG for operations, WARN for edge cases</li>
 * </ul>
 *
 * @see CustomerRepository
 * @see SpringDataCustomerRepository
 * @see CustomerEntityMapper
 */
@Component
public class JpaCustomerPersistenceAdapter implements CustomerRepository {

    private static final Logger log = LoggerFactory.getLogger(JpaCustomerPersistenceAdapter.class);

    private final SpringDataCustomerRepository jpaRepository;
    private final CustomerEntityMapper mapper;

    /**
     * Constructor injection — depends on Spring Data repository and entity mapper.
     *
     * @param jpaRepository the Spring Data JPA repository for customer entities
     * @param mapper        the entity mapper for domain-to-JPA translation
     */
    public JpaCustomerPersistenceAdapter(SpringDataCustomerRepository jpaRepository,
                                          CustomerEntityMapper mapper) {
        this.jpaRepository = Objects.requireNonNull(jpaRepository, "JPA repository must not be null");
        this.mapper = Objects.requireNonNull(mapper, "Mapper must not be null");
    }

    /**
     * {@inheritDoc}
     *
     * <p>Converts the domain customer to a JPA entity, persists it via Spring Data,
     * and returns the reconstituted domain model with updated version.</p>
     */
    @Override
    @Profiled(value = "customerRepository.save", slowThresholdMs = 500)
    public Customer save(Customer customer) {
        log.debug("Persisting customer: customerId={}, company={}, status={}",
                customer.getCustomerId().asString(), customer.getCompanyName(), customer.getStatus());

        CustomerJpaEntity entity = mapper.toEntity(customer);
        CustomerJpaEntity saved = jpaRepository.save(entity);

        log.debug("Customer persisted: customerId={}, version={}",
                saved.getId(), saved.getVersion());

        return mapper.toDomain(saved);
    }

    /**
     * {@inheritDoc}
     *
     * <p>Looks up the customer by its strongly-typed {@link CustomerId} and maps
     * the result back to the domain model if found.</p>
     */
    @Override
    public Optional<Customer> findById(CustomerId customerId) {
        log.debug("Finding customer: customerId={}", customerId.asString());

        Optional<Customer> result = jpaRepository.findById(customerId.value())
                .map(entity -> {
                    log.debug("Customer found: customerId={}, company={}, status={}",
                            entity.getId(), entity.getCompanyName(), entity.getStatus());
                    return mapper.toDomain(entity);
                });

        if (result.isEmpty()) {
            log.warn("Customer not found: customerId={}", customerId.asString());
        }

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Looks up the customer by email address and maps the result back to the
     * domain model if found.</p>
     */
    @Override
    public Optional<Customer> findByEmail(String email) {
        log.debug("Finding customer by email: email={}", email);

        Optional<Customer> result = jpaRepository.findByEmail(email)
                .map(entity -> {
                    log.debug("Customer found by email: customerId={}, email={}",
                            entity.getId(), entity.getEmail());
                    return mapper.toDomain(entity);
                });

        if (result.isEmpty()) {
            log.warn("Customer not found by email: email={}", email);
        }

        return result;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Retrieves all customers with the given status, ordered by company name ascending.</p>
     */
    @Override
    public List<Customer> findByStatus(CustomerStatus status) {
        log.debug("Finding customers by status: status={}", status);

        List<CustomerJpaEntity> entities = jpaRepository
                .findByStatusOrderByCompanyNameAsc(status.name());

        log.debug("Found {} customers with status: status={}",
                entities.size(), status);

        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Performs a case-insensitive partial match on company name using JPQL LIKE.</p>
     */
    @Override
    public List<Customer> searchByCompanyName(String companyNameFragment) {
        log.debug("Searching customers by company name: fragment='{}'", companyNameFragment);

        List<CustomerJpaEntity> entities = jpaRepository
                .searchByCompanyName(companyNameFragment);

        log.debug("Found {} customers matching company name fragment: fragment='{}'",
                entities.size(), companyNameFragment);

        return entities.stream()
                .map(mapper::toDomain)
                .toList();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Contract persistence is not yet implemented. Returns an empty list until
     * the ContractJpaEntity and SpringDataContractRepository are created.</p>
     */
    @Override
    public List<Contract> findContractsByCustomerId(CustomerId customerId) {
        log.debug("Finding contracts for customer: customerId={}", customerId.asString());
        log.warn("Contract persistence not yet implemented — returning empty list for customerId={}",
                customerId.asString());

        // TODO: Implement when ContractJpaEntity and SpringDataContractRepository are created
        return List.of();
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the Spring Data repository's existence check by customer ID.</p>
     */
    @Override
    public boolean existsById(CustomerId customerId) {
        boolean exists = jpaRepository.existsById(customerId.value());
        log.debug("Customer exists check: customerId={}, exists={}", customerId.asString(), exists);
        return exists;
    }

    /**
     * {@inheritDoc}
     *
     * <p>Delegates to the Spring Data repository's email existence check.</p>
     */
    @Override
    public boolean existsByEmail(String email) {
        boolean exists = jpaRepository.existsByEmail(email);
        log.debug("Email exists check: email={}, exists={}", email, exists);
        return exists;
    }
}
