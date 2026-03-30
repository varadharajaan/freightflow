package com.freightflow.trackingservice.domain.model;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Domain entity representing the allocation of a shipping container to a booking.
 *
 * <p>Container allocation is the process of assigning physical containers to bookings
 * for cargo transport. Each allocation tracks a single container from the moment it
 * is assigned until it is returned after delivery.</p>
 *
 * <h3>Container ID Format (ISO 6346)</h3>
 * <p>Container identifiers follow the ISO 6346 standard:</p>
 * <ul>
 *   <li>4 uppercase letters (owner code + equipment category)</li>
 *   <li>6 digits (serial number)</li>
 *   <li>1 check digit (calculated from the preceding characters)</li>
 * </ul>
 * <p>Example: {@code MSCU1234567}</p>
 *
 * <h3>Design Patterns Applied</h3>
 * <ul>
 *   <li><b>Domain Entity</b> (DDD) — identified by {@code allocationId}</li>
 *   <li><b>Factory Method</b> — {@link #allocate} encapsulates creation logic</li>
 *   <li><b>Tell, Don't Ask</b> — state transitions are internal</li>
 * </ul>
 *
 * <h3>Lifecycle</h3>
 * <pre>
 *   ALLOCATED ──→ IN_USE ──→ RETURNED
 * </pre>
 *
 * @see ContainerAllocationStatus
 */
public class ContainerAllocation {

    /** Owner code prefixes used for generating container IDs. */
    private static final String[] OWNER_CODES = {"MSCU", "CMAU", "HLXU", "EITU", "TRIU", "FFLW"};

    private final UUID allocationId;
    private final String containerId;
    private final UUID bookingId;
    private final String containerType;
    private final Instant allocatedAt;

    private ContainerAllocationStatus status;
    private Instant returnedAt;

    /**
     * Private constructor — use {@link #allocate} factory method.
     */
    private ContainerAllocation(UUID allocationId, String containerId, UUID bookingId,
                                 String containerType, Instant allocatedAt,
                                 ContainerAllocationStatus status) {
        this.allocationId = Objects.requireNonNull(allocationId, "Allocation ID must not be null");
        this.containerId = Objects.requireNonNull(containerId, "Container ID must not be null");
        this.bookingId = Objects.requireNonNull(bookingId, "Booking ID must not be null");
        this.containerType = Objects.requireNonNull(containerType, "Container type must not be null");
        this.allocatedAt = Objects.requireNonNull(allocatedAt, "Allocated at must not be null");
        this.status = Objects.requireNonNull(status, "Status must not be null");
    }

    // ==================== Factory Method ====================

    /**
     * Allocates a new container for a booking.
     *
     * <p>Generates a container ID in ISO 6346 format and creates the allocation
     * in {@link ContainerAllocationStatus#ALLOCATED} status.</p>
     *
     * @param bookingId     the booking to allocate the container for
     * @param containerType the type of container (e.g. "DRY_20", "REEFER_40")
     * @return a new ContainerAllocation in ALLOCATED status
     */
    public static ContainerAllocation allocate(UUID bookingId, String containerType) {
        Objects.requireNonNull(bookingId, "Booking ID must not be null");
        Objects.requireNonNull(containerType, "Container type must not be null");

        String containerId = generateIso6346ContainerId();

        return new ContainerAllocation(
                UUID.randomUUID(),
                containerId,
                bookingId,
                containerType,
                Instant.now(),
                ContainerAllocationStatus.ALLOCATED
        );
    }

    // ==================== Domain Logic ====================

    /**
     * Marks this container as actively in use (loaded with cargo).
     *
     * <p>Transition: ALLOCATED → IN_USE</p>
     *
     * @throws IllegalStateException if the container is not in ALLOCATED status
     */
    public void markInUse() {
        if (this.status != ContainerAllocationStatus.ALLOCATED) {
            throw new IllegalStateException(
                    "Cannot mark container %s as IN_USE: status is %s, expected ALLOCATED".formatted(
                            containerId, status));
        }
        this.status = ContainerAllocationStatus.IN_USE;
    }

    /**
     * Releases this container allocation, marking it as returned.
     *
     * <p>Transition: ALLOCATED → RETURNED or IN_USE → RETURNED</p>
     *
     * @throws IllegalStateException if the container is already returned
     */
    public void release() {
        if (this.status == ContainerAllocationStatus.RETURNED) {
            throw new IllegalStateException(
                    "Container %s is already returned".formatted(containerId));
        }
        this.status = ContainerAllocationStatus.RETURNED;
        this.returnedAt = Instant.now();
    }

    // ==================== ISO 6346 Container ID Generation ====================

    /**
     * Generates a container identifier in ISO 6346 format.
     *
     * <p>Format: 4 letters (owner code) + 6 digits (serial) + 1 check digit.</p>
     * <p>Example: {@code MSCU123456[check]}</p>
     *
     * @return a valid ISO 6346 container ID
     */
    private static String generateIso6346ContainerId() {
        ThreadLocalRandom random = ThreadLocalRandom.current();

        // Select random owner code (4 letters)
        String ownerCode = OWNER_CODES[random.nextInt(OWNER_CODES.length)];

        // Generate 6-digit serial number
        int serial = random.nextInt(100_000, 999_999);
        String serialStr = String.valueOf(serial);

        // Calculate ISO 6346 check digit
        String base = ownerCode + serialStr;
        int checkDigit = calculateCheckDigit(base);

        return base + checkDigit;
    }

    /**
     * Calculates the ISO 6346 check digit for a container ID.
     *
     * <p>Each character is assigned a numeric value, multiplied by 2^position,
     * summed, divided by 11, and the remainder is the check digit (mod 10).</p>
     *
     * @param base the 10-character base (4 letters + 6 digits)
     * @return the check digit (0-9)
     */
    private static int calculateCheckDigit(String base) {
        int sum = 0;
        for (int i = 0; i < base.length(); i++) {
            char c = base.charAt(i);
            int value;
            if (Character.isLetter(c)) {
                // A=10, B=12, C=13, ..., skipping multiples of 11
                int letterIndex = Character.toUpperCase(c) - 'A';
                value = letterIndex + 10;
                // Skip multiples of 11 per ISO 6346
                value += value / 11;
            } else {
                value = c - '0';
            }
            sum += value * (1 << i); // multiply by 2^position
        }
        int remainder = sum % 11;
        return remainder % 10;
    }

    // ==================== Reconstitution ====================

    /**
     * Reconstitutes a ContainerAllocation from persisted state.
     *
     * @return a ContainerAllocation in its persisted state
     */
    public static ContainerAllocation reconstitute(UUID allocationId, String containerId,
                                                    UUID bookingId, String containerType,
                                                    ContainerAllocationStatus status,
                                                    Instant allocatedAt, Instant returnedAt) {
        var allocation = new ContainerAllocation(allocationId, containerId, bookingId,
                containerType, allocatedAt, status);
        allocation.returnedAt = returnedAt;
        return allocation;
    }

    // ==================== Accessors ====================

    public UUID getAllocationId() {
        return allocationId;
    }

    public String getContainerId() {
        return containerId;
    }

    public UUID getBookingId() {
        return bookingId;
    }

    public String getContainerType() {
        return containerType;
    }

    public ContainerAllocationStatus getStatus() {
        return status;
    }

    public Instant getAllocatedAt() {
        return allocatedAt;
    }

    public Instant getReturnedAt() {
        return returnedAt;
    }

    @Override
    public String toString() {
        return "ContainerAllocation[id=%s, containerId=%s, bookingId=%s, type=%s, status=%s]".formatted(
                allocationId, containerId, bookingId, containerType, status);
    }

    // ==================== Status Enum ====================

    /**
     * Lifecycle states of a container allocation.
     *
     * <pre>
     *   ALLOCATED ──→ IN_USE ──→ RETURNED
     *       │                        ▲
     *       └────────────────────────┘
     * </pre>
     */
    public enum ContainerAllocationStatus {

        /** Container has been allocated to a booking but not yet in use. */
        ALLOCATED,

        /** Container is actively being used (loaded with cargo, in transit). */
        IN_USE,

        /** Container has been returned after delivery. */
        RETURNED
    }
}
