package com.freightflow.customerservice.domain.model;

/**
 * Represents the type of customer in the FreightFlow platform.
 *
 * <p>Customer type determines available features, default contract terms,
 * and pricing tiers. Each type has a distinct role in the freight supply chain.</p>
 *
 * @see Customer
 */
public enum CustomerType {

    /** A cargo owner who ships goods via freight carriers. */
    SHIPPER,

    /** A party receiving shipped goods at the destination port. */
    CONSIGNEE,

    /** An intermediary arranging transport on behalf of shippers and consignees. */
    FREIGHT_FORWARDER;

    /**
     * Returns a human-readable display label for this customer type.
     *
     * @return the display label
     */
    public String displayLabel() {
        return switch (this) {
            case SHIPPER -> "Shipper";
            case CONSIGNEE -> "Consignee";
            case FREIGHT_FORWARDER -> "Freight Forwarder";
        };
    }
}
