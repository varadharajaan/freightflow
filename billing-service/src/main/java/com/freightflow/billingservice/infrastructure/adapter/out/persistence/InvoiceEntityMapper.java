package com.freightflow.billingservice.infrastructure.adapter.out.persistence;

import com.freightflow.billingservice.domain.model.Invoice;
import com.freightflow.billingservice.domain.model.InvoiceStatus;
import com.freightflow.billingservice.domain.model.Money;
import com.freightflow.billingservice.domain.model.Payment;
import com.freightflow.billingservice.domain.model.Payment.PaymentMethod;
import com.freightflow.billingservice.infrastructure.adapter.out.persistence.entity.InvoiceJpaEntity;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import java.util.List;

/**
 * Maps between the JPA entity ({@link InvoiceJpaEntity}) and the domain model ({@link Invoice}).
 *
 * <p>This mapper is the translation layer between the persistence world (JPA annotations,
 * flat columns) and the domain world (aggregates, value objects). It ensures the domain
 * model stays completely free of persistence concerns (Dependency Inversion Principle).</p>
 *
 * <p>A hand-written mapper is used because the Invoice aggregate has a private constructor,
 * domain events, and embedded value objects (Money, Payment) that MapStruct cannot
 * handle out of the box.</p>
 *
 * @see InvoiceJpaEntity
 * @see Invoice
 */
@Component
public class InvoiceEntityMapper {

    private static final Logger log = LoggerFactory.getLogger(InvoiceEntityMapper.class);

    /**
     * Converts a domain {@link Invoice} aggregate to an {@link InvoiceJpaEntity} for persistence.
     *
     * @param invoice the domain invoice aggregate
     * @return the JPA entity ready for persistence
     */
    public InvoiceJpaEntity toEntity(Invoice invoice) {
        log.trace("Mapping domain Invoice to JPA entity: invoiceId={}", invoice.getInvoiceId());

        var entity = new InvoiceJpaEntity();
        entity.setId(invoice.getInvoiceId());
        entity.setBookingId(invoice.getBookingId());
        entity.setCustomerId(invoice.getCustomerId());
        entity.setStatus(invoice.getStatus().name());

        // Financial fields (flattened from Money value object)
        entity.setTotalAmount(invoice.getTotalAmount().amount());
        entity.setCurrency(invoice.getTotalAmount().currency());
        entity.setDueDate(invoice.getDueDate());

        // Payment fields (flattened from Payment value object, nullable)
        invoice.getPayment().ifPresent(payment -> {
            entity.setPaymentId(payment.paymentId());
            entity.setPaymentAmount(payment.amount().amount());
            entity.setPaymentMethod(payment.method().name());
            entity.setPaidAt(payment.paidAt());
            entity.setPaymentReference(payment.reference());
        });

        // Cancellation reason (nullable)
        invoice.getCancellationReason().ifPresent(entity::setCancellationReason);

        // Audit fields
        entity.setCreatedAt(invoice.getCreatedAt());
        entity.setUpdatedAt(invoice.getUpdatedAt());
        entity.setVersion(invoice.getVersion());

        return entity;
    }

    /**
     * Reconstructs a domain {@link Invoice} aggregate from an {@link InvoiceJpaEntity}.
     *
     * <p>Uses {@link Invoice#reconstitute} to rebuild the aggregate from persisted state
     * without triggering domain events. Payment and Money value objects are reconstructed
     * from the flat entity columns.</p>
     *
     * @param entity the JPA entity loaded from the database
     * @return the domain invoice aggregate in its persisted state
     */
    public Invoice toDomain(InvoiceJpaEntity entity) {
        log.trace("Mapping JPA entity to domain Invoice: invoiceId={}", entity.getId());

        // Reconstruct Money value object for total amount
        Money totalAmount = new Money(entity.getTotalAmount(), entity.getCurrency());

        // Reconstruct Payment value object if payment data is present
        Payment payment = null;
        if (entity.getPaymentId() != null && entity.getPaymentAmount() != null
                && entity.getPaymentMethod() != null && entity.getPaidAt() != null
                && entity.getPaymentReference() != null) {
            Money paymentMoney = new Money(entity.getPaymentAmount(), entity.getCurrency());
            payment = new Payment(
                    entity.getPaymentId(),
                    paymentMoney,
                    PaymentMethod.valueOf(entity.getPaymentMethod()),
                    entity.getPaidAt(),
                    entity.getPaymentReference()
            );
        }

        return Invoice.reconstitute(
                entity.getId(),
                entity.getBookingId(),
                entity.getCustomerId(),
                InvoiceStatus.valueOf(entity.getStatus()),
                List.of(), // Line items are not stored in the invoices table
                totalAmount,
                entity.getDueDate(),
                payment,
                entity.getCancellationReason(),
                entity.getCreatedAt(),
                entity.getUpdatedAt(),
                entity.getVersion()
        );
    }
}
