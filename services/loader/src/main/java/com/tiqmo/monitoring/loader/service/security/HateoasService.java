package com.tiqmo.monitoring.loader.service.security;

import com.tiqmo.monitoring.loader.domain.security.repo.ResourceActionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * HATEOAS Service
 *
 * Builds _links objects for resources based on user role and resource state.
 * Enables/disables frontend actions dynamically using HATEOAS principles.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class HateoasService {

    private final ResourceActionRepository resourceActionRepository;

    /**
     * Build _links object for a resource based on allowed actions.
     *
     * @param resourceCode The resource identifier (e.g., "LOADER_001")
     * @param resourceType The resource type (e.g., "LOADER")
     * @param resourceState The current resource state (e.g., "ENABLED", "DISABLED", "RUNNING")
     * @param roleCode The user's role (e.g., "ADMIN", "OPERATOR", "VIEWER")
     * @return Map of action_code -> link details
     */
    public Map<String, Map<String, String>> buildLinks(String resourceCode,
                                                         String resourceType,
                                                         String resourceState,
                                                         String roleCode) {
        Map<String, Map<String, String>> links = new HashMap<>();

        // Query database for allowed actions
        List<Object[]> allowedActions = resourceActionRepository.findAllowedActions(
                roleCode,
                resourceType,
                resourceState
        );

        for (Object[] action : allowedActions) {
            String actionCode = (String) action[0];
            String actionName = (String) action[1];
            String httpMethod = (String) action[2];
            String urlTemplate = (String) action[3];

            // Replace {loaderCode} placeholder with actual resource code
            String url = urlTemplate.replace("{loaderCode}", resourceCode);

            // Convert action code to camelCase for frontend
            String camelCaseAction = toCamelCase(actionCode);

            Map<String, String> linkDetails = new HashMap<>();
            linkDetails.put("href", url);
            linkDetails.put("method", httpMethod);
            linkDetails.put("title", actionName);

            links.put(camelCaseAction, linkDetails);
        }

        log.debug("Built {} _links for {} (type={}, state={}, role={})",
                links.size(), resourceCode, resourceType, resourceState, roleCode);

        return links;
    }

    /**
     * Convert action code to camelCase.
     * Examples: TOGGLE_ENABLED -> toggleEnabled, VIEW_DETAILS -> viewDetails
     *
     * @param actionCode The action code in UPPER_SNAKE_CASE
     * @return camelCase version
     */
    private String toCamelCase(String actionCode) {
        if (actionCode == null || actionCode.isEmpty()) {
            return actionCode;
        }

        String[] parts = actionCode.toLowerCase().split("_");
        StringBuilder camelCase = new StringBuilder(parts[0]);

        for (int i = 1; i < parts.length; i++) {
            camelCase.append(Character.toUpperCase(parts[i].charAt(0)));
            if (parts[i].length() > 1) {
                camelCase.append(parts[i].substring(1));
            }
        }

        return camelCase.toString();
    }

    /**
     * Determine resource state from loader properties.
     *
     * Priority:
     * 1. If approval status is PENDING_APPROVAL or REJECTED, use that as state
     * 2. Otherwise (APPROVED), use enabled/disabled state
     *
     * @param approvalStatus The approval status of the loader
     * @param enabled Whether the loader is enabled
     * @return Resource state code
     */
    public String getLoaderState(String approvalStatus, Boolean enabled) {
        // Approval status takes priority over enabled/disabled
        if ("PENDING_APPROVAL".equals(approvalStatus)) {
            return "PENDING_APPROVAL";
        }
        if ("REJECTED".equals(approvalStatus)) {
            return "REJECTED";
        }

        // For APPROVED status, determine state from enabled field
        if (enabled == null || !enabled) {
            return "DISABLED";
        }
        return "ENABLED";
    }
}
