package com.freightflow.booking.domain.model;

import java.time.Instant;
import java.time.LocalDate;
import java.time.Year;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.UUID;
import java.util.concurrent.ThreadLocalRandom;

/**
 * Domain entity representing a Bill of Lading (B/L).
 *
 * <p>The Bill of Lading is the single most important document in international shipping.
 * It serves three critical functions:</p>
 * <ol>
 *   <li><b>Receipt of Goods</b> — acknowledges that the carrier has received the cargo</li>
 *   <li><b>Contract of Carriage</b> — documents the terms under which the cargo is transported</li>
 *   <li><b>Document of Title</b> — confers ownership rights; the holder can claim the goods</li>
 * </ol>
 *
 * <p>A B/L is issued after cargo has been loaded onto the vessel (booking status = SHIPPED).
 * It contains details about the shipper, consignee, vessel, voyage, ports, and cargo.</p>
 *
 * <h3>Design Patterns Applied</h3>
 * <ul>
 *   <li><b>Domain Entity</b> (DDD) — identified by {@code bolId}</li>
 *   <li><b>Factory Method</b> — {@link #issue} encapsulates creation logic</li>
 *   <li><b>Immutable Collections</b> — container IDs are unmodifiable after creation</li>
 * </ul>
 *
 * <h3>B/L Number Format</h3>
 * <p>The B/L number follows the pattern {@code BOL-YYYY-XXXXXX} where YYYY is the
 * current year and XXXXXX is a zero-padded random sequence number.</p>
 *
 * @see Booking
 * @see BillOfLadingStatus
 */
public class BillOfLading {

    private final UUID bolId;
    private final String bookingId;
    private final String bolNumber;
    private final String shipper;
    private final String consignee;
    private final String vesselName;
    private final String voyageNumber;
    private final String portOfLoading;
    private final String portOfDischarge;
    private final String cargoDescription;
    private final List<String> containerIds;
    private final LocalDate issueDate;

    private BillOfLadingStatus status;

    /**
     * Private constructor — use {@link #issue} factory method.
     */
    private BillOfLading(UUID bolId, String bookingId, String bolNumber,
                          String shipper, String consignee, String vesselName,
                          String voyageNumber, String portOfLoading, String portOfDischarge,
                          String cargoDescription, List<String> containerIds,
                          LocalDate issueDate, BillOfLadingStatus status) {
        this.bolId = Objects.requireNonNull(bolId, "B/L ID must not be null");
        this.bookingId = Objects.requireNonNull(bookingId, "Booking ID must not be null");
        this.bolNumber = Objects.requireNonNull(bolNumber, "B/L number must not be null");
        this.shipper = Objects.requireNonNull(shipper, "Shipper must not be null");
        this.consignee = Objects.requireNonNull(consignee, "Consignee must not be null");
        this.vesselName = Objects.requireNonNull(vesselName, "Vessel name must not be null");
        this.voyageNumber = Objects.requireNonNull(voyageNumber, "Voyage number must not be null");
        this.portOfLoading = Objects.requireNonNull(portOfLoading, "Port of loading must not be null");
        this.portOfDischarge = Objects.requireNonNull(portOfDischarge, "Port of discharge must not be null");
        this.cargoDescription = Objects.requireNonNull(cargoDescription, "Cargo description must not be null");
        this.containerIds = Collections.unmodifiableList(
                Objects.requireNonNull(containerIds, "Container IDs must not be null"));
        this.issueDate = Objects.requireNonNull(issueDate, "Issue date must not be null");
        this.status = Objects.requireNonNull(status, "Status must not be null");
    }

    // ==================== Factory Method ====================

    /**
     * Issues a new Bill of Lading for a shipped booking.
     *
     * <p>Creates a B/L in {@link BillOfLadingStatus#DRAFT} status with a generated B/L number.
     * The booking must be in SHIPPED status before a B/L can be issued.</p>
     *
     * @param booking      the booking to issue the B/L for (must be in SHIPPED status)
     * @param vesselName   the name of the carrying vessel
     * @param voyageNumber the voyage number
     * @return a new BillOfLading in DRAFT status
     * @throws IllegalArgumentException if the booking is not in SHIPPED status
     */
    public static BillOfLading issue(Booking booking, String vesselName, String voyageNumber) {
        Objects.requireNonNull(booking, "Booking must not be null");
        Objects.requireNonNull(vesselName, "Vessel name must not be null");
        Objects.requireNonNull(voyageNumber, "Voyage number must not be null");

        if (booking.getStatus() != BookingStatus.SHIPPED) {
            throw new IllegalArgumentException(
                    "Cannot issue B/L for booking %s: status is %s, expected SHIPPED".formatted(
                            booking.getId().asString(), booking.getStatus()));
        }

        String bolNumber = generateBolNumber();

        return new BillOfLading(
                UUID.randomUUID(),
                booking.getId().asString(),
                bolNumber,
                booking.getCustomerId().asString(),
                booking.getCustomerId().asString(),
                vesselName,
                voyageNumber,
                booking.getCargo().origin().value(),
                booking.getCargo().destination().value(),
                booking.getCargo().description(),
                List.of(),
                LocalDate.now(),
                BillOfLadingStatus.DRAFT
        );
    }

