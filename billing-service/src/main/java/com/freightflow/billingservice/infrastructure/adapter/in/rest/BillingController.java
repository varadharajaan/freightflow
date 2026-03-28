package com.freightflow.billingservice.infrastructure.adapter.in.rest;

import com.freightflow.billingservice.application.command.BillingCommandHandler;
import com.freightflow.billingservice.application.query.BillingQueryHandler;
import com.freightflow.billingservice.domain.model.Invoice;
import com.freightflow.billingservice.domain.model.Payment;
import com.freightflow.billingservice.infrastructure.adapter.in.rest.dto.InvoiceResponse;
import com.freightflow.billingservice.infrastructure.adapter.in.rest.dto.RecordPaymentRequest;
import jakarta.validation.Valid;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * REST controller for billing and invoicing operations.
 *
 * <p>This is the primary inbound adapter in the Hexagonal Architecture, translating
 * HTTP requests into application-layer commands and queries. It delegates all business
 * logic to the command and query handlers and maps domain objects to REST DTOs.</p>
 *
 * <h3>Endpoints</h3>
 * <ul>
 *   <li>{@code GET  /api/v1/billing/invoices/{id}}       — get invoice details</li>
 *   <li>{@code GET  /api/v1/billing/invoices}             — list invoices by customer</li>
 *   <li>{@code POST /api/v1/billing/payments}             — record a payment</li>
 * </ul>
 *
 * @see BillingCommandHandler
 * @see BillingQueryHandler
 */
@RestController
@RequestMapping("/api/v1/billing")
public class BillingController {

    private static final Logger log = LoggerFactory.getLogger(BillingController.class);

    private final BillingQueryHandler queryHandler;
    private final BillingCommandHandler commandHandler;

    /**
     * Creates a new {@code BillingController} with the required handlers.
     *
     * @param queryHandler   the billing query handler (must not be null)
     * @param commandHandler the billing command handler (must not be null)
     */
    public BillingController(BillingQueryHandler queryHandler,
                             BillingCommandHandler commandHandler) {
        this.queryHandler = Objects.requireNonNull(queryHandler, "BillingQueryHandler must not be null");
        this.commandHandler = Objects.requireNonNull(commandHandler, "BillingCommandHandler must not be null");
    }

    /**
     * Retrieves an invoice by its unique identifier.
     *
     * @param invoiceId the invoice UUID (path variable)
     * @return 200 OK with the invoice details
     */
    @GetMapping("/invoices/{invoiceId}")
    public ResponseEntity<InvoiceResponse> getInvoice(@PathVariable UUID invoiceId) {
        log.debug("GET /api/v1/billing/invoices/{} — fetching invoice", invoiceId);

        Invoice invoice = queryHandler.getInvoice(invoiceId);
        InvoiceResponse response = InvoiceResponse.from(invoice);

        log.info("Invoice retrieved: invoiceId={}, status={}", response.invoiceId(), response.status());

        return ResponseEntity.ok(response);
    }

    /**
     * Lists all invoices for a given customer.
     *
     * @param customerId the customer UUID (query parameter)
     * @return 200 OK with a list of invoices (may be empty)
     */
    @GetMapping("/invoices")
    public ResponseEntity<List<InvoiceResponse>> getInvoicesByCustomer(
            @RequestParam UUID customerId) {

        log.debug("GET /api/v1/billing/invoices?customerId={} — listing invoices", customerId);

        List<InvoiceResponse> responses = queryHandler.getInvoicesByCustomer(customerId)
                .stream()
                .map(InvoiceResponse::from)
                .toList();

        log.info("Retrieved {} invoices for customerId={}", responses.size(), customerId);

        return ResponseEntity.ok(responses);
    }

    /**
     * Records a payment against an invoice.
     *
     * @param request the payment recording request
     * @return 200 OK with the updated invoice
     */
    @PostMapping("/payments")
    public ResponseEntity<InvoiceResponse> recordPayment(
            @Valid @RequestBody RecordPaymentRequest request) {

        log.debug("POST /api/v1/billing/payments — recording payment for invoiceId={}",
                request.invoiceId());

        Invoice invoice = commandHandler.recordPayment(
                request.invoiceId(),
                request.amount(),
                request.currency(),
                Payment.PaymentMethod.valueOf(request.method()),
                request.reference()
        );

        InvoiceResponse response = InvoiceResponse.from(invoice);

        log.info("Payment recorded: invoiceId={}, status={}", response.invoiceId(), response.status());

        return ResponseEntity.ok(response);
    }
}
