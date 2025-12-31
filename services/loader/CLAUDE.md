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

---

## Troubleshooting & Deployment Manual

### Packaging & Deployment Strategy

#### Full Deployment Pipeline (Backend Services)

```bash
# 1. Clean build with Maven (skip tests if they fail during compilation)
cd /Volumes/Files/Projects/newLoader/services/loader
mvn clean package -Dmaven.test.skip=true

# 2. Copy JAR to Docker-expected location (if Dockerfile expects signal-loader-*.jar)
cp target/loader-0.0.1-SNAPSHOT.jar target/signal-loader-0.0.1-SNAPSHOT.jar

# 3. Build Docker image (CRITICAL: Use --no-cache if code changes not reflected)
docker build -t signal-loader:latest .
# OR with cache bypass:
docker build --no-cache -t signal-loader:latest .

# 4. Restart Kubernetes deployment to pick up new image
kubectl rollout restart deployment/signal-loader -n monitoring-app

# 5. Wait for rollout to complete (CRITICAL: Don't skip this step!)
kubectl rollout status deployment/signal-loader -n monitoring-app --timeout=120s

# 6. Verify new pod is running
kubectl get pods -n monitoring-app -l app=signal-loader
```

**One-Liner for Quick Deployment:**
```bash
mvn clean package -Dmaven.test.skip=true && \
cp target/loader-0.0.1-SNAPSHOT.jar target/signal-loader-0.0.1-SNAPSHOT.jar && \
docker build -t signal-loader:latest . && \
kubectl rollout restart deployment/signal-loader -n monitoring-app && \
kubectl rollout status deployment/signal-loader -n monitoring-app --timeout=120s
```

#### Frontend Deployment Pipeline

```bash
# 1. Generate version.json with build timestamp
cd /Volumes/Files/Projects/newLoader/frontend
node scripts/generate-version.cjs

# 2. Update buildInfo.ts with new build number
TIMESTAMP=$(date +%Y%m%d%H%M%S)
cat > src/buildInfo.ts <<EOF
// Auto-generated build information
// This file is updated automatically by the deployment script

export const BUILD_INFO = {
  buildNumber: '${TIMESTAMP}',
  buildDate: '$(date -u +"%Y-%m-%dT%H:%M:%SZ")',
  version: '1.2.0',
};
EOF

# 3. Build React app
npm run build

# 4. Build Docker image with TIMESTAMPED TAG (avoid caching issues)
docker build -t loader-frontend:1.2.0-${TIMESTAMP} .

# 5. Update k8s_manifist/frontend-deployment.yaml with new image tag
sed -i '' "s|image: loader-frontend:.*|image: loader-frontend:1.2.0-${TIMESTAMP}|" k8s_manifist/frontend-deployment.yaml

# 6. Apply Kubernetes deployment
kubectl apply -f k8s_manifist/frontend-deployment.yaml

# 7. Wait for rollout
kubectl rollout status deployment/loader-frontend -n monitoring-app --timeout=120s
```

**CRITICAL: Frontend Image Versioning Strategy**

- **NEVER use `:latest` tag for frontend images** - causes caching issues
- **ALWAYS use timestamped tags**: `loader-frontend:1.2.0-YYYYMMDDHHMMSS`
- **Keep `imagePullPolicy: IfNotPresent`** for local Docker images (Docker Desktop shares with Kubernetes)
- **Unique tag forces pod recreation** even with IfNotPresent policy
- **Update deployment YAML** with new tag on every build

### Kubernetes Monitoring & Debugging

#### Comprehensive Log Monitoring (Production-Ready)

**Start monitoring ALL services before deploying or testing:**

```bash
# 1. Loader service errors/warnings
kubectl logs -n monitoring-app -l app=signal-loader --all-containers=true -f | \
  grep -iE "error|warning|fail|exception|sql.*error" --line-buffered

# 2. Gateway service errors/HTTP failures
kubectl logs -n monitoring-app -l app=gateway-service --all-containers=true -f | \
  grep -iE "error|warning|fail|exception|400|404|500" --line-buffered

# 3. Frontend nginx errors
kubectl logs -n monitoring-app -l app=loader-frontend --all-containers=true -f | \
  grep -iE "error|warning|fail|400|404|500" --line-buffered

# 4. All monitoring-infra pods
kubectl logs -n monitoring-infra --all-pods --all-containers=true -f 2>&1 | \
  grep -iE "error|warning|fail|exception" --line-buffered

# 5. All monitoring-app pods (comprehensive)
kubectl logs -n monitoring-app --all-pods --all-containers=true -f 2>&1 | \
  grep -iE "error|warning|fail|exception|400|404|500|approved" --line-buffered
```

