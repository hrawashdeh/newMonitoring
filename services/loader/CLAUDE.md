# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Quick Reference: Build & Development Commands

### Build
```bash
# Clean build
mvn clean package

# Build without running tests
mvn clean package -DskipTests

# Build with detailed logging
mvn clean package -X
```

### Run Application
```bash
# Run the application with dev profile
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"

# Run the built JAR directly
java -jar target/loader-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev
```

### Testing
```bash
# Run all tests
mvn test

# Run a specific test class
mvn test -Dtest=LoaderApplicationTests

# Run tests with verbose output
mvn test -X

# Run tests matching a pattern
mvn test -Dtest=*Service
```

### Code Quality & Analysis
```bash
# Compile only (catch errors early)
mvn compile

# Check for dependency issues
mvn dependency:check

# Display dependency tree
mvn dependency:tree
```

### Clean Up
```bash
# Remove all build artifacts
mvn clean

# Clean and reset
mvn clean && rm -rf target/
```

## Architecture Overview

### Project Structure
This is a **Spring Boot 3.5.6 ETL monitoring microservice** with clean layered architecture:

```
api/              → REST controllers (LoaderController, SignalsController, admin endpoints)
domain/           → JPA entities & repositories (Loader, SourceDatabase, SignalsHistory, etc.)
service/          → Business logic (LoaderService, SignalsIngestService, SignalsQueryService)
infra/            → Infrastructure (SourceRegistry connection pooling, SourceDbManager, EncryptionService)
  └─ security/    → Database encryption (EncryptionService, EncryptedStringConverter, EncryptionException)
probe/            → Security auditing (DbPermissionInspector for read-only verification)
runner/           → Startup handlers (SourceRegistryInitializer, DevDataLoader for encrypted seed data)
events/           → Application events (SourcesLoadedEvent)
dto/              → Data transfer objects (EtlLoaderDto, SourceConfig)
```

### Key Design Patterns

1. **Registry Pattern** (`SourceRegistry`)
   - Centralizes connection pool management for multiple data sources
   - Dynamically creates/destroys HikariCP pools per source database
   - Thread-safe with ConcurrentHashMap
   - Publishes `SourcesLoadedEvent` when sources are initialized/reloaded

2. **Multi-Database Support**
   - Supports both PostgreSQL (primary) and MySQL via dynamic driver detection
   - Configurable pool settings via `SourcePoolProperties` (sources.mysql.pool.*)
   - Separate `DbPermissionInspector` strategies for each database type

3. **Service Layer Pattern**
   - `LoaderService`: CRUD operations for ETL loaders
   - `SignalsIngestService`: Append-only signal history ingestion
   - `SignalsQueryService`: Read-only signal queries with time range filtering

4. **Event-Driven Architecture**
   - `ApplicationEventPublisher` for loose coupling
   - Applications can react to `SourcesLoadedEvent` for dynamic reconfiguration

5. **Security Auditing** (`DbPermissionInspector`)
   - **PostgreSQL**: Checks table privileges, schema ownership via information_schema
   - **MySQL**: Verifies global `read_only`/`super_read_only` flags and SHOW GRANTS
   - Returns `PermissionReport` with detailed violation list

6. **Database Column Encryption** (`EncryptionService` + JPA `@Convert`)
   - **Algorithm**: AES-256-GCM (military-grade authenticated encryption)
   - **Encrypted Fields**: `SourceDatabase.passWord`, `Loader.loaderSql`
   - **Automatic**: Transparent encryption/decryption via JPA AttributeConverter
   - **UTF-8 Support**: Arabic characters, emoji, all Unicode
   - **Key Management**: 256-bit key from Kubernetes sealed secret (ENCRYPTION_KEY)
   - **Non-deterministic**: Random IV per encryption (same plaintext → different ciphertext)
   - **Data Integrity**: GCM authentication tag prevents tampering
   - **Portable**: Encrypted data can be migrated across databases with same key

7. **API Security** (JWT Authentication + Role-Based Access Control)
   - **Authentication**: JWT tokens with HMAC-SHA256 signing
   - **Authorization**: Role-based access control (ADMIN, OPERATOR, VIEWER)
   - **Token Expiration**: 24 hours (configurable)
   - **Stateless**: No server-side session storage
   - **Security Filter Chain**: Spring Security with custom JWT filter
   - **Public Endpoints**: `/api/v1/auth/login`, `/actuator/health`

### Data Model

**Two PostgreSQL Schemas**:

1. **loader** schema
   - `loader`: ETL job definitions (loader_sql **encrypted**, interval, max parallelism)
   - `source_databases`: Connection details (host, port, pass_word **encrypted**, type)
   - `segments_dictionary`: Reference data for segment descriptions

