package com.freightflow.customerservice.application.command;

import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.Money;
import com.freightflow.commons.exception.ConflictException;
import com.freightflow.commons.exception.ResourceNotFoundException;
import com.freightflow.commons.observability.profiling.Profiled;
import com.freightflow.customerservice.domain.event.CustomerEvent;
import com.freightflow.customerservice.domain.model.Address;
import com.freightflow.customerservice.domain.model.Customer;
import com.freightflow.customerservice.domain.model.CustomerType;
import com.freightflow.customerservice.domain.port.CustomerRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * Command Handler — handles all write operations for the customer aggregate.
 *
 * <p>This is the <b>write side</b> of the application. It:</p>
 * <ol>
 *   <li>Receives a method call with validated parameters</li>
 *   <li>Loads the aggregate (or creates a new one)</li>
 *   <li>Executes domain logic on the aggregate</li>
 *   <li>Persists the aggregate state</li>
 *   <li>Pulls and logs domain events for downstream publishing</li>
 * </ol>
 *
 * <h3>Design Patterns</h3>
 * <ul>
 *   <li><b>Command Handler</b> — centralized write operations for the Customer aggregate</li>
 *   <li><b>Transaction Script</b> — each method represents a complete use case</li>
 *   <li><b>Domain Events</b> — events pulled from the aggregate after persistence</li>
 * </ul>
 *
 * @see com.freightflow.customerservice.application.query.CustomerQueryHandler
 * @see Customer
 */