**Run all monitors in parallel (use separate terminal tabs/tmux panes):**

```bash
# Terminal 1: Backend errors
kubectl logs -n monitoring-app -l app=signal-loader --all-containers=true -f | grep -iE "error|warning|fail|exception" --line-buffered

# Terminal 2: Gateway HTTP errors  
kubectl logs -n monitoring-app -l app=gateway-service --all-containers=true -f | grep -iE "400|404|500" --line-buffered

# Terminal 3: Frontend errors
kubectl logs -n monitoring-app -l app=loader-frontend --all-containers=true -f | grep -iE "error|400|500" --line-buffered

# Terminal 4: Watch pod status
watch -n 1 'kubectl get pods -n monitoring-app'
```

#### Common Deployment Issues & Solutions

**Issue 1: HTTP 400 Bad Request on New API Endpoint**

**Symptoms:**
- Frontend shows HTTP 400 error
- Gateway logs show 400 status
- Backend logs: `Type mismatch | parameter=XXX | expectedType=class java.lang.Long`

**Root Cause:** Spring path matching conflict - catch-all path variable `/{id}` declared before specific paths

**Solution:**
1. Check `@GetMapping` order in controller
2. Move specific paths (like `/approved`, `/pending/count`) BEFORE catch-all `/{id}` endpoints
3. Example fix in `ApprovalController.java`:
   ```java
   // CORRECT ORDER:
   @GetMapping("/approved")        // Line 94 - specific path
   @GetMapping("/pending/count")   // Line 109 - specific path  
   @GetMapping("/{requestId}")     // Line 185 - catch-all (MUST be last!)
   
   // WRONG ORDER:
   @GetMapping("/{requestId}")     // Matches EVERYTHING including "approved"
   @GetMapping("/approved")        // Never reached!
   ```
4. Rebuild and redeploy backend

**Issue 2: Frontend Changes Not Visible After Deployment**

**Symptoms:**
- Build number in footer not updated
- Old UI still showing
- `version.json` shows old timestamp

**Root Causes:**
1. **Browser caching**: Despite nginx cache headers
2. **Docker image caching**: Using `:latest` tag with `imagePullPolicy: IfNotPresent`
3. **Build artifacts not updated**: Vite build output still old

**Solutions:**

**A. Infrastructure-Level Fix (Production-Ready):**
```bash
# 1. Use timestamped image tags (NOT :latest)
TIMESTAMP=$(date +%Y%m%d%H%M%S)
docker build -t loader-frontend:1.2.0-${TIMESTAMP} .

# 2. Update deployment YAML
sed -i '' "s|image: loader-frontend:.*|image: loader-frontend:1.2.0-${TIMESTAMP}|" k8s_manifist/frontend-deployment.yaml

# 3. Apply and verify
kubectl apply -f k8s_manifist/frontend-deployment.yaml
kubectl rollout status deployment/loader-frontend -n monitoring-app --timeout=120s
```

**B. Nginx Cache-Busting Headers (Already configured in nginx.conf):**
```nginx
# NEVER cache index.html or version.json
location = /index.html {
    add_header Cache-Control "no-store, no-cache, must-revalidate" always;
    add_header Pragma "no-cache" always;
    add_header Expires "0" always;
    add_header X-Accel-Expires "0" always;
    expires -1;
}

# NO CACHING for JS/CSS in development
location ~* \.(js|css|map)$ {
    add_header Cache-Control "no-store, no-cache, must-revalidate" always;
    add_header Pragma "no-cache" always;
    add_header Expires "0" always;
    if_modified_since off;
    etag off;
}
```

**C. Verify Deployment:**
```bash
# Check deployed image tag
kubectl get deployment loader-frontend -n monitoring-app -o jsonpath='{.spec.template.spec.containers[0].image}'

# Check pod age (should be < 2 minutes after deploy)
kubectl get pods -n monitoring-app -l app=loader-frontend

# Check version.json from pod
kubectl exec -n monitoring-app deployment/loader-frontend -- cat /usr/share/nginx/html/version.json
```

