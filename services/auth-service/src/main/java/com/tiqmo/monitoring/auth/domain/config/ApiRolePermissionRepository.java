package com.tiqmo.monitoring.auth.domain.config;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface ApiRolePermissionRepository extends JpaRepository<ApiRolePermission, Integer> {

    List<ApiRolePermission> findByRoleName(String roleName);

    List<ApiRolePermission> findByEndpointKey(String endpointKey);

    Optional<ApiRolePermission> findByEndpointKeyAndRoleName(String endpointKey, String roleName);

    boolean existsByEndpointKeyAndRoleName(String endpointKey, String roleName);

    @Modifying
    @Query("DELETE FROM ApiRolePermission p WHERE p.roleName = :roleName")
    void deleteByRoleName(@Param("roleName") String roleName);

    @Modifying
    @Query("DELETE FROM ApiRolePermission p WHERE p.endpointKey = :endpointKey")
    void deleteByEndpointKey(@Param("endpointKey") String endpointKey);

    @Query("SELECT DISTINCT p.endpointKey FROM ApiRolePermission p WHERE p.roleName = :roleName")
    List<String> findEndpointKeysByRoleName(@Param("roleName") String roleName);

    @Query("SELECT DISTINCT p.roleName FROM ApiRolePermission p WHERE p.endpointKey = :endpointKey")
    List<String> findRoleNamesByEndpointKey(@Param("endpointKey") String endpointKey);
}
