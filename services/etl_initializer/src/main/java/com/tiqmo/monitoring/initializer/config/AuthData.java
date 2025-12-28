package com.tiqmo.monitoring.initializer.config;

import lombok.Data;

import java.util.List;

@Data
public class AuthData {
    private AuthMetadata metadata;
    private List<AuthUserConfig> users;

    @Data
    public static class AuthMetadata {
        private Integer loadVersion;        // Version being loaded
        private String description;         // Description of this version
    }

    @Data
    public static class AuthUserConfig {
        private String username;
        private String password;            // BCrypt hash (already encrypted in YAML)
        private String email;
        private String fullName;
        private Boolean enabled;
        private Boolean accountNonExpired;
        private Boolean accountNonLocked;
        private Boolean credentialsNonExpired;
        private List<String> roles;
    }
}
