package com.tiqmo.monitoring.loader.domain.config.repo;

import com.tiqmo.monitoring.loader.domain.config.entity.ConfigPlan;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ConfigPlan entities.
 * Provides methods to find configuration plans by parent, plan name, and active status.
 */
@Repository
public interface ConfigPlanRepository extends JpaRepository<ConfigPlan, Long> {

    /**
     * Find a configuration plan by parent and plan name.
     *
     * @param parent   the parent group (e.g., "scheduler", "loader", "api")
     * @param planName the plan name (e.g., "normal", "high-load", "maintenance")
     * @return the configuration plan, if found
     */
    Optional<ConfigPlan> findByParentAndPlanName(String parent, String planName);

    /**
     * Find the active configuration plan for a given parent.
     * Only one plan per parent can be active (enforced by unique index).
     *
     * @param parent the parent group
     * @return the active configuration plan, if found
     */
    Optional<ConfigPlan> findByParentAndIsActiveTrue(String parent);

    /**
     * Find all configuration plans for a given parent.
     *
     * @param parent the parent group
     * @return list of configuration plans for the parent
     */
    List<ConfigPlan> findByParent(String parent);

    /**
     * Check if a configuration plan exists by parent and plan name.
     *
     * @param parent   the parent group
     * @param planName the plan name
     * @return true if exists, false otherwise
     */
    boolean existsByParentAndPlanName(String parent, String planName);
}