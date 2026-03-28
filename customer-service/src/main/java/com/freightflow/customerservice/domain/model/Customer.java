package com.freightflow.customerservice.domain.model;

import com.freightflow.commons.domain.CustomerId;
import com.freightflow.commons.domain.Money;
import com.freightflow.commons.exception.ConflictException;
import com.freightflow.customerservice.domain.event.CreditAllocated;
import com.freightflow.customerservice.domain.event.CreditReleased;
import com.freightflow.customerservice.domain.event.CustomerEvent;
import com.freightflow.customerservice.domain.event.CustomerRegistered;
import com.freightflow.customerservice.domain.event.CustomerSuspended;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;

/**
 * Customer Aggregate Root — the central domain entity managing customer lifecycle and credit.
 *
 * <p>This class encapsulates all business rules and invariants for a customer account.
 * State transitions are enforced via the {@link CustomerStatus} state machine.
 * Every state change produces a domain event that is collected for later publishing.</p>
 *
 * <h3>Design Patterns Applied</h3>
 * <ul>
 *   <li><b>Aggregate Root</b> (DDD) — single entry point for all customer mutations</li>
 *   <li><b>State Pattern</b> — transitions governed by {@link CustomerStatus#canTransitionTo}</li>
 *   <li><b>Domain Events</b> — state changes emit events for downstream services</li>
 *   <li><b>Factory Method</b> — {@link #register} encapsulates creation logic</li>
 *   <li><b>Tell, Don't Ask</b> — methods mutate state internally, callers don't set fields</li>
 * </ul>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>A customer always has an ID, company name, email, and type</li>
 *   <li>State transitions follow the state machine (see {@link CustomerStatus})</li>
 *   <li>Credit allocation cannot exceed the credit limit</li>
 *   <li>A closed customer cannot be modified further</li>
 * </ul>
 *
 * @see CustomerStatus
 * @see CustomerEvent
 */
public class Customer {

    private final CustomerId customerId;
    private String companyName;
    private String email;
    private String phone;
    private Address address;
    private CustomerType customerType;
    private CustomerStatus status;
    private Money creditLimit;
    private Money currentCreditUsed;
    private Instant registeredAt;
    private Instant updatedAt;
    private long version;

    /** Uncommitted domain events — cleared after publishing. */
    private final List<CustomerEvent> domainEvents = new ArrayList<>();

    /**
     * Private constructor — use {@link #register} factory method.
     * Enforces that all customers go through proper validation (Open/Closed Principle).
     */
    private Customer(CustomerId customerId, String companyName, String email,
                     String phone, Address address, CustomerType customerType,
                     Money creditLimit) {
        this.customerId = Objects.requireNonNull(customerId, "Customer ID must not be null");
        this.companyName = Objects.requireNonNull(companyName, "Company name must not be null");
        this.email = Objects.requireNonNull(email, "Email must not be null");
        this.phone = phone;
        this.address = address;
        this.customerType = Objects.requireNonNull(customerType, "Customer type must not be null");
        this.creditLimit = Objects.requireNonNull(creditLimit, "Credit limit must not be null");
        this.currentCreditUsed = Money.zero(creditLimit.currency().getCurrencyCode());
        this.status = CustomerStatus.ACTIVE;
        this.registeredAt = Instant.now();
        this.updatedAt = this.registeredAt;
        this.version = 0;
    }

    // ==================== Factory Method ====================

