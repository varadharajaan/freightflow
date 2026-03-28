package com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.repository;

import com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.entity.VesselJpaEntity;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for vessel entities.
 *
 * <p>This is a <b>technology-specific</b> interface that provides data access
 * for the vessel schedule bounded context.</p>
 *
 * @see com.freightflow.vesselschedule.domain.port.VesselRepository
 */
@Repository
public interface SpringDataVesselRepository
        extends JpaRepository<VesselJpaEntity, UUID> {

    // ==================== Derived Query Methods ====================

    /**
     * Finds a vessel by its IMO number.
     *
     * @param imoNumber the IMO identification number
     * @return the vessel, or empty if not found
     */
    Optional<VesselJpaEntity> findByImoNumber(String imoNumber);

    /**
     * Finds all vessels in a specific status.
     *
     * @param status the vessel status
     * @return vessels in the specified status
     */
    List<VesselJpaEntity> findByStatusOrderByNameAsc(String status);

    /**
     * Finds vessels by flag state.
     *
     * @param flag the flag state
     * @return vessels registered under the flag
     */
    List<VesselJpaEntity> findByFlagOrderByCapacityTeuDesc(String flag);

    /**
     * Checks whether a vessel exists by IMO number.
     *
     * @param imoNumber the IMO identification number
     * @return true if a vessel with the IMO exists
     */
    boolean existsByImoNumber(String imoNumber);

    // ==================== JPQL Queries ====================

    /**
     * Finds all active vessels ordered by capacity.
     *
     * @return active vessels ordered by capacity descending
     */
    @Query("SELECT v FROM VesselJpaEntity v WHERE v.status = 'ACTIVE' ORDER BY v.capacityTeu DESC")
    List<VesselJpaEntity> findAllActiveVessels();

    /**
     * Calculates total fleet capacity.
     *
     * @return total TEU capacity of all active vessels
     */
    @Query("SELECT COALESCE(SUM(v.capacityTeu), 0) FROM VesselJpaEntity v WHERE v.status = 'ACTIVE'")
    long calculateTotalFleetCapacity();

    /**
     * Finds vessels with capacity exceeding a threshold.
     *
     * @param minCapacity the minimum TEU capacity
     * @return vessels meeting the capacity requirement
     */
    @Query("SELECT v FROM VesselJpaEntity v WHERE v.status = 'ACTIVE' AND v.capacityTeu >= :minCapacity ORDER BY v.capacityTeu DESC")
    List<VesselJpaEntity> findByMinCapacity(@Param("minCapacity") int minCapacity);
}
