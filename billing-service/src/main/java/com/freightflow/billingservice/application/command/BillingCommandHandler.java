package com.freightflow.billingservice.application.command;

import com.freightflow.billingservice.domain.event.BillingEvent;
import com.freightflow.billingservice.domain.model.Invoice;
import com.freightflow.billingservice.domain.model.LineItem;
import com.freightflow.billingservice.domain.model.Money;
import com.freightflow.billingservice.domain.model.Payment;
import com.freightflow.billingservice.domain.port.InvoiceRepository;
import com.freightflow.commons.exception.ResourceNotFoundException;
import com.freightflow.commons.observability.profiling.Profiled;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

/**
 * CQRS Command Handler — handles all write operations for the invoice aggregate.
 *
 * <p>This is the <b>write side</b> of CQRS. It handles invoice generation triggered
 * by booking confirmation events, payment recording, and invoice cancellation.</p>
 *
 * <h3>Design Patterns</h3>
 * <ul>
 *   <li><b>Command Handler</b> — each method handles a specific billing intention</li>
 *   <li><b>CQRS</b> — write operations separated from read operations</li>
 *   <li><b>Saga Participant</b> — invoice generation is part of the booking saga</li>
 * </ul>
 *
 * @see com.freightflow.billingservice.application.query.BillingQueryHandler
 */
@Service
public class BillingCommandHandler {

    private static final Logger log = LoggerFactory.getLogger(BillingCommandHandler.class);

    /** Default payment terms: 30 days from invoice generation. */
    private static final int DEFAULT_PAYMENT_TERMS_DAYS = 30;

    private final InvoiceRepository invoiceRepository;

    /**
     * Constructor injection — all dependencies are final (Dependency Inversion Principle).
     *
     * @param invoiceRepository the domain port for invoice persistence
     */
    public BillingCommandHandler(InvoiceRepository invoiceRepository) {
        this.invoiceRepository = Objects.requireNonNull(invoiceRepository,
                "invoiceRepository must not be null");
    }

    /**
     * Generates an invoice for a confirmed booking.
     *
     * <p>Called by the Kafka consumer when a {@code BookingConfirmed} event is received.
     * Creates a draft invoice, adds line items based on booking details, and issues it.</p>
     *
     * @param bookingId      the booking this invoice is for
     * @param customerId     the customer being invoiced
     * @param containerCount the number of containers
     * @param containerType  the type of containers
     * @param origin         the origin port
     * @param destination    the destination port
     * @return the generated and issued invoice
     */
    @Transactional
    @Profiled(value = "generateInvoice", slowThresholdMs = 500)
    public Invoice generateInvoice(UUID bookingId, UUID customerId,
                                    int containerCount, String containerType,
                                    String origin, String destination) {
        log.debug("Generating invoice: bookingId={}, customerId={}, containers={}x{}",
                bookingId, customerId, containerCount, containerType);

        LocalDate dueDate = LocalDate.now().plusDays(DEFAULT_PAYMENT_TERMS_DAYS);
        Invoice invoice = Invoice.create(bookingId, customerId, dueDate);

        // Add freight charge line item
        Money freightRate = calculateFreightRate(containerType, origin, destination);
        invoice.addLineItem(LineItem.of(
                "Ocean freight: %s → %s (%s)".formatted(origin, destination, containerType),
                containerCount,
                freightRate
        ));

        // Add terminal handling charge
        Money thc = Money.usd(BigDecimal.valueOf(250.00));
        invoice.addLineItem(LineItem.of(
                "Terminal handling charge (THC)",
                containerCount,
                thc
        ));

        // Issue the invoice
        invoice.issue();

        Invoice saved = invoiceRepository.save(invoice);
        List<BillingEvent> events = saved.pullDomainEvents();

        log.info("Invoice generated: invoiceId={}, bookingId={}, total={} {}, dueDate={}",
                saved.getInvoiceId(), bookingId,
                saved.getTotalAmount().amount(), saved.getTotalAmount().currency(),
                dueDate);

        return saved;
    }

    /**
     * Records a payment against an invoice.
     *
     * @param invoiceId the invoice being paid
     * @param amount    the payment amount
     * @param currency  the currency code
     * @param method    the payment method
     * @param reference the external payment reference
     * @return the updated invoice
     * @throws ResourceNotFoundException if the invoice is not found
     */
    @Transactional
    @Profiled(value = "recordPayment", slowThresholdMs = 500)
    public Invoice recordPayment(UUID invoiceId, BigDecimal amount, String currency,
                                  Payment.PaymentMethod method, String reference) {
        log.debug("Recording payment: invoiceId={}, amount={} {}, method={}",
                invoiceId, amount, currency, method);

        Invoice invoice = loadInvoiceOrThrow(invoiceId);

        Payment payment = new Payment(
                UUID.randomUUID(),
                new Money(amount, currency),
                method,
                Instant.now(),
                reference
        );

        invoice.recordPayment(payment);

        Invoice saved = invoiceRepository.save(invoice);
        List<BillingEvent> events = saved.pullDomainEvents();

        log.info("Payment recorded: invoiceId={}, paymentId={}, amount={} {}",
                invoiceId, payment.paymentId(), amount, currency);

        return saved;
    }

    /**
     * Cancels an invoice with a reason.
     *
     * @param invoiceId the invoice to cancel
     * @param reason    the cancellation reason
     * @return the cancelled invoice
     * @throws ResourceNotFoundException if the invoice is not found
     */
    @Transactional
    @Profiled(value = "cancelInvoice", slowThresholdMs = 500)
    public Invoice cancelInvoice(UUID invoiceId, String reason) {
        log.debug("Cancelling invoice: invoiceId={}, reason={}", invoiceId, reason);

        Invoice invoice = loadInvoiceOrThrow(invoiceId);
        invoice.cancel(reason);

        Invoice saved = invoiceRepository.save(invoice);
        List<BillingEvent> events = saved.pullDomainEvents();

        log.info("Invoice cancelled: invoiceId={}, reason={}", invoiceId, reason);

        return saved;
    }

    private Invoice loadInvoiceOrThrow(UUID invoiceId) {
        return invoiceRepository.findById(invoiceId)
                .orElseThrow(() -> {
                    log.warn("Invoice not found: invoiceId={}", invoiceId);
                    return new ResourceNotFoundException("Invoice", invoiceId.toString());
                });
    }

    /**
     * Calculates the freight rate based on container type and route.
     *
     * @param containerType the container type
     * @param origin        the origin port
     * @param destination   the destination port
     * @return the calculated freight rate per container
     */
    private Money calculateFreightRate(String containerType, String origin, String destination) {
        // Base rate calculation — in production this would query a pricing service
        BigDecimal baseRate = switch (containerType) {
            case "DRY_20" -> BigDecimal.valueOf(1500.00);
            case "DRY_40" -> BigDecimal.valueOf(2500.00);
            case "REEFER_20" -> BigDecimal.valueOf(3000.00);
            case "REEFER_40" -> BigDecimal.valueOf(4500.00);
            case "HIGH_CUBE_40" -> BigDecimal.valueOf(2800.00);
            default -> BigDecimal.valueOf(2000.00);
        };

        return Money.usd(baseRate);
    }
}