    /**
     * Factory method to register a new customer with ACTIVE status.
     *
     * <p>Validates all inputs and produces a {@link CustomerRegistered} domain event.
     * The customer is not persisted until the calling use case saves it.</p>
     *
     * @param companyName  the company name
     * @param email        the contact email
     * @param phone        the contact phone (nullable)
     * @param address      the physical address (nullable)
     * @param customerType the customer type
     * @param creditLimit  the initial credit limit
     * @return a new Customer in ACTIVE status
     * @throws IllegalArgumentException if email format is invalid
     */
    public static Customer register(String companyName, String email, String phone,
                                     Address address, CustomerType customerType,
                                     Money creditLimit) {
        if (companyName == null || companyName.isBlank()) {
            throw new IllegalArgumentException("Company name must not be blank");
        }
        if (email == null || !email.contains("@")) {
            throw new IllegalArgumentException("Invalid email format: " + email);
        }

        var customer = new Customer(
                CustomerId.generate(), companyName, email, phone,
                address, customerType, creditLimit
        );

        customer.registerEvent(new CustomerRegistered(
                customer.customerId,
                customer.companyName,
                customer.email,
                customer.customerType,
                customer.creditLimit,
                customer.registeredAt
        ));

        return customer;
    }

    // ==================== State Transitions (Tell, Don't Ask) ====================

    /**
     * Updates the customer profile with new details.
     *
     * <p>Only active or suspended customers can update their profiles.
     * Closed customers cannot be modified.</p>
     *
     * @param companyName the new company name
     * @param email       the new email address
     * @param phone       the new phone number
     * @param address     the new address
     * @throws ConflictException if the customer is closed
     */
    public void updateProfile(String companyName, String email, String phone, Address address) {
        if (this.status == CustomerStatus.CLOSED) {
            throw ConflictException.invalidStateTransition(
                    "Customer", customerId.asString(), status.name(), "PROFILE_UPDATE");
        }

        this.companyName = Objects.requireNonNull(companyName, "Company name must not be null");
        this.email = Objects.requireNonNull(email, "Email must not be null");
        this.phone = phone;
        this.address = address;
        this.updatedAt = Instant.now();
    }

    /**
     * Suspends the customer account.
     *
     * <p>Transition: ACTIVE → SUSPENDED</p>
     *
     * @param reason the suspension reason (for audit)
     * @throws ConflictException if the customer is not in ACTIVE status
     */
    public void suspend(String reason) {
        Objects.requireNonNull(reason, "Suspension reason must not be null");
        assertTransition(CustomerStatus.SUSPENDED);

        this.status = CustomerStatus.SUSPENDED;
        this.updatedAt = Instant.now();

        registerEvent(new CustomerSuspended(
                this.customerId,
                reason,
                this.updatedAt
        ));
    }

    /**
     * Re-activates a suspended customer account.
     *
     * <p>Transition: SUSPENDED → ACTIVE</p>
     *
     * @throws ConflictException if the customer is not in SUSPENDED status
     */
    public void activate() {
        assertTransition(CustomerStatus.ACTIVE);

        this.status = CustomerStatus.ACTIVE;
        this.updatedAt = Instant.now();
    }

    // ==================== Credit Operations ====================

    /**
     * Checks whether the customer has sufficient credit available for the requested amount.
     *
     * @param amount the amount to check availability for
     * @return true if sufficient credit is available
     */
    public boolean checkCreditAvailability(Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");
        Money available = creditLimit.subtract(currentCreditUsed);
        return available.amount().compareTo(amount.amount()) >= 0;
    }

    /**
     * Allocates credit for a booking or transaction.
     *
     * <p>Increases the current credit used. Fails if the allocation would exceed
     * the credit limit or if the customer is not active.</p>
     *
     * @param amount the credit amount to allocate
     * @throws ConflictException         if the customer is not active
     * @throws IllegalArgumentException  if insufficient credit is available
     */
    public void allocateCredit(Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");

        if (this.status != CustomerStatus.ACTIVE) {
            throw ConflictException.invalidStateTransition(
                    "Customer", customerId.asString(), status.name(), "CREDIT_ALLOCATION");
        }

        if (!checkCreditAvailability(amount)) {
            throw new IllegalArgumentException(
                    "Insufficient credit: available=%s, requested=%s".formatted(
                            creditLimit.subtract(currentCreditUsed), amount));
        }

        this.currentCreditUsed = this.currentCreditUsed.add(amount);
        this.updatedAt = Instant.now();

        registerEvent(new CreditAllocated(
                this.customerId,
                amount,
                this.currentCreditUsed,
                this.creditLimit,
                this.updatedAt
        ));
    }

