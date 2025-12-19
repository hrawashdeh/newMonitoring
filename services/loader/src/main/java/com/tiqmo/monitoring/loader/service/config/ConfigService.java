package com.tiqmo.monitoring.loader.service.config;

import com.tiqmo.monitoring.loader.domain.config.entity.ConfigPlan;
import com.tiqmo.monitoring.loader.domain.config.entity.ConfigValue;
import com.tiqmo.monitoring.loader.domain.config.repo.ConfigPlanRepository;
import com.tiqmo.monitoring.loader.domain.config.repo.ConfigValueRepository;
import com.tiqmo.monitoring.loader.dto.common.ErrorCode;
import com.tiqmo.monitoring.loader.events.ConfigPlanSwitchedEvent;
import com.tiqmo.monitoring.loader.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

/**
 * Service for managing configuration plans and retrieving configuration values.
 * Provides in-memory caching for active configurations to minimize database queries.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * // Get polling interval from scheduler config
 * Integer pollingInterval = configService.getConfigAsInt("scheduler", "polling-interval-seconds", 1);
 *
 * // Switch to high-load plan
 * configService.activatePlan("scheduler", "high-load");
 * }
 * </pre>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ConfigService {

    private final ConfigPlanRepository planRepo;
    private final ConfigValueRepository valueRepo;
    private final ApplicationEventPublisher eventPublisher;

    /**
     * Cache for active configurations.
     * Key: parent (e.g., "scheduler")
     * Value: Map of config key -> config value
     * Refreshed on plan switch.
     */
    private final ConcurrentHashMap<String, Map<String, String>> activeConfigCache = new ConcurrentHashMap<>();

    /**
     * Get configuration value as a string from the active plan.
     * Returns default value if not found.
     *
     * @param parent       the parent group (e.g., "scheduler", "loader", "api")
     * @param key          the configuration key (e.g., "polling-interval-seconds")
     * @param defaultValue the default value to return if config not found
     * @return the configuration value or default value
     */
    public String getConfig(String parent, String key, String defaultValue) {
        Map<String, String> parentConfigs = activeConfigCache.computeIfAbsent(
                parent, p -> loadActiveConfigs(p)
        );
        return parentConfigs.getOrDefault(key, defaultValue);
    }

    /**
     * Get configuration value as an integer from the active plan.
     * Returns default value if not found or if parsing fails.
     *
     * @param parent       the parent group
     * @param key          the configuration key
     * @param defaultValue the default value
     * @return the configuration value as integer or default value
     */
    public Integer getConfigAsInt(String parent, String key, Integer defaultValue) {
        String value = getConfig(parent, key, String.valueOf(defaultValue));
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid integer config: {}.{} = {}, using default: {}",
                    parent, key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get configuration value as a boolean from the active plan.
     * Returns default value if not found.
     *
     * @param parent       the parent group
     * @param key          the configuration key
     * @param defaultValue the default value
     * @return the configuration value as boolean or default value
     */
    public Boolean getConfigAsBoolean(String parent, String key, Boolean defaultValue) {
        String value = getConfig(parent, key, String.valueOf(defaultValue));
        return Boolean.parseBoolean(value);
    }

    /**
     * Get configuration value as a long from the active plan.
     * Returns default value if not found or if parsing fails.
     *
     * @param parent       the parent group
     * @param key          the configuration key
     * @param defaultValue the default value
     * @return the configuration value as long or default value
     */
    public Long getConfigAsLong(String parent, String key, Long defaultValue) {
        String value = getConfig(parent, key, String.valueOf(defaultValue));
        try {
            return Long.parseLong(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid long config: {}.{} = {}, using default: {}",
                    parent, key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Get configuration value as a double from the active plan.
     * Returns default value if not found or if parsing fails.
     *
     * @param parent       the parent group
     * @param key          the configuration key
     * @param defaultValue the default value
     * @return the configuration value as double or default value
     */
    public Double getConfigAsDouble(String parent, String key, Double defaultValue) {
        String value = getConfig(parent, key, String.valueOf(defaultValue));
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            log.warn("Invalid double config: {}.{} = {}, using default: {}",
                    parent, key, value, defaultValue);
            return defaultValue;
        }
    }

    /**
     * Switch active plan for a parent.
     * Publishes ConfigPlanSwitchedEvent for listeners to react.
     *
     * @param parent   the parent group
     * @param planName the plan name to activate
     * @throws IllegalArgumentException if plan not found
     */
    @Transactional
    public void activatePlan(String parent, String planName) {
        activatePlan(parent, planName, "system");
    }

    /**
     * Switch active plan for a parent with user tracking.
     * Publishes ConfigPlanSwitchedEvent for listeners to react.
     *
     * @param parent     the parent group
     * @param planName   the plan name to activate
     * @param switchedBy user or system that triggered the switch
     * @throws BusinessException if validation fails or plan not found
     */
    @Transactional
    public void activatePlan(String parent, String planName, String switchedBy) {
        MDC.put("parent", parent);
        MDC.put("planName", planName);

        try {
            log.info("Activating config plan | parent={} | planName={} | switchedBy={}",
                parent, planName, switchedBy);

            // Validation
            if (parent == null || parent.isBlank()) {
                log.warn("Parent is null or blank");
                throw new BusinessException(
                    ErrorCode.VALIDATION_REQUIRED_FIELD,
                    "Parent is required",
                    "parent"
                );
            }

            if (planName == null || planName.isBlank()) {
                log.warn("Plan name is null or blank | parent={}", parent);
                throw new BusinessException(
                    ErrorCode.VALIDATION_REQUIRED_FIELD,
                    "Plan name is required",
                    "planName"
                );
            }

            // Deactivate current active plan
            planRepo.findByParentAndIsActiveTrue(parent)
                    .ifPresent(currentPlan -> {
                        log.debug("Deactivating current plan | parent={} | planName={}",
                            parent, currentPlan.getPlanName());
                        currentPlan.setIsActive(false);
                        currentPlan.setUpdatedBy(switchedBy);
                        planRepo.save(currentPlan);
                    });

            // Activate new plan
            ConfigPlan newPlan = planRepo.findByParentAndPlanName(parent, planName)
                    .orElseThrow(() -> {
                        log.warn("Config plan not found | parent={} | planName={}", parent, planName);
                        return new BusinessException(
                            ErrorCode.CONFIG_PLAN_NOT_FOUND,
                            String.format("Configuration plan '%s' for parent '%s' not found", planName, parent)
                        );
                    });

            newPlan.setIsActive(true);
            newPlan.setUpdatedBy(switchedBy);
            planRepo.save(newPlan);

            log.debug("Config plan activated | parent={} | planName={}", parent, planName);

            // Refresh cache
            activeConfigCache.remove(parent);
            loadActiveConfigs(parent);

            log.debug("Config cache refreshed | parent={}", parent);

            // Publish event for listeners (e.g., scheduler to reload polling interval)
            eventPublisher.publishEvent(new ConfigPlanSwitchedEvent(parent, planName, switchedBy));

            log.info("Config plan activation completed | parent={} | planName={} | switchedBy={}",
                parent, planName, switchedBy);

        } finally {
            MDC.remove("parent");
            MDC.remove("planName");
        }
    }

    /**
     * Get the active plan for a parent.
     *
     * @param parent the parent group
     * @return the active ConfigPlan, or null if not found
     */
    @Transactional(readOnly = true)
    public ConfigPlan getActivePlan(String parent) {
        return planRepo.findByParentAndIsActiveTrue(parent).orElse(null);
    }

    /**
     * Get all plans for a parent.
     *
     * @param parent the parent group
     * @return list of all ConfigPlans for the parent
     */
    @Transactional(readOnly = true)
    public List<ConfigPlan> getAllPlans(String parent) {
        return planRepo.findByParent(parent);
    }

    /**
     * Refresh the cache for a specific parent.
     * Useful for manually refreshing after external database changes.
     *
     * @param parent the parent group to refresh
     */
    public void refreshCache(String parent) {
        log.debug("Refreshing config cache for parent: {}", parent);
        activeConfigCache.remove(parent);
        loadActiveConfigs(parent);
    }

    /**
     * Clear all cached configurations.
     * Forces reload on next access.
     */
    public void clearCache() {
        log.info("Clearing all config cache");
        activeConfigCache.clear();
    }

    /**
     * Load active configurations for a parent from the database.
     * Called automatically when cache miss occurs.
     *
     * @param parent the parent group
     * @return map of config key -> config value
     */
    private Map<String, String> loadActiveConfigs(String parent) {
        ConfigPlan activePlan = planRepo.findByParentAndIsActiveTrue(parent).orElse(null);

        if (activePlan == null) {
            log.warn("No active config plan for parent: {}, using empty config", parent);
            return new HashMap<>();
        }

        List<ConfigValue> values = valueRepo.findByPlanId(activePlan.getId());
        Map<String, String> configs = values.stream()
                .collect(Collectors.toMap(
                        ConfigValue::getConfigKey,
                        ConfigValue::getConfigValue
                ));

        log.debug("Loaded {} configs for plan: {}.{} ({})",
                configs.size(), parent, activePlan.getPlanName(), activePlan.getDescription());

        return configs;
    }
}