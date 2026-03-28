package com.freightflow.trackingservice.infrastructure.adapter.out.persistence.repository;

import com.freightflow.trackingservice.infrastructure.adapter.out.persistence.entity.ContainerJpaEntity;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Spring Data JPA repository for container tracking entities.
 *
 * <p>This is a <b>technology-specific</b> interface that provides data access
 * for the tracking bounded context. It is NOT the domain port — the domain port
 * is {@link com.freightflow.trackingservice.domain.port.ContainerRepository}.</p>
 *
 * <h3>Features Demonstrated</h3>
 * <ol>
 *   <li>Derived query methods (method name parsing)</li>
 *   <li>JPQL queries via {@code @Query}</li>
 *   <li>Native SQL queries for PostgreSQL-specific features</li>
 *   <li>Bulk {@code @Modifying} operations</li>
 *   <li>Pagination ({@link Page})</li>
 * </ol>
 *
 * @see com.freightflow.trackingservice.domain.port.ContainerRepository
 */
@Repository
public interface SpringDataContainerRepository
        extends JpaRepository<ContainerJpaEntity, String> {

    // ==================== Derived Query Methods ====================

    /**
     * Finds a container by its ISO identifier.
     *
     * @param containerId the ISO container identifier
     * @return the container entity, or empty if not found
     */
    Optional<ContainerJpaEntity> findByContainerId(String containerId);

    /**
     * Finds all containers for a booking, ordered by creation date.
     *
     * @param bookingId the booking UUID
     * @return containers for the booking, ordered by creation date descending
     */
    List<ContainerJpaEntity> findByBookingIdOrderByCreatedAtDesc(UUID bookingId);

    /**
     * Finds all containers in a specific status, ordered by last update.
     *
     * @param status the container status
     * @return containers in the specified status
     */
    List<ContainerJpaEntity> findByStatusOrderByUpdatedAtDesc(String status);

    /**
     * Checks whether a container exists by its ISO identifier.
     *
     * @param containerId the ISO container identifier
     * @return true if the container exists
     */
    boolean existsByContainerId(String containerId);

    /**
     * Finds containers by status with pagination support.
     *
     * @param status   the container status
     * @param pageable pagination and sorting parameters
     * @return a page of matching containers
     */
    Page<ContainerJpaEntity> findByStatus(String status, Pageable pageable);

    // ==================== JPQL Queries ====================

    /**
     * Finds containers on a specific voyage.
     *
     * @param voyageId the voyage UUID
     * @return containers on the voyage
     */
    @Query("SELECT c FROM ContainerJpaEntity c WHERE c.voyageId = :voyageId ORDER BY c.updatedAt DESC")
    List<ContainerJpaEntity> findByVoyageId(@Param("voyageId") UUID voyageId);

    /**
     * Counts containers per status for a booking.
     *
     * @param bookingId the booking UUID
     * @param status    the container status
     * @return count of containers in the status
     */
    @Query("SELECT COUNT(c) FROM ContainerJpaEntity c WHERE c.bookingId = :bookingId AND c.status = :status")
    long countByBookingIdAndStatus(@Param("bookingId") UUID bookingId, @Param("status") String status);

    // ==================== Native SQL Queries ====================

    /**
     * Finds containers that haven't been updated within the given threshold.
     *
     * <p>Uses PostgreSQL-specific INTERVAL syntax for identifying stale positions.</p>
     *
     * @param status the container status to check
     * @param cutoff containers not updated since this timestamp are considered stale
     * @return stale containers
     */
    @Query(value = """
            SELECT c.* FROM containers c
            WHERE c.status = :status
            AND c.updated_at < :cutoff
            ORDER BY c.updated_at ASC
            """, nativeQuery = true)
    List<ContainerJpaEntity> findStaleContainers(
            @Param("status") String status, @Param("cutoff") Instant cutoff);

    // ==================== @Modifying Bulk Operations ====================

    /**
     * Bulk-updates the status of containers on a voyage.
     *
     * @param voyageId  the voyage UUID
     * @param oldStatus the current status to match
     * @param newStatus the new status to set
     * @return the number of rows updated
     */
    @Modifying
    @Query("UPDATE ContainerJpaEntity c SET c.status = :newStatus, c.updatedAt = CURRENT_TIMESTAMP "
            + "WHERE c.voyageId = :voyageId AND c.status = :oldStatus")
    int bulkUpdateStatusByVoyage(
            @Param("voyageId") UUID voyageId,
            @Param("oldStatus") String oldStatus,
            @Param("newStatus") String newStatus);
}
