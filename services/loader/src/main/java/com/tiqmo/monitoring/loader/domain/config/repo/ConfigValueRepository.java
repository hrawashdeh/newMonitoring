package com.tiqmo.monitoring.loader.domain.config.repo;

import com.tiqmo.monitoring.loader.domain.config.entity.ConfigValue;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ConfigValue entities.
 * Provides methods to find configuration values by plan ID and config key.
 */
@Repository
public interface ConfigValueRepository extends JpaRepository<ConfigValue, Long> {

    /**
     * Find all configuration values for a given plan.
     *
     * @param planId the configuration plan ID
     * @return list of configuration values
     */
    List<ConfigValue> findByPlanId(Long planId);

    /**
     * Find a specific configuration value by plan ID and config key.
     *
     * @param planId    the configuration plan ID
     * @param configKey the configuration key
     * @return the configuration value, if found
     */
    Optional<ConfigValue> findByPlanIdAndConfigKey(Long planId, String configKey);
}