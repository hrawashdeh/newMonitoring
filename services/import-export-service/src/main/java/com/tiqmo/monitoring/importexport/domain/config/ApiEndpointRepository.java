package com.tiqmo.monitoring.importexport.domain.config;

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

    Optional<ApiEndpoint> findByEndpointKey(String endpointKey);

    List<ApiEndpoint> findByServiceId(String serviceId);

    @Modifying
    @Query("UPDATE ApiEndpoint e SET e.status = 'REMOVED' WHERE e.serviceId = :serviceId AND e.lastSeenAt < :threshold")
    int markRemovedEndpoints(@Param("serviceId") String serviceId, @Param("threshold") Instant threshold);
}
