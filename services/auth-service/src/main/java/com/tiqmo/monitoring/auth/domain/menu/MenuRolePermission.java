package com.tiqmo.monitoring.auth.domain.menu;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;

/**
 * JPA entity for config.menu_role_permissions table.
 * Maps roles to menu items they can access.
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
@Table(name = "menu_role_permissions", schema = "config",
       uniqueConstraints = @UniqueConstraint(columnNames = {"menu_code", "role_name"}))
public class MenuRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "menu_code", nullable = false, length = 50)
    private String menuCode;

    @Column(name = "role_name", nullable = false, length = 50)
    private String roleName;

    @Column(name = "granted_by", length = 100)
    private String grantedBy;

    @Column(name = "granted_at")
    private Instant grantedAt;

    @PrePersist
    protected void onCreate() {
        if (this.grantedAt == null) {
            this.grantedAt = Instant.now();
        }
    }
}
