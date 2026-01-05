package com.tiqmo.monitoring.loader.domain.config.repo;

import com.tiqmo.monitoring.loader.domain.config.entity.ApiEndpoint;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/**
 * Repository for API endpoint persistence.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Repository
public interface ApiEndpointRepository extends JpaRepository<ApiEndpoint, Integer> {

    /**
     * Find endpoint by its logical key.
     */
    Optional<ApiEndpoint> findByEndpointKey(String endpointKey);

    /**
     * Find all endpoints for a service.
     */
    List<ApiEndpoint> findByServiceId(String serviceId);

    /**
     * Find all active endpoints for a service.
     */
    List<ApiEndpoint> findByServiceIdAndStatus(String serviceId, String status);

    /**
     * Find all enabled endpoints.
     */
    List<ApiEndpoint> findByEnabledTrue();

    /**
     * Find all active and enabled endpoints.
     */
    @Query("SELECT e FROM ApiEndpoint e WHERE e.enabled = true AND e.status = 'ACTIVE'")
    List<ApiEndpoint> findAllActive();

    /**
     * Find all distinct service IDs.
     */
    @Query("SELECT DISTINCT e.serviceId FROM ApiEndpoint e WHERE e.status = 'ACTIVE'")
    List<String> findDistinctServiceIds();

    /**
     * Check if endpoint exists by key.
     */
    boolean existsByEndpointKey(String endpointKey);

    /**
     * Update last_seen_at and last_registered_by for existing endpoint.
     */
    @Modifying
    @Query("UPDATE ApiEndpoint e SET e.lastSeenAt = :lastSeenAt, e.lastRegisteredBy = :registeredBy WHERE e.endpointKey = :endpointKey")
    int updateLastSeen(@Param("endpointKey") String endpointKey,
                       @Param("lastSeenAt") Instant lastSeenAt,
                       @Param("registeredBy") String registeredBy);

    /**
     * Mark endpoints not seen since given time as REMOVED.
     */
    @Modifying
    @Query("UPDATE ApiEndpoint e SET e.status = 'REMOVED' WHERE e.serviceId = :serviceId AND e.lastSeenAt < :threshold")
    int markRemovedEndpoints(@Param("serviceId") String serviceId, @Param("threshold") Instant threshold);
}