**Issue 3: Maven Build Missing JAR File**

**Symptoms:**
- `target/signal-loader-0.0.1-SNAPSHOT.jar` not found
- Docker build fails with "COPY failed: file not found"
- Previous deployment worked, but `mvn clean package` deleted target directory

**Root Cause:** `mvn clean` deletes entire `target/` directory; if build fails silently, JAR won't exist

**Solution:**
```bash
# 1. Always check Maven build output for errors
mvn clean package -Dmaven.test.skip=true
# Look for "BUILD SUCCESS" at the end

# 2. Verify JAR exists before Docker build
ls -lh target/loader-0.0.1-SNAPSHOT.jar

# 3. Copy to Docker-expected name
cp target/loader-0.0.1-SNAPSHOT.jar target/signal-loader-0.0.1-SNAPSHOT.jar

# 4. Only then build Docker image
docker build -t signal-loader:latest .
```

**Issue 4: Deployment Rollout Stuck or Failed**

**Symptoms:**
- `kubectl rollout status` times out
- Old pods still running
- New pods in `CrashLoopBackOff` or `ImagePullBackOff`

**Diagnosis:**
```bash
# Check pod status
kubectl get pods -n monitoring-app -l app=signal-loader

# Check pod events
kubectl describe pod <pod-name> -n monitoring-app

# Check pod logs
kubectl logs <pod-name> -n monitoring-app --tail=100

# Check deployment events
kubectl describe deployment signal-loader -n monitoring-app
```

**Common Causes & Fixes:**

**A. ImagePullBackOff (image not found):**
```bash
# Verify image exists locally
docker images signal-loader

# If missing, rebuild
docker build -t signal-loader:latest .

# Restart deployment
kubectl rollout restart deployment/signal-loader -n monitoring-app
```

**B. CrashLoopBackOff (application startup failure):**
```bash
# Check application logs
kubectl logs -n monitoring-app -l app=signal-loader --tail=200 | grep -i error

# Common causes:
# - Missing environment variables (ENCRYPTION_KEY, JWT_SECRET)
# - Database connection failure
# - Port already in use (change containerPort in deployment.yaml)
```

**C. Stuck rollout (old pod not terminating):**
```bash
# Force delete old pods
kubectl delete pod <old-pod-name> -n monitoring-app --grace-period=0 --force

# Check for PodDisruptionBudget blocking termination
kubectl get pdb -n monitoring-app
```

### Investigation & Troubleshooting Workflow

**Step 1: Identify the Problem Layer**

```bash
# Quick health check of all services
kubectl get pods -n monitoring-app -o wide

# Check recent events
kubectl get events -n monitoring-app --sort-by='.lastTimestamp' | tail -20
```

**Step 2: Frontend Issues**

```bash
# Check nginx access logs for HTTP errors
kubectl logs -n monitoring-app -l app=loader-frontend | grep -E "400|404|500"

# Check if API calls are reaching gateway
kubectl logs -n monitoring-app -l app=gateway-service | grep "/api/approvals"

# Verify version.json is updated
curl http://localhost:30080/version.json
```

**Step 3: Backend Issues**

```bash
# Check Spring Boot startup logs
kubectl logs -n monitoring-app -l app=signal-loader --tail=200 | grep "Started LoaderApplication"

# Check for controller mapping issues
kubectl logs -n monitoring-app -l app=signal-loader | grep "Mapped \[" | grep approval

# Check for runtime errors
kubectl logs -n monitoring-app -l app=signal-loader | grep -iE "error|exception|warn" | tail -50
```

**Step 4: Database Issues**

```bash
# Check database connectivity from loader pod
kubectl exec -n monitoring-app deployment/signal-loader -- nc -zv postgres-monitoring-service.monitoring-infra.svc.cluster.local 5432

# Check database logs
kubectl logs -n monitoring-infra postgres-monitoring-0

# Check if schema exists
kubectl exec -n monitoring-infra postgres-monitoring-0 -- psql -U monitoring -d monitoringdb -c "\dt loader.*"
```

**Step 5: Network/Gateway Issues**

```bash
# Check gateway routing rules
kubectl logs -n monitoring-app -l app=gateway-service | grep "Route matched"

# Test direct access to backend (bypass gateway)
kubectl port-forward -n monitoring-app deployment/signal-loader 8080:8080 &
curl -H "Authorization: Bearer YOUR_TOKEN" http://localhost:8080/api/approvals/approved
```

