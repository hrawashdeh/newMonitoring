package com.tiqmo.monitoring.loader.events;

import lombok.Getter;

import java.time.Instant;

/**
 * Event published when a configuration plan is switched.
 * Listeners can react to this event to reconfigure themselves based on the new plan.
 *
 * <p>Example usage:
 * <pre>
 * {@code
 * @EventListener
 * public void onConfigPlanSwitched(ConfigPlanSwitchedEvent event) {
 *     if ("scheduler".equals(event.getParent())) {
 *         log.info("Scheduler config switched to: {}", event.getPlanName());
 *         restartSchedulerWithNewConfig();
 *     }
 * }
 * }
 * </pre>
 */
@Getter
public class ConfigPlanSwitchedEvent {

    /**
     * The parent group whose plan was switched (e.g., "scheduler", "loader", "api").
     */
    private final String parent;

    /**
     * The name of the newly activated plan (e.g., "normal", "high-load", "maintenance").
     */
    private final String planName;

    /**
     * Timestamp when the plan was switched.
     */
    private final Instant switchedAt;

    /**
     * User or system that triggered the plan switch.
     */
    private final String switchedBy;

    public ConfigPlanSwitchedEvent(String parent, String planName) {
        this(parent, planName, "system");
    }

    public ConfigPlanSwitchedEvent(String parent, String planName, String switchedBy) {
        this.parent = parent;
        this.planName = planName;
        this.switchedAt = Instant.now();
        this.switchedBy = switchedBy;
    }

    @Override
    public String toString() {
        return "ConfigPlanSwitchedEvent{" +
                "parent='" + parent + '\'' +
                ", planName='" + planName + '\'' +
                ", switchedAt=" + switchedAt +
                ", switchedBy='" + switchedBy + '\'' +
                '}';
    }
}