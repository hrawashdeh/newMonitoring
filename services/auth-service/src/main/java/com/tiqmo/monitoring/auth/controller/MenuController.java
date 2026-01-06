package com.tiqmo.monitoring.auth.controller;

import com.tiqmo.monitoring.auth.infra.config.ApiKey;
import com.tiqmo.monitoring.auth.infra.logging.Logged;
import com.tiqmo.monitoring.auth.service.MenuService;
import io.jsonwebtoken.Claims;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;

/**
 * Controller for menu management and retrieval.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@RestController
@RequestMapping("/api/v1/auth/menus")
@RequiredArgsConstructor
@Logged(level = Logged.LogLevel.INFO)
public class MenuController {

    private final MenuService menuService;

    @Value("${jwt.secret:your-secret-key}")
    private String jwtSecret;

    /**
     * Get menu tree for the current user based on their roles.
     * Called by frontend to build the sidebar navigation.
     */
    @GetMapping
    @ApiKey(value = "auth.menus.list", description = "Get user menus", tags = {"menu", "auth"})
    public ResponseEntity<List<MenuService.MenuItemDTO>> getMenusForUser(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        List<String> roles = extractRolesFromToken(authHeader);
        if (roles.isEmpty()) {
            log.warn("No roles found in token, returning empty menu");
            return ResponseEntity.ok(Collections.emptyList());
        }

        log.debug("Fetching menus for roles: {}", roles);
        List<MenuService.MenuItemDTO> menus = menuService.getMenuTreeForRoles(roles);
        return ResponseEntity.ok(menus);
    }

    /**
     * Get all menu items (admin use).
     */
    @GetMapping("/all")
    @ApiKey(value = "auth.menus.all", description = "Get all menu items", tags = {"menu", "admin"})
    public ResponseEntity<List<MenuService.MenuItemDTO>> getAllMenus(
            @RequestHeader(value = "Authorization", required = false) String authHeader) {

        List<String> roles = extractRolesFromToken(authHeader);
        if (!roles.contains("ROLE_ADMIN") && !roles.contains("ROLE_SUPER_ADMIN")) {
            return ResponseEntity.status(403).build();
        }

        // Return full menu tree for admins
        List<MenuService.MenuItemDTO> menus = menuService.getMenuTreeForRoles(List.of("ROLE_ADMIN"));
        return ResponseEntity.ok(menus);
    }

    /**
     * Extract roles from JWT token.
     */
    @SuppressWarnings("unchecked")
    private List<String> extractRolesFromToken(String authHeader) {
        if (authHeader == null || !authHeader.startsWith("Bearer ")) {
            return Collections.emptyList();
        }

        try {
            String token = authHeader.substring(7);
            Claims claims = Jwts.parser()
                    .verifyWith(Keys.hmacShaKeyFor(jwtSecret.getBytes()))
                    .build()
                    .parseSignedClaims(token)
                    .getPayload();

            Object rolesObj = claims.get("roles");
            if (rolesObj instanceof List) {
                return (List<String>) rolesObj;
            }
            return Collections.emptyList();
        } catch (Exception e) {
            log.error("Failed to extract roles from token: {}", e.getMessage());
            return Collections.emptyList();
        }
    }
}
