package com.tiqmo.monitoring.loader.api.admin;

import com.tiqmo.monitoring.loader.infra.config.ApiKey;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.web.bind.annotation.*;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service ID: ldr (Loader Service), Controller ID: menu (Menu Controller)
 *
 * <p>Menu Controller for serving DB-driven admin menus.
 *
 * <p>Endpoints:
 * <ul>
 *   <li>GET /api/v1/ldr/menu/user - Get menus for current user's roles</li>
 *   <li>GET /api/v1/ldr/menu/all - Get all menus (admin only)</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@RestController
@RequestMapping("/api/v1/ldr/menu")
@RequiredArgsConstructor
@Slf4j
public class MenuController {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Get menus for current user's roles.
     */
    @GetMapping("/user")
    @ApiKey(value = "ldr.menu.user", description = "Get menus for current user")
    public ResponseEntity<Map<String, Object>> getUserMenus(Authentication authentication) {
        if (authentication == null) {
            return ResponseEntity.ok(Map.of(
                "menus", List.of(),
                "role", "anonymous"
            ));
        }

        // Get user's roles
        Set<String> roles = authentication.getAuthorities().stream()
            .map(GrantedAuthority::getAuthority)
            .collect(Collectors.toSet());

        log.debug("Fetching menus for roles: {}", roles);

        // Query menus accessible by user's roles
        String sql = """
            SELECT DISTINCT mi.menu_code, mi.parent_code, mi.label, mi.icon,
                   mi.route, mi.required_api_key, mi.sort_order, mi.menu_type, mi.enabled
            FROM config.menu_items mi
            INNER JOIN config.menu_role_permissions mrp ON mi.menu_code = mrp.menu_code
            WHERE mrp.role_name IN (%s)
              AND mi.enabled = true
            ORDER BY mi.sort_order
            """;

        // Build placeholders for roles
        String placeholders = roles.stream()
            .map(r -> "?")
            .collect(Collectors.joining(","));

        List<Map<String, Object>> menus = jdbcTemplate.queryForList(
            String.format(sql, placeholders),
            roles.toArray()
        );

        log.info("Returning {} menu items for roles: {}", menus.size(), roles);

        return ResponseEntity.ok(Map.of(
            "menus", menus,
            "role", roles.stream().findFirst().orElse("ROLE_VIEWER")
        ));
    }

    /**
     * Get all menus (admin only).
     */
    @GetMapping("/all")
    @ApiKey(value = "ldr.menu.all", description = "Get all menus", tags = {"admin"})
    public ResponseEntity<List<Map<String, Object>>> getAllMenus() {
        log.debug("Fetching all menus");

        String sql = """
            SELECT mi.menu_code, mi.parent_code, mi.label, mi.icon,
                   mi.route, mi.required_api_key, mi.sort_order, mi.menu_type, mi.enabled,
                   mi.created_at, mi.updated_at
            FROM config.menu_items mi
            ORDER BY mi.parent_code NULLS FIRST, mi.sort_order
            """;

        List<Map<String, Object>> menus = jdbcTemplate.queryForList(sql);

        log.info("Returning {} total menu items", menus.size());

        return ResponseEntity.ok(menus);
    }

    /**
     * Get menu permissions by role.
     */
    @GetMapping("/permissions")
    @ApiKey(value = "ldr.menu.permissions", description = "Get menu role permissions", tags = {"admin"})
    public ResponseEntity<List<Map<String, Object>>> getMenuPermissions() {
        log.debug("Fetching menu permissions");

        String sql = """
            SELECT mrp.menu_code, mrp.role_name, mi.label
            FROM config.menu_role_permissions mrp
            INNER JOIN config.menu_items mi ON mrp.menu_code = mi.menu_code
            ORDER BY mrp.role_name, mi.sort_order
            """;

        List<Map<String, Object>> permissions = jdbcTemplate.queryForList(sql);

        log.info("Returning {} menu permissions", permissions.size());

        return ResponseEntity.ok(permissions);
    }
}
