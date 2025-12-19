package com.tiqmo.monitoring.loader.dto.admin;

import jakarta.validation.constraints.NotBlank;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Request DTO for activating a configuration plan.
 *
 * <p>Example:
 * <pre>
 * {
 *   "parent": "scheduler",
 *   "planName": "high-load",
 *   "switchedBy": "admin@example.com"
 * }
 * </pre>
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ActivatePlanRequest {

    /**
     * The parent group whose plan should be activated (e.g., "scheduler", "loader", "api").
     */
    @NotBlank(message = "Parent is required")
    private String parent;

    /**
     * The name of the plan to activate (e.g., "normal", "high-load", "maintenance").
     */
    @NotBlank(message = "Plan name is required")
    private String planName;

    /**
     * User or system that triggered the plan activation (optional).
     * Defaults to "system" if not provided.
     */
    private String switchedBy;
}
