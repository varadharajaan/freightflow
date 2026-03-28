package com.freightflow.customerservice.application.query;

import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.exception.ResourceNotFoundException;
import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.customerservice.domain.model.Contract;
import com.freightflow.customerservice.domain.model.Customer;
import com.freightflow.customerservice.domain.model.CustomerStatus;
import com.freightflow.customerservice.domain.port.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

/**
 * Query Handler — handles all <b>read</b> operations for the customer domain.
 *
 * <p>This is the <b>read side</b> of the application. It provides optimized
 * query methods for retrieving customers, searching, and loading contracts.
 * Results are cached where appropriate to reduce database load.</p>
 *
 * <h3>Caching Strategy</h3>
 * <p>Individual customer lookups are cached in the "customers" cache region.
 * Cache is evicted when the customer is updated by the command handler.
 * Search results are not cached due to their dynamic nature.</p>
 *
 * @see com.freightflow.customerservice.application.command.CustomerCommandHandler
 * @see CustomerRepository
 */
@Service
public class CustomerQueryHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomerQueryHandler.class);

    private final CustomerRepository customerRepository;

    /**
     * Constructor injection — all dependencies are final (Dependency Inversion Principle).
     *
     * @param customerRepository the customer persistence port
     */
    public CustomerQueryHandler(CustomerRepository customerRepository) {
        this.customerRepository = Objects.requireNonNull(customerRepository,
                "CustomerRepository must not be null");
    }

    /**
     * Retrieves a single customer by its ID.
     *
     * <h3>Spring Advanced Feature: @Cacheable</h3>
     * <p>Results are cached in the "customers" cache region. Cache is evicted
     * when the customer is updated by the command handler.</p>
     *
     * @param customerId the customer aggregate ID
     * @return the customer
     * @throws ResourceNotFoundException if no customer exists for the given ID
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "customers", key = "#customerId", unless = "#result == null")
    @Profiled(value = "getCustomerQuery", slowThresholdMs = 200)
    public Customer getCustomer(String customerId) {
        log.debug("Querying customer: customerId={}", customerId);

        return customerRepository.findById(CustomerId.fromString(customerId))
                .orElseThrow(() -> {
                    log.warn("Customer not found: customerId={}", customerId);
                    return ResourceNotFoundException.forCustomer(customerId);
                });
    }

    /**
     * Searches customers by company name (case-insensitive partial match).
     *
     * @param companyNameFragment the search fragment
     * @return list of matching customers (may be empty, never null)
     */
    @Transactional(readOnly = true)
    @Profiled(value = "searchCustomers", slowThresholdMs = 300)
    public List<Customer> searchCustomers(String companyNameFragment) {
        log.debug("Searching customers: fragment='{}'", companyNameFragment);

        List<Customer> results = customerRepository.searchByCompanyName(companyNameFragment);

        log.debug("Found {} customer(s) matching '{}'", results.size(), companyNameFragment);
        return results;
    }

    /**
     * Retrieves all customers in a given status.
     *
     * @param status the customer status to filter by
     * @return list of customers in the given status (may be empty, never null)
     */
    @Transactional(readOnly = true)
    @Profiled(value = "getCustomersByStatus", slowThresholdMs = 300)
    public List<Customer> getCustomersByStatus(CustomerStatus status) {
        log.debug("Querying customers by status: status={}", status);

        List<Customer> results = customerRepository.findByStatus(status);

        log.debug("Found {} customer(s) with status={}", results.size(), status);
        return results;
    }

    /**
     * Retrieves all contracts for a customer.
     *
     * @param customerId the customer ID
     * @return list of contracts (may be empty, never null)
     * @throws ResourceNotFoundException if the customer does not exist
     */
    @Transactional(readOnly = true)
    @Cacheable(value = "customerContracts", key = "#customerId", unless = "#result.isEmpty()")
    @Profiled(value = "getCustomerContracts", slowThresholdMs = 200)
    public List<Contract> getContracts(String customerId) {
        log.debug("Querying contracts: customerId={}", customerId);

        if (!customerRepository.existsById(CustomerId.fromString(customerId))) {
            log.warn("Customer not found for contract query: customerId={}", customerId);
            throw ResourceNotFoundException.forCustomer(customerId);
        }

        List<Contract> contracts = customerRepository.findContractsByCustomerId(
                CustomerId.fromString(customerId));

        log.debug("Found {} contract(s) for customerId={}", contracts.size(), customerId);
        return contracts;
    }
}
