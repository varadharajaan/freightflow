package com.freightflow.booking.domain.model;

/**
 * Supported container types for freight bookings.
 *
 * <p>Each type has a standard TEU (Twenty-foot Equivalent Unit) factor
 * used for vessel capacity calculations.</p>
 */
public enum ContainerType {

    /** Standard 20-foot dry container. */
    DRY_20(1.0),

    /** Standard 40-foot dry container. */
    DRY_40(2.0),

    /** 20-foot refrigerated container for temperature-sensitive cargo. */
    REEFER_20(1.0),

    /** 40-foot refrigerated container for temperature-sensitive cargo. */
    REEFER_40(2.0),

    /** 40-foot high-cube container (extra height). */
    HIGH_CUBE_40(2.0),

    /** 20-foot open-top container for oversized cargo. */
    OPEN_TOP_20(1.0);

    private final double teuFactor;

    ContainerType(double teuFactor) {
        this.teuFactor = teuFactor;
    }

    /**
     * Returns the TEU factor for capacity planning.
     *
     * @return the TEU equivalent (1.0 for 20ft, 2.0 for 40ft)
     */
    public double teuFactor() {
        return teuFactor;
    }
}