### User Stories Tracking

**Location:** `/Volumes/Files/Projects/newLoader/USER_STORIES_TRACKER.md`

**Update workflow:**

```bash
# After completing a feature
vim USER_STORIES_TRACKER.md

# Add completed feature with timestamp
- ✅ [2025-12-29] Approval Management - Revoke UI implemented
  - Backend: GET /api/approvals/approved endpoint
  - Frontend: Tabs interface (Pending/Approved)
  - Frontend: Revoke dialog with mandatory reason
  - Fixed: Spring path matching conflict (/{id} vs /approved)

# Commit changes
git add USER_STORIES_TRACKER.md
git commit -m "Update user stories: Approval revoke UI completed"
```

**Format:**
```markdown
## Sprint N - YYYY-MM-DD to YYYY-MM-DD

### In Progress
- ⏳ Story title
  - Acceptance criteria 1
  - Acceptance criteria 2

### Completed
- ✅ [YYYY-MM-DD] Story title
  - What was implemented
  - Files changed
  - Known issues/limitations

### Blocked
- ❌ Story title
  - Blocker description
  - Dependencies
```

### Deployment Scripts Best Practices

**Script Template:** `/Volumes/Files/Projects/newLoader/scripts/deploy-loader-service.sh`

```bash
#!/bin/bash
set -euo pipefail  # Exit on error, undefined variable, pipe failure

# Configuration
SERVICE_NAME="signal-loader"
NAMESPACE="monitoring-app"
DOCKERFILE_PATH="/Volumes/Files/Projects/newLoader/services/loader"
TIMESTAMP=$(date +%Y%m%d%H%M%S)

# Color output
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m' # No Color

echo -e "${GREEN}=== Deploying ${SERVICE_NAME} ===${NC}"

# Step 1: Build with Maven
echo -e "${YELLOW}Step 1/6: Building with Maven...${NC}"
cd "${DOCKERFILE_PATH}"
mvn clean package -Dmaven.test.skip=true || {
  echo -e "${RED}Maven build failed!${NC}"
  exit 1
}

# Step 2: Verify JAR exists
echo -e "${YELLOW}Step 2/6: Verifying JAR file...${NC}"
if [[ ! -f "target/loader-0.0.1-SNAPSHOT.jar" ]]; then
  echo -e "${RED}JAR file not found!${NC}"
  exit 1
fi
cp target/loader-0.0.1-SNAPSHOT.jar target/signal-loader-0.0.1-SNAPSHOT.jar
echo -e "${GREEN}JAR file verified: $(ls -lh target/signal-loader-0.0.1-SNAPSHOT.jar | awk '{print $5}')${NC}"

# Step 3: Build Docker image
echo -e "${YELLOW}Step 3/6: Building Docker image...${NC}"
docker build -t ${SERVICE_NAME}:latest . || {
  echo -e "${RED}Docker build failed!${NC}"
  exit 1
}
echo -e "${GREEN}Docker image built successfully${NC}"

# Step 4: Restart Kubernetes deployment
echo -e "${YELLOW}Step 4/6: Restarting Kubernetes deployment...${NC}"
kubectl rollout restart deployment/${SERVICE_NAME} -n ${NAMESPACE} || {
  echo -e "${RED}Deployment restart failed!${NC}"
  exit 1
}

# Step 5: Wait for rollout
echo -e "${YELLOW}Step 5/6: Waiting for rollout to complete...${NC}"
kubectl rollout status deployment/${SERVICE_NAME} -n ${NAMESPACE} --timeout=120s || {
  echo -e "${RED}Rollout failed or timed out!${NC}"
  kubectl get pods -n ${NAMESPACE} -l app=${SERVICE_NAME}
  kubectl logs -n ${NAMESPACE} -l app=${SERVICE_NAME} --tail=50
  exit 1
}

# Step 6: Verify deployment
echo -e "${YELLOW}Step 6/6: Verifying deployment...${NC}"
POD_NAME=$(kubectl get pods -n ${NAMESPACE} -l app=${SERVICE_NAME} -o jsonpath='{.items[0].metadata.name}')
POD_AGE=$(kubectl get pod ${POD_NAME} -n ${NAMESPACE} -o jsonpath='{.status.startTime}')

echo -e "${GREEN}=== Deployment Successful ===${NC}"
echo -e "Pod Name: ${POD_NAME}"
echo -e "Pod Age: ${POD_AGE}"
echo -e "Image: $(kubectl get pod ${POD_NAME} -n ${NAMESPACE} -o jsonpath='{.spec.containers[0].image}')"

# Optional: Tail logs
echo -e "${YELLOW}Tailing logs (Ctrl+C to exit)...${NC}"
kubectl logs -n ${NAMESPACE} ${POD_NAME} -f
```

