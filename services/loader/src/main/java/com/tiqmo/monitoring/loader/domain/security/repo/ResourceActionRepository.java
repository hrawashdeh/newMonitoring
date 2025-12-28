package com.tiqmo.monitoring.loader.domain.security.repo;

import com.tiqmo.monitoring.loader.domain.security.entity.ResourceAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Resource Actions
 *
 * Queries auth.actions table to get available actions for resources.
 */
@Repository
public interface ResourceActionRepository extends JpaRepository<ResourceAction, Long> {

    /**
     * Get allowed actions for a specific role and resource type.
     * Uses the database function resource_management.get_allowed_actions().
     *
     * @param roleCode The user's role (e.g., "ADMIN", "OPERATOR", "VIEWER")
     * @param resourceType The resource type (e.g., "LOADER")
     * @param resourceState The current resource state (e.g., "ENABLED", "DISABLED")
     * @return List of allowed actions with their details
     */
    @Query(value = "SELECT action_code, action_name, http_method, url_template, resource_type " +
                   "FROM resource_management.get_allowed_actions(:roleCode, :resourceType, :resourceState)",
           nativeQuery = true)
    List<Object[]> findAllowedActions(@Param("roleCode") String roleCode,
                                       @Param("resourceType") String resourceType,
                                       @Param("resourceState") String resourceState);
}
