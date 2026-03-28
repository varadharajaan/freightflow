package com.freightflow.customerservice.infrastructure.adapter.out.persistence.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EntityListeners;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import org.springframework.data.annotation.CreatedBy;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedBy;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.UUID;

/**
 * JPA entity for persisting customer aggregates to PostgreSQL.
 *
 * <p>This entity lives in the infrastructure layer (outbound adapter) and is
 * NOT exposed to the domain layer. The domain works with
 * {@link com.freightflow.customerservice.domain.model.Customer},
 * and this entity is mapped to/from the domain model via the persistence adapter.</p>
 *
 * <p>Follows the separation mandated by Hexagonal Architecture:
 * domain model is persistence-ignorant, JPA annotations stay in infrastructure.</p>
 *
 * <h3>Features</h3>
 * <ul>
 *   <li>JPA auditing via {@link AuditingEntityListener} — auto-populates created/updated timestamps and users</li>
 *   <li>Optimistic locking via {@link Version} — prevents lost updates on concurrent modifications</li>
 *   <li>All columns explicitly mapped — no reliance on implicit naming</li>
 * </ul>
 *
 * @see com.freightflow.customerservice.domain.model.Customer
 */
@Entity
@Table(name = "customers")
@EntityListeners(AuditingEntityListener.class)
public class CustomerJpaEntity {

    @Id
    @Column(name = "id", nullable = false, updatable = false)
    private UUID id;

    @Column(name = "company_name", nullable = false, length = 255)
    private String companyName;

    @Column(name = "email", nullable = false, unique = true, length = 255)
    private String email;

    @Column(name = "phone", length = 50)
    private String phone;

    // ==================== Address (embedded value object) ====================

    @Column(name = "address_street", length = 255)
    private String addressStreet;

    @Column(name = "address_city", length = 100)
    private String addressCity;

    @Column(name = "address_state", length = 100)
    private String addressState;

    @Column(name = "address_postal_code", length = 20)
    private String addressPostalCode;

    @Column(name = "address_country", length = 2)
    private String addressCountry;

    // ==================== Type & Status ====================

    @Column(name = "customer_type", nullable = false, length = 30)
    private String customerType;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    // ==================== Credit ====================

    @Column(name = "credit_limit_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal creditLimitAmount;

    @Column(name = "credit_limit_currency", nullable = false, length = 3)
    private String creditLimitCurrency;

    @Column(name = "current_credit_used_amount", nullable = false, precision = 15, scale = 2)
    private BigDecimal currentCreditUsedAmount;

    @Column(name = "current_credit_used_currency", nullable = false, length = 3)
    private String currentCreditUsedCurrency;

    // ==================== Audit Columns ====================

    @Column(name = "registered_at", nullable = false, updatable = false)
    private Instant registeredAt;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @LastModifiedDate
    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @CreatedBy
    @Column(name = "created_by", length = 100)
    private String createdBy;

    @LastModifiedBy
    @Column(name = "updated_by", length = 100)
    private String updatedBy;

    // ==================== Optimistic Locking ====================

    @Version
    @Column(name = "version", nullable = false)
    private long version;

    /**
     * Required by JPA. Do not use directly — use the mapper to create entities from domain objects.
     */
    protected CustomerJpaEntity() {
        // JPA requires a no-arg constructor
    }

    // ==================== Getters and Setters ====================
    // Required by JPA — the mapper uses these to populate the entity.
    // No business logic here — that belongs in the domain model.

    public UUID getId() { return id; }
    public void setId(UUID id) { this.id = id; }

    public String getCompanyName() { return companyName; }
    public void setCompanyName(String companyName) { this.companyName = companyName; }

    public String getEmail() { return email; }
    public void setEmail(String email) { this.email = email; }

    public String getPhone() { return phone; }
    public void setPhone(String phone) { this.phone = phone; }

    public String getAddressStreet() { return addressStreet; }
    public void setAddressStreet(String addressStreet) { this.addressStreet = addressStreet; }

    public String getAddressCity() { return addressCity; }
    public void setAddressCity(String addressCity) { this.addressCity = addressCity; }

    public String getAddressState() { return addressState; }
    public void setAddressState(String addressState) { this.addressState = addressState; }

    public String getAddressPostalCode() { return addressPostalCode; }
    public void setAddressPostalCode(String addressPostalCode) { this.addressPostalCode = addressPostalCode; }

    public String getAddressCountry() { return addressCountry; }
    public void setAddressCountry(String addressCountry) { this.addressCountry = addressCountry; }

    public String getCustomerType() { return customerType; }
    public void setCustomerType(String customerType) { this.customerType = customerType; }

    public String getStatus() { return status; }
    public void setStatus(String status) { this.status = status; }

    public BigDecimal getCreditLimitAmount() { return creditLimitAmount; }
    public void setCreditLimitAmount(BigDecimal creditLimitAmount) { this.creditLimitAmount = creditLimitAmount; }

    public String getCreditLimitCurrency() { return creditLimitCurrency; }
    public void setCreditLimitCurrency(String creditLimitCurrency) { this.creditLimitCurrency = creditLimitCurrency; }

    public BigDecimal getCurrentCreditUsedAmount() { return currentCreditUsedAmount; }
    public void setCurrentCreditUsedAmount(BigDecimal currentCreditUsedAmount) { this.currentCreditUsedAmount = currentCreditUsedAmount; }

    public String getCurrentCreditUsedCurrency() { return currentCreditUsedCurrency; }
    public void setCurrentCreditUsedCurrency(String currentCreditUsedCurrency) { this.currentCreditUsedCurrency = currentCreditUsedCurrency; }

    public Instant getRegisteredAt() { return registeredAt; }
    public void setRegisteredAt(Instant registeredAt) { this.registeredAt = registeredAt; }

    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }

    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }

    public String getCreatedBy() { return createdBy; }
    public void setCreatedBy(String createdBy) { this.createdBy = createdBy; }

    public String getUpdatedBy() { return updatedBy; }
    public void setUpdatedBy(String updatedBy) { this.updatedBy = updatedBy; }

    public long getVersion() { return version; }
    public void setVersion(long version) { this.version = version; }

    @Override
    public String toString() {
        return "CustomerJpaEntity[id=%s, company=%s, status=%s, type=%s]".formatted(
                id, companyName, status, customerType);
    }
}
