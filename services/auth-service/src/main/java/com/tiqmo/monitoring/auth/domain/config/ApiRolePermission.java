package com.tiqmo.monitoring.auth.domain.config;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;

@Entity
@Table(name = "api_role_permissions", schema = "config")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ApiRolePermission {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Integer id;

    @Column(name = "endpoint_key", nullable = false)
    private String endpointKey;

    @Column(name = "role_name", nullable = false)
    private String roleName;

    @Column(name = "granted_by")
    private String grantedBy;

    @CreationTimestamp
    @Column(name = "granted_at")
    private Instant grantedAt;
}
