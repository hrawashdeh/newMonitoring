package com.tiqmo.monitoring.loader.service.security;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.tiqmo.monitoring.loader.domain.security.entity.FieldProtection;
import com.tiqmo.monitoring.loader.domain.security.repo.FieldProtectionRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Field Protection Service
 *
 * Filters DTO fields based on user role and resource_management.field_protection configuration.
 * Implements redaction strategies: REMOVE, MASK, TRUNCATE, HASH.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FieldProtectionService {

    private final FieldProtectionRepository fieldProtectionRepository;
    private final ObjectMapper objectMapper;

    /**
     * Filter a single DTO object based on field protection rules.
     *
     * @param dto The DTO object to filter
     * @param resourceType The resource type (e.g., "LOADER")
     * @param roleCode The user's role (e.g., "ADMIN", "OPERATOR", "VIEWER")
     * @return Filtered map with only visible fields
     */
    public Map<String, Object> filterFields(Object dto, String resourceType, String roleCode) {
        if (dto == null) {
            return Collections.emptyMap();
        }

        // Convert DTO to Map for easy field manipulation
        @SuppressWarnings("unchecked")
        Map<String, Object> dtoMap = objectMapper.convertValue(dto, Map.class);

        // Get field protection rules
        List<FieldProtection> protectionRules = fieldProtectionRepository
                .findByResourceTypeAndRoleCode(resourceType, roleCode);

        if (protectionRules.isEmpty()) {
            log.warn("No field protection rules found for resourceType={} and roleCode={}. Returning all fields.",
                    resourceType, roleCode);
            return dtoMap;
        }

        // Build maps for quick lookup
        Map<String, FieldProtection> protectionMap = protectionRules.stream()
                .collect(Collectors.toMap(FieldProtection::getFieldName, fp -> fp));

        // Filter fields
        Map<String, Object> filteredMap = new LinkedHashMap<>();

        for (Map.Entry<String, Object> entry : dtoMap.entrySet()) {
            String fieldName = entry.getKey();
            Object fieldValue = entry.getValue();

            FieldProtection protection = protectionMap.get(fieldName);

            if (protection == null) {
                // No rule found - default to VISIBLE (allows DTO fields not yet configured in field_protection)
                filteredMap.put(fieldName, fieldValue);
                continue;
            }

            if (Boolean.TRUE.equals(protection.getIsVisible())) {
                // Field is visible - include as-is
                filteredMap.put(fieldName, fieldValue);
            } else {
                // Field is hidden - apply redaction strategy
                Object redactedValue = applyRedaction(fieldValue, protection);
                if (redactedValue != null) {
                    filteredMap.put(fieldName, redactedValue);
                }
                // If redactedValue is null, field is completely removed (REMOVE strategy)
            }
        }

        return filteredMap;
    }

    /**
     * Filter a list of DTOs.
     *
     * @param dtoList List of DTOs to filter
     * @param resourceType The resource type (e.g., "LOADER")
     * @param roleCode The user's role (e.g., "ADMIN", "OPERATOR", "VIEWER")
     * @return List of filtered maps
     */
    public List<Map<String, Object>> filterFields(List<?> dtoList, String resourceType, String roleCode) {
        if (dtoList == null || dtoList.isEmpty()) {
            return Collections.emptyList();
        }

        return dtoList.stream()
                .map(dto -> filterFields(dto, resourceType, roleCode))
                .collect(Collectors.toList());
    }

    /**
     * Apply redaction strategy to a field value.
     *
     * @param value The original field value
     * @param protection The field protection rule
     * @return Redacted value, or null if field should be removed
     */
    private Object applyRedaction(Object value, FieldProtection protection) {
        if (value == null) {
            return null;
        }

        String redactionType = protection.getRedactionType();
        if (redactionType == null) {
            redactionType = "REMOVE"; // Default
        }

        switch (redactionType.toUpperCase()) {
            case "REMOVE":
                // Completely remove field from response
                return null;

            case "MASK":
                // Replace with asterisks or custom value
                String maskValue = protection.getRedactionValue();
                return maskValue != null ? maskValue : "***REDACTED***";

            case "TRUNCATE":
                // Show first N characters only
                if (value instanceof String) {
                    String strValue = (String) value;
                    int length = 50; // Default truncation length
                    if (protection.getRedactionValue() != null) {
                        try {
                            length = Integer.parseInt(protection.getRedactionValue());
                        } catch (NumberFormatException e) {
                            log.warn("Invalid truncation length '{}'. Using default: 50", protection.getRedactionValue());
                        }
                    }
                    if (strValue.length() > length) {
                        return strValue.substring(0, length) + "...";
                    }
                    return strValue;
                }
                return value; // Non-string values returned as-is

            case "HASH":
                // Replace with hash value
                if (value instanceof String) {
                    try {
                        java.security.MessageDigest md = java.security.MessageDigest.getInstance("SHA-256");
                        byte[] hash = md.digest(((String) value).getBytes(java.nio.charset.StandardCharsets.UTF_8));
                        return "SHA256:" + Base64.getEncoder().encodeToString(hash).substring(0, 32);
                    } catch (Exception e) {
                        log.error("Failed to hash value for field {}: {}", protection.getFieldName(), e.getMessage());
                        return "***HASH_ERROR***";
                    }
                }
                return value;

            default:
                log.warn("Unknown redaction type '{}' for field {}. Removing field.",
                        redactionType, protection.getFieldName());
                return null;
        }
    }

    /**
     * Get list of protected (hidden) field names for a resource type and role.
     * Used by frontend to visually mark protected fields.
     *
     * @param resourceType The resource type (e.g., "LOADER")
     * @param roleCode The user's role (e.g., "ADMIN", "OPERATOR", "VIEWER")
     * @return List of hidden field names
     */
    public List<String> getProtectedFields(String resourceType, String roleCode) {
        List<FieldProtection> protectionRules = fieldProtectionRepository
                .findByResourceTypeAndRoleCode(resourceType, roleCode);

        return protectionRules.stream()
                .filter(fp -> Boolean.FALSE.equals(fp.getIsVisible()))
                .map(FieldProtection::getFieldName)
                .collect(Collectors.toList());
    }

    /**
     * Extract role code from Spring Security role string.
     * Handles both "ROLE_ADMIN" and "ADMIN" formats.
     *
     * @param role The role string from authentication
     * @return The role code (e.g., "ADMIN")
     */
    public String extractRoleCode(String role) {
        if (role == null) {
            return "VIEWER"; // Default to most restrictive
        }
        // Remove "ROLE_" prefix if present
        if (role.startsWith("ROLE_")) {
            return role.substring(5);
        }
        return role;
    }
}
