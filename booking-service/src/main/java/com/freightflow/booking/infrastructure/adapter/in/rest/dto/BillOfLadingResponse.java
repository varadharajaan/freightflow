package com.freightflow.booking.infrastructure.adapter.in.rest.dto;

import com.freightflow.booking.domain.model.BillOfLading;

import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

/**
 * Outbound REST DTO representing a Bill of Lading in API responses.
 *
 * <p>This record maps from the {@link BillOfLading} domain entity to a flat JSON-serializable
 * structure. It exposes all relevant B/L fields while hiding internal domain details
 * from API consumers, following the Interface Segregation Principle.</p>
 *
 * @param bolId            unique B/L identifier
 * @param bookingId        the associated booking identifier
 * @param bolNumber        the formatted B/L number (e.g. "BOL-2026-001234")
 * @param shipper          the shipper (sender) identifier
 * @param consignee        the consignee (receiver) identifier
 * @param vesselName       the name of the carrying vessel
 * @param voyageNumber     the voyage number
 * @param portOfLoading    the port where cargo was loaded (UN/LOCODE)
 * @param portOfDischarge  the port where cargo will be discharged (UN/LOCODE)
 * @param cargoDescription human-readable description of the cargo
 * @param containerIds     list of container identifiers on this B/L
 * @param issueDate        the date the B/L was issued
 * @param status           current B/L lifecycle status
 */
public record BillOfLadingResponse(
        UUID bolId,
        String bookingId,
        String bolNumber,
        String shipper,
        String consignee,
        String vesselName,
        String voyageNumber,
        String portOfLoading,
        String portOfDischarge,
        String cargoDescription,
        List<String> containerIds,
        LocalDate issueDate,
        String status
) {

    /**
     * Factory method that maps a domain {@link BillOfLading} to an API response DTO.
     *
     * @param bol the domain Bill of Lading entity
     * @return a new {@code BillOfLadingResponse} DTO
     */
    public static BillOfLadingResponse from(BillOfLading bol) {
        return new BillOfLadingResponse(
                bol.getBolId(),
                bol.getBookingId(),
                bol.getBolNumber(),
                bol.getShipper(),
                bol.getConsignee(),
                bol.getVesselName(),
                bol.getVoyageNumber(),
                bol.getPortOfLoading(),
                bol.getPortOfDischarge(),
                bol.getCargoDescription(),
                bol.getContainerIds(),
                bol.getIssueDate(),
                bol.getStatus().name()
        );
    }
}
