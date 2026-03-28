package com.freightflow.billingservice.domain.model;

import com.freightflow.billingservice.domain.event.BillingEvent;
import com.freightflow.billingservice.domain.event.InvoiceGenerated;
import com.freightflow.billingservice.domain.event.PaymentReceived;
import com.freightflow.billingservice.domain.event.RefundIssued;
import com.freightflow.commons.exception.ConflictException;

import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * Invoice Aggregate Root — the central domain entity managing the invoice lifecycle.
 *
 * <p>This class encapsulates all business rules and invariants for financial invoicing.
 * State transitions are enforced via the {@link InvoiceStatus} state machine.
 * Every state change produces a domain event that is collected for later publishing.</p>
 *
 * <h3>Design Patterns Applied</h3>
 * <ul>
 *   <li><b>Aggregate Root</b> (DDD) — single entry point for all invoice mutations</li>
 *   <li><b>State Pattern</b> — transitions governed by {@link InvoiceStatus#canTransitionTo}</li>
 *   <li><b>Domain Events</b> — state changes emit events for downstream services</li>
 *   <li><b>Factory Method</b> — {@link #create} encapsulates creation logic</li>
 *   <li><b>Saga Participant</b> — invoice generation is part of the booking saga</li>
 * </ul>
 *
 * <h3>Invariants</h3>
 * <ul>
 *   <li>An invoice always has an ID, booking reference, and customer reference</li>
 *   <li>State transitions follow the state machine (see {@link InvoiceStatus})</li>
 *   <li>An issued invoice must have at least one line item</li>
 *   <li>A paid invoice cannot be modified further</li>
 *   <li>Total amount is always the sum of line item totals</li>
 * </ul>
 *
 * @see InvoiceStatus
 * @see BillingEvent
 */
public class Invoice {

    private final UUID invoiceId;
    private final UUID bookingId;
    private final UUID customerId;
    private final List<LineItem> lineItems;
    private Instant createdAt;

    private InvoiceStatus status;
    private Money totalAmount;
    private LocalDate dueDate;
    private Payment payment;
    private String cancellationReason;
    private Instant updatedAt;
    private long version;

    /** Uncommitted domain events — cleared after publishing. */
    private final List<BillingEvent> domainEvents = new ArrayList<>();

    /**
     * Private constructor — use {@link #create} factory method.
     */
    private Invoice(UUID invoiceId, UUID bookingId, UUID customerId) {
        this.invoiceId = Objects.requireNonNull(invoiceId, "Invoice ID must not be null");
        this.bookingId = Objects.requireNonNull(bookingId, "Booking ID must not be null");
        this.customerId = Objects.requireNonNull(customerId, "Customer ID must not be null");
        this.lineItems = new ArrayList<>();
        this.status = InvoiceStatus.DRAFT;
        this.totalAmount = Money.zero(Money.DEFAULT_CURRENCY);
        this.createdAt = Instant.now();
        this.updatedAt = this.createdAt;
        this.version = 0;
    }

    // ==================== Factory Method ====================

    /**
     * Factory method to create a new invoice in DRAFT status.
     *
     * @param bookingId  the booking this invoice is for
     * @param customerId the customer being invoiced
     * @param dueDate    the payment due date
     * @return a new Invoice in DRAFT status
     * @throws IllegalArgumentException if dueDate is in the past
     */
    public static Invoice create(UUID bookingId, UUID customerId, LocalDate dueDate) {
        if (dueDate.isBefore(LocalDate.now())) {
            throw new IllegalArgumentException(
                    "Due date must be in the future, got: " + dueDate);
        }

        var invoice = new Invoice(UUID.randomUUID(), bookingId, customerId);
        invoice.dueDate = dueDate;
        return invoice;
    }

    // ==================== State Transitions (Tell, Don't Ask) ====================

    /**
     * Adds a line item to the invoice.
     *
     * <p>Only allowed when the invoice is in DRAFT status.</p>
     *
     * @param lineItem the line item to add
     * @throws ConflictException if the invoice is not in DRAFT status
     */
    public void addLineItem(LineItem lineItem) {
        Objects.requireNonNull(lineItem, "Line item must not be null");
        if (this.status != InvoiceStatus.DRAFT) {
            throw ConflictException.invalidStateTransition(
                    "Invoice", invoiceId.toString(), status.name(), "ADD_LINE_ITEM");
        }

        this.lineItems.add(lineItem);
        recalculateTotal();
        this.updatedAt = Instant.now();
    }

    /**
     * Issues the invoice to the customer.
     *
     * <p>Transition: DRAFT → ISSUED. Invoice must have at least one line item.</p>
     *
     * @throws ConflictException if the invoice is not in DRAFT status
     * @throws IllegalStateException if the invoice has no line items
     */
    public void issue() {
        assertTransition(InvoiceStatus.ISSUED);
        if (lineItems.isEmpty()) {
            throw new IllegalStateException("Cannot issue an invoice with no line items");
        }

        this.status = InvoiceStatus.ISSUED;
        this.updatedAt = Instant.now();

        registerEvent(new InvoiceGenerated(
                this.invoiceId, this.bookingId, this.customerId,
                this.totalAmount, this.dueDate, this.updatedAt
        ));
    }

    /**
     * Records a payment against the invoice.
     *
     * <p>Transition: ISSUED → PAID or OVERDUE → PAID</p>
     *
     * @param paymentRecord the payment details
     * @throws ConflictException if the invoice is not in ISSUED or OVERDUE status
     */
    public void recordPayment(Payment paymentRecord) {
        Objects.requireNonNull(paymentRecord, "Payment must not be null");
        assertTransition(InvoiceStatus.PAID);

        this.status = InvoiceStatus.PAID;
        this.payment = paymentRecord;
        this.updatedAt = Instant.now();

        registerEvent(new PaymentReceived(
                this.invoiceId, this.bookingId, paymentRecord.paymentId(),
                paymentRecord.amount(), paymentRecord.method(), this.updatedAt
        ));
    }

    /**
     * Cancels the invoice with a reason.
     *
     * <p>Transition: DRAFT → CANCELLED, ISSUED → CANCELLED, or OVERDUE → CANCELLED</p>
     *
     * @param reason the cancellation reason
     * @throws ConflictException if the invoice cannot be cancelled from its current state
     */
    public void cancel(String reason) {
        Objects.requireNonNull(reason, "Cancellation reason must not be null");
        assertTransition(InvoiceStatus.CANCELLED);

        this.status = InvoiceStatus.CANCELLED;
        this.cancellationReason = reason;
        this.updatedAt = Instant.now();

        registerEvent(new RefundIssued(
                this.invoiceId, this.bookingId, this.customerId,
                this.totalAmount, reason, this.updatedAt
        ));
    }

    /**
     * Marks the invoice as overdue.
     *
     * <p>Transition: ISSUED → OVERDUE</p>
     *
     * @throws ConflictException if the invoice is not in ISSUED status
     */
    public void markOverdue() {
        assertTransition(InvoiceStatus.OVERDUE);
        this.status = InvoiceStatus.OVERDUE;
        this.updatedAt = Instant.now();
    }

    // ==================== Domain Event Management ====================

    /**
     * Reconstitutes an Invoice aggregate from persisted state.
     *
     * @return an Invoice aggregate in its persisted state
     */
    public static Invoice reconstitute(UUID invoiceId, UUID bookingId, UUID customerId,
                                        InvoiceStatus status, List<LineItem> lineItems,
                                        Money totalAmount, LocalDate dueDate,
                                        Payment payment, String cancellationReason,
                                        Instant createdAt, Instant updatedAt, long version) {
        var invoice = new Invoice(invoiceId, bookingId, customerId);
        invoice.status = status;
        invoice.lineItems.addAll(lineItems != null ? lineItems : List.of());
        invoice.totalAmount = totalAmount;
        invoice.dueDate = dueDate;
        invoice.payment = payment;
        invoice.cancellationReason = cancellationReason;
        invoice.createdAt = createdAt;
        invoice.updatedAt = updatedAt;
        invoice.version = version;
        return invoice;
    }

    /**
     * Returns and clears all uncommitted domain events.
     *
     * @return an unmodifiable list of domain events
     */
    public List<BillingEvent> pullDomainEvents() {
        List<BillingEvent> events = Collections.unmodifiableList(new ArrayList<>(domainEvents));
        domainEvents.clear();
        return events;
    }

    private void registerEvent(BillingEvent event) {
        domainEvents.add(event);
    }

    // ==================== Internal Helpers ====================

    private void assertTransition(InvoiceStatus target) {
        if (!status.canTransitionTo(target)) {
            throw ConflictException.invalidStateTransition(
                    "Invoice", invoiceId.toString(), status.name(), target.name());
        }
    }

    private void recalculateTotal() {
        Money sum = Money.zero(Money.DEFAULT_CURRENCY);
        for (LineItem item : lineItems) {
            sum = sum.add(item.totalPrice());
        }
        this.totalAmount = sum;
    }

    // ==================== Accessors ====================

    public UUID getInvoiceId() { return invoiceId; }
    public UUID getBookingId() { return bookingId; }
    public UUID getCustomerId() { return customerId; }
    public InvoiceStatus getStatus() { return status; }
    public List<LineItem> getLineItems() { return Collections.unmodifiableList(lineItems); }
    public Money getTotalAmount() { return totalAmount; }
    public LocalDate getDueDate() { return dueDate; }
    public Optional<Payment> getPayment() { return Optional.ofNullable(payment); }
    public Optional<String> getCancellationReason() { return Optional.ofNullable(cancellationReason); }
    public Instant getCreatedAt() { return createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public long getVersion() { return version; }

    @Override
    public String toString() {
        return "Invoice[id=%s, status=%s, booking=%s, total=%s %s]".formatted(
                invoiceId, status, bookingId,
                totalAmount.amount(), totalAmount.currency());
    }
}
