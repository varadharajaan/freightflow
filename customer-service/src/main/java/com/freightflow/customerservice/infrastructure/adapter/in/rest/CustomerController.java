package com.freightflow.customerservice.infrastructure.adapter.in.rest;

import com.freightflow.commons.domain.FreightFlowConstants;
import com.freightflow.commons.domain.Money;
import com.freightflow.customerservice.application.command.CustomerCommandHandler;
import com.freightflow.customerservice.application.query.CustomerQueryHandler;
import com.freightflow.customerservice.domain.model.Address;
import com.freightflow.customerservice.domain.model.Contract;
import com.freightflow.customerservice.domain.model.Customer;
import com.freightflow.customerservice.infrastructure.adapter.in.rest.dto.CreateCustomerRequest;
import com.freightflow.customerservice.infrastructure.adapter.in.rest.dto.CustomerResponse;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.math.BigDecimal;
import java.util.List;
import java.util.Objects;

/**
 * REST controller for managing customers, contracts, and credit operations.
 *
 * <p>This is the primary inbound adapter in the Hexagonal Architecture, translating
 * HTTP requests into application-layer commands and queries. It delegates all business
 * logic to the command and query handlers and maps domain objects to REST DTOs.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code POST   /api/v1/customers}                         — register a new customer</li>
 *   <li>{@code GET    /api/v1/customers/{customerId}}             — retrieve a customer</li>
 *   <li>{@code PUT    /api/v1/customers/{customerId}}             — update customer profile</li>
 *   <li>{@code POST   /api/v1/customers/{customerId}/suspend}     — suspend a customer</li>
 *   <li>{@code POST   /api/v1/customers/{customerId}/activate}    — re-activate a customer</li>
 *   <li>{@code GET    /api/v1/customers?search={name}}            — search customers by name</li>
 *   <li>{@code GET    /api/v1/customers/{customerId}/contracts}   — get customer contracts</li>
 *   <li>{@code POST   /api/v1/customers/{customerId}/credit/allocate} — allocate credit</li>
 *   <li>{@code POST   /api/v1/customers/{customerId}/credit/release}  — release credit</li>
 * </ul>
 *
 * @see CustomerCommandHandler
 * @see CustomerQueryHandler
 * @see CustomerResponse
 */
@RestController
@RequestMapping(FreightFlowConstants.API_V1_CUSTOMERS)
public class CustomerController {

    private static final Logger log = LoggerFactory.getLogger(CustomerController.class);

    private final CustomerCommandHandler commandHandler;
    private final CustomerQueryHandler queryHandler;

    /**
     * Creates a new {@code CustomerController} with the required handlers.
     *
     * @param commandHandler the customer command handler (must not be null)
     * @param queryHandler   the customer query handler (must not be null)
     */
    public CustomerController(CustomerCommandHandler commandHandler,
                               CustomerQueryHandler queryHandler) {
        this.commandHandler = Objects.requireNonNull(commandHandler,
                "CustomerCommandHandler must not be null");
        this.queryHandler = Objects.requireNonNull(queryHandler,
                "CustomerQueryHandler must not be null");
    }