@Service
public class CustomerCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(CustomerCommandHandler.class);

    private final CustomerRepository customerRepository;

    /**
     * Constructor injection — all dependencies are final (Dependency Inversion Principle).
     *
     * @param customerRepository the customer persistence port
     */
    public CustomerCommandHandler(CustomerRepository customerRepository) {
        this.customerRepository = Objects.requireNonNull(customerRepository,
                "CustomerRepository must not be null");
    }

    /**
     * Registers a new customer with ACTIVE status.
     *
     * <p>Validates that the email is not already in use, creates the aggregate
     * via the factory method, persists it, and logs domain events.</p>
     *
     * @param companyName  the company name
     * @param email        the contact email
     * @param phone        the contact phone
     * @param address      the physical address
     * @param customerType the customer type
     * @param creditLimit  the initial credit limit
     * @return the registered customer
     * @throws ConflictException if the email is already registered
     */
    @Transactional
    @Profiled(value = "registerCustomer", slowThresholdMs = 500)
    public Customer registerCustomer(String companyName, String email, String phone,
                                      Address address, CustomerType customerType,
                                      Money creditLimit) {
        log.debug("Registering customer: companyName={}, email={}, type={}",
                companyName, email, customerType);

        if (customerRepository.existsByEmail(email)) {
            throw new ConflictException(
                    "EMAIL_ALREADY_EXISTS",
                    "Email '%s' is already registered".formatted(email),
                    "DUPLICATE");
        }

        Customer customer = Customer.register(
                companyName, email, phone, address, customerType, creditLimit
        );

        Customer saved = customerRepository.save(customer);
        List<CustomerEvent> events = saved.pullDomainEvents();

        log.info("Customer registered: customerId={}, companyName={}, type={}, events={}",
                saved.getCustomerId().asString(), companyName, customerType, events.size());

        return saved;
    }

    /**
     * Updates an existing customer's profile.
     *
     * @param customerId  the customer to update
     * @param companyName the new company name
     * @param email       the new email
     * @param phone       the new phone
     * @param address     the new address
     * @return the updated customer
     * @throws ResourceNotFoundException if the customer does not exist
     */
    @Transactional
    @Profiled(value = "updateCustomerProfile", slowThresholdMs = 300)
    public Customer updateCustomerProfile(String customerId, String companyName,
                                           String email, String phone, Address address) {
        log.debug("Updating customer profile: customerId={}", customerId);

        Customer customer = loadCustomerOrThrow(customerId);
        customer.updateProfile(companyName, email, phone, address);

        Customer saved = customerRepository.save(customer);

        log.info("Customer profile updated: customerId={}, companyName={}",
                saved.getCustomerId().asString(), companyName);

        return saved;
    }

    /**
     * Suspends a customer account.
     *
     * @param customerId the customer to suspend
     * @param reason     the suspension reason
     * @return the suspended customer
     * @throws ResourceNotFoundException if the customer does not exist
     * @throws ConflictException         if the customer cannot be suspended from current state
     */
    @Transactional
    @Profiled(value = "suspendCustomer", slowThresholdMs = 300)
    public Customer suspendCustomer(String customerId, String reason) {
        log.debug("Suspending customer: customerId={}, reason={}", customerId, reason);

        Customer customer = loadCustomerOrThrow(customerId);
        customer.suspend(reason);

        Customer saved = customerRepository.save(customer);
        List<CustomerEvent> events = saved.pullDomainEvents();

        log.info("Customer suspended: customerId={}, reason={}, events={}",
                saved.getCustomerId().asString(), reason, events.size());

        return saved;
    }

    /**
     * Re-activates a suspended customer account.
     *
     * @param customerId the customer to activate
     * @return the activated customer
     * @throws ResourceNotFoundException if the customer does not exist
     * @throws ConflictException         if the customer cannot be activated from current state
     */
    @Transactional
    @Profiled(value = "activateCustomer", slowThresholdMs = 300)
    public Customer activateCustomer(String customerId) {
        log.debug("Activating customer: customerId={}", customerId);

        Customer customer = loadCustomerOrThrow(customerId);
        customer.activate();

        Customer saved = customerRepository.save(customer);

        log.info("Customer activated: customerId={}", saved.getCustomerId().asString());

        return saved;
    }

    /**
     * Allocates credit for a customer (e.g., when a booking is confirmed).
     *
     * @param customerId the customer whose credit to allocate
     * @param amount     the amount to allocate
     * @return the customer with updated credit
     * @throws ResourceNotFoundException if the customer does not exist
     * @throws ConflictException         if the customer is not active
     * @throws IllegalArgumentException  if insufficient credit is available
     */
    @Transactional
    @Profiled(value = "allocateCredit", slowThresholdMs = 300)
    public Customer allocateCredit(String customerId, Money amount) {
        log.debug("Allocating credit: customerId={}, amount={}", customerId, amount);

        Customer customer = loadCustomerOrThrow(customerId);
        customer.allocateCredit(amount);

        Customer saved = customerRepository.save(customer);
        List<CustomerEvent> events = saved.pullDomainEvents();

        log.info("Credit allocated: customerId={}, amount={}, currentUsed={}, events={}",
                saved.getCustomerId().asString(), amount,
                saved.getCurrentCreditUsed(), events.size());

        return saved;
    }

    /**
     * Releases previously allocated credit (e.g., on booking cancellation).
     *
     * @param customerId the customer whose credit to release
     * @param amount     the amount to release
     * @return the customer with updated credit
     * @throws ResourceNotFoundException if the customer does not exist
     * @throws IllegalArgumentException  if the release amount exceeds current usage
     */
    @Transactional
    @Profiled(value = "releaseCredit", slowThresholdMs = 300)
    public Customer releaseCredit(String customerId, Money amount) {
        log.debug("Releasing credit: customerId={}, amount={}", customerId, amount);

        Customer customer = loadCustomerOrThrow(customerId);
        customer.releaseCredit(amount);

        Customer saved = customerRepository.save(customer);
        List<CustomerEvent> events = saved.pullDomainEvents();

        log.info("Credit released: customerId={}, amount={}, currentUsed={}, events={}",
                saved.getCustomerId().asString(), amount,
                saved.getCurrentCreditUsed(), events.size());

        return saved;
    }

    /**
     * Loads a customer by ID or throws ResourceNotFoundException.
     *
     * @param customerId the customer ID string
     * @return the loaded customer aggregate
     * @throws ResourceNotFoundException if not found
     */
    private Customer loadCustomerOrThrow(String customerId) {
        return customerRepository.findById(CustomerId.fromString(customerId))
                .orElseThrow(() -> {
                    log.warn("Customer not found: customerId={}", customerId);
                    return ResourceNotFoundException.forCustomer(customerId);
                });
    }
}
