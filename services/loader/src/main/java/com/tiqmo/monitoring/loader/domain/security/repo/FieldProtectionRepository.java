package com.tiqmo.monitoring.loader.domain.security.repo;

import com.tiqmo.monitoring.loader.domain.security.entity.FieldProtection;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for Field Protection Configuration
 *
 * Queries resource_management.field_protection table to determine
 * which fields should be visible/hidden for specific roles.
 */
@Repository
public interface FieldProtectionRepository extends JpaRepository<FieldProtection, Long> {

    /**
     * Get all field protection rules for a specific resource type and role.
     *
     * @param resourceType The resource type (e.g., "LOADER")
     * @param roleCode The role code (e.g., "ADMIN", "OPERATOR", "VIEWER")
     * @return List of field protection rules
     */
    List<FieldProtection> findByResourceTypeAndRoleCode(String resourceType, String roleCode);

    /**
     * Get visible fields for a specific resource type and role.
     * Uses the database function for optimized query.
     *
     * @param resourceType The resource type (e.g., "LOADER")
     * @param roleCode The role code (e.g., "ADMIN", "OPERATOR", "VIEWER")
     * @return List of visible field names
     */
    @Query(value = "SELECT field_name FROM resource_management.get_visible_fields(:resourceType, :roleCode) WHERE is_visible = true",
           nativeQuery = true)
    List<String> findVisibleFields(@Param("resourceType") String resourceType,
                                   @Param("roleCode") String roleCode);

    /**
     * Get hidden fields with their redaction types.
     *
     * @param resourceType The resource type (e.g., "LOADER")
     * @param roleCode The role code (e.g., "ADMIN", "OPERATOR", "VIEWER")
     * @return List of field protection rules for hidden fields
     */
    @Query("SELECT fp FROM FieldProtection fp " +
           "WHERE fp.resourceType = :resourceType " +
           "AND fp.roleCode = :roleCode " +
           "AND fp.isVisible = false")
    List<FieldProtection> findHiddenFields(@Param("resourceType") String resourceType,
                                           @Param("roleCode") String roleCode);
}