2. **signals** schema
   - `signals_history`: Execution metrics (record count, min/max/avg values, timestamps)
   - `segment_combination`: Read-only view of segment-to-loader mappings

### Configuration

**Profiles**:
- `dev`: PostgreSQL localhost:5433, `create-drop` DDL mode, sample data initialization
- Configure via `application.yaml` (base) + `application-{profile}.yaml` (overrides)

**Key Properties** (application-dev.yaml):
- `spring.datasource.url`: PostgreSQL connection
- `spring.jpa.hibernate.ddl-auto: create-drop`: Recreate schema on startup
- `spring.jpa.defer-datasource-initialization: true`: Load schema before data
- `sources.mysql.pool.*`: MySQL connection pool settings
- `encryption.key`: AES-256 encryption key (32 bytes Base64) from `ENCRYPTION_KEY` env var
- `jwt.secret`: JWT signing secret (min 256 bits) from `JWT_SECRET` env var
- `jwt.expiration-ms`: JWT token expiration time in milliseconds (default: 86400000 = 24 hours)

### API Endpoints

| Method | Path | Purpose |
|--------|------|---------|
| GET | `/api/v1/res/loaders/loaders` | List all ETL loaders |
| GET | `/api/v1/res/signals/signal/{loaderCode}` | Query signals by loader code and epoch time range (fromEpoch, toEpoch) |
| GET | `/ops/v1/admin/res/db-sources` | List source databases (credentials redacted) |
| POST | `/ops/v1/admin/security/reload` | Reload all source database connections and publish event |
| GET | `/api/v1/admin/security/read-only-check` | Audit all sources for read-only compliance with detailed violations |

**Actuator Endpoints**:
- `GET /actuator/health`: Health check (public, no authentication required)

### API Security & Authentication

**Authentication Method:** JWT (JSON Web Tokens) with HMAC-SHA256 signing

**Default Users** (in-memory, development only):
| Username | Password | Role | Permissions |
|----------|----------|------|-------------|
| admin | admin123 | ROLE_ADMIN | Full access (read, write, delete, admin operations) |
| operator | operator123 | ROLE_OPERATOR | Read + operational endpoints (pause/resume, reload) |
| viewer | viewer123 | ROLE_VIEWER | Read-only access to data endpoints |

**Authentication Flow:**

1. **Login to get JWT token:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{
    "username": "admin",
    "password": "admin123"
  }'

# Response:
{
  "token": "eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...",
  "type": "Bearer",
  "username": "admin",
  "roles": ["ROLE_ADMIN"]
}
```

2. **Use token in subsequent requests:**
```bash
# Get all loaders (requires authentication)
curl -X GET http://localhost:8080/api/v1/res/loaders/loaders \
  -H "Authorization: Bearer eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9..."

# Create new loader (requires ROLE_ADMIN)
curl -X POST http://localhost:8080/api/v1/res/loaders \
  -H "Authorization: Bearer YOUR_TOKEN_HERE" \
  -H "Content-Type: application/json" \
  -d '{
    "loaderCode": "NEW_LOADER",
    "sourceDatabase": {...},
    ...
  }'
