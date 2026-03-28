package com.freightflow.customerservice.domain.port;

import com.freightflow.commons.domain.CustomerId;
import com.freightflow.customerservice.domain.model.Contract;
import com.freightflow.customerservice.domain.model.Customer;
import com.freightflow.customerservice.domain.model.CustomerStatus;

import java.util.List;
import java.util.Optional;

/**
 * Outbound port for customer persistence.
 *
 * <p>This interface defines the contract that the domain layer expects from
 * the persistence layer (Dependency Inversion Principle). The domain does NOT
 * depend on JPA, Hibernate, or any infrastructure technology.</p>
 *
 * <p>The implementation (JPA adapter) lives in the infrastructure layer
 * and adapts this port to Spring Data JPA.</p>
 *
 * @see com.freightflow.customerservice.infrastructure.adapter.out.persistence
 */
public interface CustomerRepository {

    /**
     * Persists a new or updated customer.
     *
     * @param customer the customer aggregate to save
     * @return the saved customer (with updated version)
     */
    Customer save(Customer customer);

    /**
     * Finds a customer by its ID.
     *
     * @param customerId the customer identifier
     * @return the customer, or empty if not found
     */
    Optional<Customer> findById(CustomerId customerId);

    /**
     * Finds a customer by email address.
     *
     * @param email the email address to search for
     * @return the customer, or empty if not found
     */
    Optional<Customer> findByEmail(String email);

    /**
     * Finds all customers in a given status.
     *
     * @param status the customer status to filter by
     * @return list of customers (may be empty)
     */
    List<Customer> findByStatus(CustomerStatus status);

    /**
     * Searches customers by company name (case-insensitive partial match).
     *
     * @param companyNameFragment the search fragment
     * @return list of matching customers (may be empty)
     */
    List<Customer> searchByCompanyName(String companyNameFragment);

    /**
     * Finds all contracts for a customer.
     *
     * @param customerId the customer identifier
     * @return list of contracts (may be empty)
     */
    List<Contract> findContractsByCustomerId(CustomerId customerId);

    /**
     * Checks whether a customer exists.
     *
     * @param customerId the customer identifier
     * @return true if the customer exists
     */
    boolean existsById(CustomerId customerId);

    /**
     * Checks whether an email address is already registered.
     *
     * @param email the email to check
     * @return true if the email is already in use
     */
    boolean existsByEmail(String email);
}
