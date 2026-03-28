package com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.repository;

import com.freightflow.vesselschedule.infrastructure.adapter.out.persistence.entity.VoyageJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

/**
 * Spring Data JPA repository for voyage entities.
 *
 * <p>This is a <b>technology-specific</b> interface that provides data access
 * for the vessel schedule bounded context.</p>
 *
 * @see com.freightflow.vesselschedule.domain.port.VoyageRepository
 */
@Repository
public interface SpringDataVoyageRepository
        extends JpaRepository<VoyageJpaEntity, UUID> {

    // ==================== Derived Query Methods ====================

    /**
     * Finds voyages for a vessel, ordered by creation date.
     *
     * @param vesselId the vessel UUID
     * @return voyages for the vessel
     */
    List<VoyageJpaEntity> findByVesselIdOrderByCreatedAtDesc(UUID vesselId);

    /**
     * Finds voyages in a specific status.
     *
     * @param status the voyage status
     * @return voyages in the status
     */
    List<VoyageJpaEntity> findByStatusOrderByCreatedAtDesc(String status);

    /**
     * Finds a voyage by its voyage number.
     *
     * @param voyageNumber the voyage number
     * @return the voyage entity, or empty
     */
    java.util.Optional<VoyageJpaEntity> findByVoyageNumber(String voyageNumber);

    /**
     * Finds voyages by status with pagination.
     *
     * @param status   the voyage status
     * @param pageable pagination parameters
     * @return a page of voyages
     */
    Page<VoyageJpaEntity> findByStatus(String status, Pageable pageable);

    // ==================== JPQL Queries ====================

    /**
     * Finds scheduled voyages with available capacity.
     *
     * @param minCapacity the minimum remaining TEU capacity
     * @return voyages with sufficient capacity
     */
    @Query("SELECT v FROM VoyageJpaEntity v WHERE v.status = 'SCHEDULED' AND v.remainingCapacityTeu >= :minCapacity ORDER BY v.remainingCapacityTeu DESC")
    List<VoyageJpaEntity> findScheduledWithCapacity(@Param("minCapacity") int minCapacity);

    /**
     * Calculates total remaining capacity for a vessel.
     *
     * @param vesselId the vessel UUID
     * @return total remaining TEU on scheduled voyages
     */
    @Query("SELECT COALESCE(SUM(v.remainingCapacityTeu), 0) FROM VoyageJpaEntity v WHERE v.vesselId = :vesselId AND v.status = 'SCHEDULED'")
    long calculateRemainingCapacityForVessel(@Param("vesselId") UUID vesselId);

    /**
     * Counts voyages per status for a vessel.
     *
     * @param vesselId the vessel UUID
     * @return raw result arrays of [status, count]
     */
    @Query(value = """
            SELECT v.status, COUNT(*) as voyage_count
            FROM voyages v
            WHERE v.vessel_id = :vesselId
            GROUP BY v.status
            """, nativeQuery = true)
    List<Object[]> getVoyageStatisticsByVessel(@Param("vesselId") UUID vesselId);

    // ==================== @Modifying Bulk Operations ====================

    /**
     * Bulk-cancels all scheduled voyages for a vessel (e.g., vessel going to maintenance).
     *
     * @param vesselId the vessel UUID
     * @return the number of voyages cancelled
     */
    @Modifying
    @Query("UPDATE VoyageJpaEntity v SET v.status = 'CANCELLED', v.updatedAt = CURRENT_TIMESTAMP WHERE v.vesselId = :vesselId AND v.status = 'SCHEDULED'")
    int bulkCancelScheduledVoyages(@Param("vesselId") UUID vesselId);
}