**Make script executable:**
```bash
chmod +x scripts/deploy-loader-service.sh
```

**Usage:**
```bash
./scripts/deploy-loader-service.sh
```

### Pre-Deployment Checklist

Before deploying to production:

- [ ] **Code changes tested locally**
  ```bash
  mvn test
  mvn spring-boot:run  # Test manually
  ```

- [ ] **Database migrations reviewed**
  ```bash
  ls -ltr src/main/resources/db/migration/
  # Check latest Vxxx__*.sql files
  ```

- [ ] **Secrets/environment variables updated**
  ```bash
  kubectl get secret app-secrets -n monitoring-app -o yaml
  # Verify ENCRYPTION_KEY, JWT_SECRET, etc.
  ```

- [ ] **Kubernetes resources allocated**
  ```bash
  kubectl top nodes
  kubectl top pods -n monitoring-app
  # Ensure sufficient CPU/memory
  ```

- [ ] **Backup database before schema changes**
  ```bash
  kubectl exec -n monitoring-infra postgres-monitoring-0 -- \
    pg_dump -U monitoring monitoringdb > backup-$(date +%Y%m%d).sql
  ```

- [ ] **Monitoring setup for deployment**
  ```bash
  # Start log monitoring in separate terminals
  kubectl logs -n monitoring-app -l app=signal-loader -f | grep -i error &
  kubectl logs -n monitoring-app -l app=gateway-service -f | grep -i error &
  ```

- [ ] **Rollback plan documented**
  ```bash
  # Save current deployment state
  kubectl get deployment signal-loader -n monitoring-app -o yaml > rollback-deployment.yaml
  
  # To rollback:
  kubectl apply -f rollback-deployment.yaml
  # OR
  kubectl rollout undo deployment/signal-loader -n monitoring-app
  ```

- [ ] **User stories updated**
  ```bash
  vim USER_STORIES_TRACKER.md
  git add USER_STORIES_TRACKER.md
  git commit -m "Update user stories: [Feature Name]"
  ```

### Post-Deployment Verification

```bash
# 1. Check pod status
kubectl get pods -n monitoring-app -l app=signal-loader

# 2. Check application logs for startup success
kubectl logs -n monitoring-app -l app=signal-loader --tail=100 | grep "Started LoaderApplication"

# 3. Verify API endpoints
TOKEN=$(curl -X POST http://localhost:30080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

curl -H "Authorization: Bearer $TOKEN" http://localhost:30080/api/approvals/approved

# 4. Check database connectivity
kubectl logs -n monitoring-app -l app=signal-loader | grep "HikariPool-1 - Start completed"

# 5. Monitor for errors (5 minutes)
kubectl logs -n monitoring-app -l app=signal-loader -f --since=5m | grep -iE "error|exception|warn"
```

### Common Spring Boot Path Mapping Issues

**Issue:** Endpoints returning HTTP 400 due to path variable type mismatch

**Symptoms:**
```
Type mismatch | parameter=requestId | expectedType=class java.lang.Long
Invalid value for parameter 'requestId': expected Long
```

**Root Cause:** Spring matches paths in declaration order. Catch-all path variables like `/{id}` match EVERYTHING.

**Correct Endpoint Order in Controllers:**

```java
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {
    
    // 1. SPECIFIC PATHS FIRST (no path variables)
    @GetMapping("/pending")
    @GetMapping("/approved")
    @PostMapping("/submit")
    @PostMapping("/approve")
    @PostMapping("/reject")
    
    // 2. SPECIFIC PATHS WITH MULTIPLE SEGMENTS (more specific)
    @GetMapping("/pending/count")
    @GetMapping("/pending/{entityType}")
    @GetMapping("/history/{entityType}/{entityId}")
    @GetMapping("/{requestId}/actions")
    
    // 3. CATCH-ALL PATHS LAST (single path variable)
    @GetMapping("/{requestId}")  // MUST BE LAST!
}
```

