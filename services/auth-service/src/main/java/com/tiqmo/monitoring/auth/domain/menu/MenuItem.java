package com.tiqmo.monitoring.auth.domain.menu;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for config.menu_items table.
 * Represents a menu item in the admin dashboard.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
@Table(name = "menu_items", schema = "config")
public class MenuItem {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "menu_code", nullable = false, unique = true, length = 50)
    private String menuCode;

    @Column(name = "parent_code", length = 50)
    private String parentCode;

    @Column(name = "label", nullable = false, length = 100)
    private String label;

    @Column(name = "icon", length = 50)
    private String icon;

    @Column(name = "route", length = 255)
    private String route;

    @Column(name = "required_api_key", length = 100)
    private String requiredApiKey;

    @Column(name = "sort_order")
    @Builder.Default
    private Integer sortOrder = 0;

    @Column(name = "enabled")
    @Builder.Default
    private Boolean enabled = true;

    @Column(name = "menu_type", length = 20)
    @Builder.Default
    private String menuType = "LINK";

    @Column(name = "created_at")
    private Instant createdAt;

    @Column(name = "updated_at")
    private Instant updatedAt;

    @PrePersist
    protected void onCreate() {
        Instant now = Instant.now();
        this.createdAt = now;
        this.updatedAt = now;
    }

    @PreUpdate
    protected void onUpdate() {
        this.updatedAt = Instant.now();
    }
}