    /**
     * Registers a new customer.
     *
     * @param request the customer registration request (validated via Bean Validation)
     * @return 201 Created with the registered customer
     */
    @PostMapping
    public ResponseEntity<CustomerResponse> registerCustomer(
            @Valid @RequestBody CreateCustomerRequest request) {
        log.debug("POST /api/v1/customers — registering customer: companyName={}, email={}",
                request.companyName(), request.email());

        Address address = request.address() != null
                ? new Address(request.address().street(), request.address().city(),
                              request.address().state(), request.address().postalCode(),
                              request.address().country())
                : null;

        Customer customer = commandHandler.registerCustomer(
                request.companyName(),
                request.email(),
                request.phone(),
                address,
                request.customerType(),
                Money.of(request.creditLimitUsd(), "USD")
        );

        CustomerResponse response = CustomerResponse.from(customer);

        log.info("Customer registered successfully: customerId={}, companyName={}",
                response.customerId(), response.companyName());

        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    /**
     * Retrieves a customer by its unique identifier.
     *
     * @param customerId the customer UUID (path variable)
     * @return 200 OK with the customer details
     */
    @GetMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> getCustomer(@PathVariable String customerId) {
        log.debug("GET /api/v1/customers/{} — fetching customer", customerId);

        Customer customer = queryHandler.getCustomer(customerId);
        CustomerResponse response = CustomerResponse.from(customer);

        log.info("Customer retrieved: customerId={}, status={}",
                response.customerId(), response.status());

        return ResponseEntity.ok(response);
    }

    /**
     * Updates a customer's profile.
     *
     * @param customerId the customer UUID (path variable)
     * @param request    the update request containing new profile data
     * @return 200 OK with the updated customer
     */
    @PutMapping("/{customerId}")
    public ResponseEntity<CustomerResponse> updateCustomer(
            @PathVariable String customerId,
            @Valid @RequestBody CreateCustomerRequest request) {
        log.debug("PUT /api/v1/customers/{} — updating profile", customerId);

        Address address = request.address() != null
                ? new Address(request.address().street(), request.address().city(),
                              request.address().state(), request.address().postalCode(),
                              request.address().country())
                : null;

        Customer customer = commandHandler.updateCustomerProfile(
                customerId, request.companyName(), request.email(),
                request.phone(), address
        );

        CustomerResponse response = CustomerResponse.from(customer);

        log.info("Customer updated: customerId={}, companyName={}",
                response.customerId(), response.companyName());

        return ResponseEntity.ok(response);
    }

    /**
     * Searches customers by company name.
     *
     * @param search the company name search fragment (query parameter)
     * @return 200 OK with a list of matching customers
     */
    @GetMapping
    public ResponseEntity<List<CustomerResponse>> searchCustomers(
            @RequestParam(required = false) String search) {
        log.debug("GET /api/v1/customers?search={} — searching customers", search);

        List<CustomerResponse> responses;
        if (search != null && !search.isBlank()) {
            responses = queryHandler.searchCustomers(search)
                    .stream()
                    .map(CustomerResponse::from)
                    .toList();
        } else {
            responses = queryHandler.getCustomersByStatus(
                    com.freightflow.customerservice.domain.model.CustomerStatus.ACTIVE)
                    .stream()
                    .map(CustomerResponse::from)
                    .toList();
        }

        log.info("Search returned {} customer(s)", responses.size());

        return ResponseEntity.ok(responses);
    }

    /**
     * Suspends a customer account.
     *
     * @param customerId the customer UUID (path variable)
     * @param reason     the suspension reason (query parameter)
     * @return 200 OK with the suspended customer
     */
    @PostMapping("/{customerId}/suspend")
    public ResponseEntity<CustomerResponse> suspendCustomer(
            @PathVariable String customerId,
            @RequestParam String reason) {
        log.debug("POST /api/v1/customers/{}/suspend — reason='{}'", customerId, reason);

        Customer customer = commandHandler.suspendCustomer(customerId, reason);
        CustomerResponse response = CustomerResponse.from(customer);

        log.info("Customer suspended: customerId={}", response.customerId());

        return ResponseEntity.ok(response);
    }

    /**
     * Re-activates a suspended customer account.
     *
     * @param customerId the customer UUID (path variable)
     * @return 200 OK with the activated customer
     */
    @PostMapping("/{customerId}/activate")
    public ResponseEntity<CustomerResponse> activateCustomer(@PathVariable String customerId) {
        log.debug("POST /api/v1/customers/{}/activate", customerId);

        Customer customer = commandHandler.activateCustomer(customerId);
        CustomerResponse response = CustomerResponse.from(customer);

        log.info("Customer activated: customerId={}", response.customerId());

        return ResponseEntity.ok(response);
    }

    /**
     * Retrieves all contracts for a customer.
     *
     * @param customerId the customer UUID (path variable)
     * @return 200 OK with a list of contracts
     */
    @GetMapping("/{customerId}/contracts")
    public ResponseEntity<List<Contract>> getContracts(@PathVariable String customerId) {
        log.debug("GET /api/v1/customers/{}/contracts — fetching contracts", customerId);

        List<Contract> contracts = queryHandler.getContracts(customerId);

        log.info("Retrieved {} contract(s) for customerId={}", contracts.size(), customerId);

        return ResponseEntity.ok(contracts);
    }

    /**
     * Allocates credit for a customer.
     *
     * @param customerId the customer UUID (path variable)
     * @param amount     the credit amount to allocate (query parameter)
     * @return 200 OK with the updated customer
     */
    @PostMapping("/{customerId}/credit/allocate")
    public ResponseEntity<CustomerResponse> allocateCredit(
            @PathVariable String customerId,
            @RequestParam BigDecimal amount) {
        log.debug("POST /api/v1/customers/{}/credit/allocate — amount={}", customerId, amount);

        Customer customer = commandHandler.allocateCredit(customerId, Money.of(amount, "USD"));
        CustomerResponse response = CustomerResponse.from(customer);

        log.info("Credit allocated: customerId={}, amount={}", response.customerId(), amount);

        return ResponseEntity.ok(response);
    }

    /**
     * Releases previously allocated credit for a customer.
     *
     * @param customerId the customer UUID (path variable)
     * @param amount     the credit amount to release (query parameter)
     * @return 200 OK with the updated customer
     */
    @PostMapping("/{customerId}/credit/release")
    public ResponseEntity<CustomerResponse> releaseCredit(
            @PathVariable String customerId,
            @RequestParam BigDecimal amount) {
        log.debug("POST /api/v1/customers/{}/credit/release — amount={}", customerId, amount);

        Customer customer = commandHandler.releaseCredit(customerId, Money.of(amount, "USD"));
        CustomerResponse response = CustomerResponse.from(customer);

        log.info("Credit released: customerId={}, amount={}", response.customerId(), amount);

        return ResponseEntity.ok(response);
    }
}
