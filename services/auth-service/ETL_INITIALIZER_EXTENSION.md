# ETL Initializer Extension for Auth Users

## Overview

The `auth-data-v1.yaml` file has been created and integrated into the installer. The ETL Initializer service needs to be extended to process this YAML file and insert users into the `auth.users` table.

## What's Already Done

✅ Created `services/testData/auth-data-v1.yaml` with initial users
✅ Updated `app_installer.sh` to copy YAML file to ETL pod
✅ Auth service ready to authenticate users from database

## What Needs to Be Done

The ETL Initializer service needs to:

1. **Detect `auth-data-v1.yaml` files** in `/data/uploads/`
2. **Parse the YAML structure** (users with roles)
3. **Insert users into `auth.users` table**
4. **Link users to roles via `auth.user_roles` table**

## YAML File Structure

```yaml
auth:
  metadata:
    load_version: 1
    description: "Initial authentication users"

  users:
    - username: admin
      password: $2a$10$wL1b5fVd1RZ7yT.VkXqKOe...  # BCrypt hash
      email: admin@me.sa
      full_name: System Administrator
      enabled: true
      account_non_expired: true
      account_non_locked: true
      credentials_non_expired: true
      roles:
        - ROLE_ADMIN
```

## Implementation Approach

### Option 1: Extend Existing ETL Initializer (Recommended)

Add a new YAML processor similar to the existing ETL processor:

**Directory:** `services/etl_initializer/src/main/java/com/tiqmo/monitoring/etl_initializer/`

**New Classes:**
```java
// Model classes for YAML parsing
public class AuthDataYaml {
    private AuthMetadata metadata;
    private List<AuthUser> users;
}

public class AuthUser {
    private String username;
    private String password;  // BCrypt hash
    private String email;
    private String fullName;
    private Boolean enabled;
    private Boolean accountNonExpired;
    private Boolean accountNonLocked;
    private Boolean credentialsNonExpired;
    private List<String> roles;
}

// Processor class
@Component
public class AuthDataProcessor {

    @Autowired
    private JdbcTemplate jdbcTemplate;

    public void processAuthData(File yamlFile) {
        // 1. Parse YAML
        ObjectMapper mapper = new ObjectMapper(new YAMLFactory());
        AuthDataYaml authData = mapper.readValue(yamlFile, AuthDataYaml.class);

        // 2. For each user:
        for (AuthUser user : authData.getUsers()) {
            // Insert into auth.users
            Long userId = insertUser(user);

            // Insert roles into auth.user_roles
            for (String roleName : user.getRoles()) {
                linkUserToRole(userId, roleName);
            }
        }
    }

    private Long insertUser(AuthUser user) {
        String sql = """
            INSERT INTO auth.users
            (username, password, email, full_name, enabled,
             account_non_expired, account_non_locked,
             credentials_non_expired, created_by)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, 'etl-initializer')
            ON CONFLICT (username) DO UPDATE SET
                password = EXCLUDED.password,
                email = EXCLUDED.email,
                updated_at = NOW(),
                updated_by = 'etl-initializer'
            RETURNING id
            """;

        return jdbcTemplate.queryForObject(sql, Long.class,
            user.getUsername(),
            user.getPassword(),
            user.getEmail(),
            user.getFullName(),
            user.getEnabled(),
            user.getAccountNonExpired(),
            user.getAccountNonLocked(),
            user.getCredentialsNonExpired()
        );
    }

    private void linkUserToRole(Long userId, String roleName) {
        String sql = """
            INSERT INTO auth.user_roles (user_id, role_id, granted_by)
            SELECT ?, r.id, 'etl-initializer'
            FROM auth.roles r
            WHERE r.role_name = ?
            ON CONFLICT (user_id, role_id) DO NOTHING
            """;

        jdbcTemplate.update(sql, userId, roleName);
    }
}
```

**File Watcher Update:**

Modify the existing file watcher to detect `auth-data-*.yaml` files:

```java
@Component
public class YamlFileWatcher {

    @Autowired
    private EtlDataProcessor etlProcessor;

    @Autowired
    private AuthDataProcessor authProcessor;

    @Scheduled(fixedDelay = 5000)
    public void watchForFiles() {
        File uploadDir = new File("/data/uploads");
        File[] files = uploadDir.listFiles();

        for (File file : files) {
            if (file.getName().startsWith("etl-data") && file.getName().endsWith(".yaml")) {
                processEtlFile(file);
            }
            else if (file.getName().startsWith("auth-data") && file.getName().endsWith(".yaml")) {
                processAuthFile(file);  // NEW
            }
        }
    }

    private void processAuthFile(File file) {
        try {
            log.info("Processing auth data file: {}", file.getName());
            authProcessor.processAuthData(file);
            log.info("Successfully processed auth data: {}", file.getName());
            archiveFile(file);
        } catch (Exception e) {
            log.error("Failed to process auth data file: {}", file.getName(), e);
            moveToErrorDir(file);
        }
    }
}
```

### Option 2: Simple SQL Script Approach

Alternatively, create a simple SQL processor that executes SQL from YAML:

**YAML Structure:**
```yaml
auth:
  sql: |
    INSERT INTO auth.users (username, password, ...) VALUES
    ('admin', '$2a$10$...', ...),
    ('operator', '$2a$10$...', ...)
    ON CONFLICT DO NOTHING;

    INSERT INTO auth.user_roles (user_id, role_id, granted_by)
    SELECT u.id, r.id, 'system'
    FROM auth.users u, auth.roles r
    WHERE ...
```

This is simpler but less flexible.

## Testing

After extending the ETL Initializer:

1. **Deploy with installer:**
   ```bash
   ./app_installer.sh
   ```

2. **Verify users created:**
   ```bash
   kubectl exec -n monitoring-infra postgres-postgresql-0 -- \
     env PGPASSWORD=HaAirK101348App psql -U alerts_user -d alerts_db -c \
     "SELECT username, email, array_agg(r.role_name) as roles
      FROM auth.users u
      LEFT JOIN auth.user_roles ur ON u.id = ur.user_id
      LEFT JOIN auth.roles r ON ur.role_id = r.id
      GROUP BY u.username, u.email;"
   ```

3. **Test login:**
   ```bash
   curl -X POST http://localhost:30081/api/v1/auth/login \
     -H "Content-Type: application/json" \
     -d '{"username":"admin","password":"HaAdmin123"}'
   ```

## Dependencies

Add to ETL Initializer `pom.xml` if needed:

```xml
<!-- YAML parsing -->
<dependency>
    <groupId>com.fasterxml.jackson.dataformat</groupId>
    <artifactId>jackson-dataformat-yaml</artifactId>
</dependency>

<!-- JDBC for database operations -->
<dependency>
    <groupId>org.springframework.boot</groupId>
    <artifactId>spring-boot-starter-jdbc</artifactId>
</dependency>
```

## Notes

- Passwords in YAML are **already BCrypt hashed**
- Use `ON CONFLICT` to make operations idempotent
- Log all user creation activities
- Archive processed files to `/data/processed/`
- Move failed files to `/data/errors/`

## Summary

The infrastructure is ready:
- ✅ Auth service deployed
- ✅ YAML file created
- ✅ Installer updated
- 🔄 **ETL Initializer needs extension to process auth-data-v1.yaml**

Once the ETL Initializer is extended, the full authentication flow will work end-to-end!
