package com.tiqmo.monitoring.loader.api.admin;

import com.tiqmo.monitoring.loader.domain.config.entity.ConfigPlan;
import com.tiqmo.monitoring.loader.domain.config.entity.ConfigValue;
import com.tiqmo.monitoring.loader.dto.admin.ActivatePlanRequest;
import com.tiqmo.monitoring.loader.service.config.ConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Service ID: ldr (Loader Service), Controller ID: cfg (Config Controller)
 *
 * <p>Admin Controller for managing configuration plans.
 * Provides endpoints to activate plans, view active plans, and list all plans.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>POST /api/ldr/cfg/activate-plan - Activate a configuration plan</li>
 *   <li>GET /api/ldr/cfg/{parent}/active-plan - Get active plan for a parent</li>
 *   <li>GET /api/ldr/cfg/{parent}/plans - List all plans for a parent</li>
 *   <li>POST /api/ldr/cfg/{parent}/refresh-cache - Refresh config cache for a parent</li>
 * </ul>
 */
@RestController
@RequestMapping("/api/ldr/cfg")
@RequiredArgsConstructor
@Slf4j
public class ConfigAdminController {

    private final ConfigService configService;

    /**
     * Activate a configuration plan.
     * Switches the active plan for the specified parent and publishes an event.
     *
     * @param request the activation request
     * @return response with activation details
     */
    @PostMapping("/activate-plan")
    public ResponseEntity<Map<String, Object>> activatePlan(@Valid @RequestBody ActivatePlanRequest request) {
        log.info("Received request to activate plan: {}.{}", request.getParent(), request.getPlanName());

        String switchedBy = request.getSwitchedBy() != null ? request.getSwitchedBy() : "system";
        configService.activatePlan(request.getParent(), request.getPlanName(), switchedBy);

        return ResponseEntity.ok(Map.of(
                "parent", request.getParent(),
                "planName", request.getPlanName(),
                "switchedBy", switchedBy,
                "activatedAt", Instant.now().getEpochSecond(),
                "message", String.format("Successfully activated plan %s.%s",
                        request.getParent(), request.getPlanName())
        ));
    }

    /**
     * Get the active configuration plan for a parent.
     *
     * @param parent the parent group (e.g., "scheduler", "loader", "api")
     * @return response with active plan details including all config values
     */
    @GetMapping("/{parent}/active-plan")
    public ResponseEntity<Map<String, Object>> getActivePlan(@PathVariable String parent) {
        log.debug("Fetching active plan for parent: {}", parent);

        ConfigPlan activePlan = configService.getActivePlan(parent);

        if (activePlan == null) {
            return ResponseEntity.ok(Map.of(
                    "parent", parent,
                    "message", "No active plan found for parent: " + parent
            ));
        }

        // Build config values map
        Map<String, String> configs = activePlan.getConfigValues().stream()
                .collect(Collectors.toMap(
                        ConfigValue::getConfigKey,
                        ConfigValue::getConfigValue
                ));

        return ResponseEntity.ok(Map.of(
                "parent", activePlan.getParent(),
                "planName", activePlan.getPlanName(),
                "description", activePlan.getDescription() != null ? activePlan.getDescription() : "",
                "isActive", activePlan.getIsActive(),
                "configs", configs,
                "updatedAt", activePlan.getUpdatedAt().getEpochSecond(),
                "updatedBy", activePlan.getUpdatedBy() != null ? activePlan.getUpdatedBy() : "system"
        ));
    }

    /**
     * List all configuration plans for a parent.
     *
     * @param parent the parent group
     * @return response with list of all plans
     */
    @GetMapping("/{parent}/plans")
    public ResponseEntity<Map<String, Object>> listPlans(@PathVariable String parent) {
        log.debug("Listing all plans for parent: {}", parent);

        List<ConfigPlan> plans = configService.getAllPlans(parent);

        List<Map<String, Object>> planList = plans.stream()
                .map(plan -> {
                    Map<String, Object> planMap = new java.util.HashMap<>();
                    planMap.put("planName", plan.getPlanName());
                    planMap.put("description", plan.getDescription() != null ? plan.getDescription() : "");
                    planMap.put("isActive", plan.getIsActive());
                    planMap.put("configCount", plan.getConfigValues().size());
                    planMap.put("updatedAt", plan.getUpdatedAt().getEpochSecond());
                    planMap.put("updatedBy", plan.getUpdatedBy() != null ? plan.getUpdatedBy() : "system");
                    return planMap;
                })
                .collect(Collectors.toList());

        return ResponseEntity.ok(Map.of(
                "parent", parent,
                "plans", planList,
                "totalPlans", plans.size()
        ));
    }

    /**
     * Refresh the configuration cache for a specific parent.
     * Useful for manually refreshing after external database changes.
     *
     * @param parent the parent group to refresh
     * @return response with refresh status
     */
    @PostMapping("/{parent}/refresh-cache")
    public ResponseEntity<Map<String, Object>> refreshCache(@PathVariable String parent) {
        log.info("Refreshing config cache for parent: {}", parent);

        configService.refreshCache(parent);

        return ResponseEntity.ok(Map.of(
                "parent", parent,
                "refreshedAt", Instant.now().getEpochSecond(),
                "message", String.format("Cache refreshed for parent: %s", parent)
        ));
    }
}
