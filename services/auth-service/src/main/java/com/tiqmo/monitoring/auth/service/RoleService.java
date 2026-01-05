package com.tiqmo.monitoring.auth.service;

import com.tiqmo.monitoring.auth.domain.Role;
import com.tiqmo.monitoring.auth.repository.RoleRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class RoleService {

    private final RoleRepository roleRepository;

    public List<Role> getAllRoles() {
        return roleRepository.findAll();
    }

    public Optional<Role> getRoleById(Long id) {
        return roleRepository.findById(id);
    }

    public Optional<Role> getRoleByName(String roleName) {
        return roleRepository.findByRoleName(roleName);
    }

    @Transactional
    public Role createRole(String roleName, String description) {
        if (roleRepository.findByRoleName(roleName).isPresent()) {
            throw new IllegalArgumentException("Role already exists: " + roleName);
        }

        Role role = Role.builder()
                .roleName(roleName)
                .description(description)
                .build();

        Role saved = roleRepository.save(role);
        log.info("Created role: {}", roleName);
        return saved;
    }

    @Transactional
    public Role updateRole(Long id, String description) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));

        if (description != null) {
            role.setDescription(description);
        }

        Role saved = roleRepository.save(role);
        log.info("Updated role: {}", role.getRoleName());
        return saved;
    }

    @Transactional
    public void deleteRole(Long id) {
        Role role = roleRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Role not found: " + id));

        // Prevent deleting system roles
        if (role.getRoleName().equals("ROLE_ADMIN") || role.getRoleName().equals("ROLE_VIEWER")) {
            throw new IllegalArgumentException("Cannot delete system role: " + role.getRoleName());
        }

        roleRepository.delete(role);
        log.info("Deleted role: {}", role.getRoleName());
    }
}