    /**
     * Releases previously allocated credit (e.g., on booking cancellation or payment).
     *
     * @param amount the credit amount to release
     * @throws IllegalArgumentException if the release amount exceeds current usage
     */
    public void releaseCredit(Money amount) {
        Objects.requireNonNull(amount, "Amount must not be null");

        if (amount.amount().compareTo(this.currentCreditUsed.amount()) > 0) {
            throw new IllegalArgumentException(
                    "Cannot release more credit than currently used: used=%s, releasing=%s".formatted(
                            this.currentCreditUsed, amount));
        }

        this.currentCreditUsed = this.currentCreditUsed.subtract(amount);
        this.updatedAt = Instant.now();

        registerEvent(new CreditReleased(
                this.customerId,
                amount,
                this.currentCreditUsed,
                this.creditLimit,
                this.updatedAt
        ));
    }

    // ==================== Domain Event Management ====================

    /**
     * Reconstitutes a Customer aggregate from persisted state.
     *
     * <p>This method is used ONLY by the persistence adapter when loading a customer
     * from the database. It bypasses the normal factory method and validation because
     * the data has already been validated when it was originally created.</p>
     *
     * <p>No domain events are emitted — this is state reconstruction, not a new action.</p>
     *
     * @return a Customer aggregate in its persisted state
     */
    public static Customer reconstitute(CustomerId customerId, String companyName, String email,
                                         String phone, Address address, CustomerType customerType,
                                         CustomerStatus status, Money creditLimit,
                                         Money currentCreditUsed, Instant registeredAt,
                                         Instant updatedAt, long version) {
        var customer = new Customer(customerId, companyName, email, phone,
                address, customerType, creditLimit);
        customer.status = status;
        customer.currentCreditUsed = currentCreditUsed;
        customer.registeredAt = registeredAt;
        customer.updatedAt = updatedAt;
        customer.version = version;
        return customer;
    }

    /**
     * Returns and clears all uncommitted domain events.
     *
     * <p>This method is called by the infrastructure layer after persisting the aggregate,
     * to publish events to Kafka. Events are cleared to prevent duplicate publishing.</p>
     *
     * @return an unmodifiable list of domain events
     */
    public List<CustomerEvent> pullDomainEvents() {
        List<CustomerEvent> events = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return events;
    }

    private void registerEvent(CustomerEvent event) {
        domainEvents.add(event);
    }

    // ==================== State Transition Guard ====================

    /**
     * Validates that a state transition is allowed; throws ConflictException if not.
     *
     * @param target the desired target state
     * @throws ConflictException if the transition is not valid
     */
    private void assertTransition(CustomerStatus target) {
        if (!status.canTransitionTo(target)) {
            throw ConflictException.invalidStateTransition(
                    "Customer", customerId.asString(), status.name(), target.name());
        }
    }

    // ==================== Accessors (No Setters — Immutable from outside) ====================

    public CustomerId getCustomerId() { return customerId; }
    public String getCompanyName() { return companyName; }
    public String getEmail() { return email; }
    public String getPhone() { return phone; }
    public Address getAddress() { return address; }
    public CustomerType getCustomerType() { return customerType; }
    public CustomerStatus getStatus() { return status; }
    public Money getCreditLimit() { return creditLimit; }
    public Money getCurrentCreditUsed() { return currentCreditUsed; }
    public Instant getRegisteredAt() { return registeredAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    @Override
    public String toString() {
        return "Customer[id=%s, company=%s, status=%s, type=%s, credit=%s/%s]".formatted(
                customerId.asString(), companyName, status, customerType,
                currentCreditUsed, creditLimit);
    }
}
