package com.tiqmo.monitoring.loader.domain.security.entity;

import jakarta.persistence.*;
import lombok.*;

/**
 * Resource Action Entity
 *
 * Maps to auth.actions table.
 * Defines available actions for each resource type (LOADER, ALERT, etc.).
 */
@Entity
@Table(name = "actions", schema = "auth")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ResourceAction {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "action_code", nullable = false, length = 50)
    private String actionCode;

    @Column(name = "action_name", nullable = false, length = 100)
    private String actionName;

    @Column(name = "http_method", nullable = false, length = 10)
    private String httpMethod;

    @Column(name = "url_template", nullable = false, length = 255)
    private String urlTemplate;

    @Column(name = "resource_type", nullable = false, length = 50)
    private String resourceType;

    @Column(name = "description", columnDefinition = "TEXT")
    private String description;
}