**Testing Path Matching:**

```bash
# Check Spring request mappings
kubectl logs -n monitoring-app -l app=signal-loader | grep "Mapped \[" | grep approval

# Should see:
# GET /api/approvals/approved  → getAllApprovedApprovals()
# GET /api/approvals/pending   → getAllPendingApprovals()
# GET /api/approvals/{requestId} → getApprovalRequest()
```

### Emergency Rollback Procedures

**Scenario 1: Bad deployment causing 500 errors**

```bash
# 1. Rollback to previous deployment
kubectl rollout undo deployment/signal-loader -n monitoring-app

# 2. Check rollback status
kubectl rollout status deployment/signal-loader -n monitoring-app

# 3. Verify old pod is running
kubectl get pods -n monitoring-app -l app=signal-loader
```

**Scenario 2: Database schema migration failed**

```bash
# 1. Stop application
kubectl scale deployment signal-loader -n monitoring-app --replicas=0

# 2. Restore database backup
kubectl exec -n monitoring-infra postgres-monitoring-0 -- \
  psql -U monitoring monitoringdb < backup-YYYYMMDD.sql

# 3. Restart with old version
kubectl set image deployment/signal-loader \
  signal-loader=signal-loader:previous-tag -n monitoring-app

# 4. Scale back up
kubectl scale deployment signal-loader -n monitoring-app --replicas=1
```

**Scenario 3: Kubernetes cluster issues**

```bash
# 1. Check node status
kubectl get nodes

# 2. Check system pods
kubectl get pods -n kube-system

# 3. Restart Docker Desktop Kubernetes cluster (last resort)
# Settings → Kubernetes → Reset Kubernetes Cluster
```

---

## Quick Reference: Critical Commands

### Most Common Deployment Workflow

```bash
# Backend (loader service)
\
mvn clean package -Dmaven.test.skip=true && \
cp target/loader-0.0.1-SNAPSHOT.jar target/signal-loader-0.0.1-SNAPSHOT.jar && \
docker build -t signal-loader:latest . && \
kubectl rollout restart deployment/signal-loader -n monitoring-app && \
kubectl rollout status deployment/signal-loader -n monitoring-app --timeout=120s

# Frontend (with versioned tagging)
cd /Volumes/Files/Projects/newLoader/frontend && \
TIMESTAMP=$(date +%Y%m%d%H%M%S) && \
npm run build && \
docker build -t loader-frontend:1.2.0-${TIMESTAMP} . && \
sed -i '' "s|image: loader-frontend:.*|image: loader-frontend:1.2.0-${TIMESTAMP}|" k8s_manifist/frontend-deployment.yaml && \
kubectl apply -f k8s_manifist/frontend-deployment.yaml && \
kubectl rollout status deployment/loader-frontend -n monitoring-app --timeout=120s
```

### Most Common Debugging Commands

```bash
# Check all pods
kubectl get pods -n monitoring-app -o wide

# Monitor errors across all services
kubectl logs -n monitoring-app -l app=signal-loader --all-containers=true -f | grep -iE "error|warning|fail|exception"

# Check specific pod logs
kubectl logs <pod-name> -n monitoring-app --tail=200 | grep -i error

# Exec into pod for debugging
kubectl exec -it <pod-name> -n monitoring-app -- /bin/sh

# Port forward for local testing
kubectl port-forward -n monitoring-app deployment/signal-loader 8080:8080
```

### Most Common Rollback Commands

```bash
# Rollback deployment
kubectl rollout undo deployment/signal-loader -n monitoring-app

# Check rollout history
kubectl rollout history deployment/signal-loader -n monitoring-app

# Rollback to specific revision
kubectl rollout undo deployment/signal-loader -n monitoring-app --to-revision=2
```


---

## CRITICAL: Docker Image Tag Mismatch Issue

**SYMPTOM**: Code changes deployed but not reflected in running pods. HTTP errors persist despite fixes.

**ROOT CAUSE**: Kubernetes deployment YAML specifies `image: signal-loader:0.0.1-SNAPSHOT` but Docker build creates `signal-loader:latest`. Kubernetes pulls the OLD image even after `kubectl rollout restart`.

**PERMANENT FIX**:

**Option 1: Always use `:latest` in deployment YAML (RECOMMENDED for local development)**

