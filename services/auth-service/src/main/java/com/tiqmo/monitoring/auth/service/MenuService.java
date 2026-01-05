package com.tiqmo.monitoring.auth.service;

import com.tiqmo.monitoring.auth.domain.menu.MenuItem;
import com.tiqmo.monitoring.auth.domain.menu.MenuItemRepository;
import com.tiqmo.monitoring.auth.domain.menu.MenuRolePermissionRepository;
import com.tiqmo.monitoring.auth.infra.logging.Logged;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Service for managing menu items and role-based access.
 *
 * @author Hassan Rawashdeh
 * @since 1.0.0
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Logged(level = Logged.LogLevel.DEBUG)
public class MenuService {

    private final MenuItemRepository menuItemRepository;
    private final MenuRolePermissionRepository menuRolePermissionRepository;

    /**
     * Get all menu items as a hierarchical tree for the given roles.
     */
    @Transactional(readOnly = true)
    public List<MenuItemDTO> getMenuTreeForRoles(List<String> roles) {
        if (roles == null || roles.isEmpty()) {
            return Collections.emptyList();
        }

        // Get all menu codes the user has access to
        Set<String> allowedMenuCodes = new HashSet<>(menuRolePermissionRepository.findMenuCodesByRoles(roles));

        // Get all enabled menu items
        List<MenuItem> allMenus = menuItemRepository.findByEnabledTrueOrderBySortOrder();

        // Build tree structure
        return buildMenuTree(allMenus, allowedMenuCodes);
    }

    /**
     * Get all menu items as a flat list (admin use).
     */
    @Transactional(readOnly = true)
    public List<MenuItem> getAllMenuItems() {
        return menuItemRepository.findByEnabledTrueOrderBySortOrder();
    }

    /**
     * Build hierarchical menu tree from flat list.
     */
    private List<MenuItemDTO> buildMenuTree(List<MenuItem> allMenus, Set<String> allowedMenuCodes) {
        // Map menu items by code
        Map<String, MenuItem> menuMap = allMenus.stream()
                .collect(Collectors.toMap(MenuItem::getMenuCode, m -> m));

        // Expand allowed codes to include parent sections
        Set<String> expandedCodes = new HashSet<>(allowedMenuCodes);
        for (String code : allowedMenuCodes) {
            MenuItem item = menuMap.get(code);
            while (item != null && item.getParentCode() != null) {
                expandedCodes.add(item.getParentCode());
                item = menuMap.get(item.getParentCode());
            }
        }

        // Filter to only allowed items
        List<MenuItem> allowedMenus = allMenus.stream()
                .filter(m -> expandedCodes.contains(m.getMenuCode()))
                .collect(Collectors.toList());

        // Group by parent
        Map<String, List<MenuItem>> childrenByParent = allowedMenus.stream()
                .filter(m -> m.getParentCode() != null)
                .collect(Collectors.groupingBy(MenuItem::getParentCode));

        // Build tree starting from root items
        return allowedMenus.stream()
                .filter(m -> m.getParentCode() == null)
                .sorted(Comparator.comparingInt(m -> m.getSortOrder() != null ? m.getSortOrder() : 0))
                .map(m -> toDTO(m, childrenByParent))
                .collect(Collectors.toList());
    }

    private MenuItemDTO toDTO(MenuItem item, Map<String, List<MenuItem>> childrenByParent) {
        List<MenuItemDTO> children = childrenByParent.getOrDefault(item.getMenuCode(), Collections.emptyList())
                .stream()
                .sorted(Comparator.comparingInt(m -> m.getSortOrder() != null ? m.getSortOrder() : 0))
                .map(child -> toDTO(child, childrenByParent))
                .collect(Collectors.toList());

        return MenuItemDTO.builder()
                .menuCode(item.getMenuCode())
                .parentCode(item.getParentCode())
                .label(item.getLabel())
                .icon(item.getIcon())
                .route(item.getRoute())
                .menuType(item.getMenuType())
                .children(children.isEmpty() ? null : children)
                .build();
    }

    /**
     * DTO for menu items with nested children.
     */
    @lombok.Data
    @lombok.Builder
    @lombok.NoArgsConstructor
    @lombok.AllArgsConstructor
    public static class MenuItemDTO {
        private String menuCode;
        private String parentCode;
        private String label;
        private String icon;
        private String route;
        private String menuType;
        private List<MenuItemDTO> children;
    }
}
