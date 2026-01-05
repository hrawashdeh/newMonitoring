package com.tiqmo.monitoring.auth.domain.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for MenuRolePermission entities.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Repository
public interface MenuRolePermissionRepository extends JpaRepository<MenuRolePermission, Integer> {

    List<MenuRolePermission> findByRoleName(String roleName);

    List<MenuRolePermission> findByMenuCode(String menuCode);

    @Query("SELECT DISTINCT p.menuCode FROM MenuRolePermission p WHERE p.roleName IN :roles")
    List<String> findMenuCodesByRoles(@Param("roles") List<String> roles);

    boolean existsByMenuCodeAndRoleName(String menuCode, String roleName);
}