```bash
# Check current image in deployment
kubectl get deployment signal-loader -n monitoring-app -o jsonpath='{.spec.template.spec.containers[0].image}'

# If it shows signal-loader:0.0.1-SNAPSHOT, update it:
kubectl set image deployment/signal-loader signal-loader=signal-loader:latest -n monitoring-app

# Verify change persists
kubectl get deployment signal-loader -n monitoring-app -o yaml | grep "image:"
```

**Option 2: Build Docker image with matching tag**

```bash
# Build with the tag specified in deployment YAML
docker build -t signal-loader:0.0.1-SNAPSHOT .

# Then restart deployment
kubectl rollout restart deployment/signal-loader -n monitoring-app
```

**DEPLOYMENT CHECKLIST** (ALWAYS follow these steps):

```bash
# 1. Build Maven JAR
mvn clean package -Dmaven.test.skip=true

# 2. Copy JAR to expected location
cp target/loader-0.0.1-SNAPSHOT.jar target/signal-loader-0.0.1-SNAPSHOT.jar

# 3. Check deployment's expected image tag
EXPECTED_TAG=$(kubectl get deployment signal-loader -n monitoring-app -o jsonpath='{.spec.template.spec.containers[0].image}')
echo "Deployment expects: $EXPECTED_TAG"

# 4. Build Docker image with MATCHING tag
if [[ "$EXPECTED_TAG" == *":latest"* ]]; then
  docker build -t signal-loader:latest .
else
  docker build -t signal-loader:0.0.1-SNAPSHOT .
fi

# 5. Force image update (kubectl rollout restart is NOT enough!)
kubectl set image deployment/signal-loader signal-loader=$EXPECTED_TAG -n monitoring-app

# 6. Wait for rollout
kubectl rollout status deployment/signal-loader -n monitoring-app --timeout=120s

# 7. Verify new pod is using correct image
kubectl get pods -n monitoring-app -l app=signal-loader -o jsonpath='{.items[0].spec.containers[0].image}'
```

**AUTOMATED DEPLOYMENT SCRIPT** (add to `/Volumes/Files/Projects/newLoader/scripts/deploy-loader.sh`):

```bash
#!/bin/bash
set -euo pipefail

cd /Volumes/Files/Projects/newLoader/services/loader

echo "=== Building Maven JAR ==="
mvn clean package -Dmaven.test.skip=true
cp target/loader-0.0.1-SNAPSHOT.jar target/signal-loader-0.0.1-SNAPSHOT.jar

echo "=== Checking deployment image tag ==="
EXPECTED_TAG=$(kubectl get deployment signal-loader -n monitoring-app -o jsonpath='{.spec.template.spec.containers[0].image}')
echo "Deployment expects: $EXPECTED_TAG"

echo "=== Building Docker image ==="
docker build --no-cache -t $EXPECTED_TAG .

echo "=== Updating deployment ==="
kubectl set image deployment/signal-loader signal-loader=$EXPECTED_TAG -n monitoring-app

echo "=== Waiting for rollout ==="
kubectl rollout status deployment/signal-loader -n monitoring-app --timeout=120s

echo "=== Verifying deployment ==="
ACTUAL_IMAGE=$(kubectl get pods -n monitoring-app -l app=signal-loader -o jsonpath='{.items[0].spec.containers[0].image}')
echo "Pod is using: $ACTUAL_IMAGE"

if [[ "$ACTUAL_IMAGE" == "$EXPECTED_TAG" ]]; then
  echo "✅ SUCCESS: Image tags match!"
else
  echo "❌ ERROR: Image tag mismatch!"
  exit 1
fi
```

**WHY `kubectl rollout restart` IS NOT ENOUGH**:

- `kubectl rollout restart` only recreates pods with the SAME image tag
- If deployment says `image: signal-loader:0.0.1-SNAPSHOT`, it will ALWAYS pull that tag
- Even if you build `signal-loader:latest`, Kubernetes won't use it
- You MUST use `kubectl set image` to update the tag in the deployment spec

**ALWAYS CHECK AFTER DEPLOYMENT**:

```bash
# What deployment expects
kubectl get deployment signal-loader -n monitoring-app -o jsonpath='{.spec.template.spec.containers[0].image}'

# What pod is actually running
kubectl get pods -n monitoring-app -l app=signal-loader -o jsonpath='{.items[0].spec.containers[0].image}'

# What Docker images you have locally
docker images signal-loader

# These THREE must match!
```