    // ==================== Domain Logic ====================

    /**
     * Marks this B/L as officially issued (transitions from DRAFT to ISSUED).
     *
     * @throws IllegalStateException if the B/L is not in DRAFT status
     */
    public void markIssued() {
        if (this.status != BillOfLadingStatus.DRAFT) {
            throw new IllegalStateException(
                    "Cannot issue B/L %s: status is %s, expected DRAFT".formatted(bolNumber, status));
        }
        this.status = BillOfLadingStatus.ISSUED;
    }

    /**
     * Surrenders this B/L (transitions from ISSUED to SURRENDERED).
     *
     * <p>A surrendered B/L indicates the cargo has been released to the consignee.</p>
     *
     * @throws IllegalStateException if the B/L is not in ISSUED status
     */
    public void surrender() {
        if (this.status != BillOfLadingStatus.ISSUED) {
            throw new IllegalStateException(
                    "Cannot surrender B/L %s: status is %s, expected ISSUED".formatted(bolNumber, status));
        }
        this.status = BillOfLadingStatus.SURRENDERED;
    }

    // ==================== Private Helpers ====================

    /**
     * Generates a B/L number in the format "BOL-YYYY-XXXXXX".
     *
     * @return a formatted B/L number
     */
    private static String generateBolNumber() {
        int year = Year.now().getValue();
        int sequence = ThreadLocalRandom.current().nextInt(1, 999_999);
        return "BOL-%d-%06d".formatted(year, sequence);
    }

    // ==================== Reconstitution ====================

    /**
     * Reconstitutes a BillOfLading from persisted state.
     *
     * <p>Used only by the persistence adapter when loading from the database.
     * No validation is performed since data was validated at creation time.</p>
     *
     * @return a BillOfLading in its persisted state
     */
    public static BillOfLading reconstitute(UUID bolId, String bookingId, String bolNumber,
                                             String shipper, String consignee, String vesselName,
                                             String voyageNumber, String portOfLoading,
                                             String portOfDischarge, String cargoDescription,
                                             List<String> containerIds, LocalDate issueDate,
                                             BillOfLadingStatus status) {
        return new BillOfLading(bolId, bookingId, bolNumber, shipper, consignee,
                vesselName, voyageNumber, portOfLoading, portOfDischarge,
                cargoDescription, containerIds, issueDate, status);
    }

    // ==================== Accessors ====================

    public UUID getBolId() {
        return bolId;
    }

    public String getBookingId() {
        return bookingId;
    }

    public String getBolNumber() {
        return bolNumber;
    }

    public String getShipper() {
        return shipper;
    }

    public String getConsignee() {
        return consignee;
    }

    public String getVesselName() {
        return vesselName;
    }

    public String getVoyageNumber() {
        return voyageNumber;
    }

    public String getPortOfLoading() {
        return portOfLoading;
    }

    public String getPortOfDischarge() {
        return portOfDischarge;
    }

    public String getCargoDescription() {
        return cargoDescription;
    }

    public List<String> getContainerIds() {
        return containerIds;
    }

    public LocalDate getIssueDate() {
        return issueDate;
    }

    public BillOfLadingStatus getStatus() {
        return status;
    }

    @Override
    public String toString() {
        return "BillOfLading[bolNumber=%s, bookingId=%s, route=%s→%s, vessel=%s, status=%s]".formatted(
                bolNumber, bookingId, portOfLoading, portOfDischarge, vesselName, status);
    }

    // ==================== Status Enum ====================

    /**
     * Lifecycle states of a Bill of Lading.
     *
     * <pre>
     *   DRAFT ──→ ISSUED ──→ SURRENDERED
     * </pre>
     */
    public enum BillOfLadingStatus {

        /** B/L has been created but not yet officially issued. */
        DRAFT,

        /** B/L has been officially issued by the carrier. */
        ISSUED,

        /** B/L has been surrendered — cargo released to consignee. */
        SURRENDERED
    }
}
