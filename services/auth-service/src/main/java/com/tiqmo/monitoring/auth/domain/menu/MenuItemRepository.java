package com.tiqmo.monitoring.auth.domain.menu;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for MenuItem entities.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Repository
public interface MenuItemRepository extends JpaRepository<MenuItem, Integer> {

    Optional<MenuItem> findByMenuCode(String menuCode);

    List<MenuItem> findByParentCodeIsNullAndEnabledTrueOrderBySortOrder();

    List<MenuItem> findByParentCodeAndEnabledTrueOrderBySortOrder(String parentCode);

    List<MenuItem> findByEnabledTrueOrderBySortOrder();

    @Query("SELECT m FROM MenuItem m WHERE m.enabled = true AND m.menuCode IN " +
           "(SELECT p.menuCode FROM MenuRolePermission p WHERE p.roleName IN :roles) " +
           "ORDER BY m.sortOrder")
    List<MenuItem> findByRoles(@Param("roles") List<String> roles);

    @Query("SELECT m FROM MenuItem m WHERE m.enabled = true AND m.parentCode IS NULL AND m.menuCode IN " +
           "(SELECT p.menuCode FROM MenuRolePermission p WHERE p.roleName IN :roles) " +
           "ORDER BY m.sortOrder")
    List<MenuItem> findRootMenusByRoles(@Param("roles") List<String> roles);
}