```

**Authorization Rules:**

| Endpoint Pattern | Method | Required Role | Notes |
|------------------|--------|---------------|-------|
| `/api/v1/auth/**` | ALL | Public | Login endpoint |
| `/actuator/health` | GET | Public | Health check |
| `/api/v1/res/loaders` | POST | ROLE_ADMIN | Create loader |
| `/api/v1/res/loaders/{code}` | PUT | ROLE_ADMIN | Update loader |
| `/api/v1/res/loaders/{code}` | DELETE | ROLE_ADMIN | Delete loader |
| `/api/v1/res/signals/**` | POST | ROLE_ADMIN | Ingest signals |
| `/ops/v1/admin/**` | POST | ROLE_ADMIN or ROLE_OPERATOR | Operational endpoints |
| `/ops/v1/admin/**` | PUT/DELETE | ROLE_ADMIN | Admin operations |
| `/api/v1/res/**` | GET | Any authenticated user | Read operations |
| `/ops/v1/admin/**` | GET | Any authenticated user | Admin read operations |

**Security Best Practices:**

1. **Production Deployment:**
   - Replace in-memory users with database-backed `UserDetailsService`
   - Generate secure JWT secret: `openssl rand -base64 32`
   - Set `JWT_SECRET` environment variable in Kubernetes secret
   - Use HTTPS/TLS for all communications
   - Implement refresh tokens for long-lived sessions

2. **Token Management:**
   - Tokens expire after 24 hours (configurable via `jwt.expiration-ms`)
   - No server-side session storage (stateless)
   - Tokens contain username and roles in claims
   - Tokens are signed with HMAC-SHA256

3. **Error Handling:**
   - 401 Unauthorized: Missing or invalid token
   - 403 Forbidden: Valid token but insufficient permissions
   - 404 Not Found: Resource doesn't exist
   - 500 Internal Server Error: Server-side error

**Testing Security:**

```bash
# Test without authentication (should fail with 401)
curl -X GET http://localhost:8080/api/v1/res/loaders/loaders

# Test with invalid token (should fail with 401)
curl -X GET http://localhost:8080/api/v1/res/loaders/loaders \
  -H "Authorization: Bearer invalid_token"

# Test viewer role trying to create loader (should fail with 403)
# First login as viewer
TOKEN=$(curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"viewer","password":"viewer123"}' \
  | jq -r '.token')

# Then try to create loader (should fail)
curl -X POST http://localhost:8080/api/v1/res/loaders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{...}'
```

## Working with This Codebase

### Key Classes & Their Responsibilities

**API Layer** (`api/`):
- `LoaderController`: Exposes loader list endpoint
- `SignalsController`: Time-range signal queries
- `SourcesAdminController`: Dynamic source management with event publishing
- `SecurityAdminController`: Read-only compliance verification

**Infrastructure** (`infra/`):
- `SourceRegistry`: Singleton managing connection pools; initializes on `ApplicationReadyEvent`
- `SourceDbManager`: Executes queries and connection probes against source databases
- `SourcePoolProperties`: Configuration POJO for MySQL pool settings

**Security** (`probe/`):
- `DbPermissionInspector`: Database-specific read-only verification logic
- `PermissionReport`: Immutable audit result with violations list

**Encryption** (`infra/security/`):
- `EncryptionService`: AES-256-GCM encryption/decryption; loads key from `ENCRYPTION_KEY` env var
- `EncryptedStringConverter`: JPA AttributeConverter for transparent column encryption
- `EncryptionException`: Runtime exception for encryption/decryption failures

**API Security** (`infra/security/`):
- `JwtTokenProvider`: JWT token generation and validation; HMAC-SHA256 signing
- `JwtAuthenticationFilter`: Intercepts requests and validates JWT tokens
- `SecurityConfig`: Spring Security configuration with role-based access control
- `AuthController`: Authentication endpoint for user login and token generation

**Data Loading** (`runner/`):
- `DevDataLoader`: @Profile("dev") component that seeds encrypted data via JPA (replaces data-dev.sql)
- `SourceRegistryInitializer`: Loads source database connection pools on startup

### Startup Flow
1. Spring loads `application.yaml` + profile-specific config
2. `EncryptionService` initializes with `ENCRYPTION_KEY` from environment (fails fast if missing/invalid)
3. `schema-dev.sql` creates `loader` and `signals` schemas (DDL: create-drop in dev)
4. `DevDataLoader` (dev profile only) seeds initial data via JPA with automatic encryption
5. `SourceRegistry.onReady()` listens for `ApplicationReadyEvent`
6. Registry loads all `SourceDatabase` entities from JPA repo (passwords auto-decrypted)
7. For each source: detect DB type → create HikariCP pool with decrypted credentials
8. `SourcesLoadedEvent` published; listeners can now use pools

### Making Code Changes

**Adding a New API Endpoint**:
1. Create controller method with `@GetMapping` / `@PostMapping` in `api/` package
2. Inject corresponding service via constructor
3. Follow DTO pattern: accept/return DTOs, not entities
4. Transactional boundaries managed by service layer

**Adding a New Database Query**:
1. If read-only: add method to repository, inject as `@Transactional(readOnly = true)` service method
2. Use named queries or Querydsl for complex queries
3. Return projections or DTOs to API layer, never raw entities

**Modifying Entity Schema**:
1. Edit entity class (add/remove fields, update `@Table`, `@Column` annotations)
2. Update corresponding `schema-dev.sql` for local testing
3. Regenerate schema with `mvn clean package` (DDL mode: create-drop in dev)

**Connecting to New Data Source Type**:
1. Add `DbType` enum variant if needed (currently MYSQL, POSTGRESQL)
2. Extend `SourceRegistry` URL builder for new dialect
3. Update `DbPermissionInspector.inspect()` with database-specific audit logic
4. Test with `DbConnectivityRunner` or probe endpoint

### Testing Considerations

- **Spring Boot Test**: Full application context for integration tests
- **Transactional Tests**: Mark with `@Transactional` for automatic rollback
- **Profiles**: Use `@ActiveProfiles("test")` for test-specific configuration
- **Test Data**: Leverage `schema-dev.sql` and `data-dev.sql` initialization

### Common Development Tasks

**Debug Connection Pool Issues**:
- Check `SourceRegistry.pools` state: is pool created?
- Review HikariCP logs: connection timeouts, pool exhaustion
- Verify credentials in `source_databases` table
- Probe endpoint: `GET /api/v1/admin/security/read-only-check`

**Add Resilience (Circuit Breaker / Retry)**:
- Resilience4j BOM already imported (v2.3.0)
- Annotate service methods: `@Retry`, `@CircuitBreaker`, `@RateLimiter`
- Configure via `application.yaml` under `resilience4j.*`

**Performance Optimization**:
- Review SQL logs in dev profile (formatted_sql: true)
- Add database indexes to frequently-queried columns
- Use `@Transactional(readOnly = true)` for queries
- Profile with Spring Actuator `/actuator/prometheus`

**Working with Encrypted Fields**:

**Adding New Encrypted Column**:
1. Annotate entity field with `@Convert(converter = EncryptedStringConverter.class)`
2. Ensure column size is sufficient (recommend VARCHAR(512) for passwords, TEXT for SQL)
3. Example:
   ```java
   @Convert(converter = EncryptedStringConverter.class)
   @Column(name = "api_key", length = 512)
   private String apiKey;
   ```
4. Data automatically encrypted before INSERT/UPDATE, decrypted after SELECT

**Development Environment Setup**:
1. Set encryption key in `application-dev.yaml` (DEV ONLY - do not use in production):
   ```yaml
   encryption:
     key: ${ENCRYPTION_KEY:e2mWpFHBa3+3DRfZtaNCADsy3E7FArTZ2RFGCRsxod4=}
   ```
2. Or export environment variable:
   ```bash
   export ENCRYPTION_KEY="e2mWpFHBa3+3DRfZtaNCADsy3E7FArTZ2RFGCRsxod4="
   mvn spring-boot:run
   ```

**Production Environment (Kubernetes)**:
1. Generate production key (ONE TIME):
   ```bash
   openssl rand -base64 32
   # Store securely: password manager, HashiCorp Vault, AWS Secrets Manager
   ```
2. Create sealed secret:
   ```bash
   cd deployment/scripts
   ./create-sealed-secret.sh
   # Script prompts for encryption key (or generates new one)
   ```
3. Deploy sealed secret:
   ```bash
   kubectl apply -f deployment/k8s/sealed-secret.yaml
   kubectl apply -f deployment/k8s/deployment.yaml
   ```

**Seeding Encrypted Data**:
- Use `DevDataLoader` component (JPA) instead of SQL INSERT
- Example:
  ```java
  SourceDatabase source = SourceDatabase.builder()
    .passWord("PlaintextPassword")  // Auto-encrypted on save
    .build();
  repository.save(source);
  ```
- SQL INSERT statements bypass JPA converter (data stored as plaintext!)

**Database Migration with Encryption**:
- Encrypted data is portable across databases with same key
- Use direct database copy (pg_dump/restore) to preserve ciphertext
- See: `docs/DATABASE_MIGRATION_WITH_ENCRYPTION.md` for detailed guide

**Encryption Security**:
- Algorithm: AES-256-GCM (NIST-approved, cannot be decrypted without key)
- Key Storage: Kubernetes sealed secret (encrypted with cluster key)
- UTF-8 Support: Arabic characters, emoji, all Unicode
- Non-deterministic: Same plaintext → different ciphertext each time (random IV)
- Tamper Detection: GCM authentication tag prevents data modification
- Never log decrypted values (EncryptedStringConverter prevents exposure)

**Troubleshooting Encryption**:
- **"Encryption key not found"**: Set `ENCRYPTION_KEY` environment variable
- **"Invalid encryption key size"**: Must be 32 bytes Base64-encoded
- **"Decryption failed"**: Wrong key or corrupted data; verify `ENCRYPTION_KEY` matches
- **Plaintext in database**: Check entity has `@Convert` annotation; verify using JPA (not SQL INSERT)

### Dependency Management
- Parent POM: `spring-boot-starter-parent` 3.5.6
- Resilience4j v2.3.0 (circuit breaker, retry, rate limiting)
- Lombok v1.18.32 (code generation)
- PostgreSQL driver (runtime scope)
- MySQL connector (runtime scope)
- Spring DevTools (for hot reload during development)

### Git Workflow
- Active development on `master` branch
- Recent major refactor: "both JPA sources" (multi-database support)
- Staged files include new domain/service/API restructuring
