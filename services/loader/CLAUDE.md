# CLAUDE.md

Project guidance for Claude Code when working with this repository.

## Quick Commands

```bash
# Build & Run
mvn clean package -DskipTests                # Build without tests
mvn spring-boot:run -Dspring-boot.run.arguments="--spring.profiles.active=dev"
java -jar target/loader-0.0.1-SNAPSHOT.jar --spring.profiles.active=dev

# Testing
mvn test                                     # All tests
mvn test -Dtest=LoaderApplicationTests       # Specific test
mvn test -Dtest=*Service                     # Pattern matching

# Development
mvn compile                                  # Compile only
mvn dependency:tree                          # Check dependencies
```

## Architecture

**Spring Boot 3.5.6 ETL Monitoring Microservice**

### Project Structure
```
api/              → REST controllers
domain/           → JPA entities & repositories
service/          → Business logic
infra/            → Infrastructure (connection pooling, encryption)
  └─ security/    → Encryption & JWT authentication
probe/            → Database security auditing
runner/           → Startup initialization
events/           → Application events
dto/              → Data transfer objects
```

### Key Components

| Component | Purpose |
|-----------|---------|
| `SourceRegistry` | HikariCP connection pool management (thread-safe, event-driven) |
| `EncryptionService` | AES-256-GCM encryption (passwords, SQL queries) |
| `DbPermissionInspector` | Read-only compliance auditing (PostgreSQL/MySQL) |
| `JwtTokenProvider` | JWT authentication (HMAC-SHA256, 24hr expiration) |
| `LoaderService` | CRUD for ETL loaders |
| `SignalsIngestService` | Append-only signal history |

### Database Schema

**loader** schema: `loader`, `source_databases`, `segments_dictionary`
**signals** schema: `signals_history`, `segment_combination`

**Encrypted fields**: `SourceDatabase.passWord`, `Loader.loaderSql`

### Configuration (application-dev.yaml)

```yaml
spring.datasource.url: jdbc:postgresql://localhost:5433/monitoringdb
spring.jpa.hibernate.ddl-auto: create-drop    # Dev only
encryption.key: ${ENCRYPTION_KEY}             # AES-256 key (32 bytes Base64)
jwt.secret: ${JWT_SECRET}                     # JWT signing key (min 256 bits)
jwt.expiration-ms: 86400000                   # 24 hours
```

## API Endpoints

### Core Endpoints
| Method | Path | Purpose | Auth |
|--------|------|---------|------|
| POST | `/api/v1/auth/login` | Get JWT token | Public |
| GET | `/actuator/health` | Health check | Public |
| GET | `/api/v1/res/loaders/loaders` | List all loaders | Any user |
| GET | `/api/v1/res/signals/signal/{loaderCode}` | Query signals (fromEpoch, toEpoch) | Any user |
| GET | `/ops/v1/admin/res/db-sources` | List sources (redacted) | Any user |
| POST | `/ops/v1/admin/security/reload` | Reload connections | ADMIN/OPERATOR |
| GET | `/api/v1/admin/security/read-only-check` | Audit read-only compliance | Any user |

### Authentication

**Default Users** (dev only):
- **admin** / admin123 → ROLE_ADMIN (full access)
- **operator** / operator123 → ROLE_OPERATOR (read + ops)
- **viewer** / viewer123 → ROLE_VIEWER (read-only)

**Get JWT Token:**
```bash
curl -X POST http://localhost:8080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'
# Returns: {"token": "eyJ...", "type": "Bearer", "roles": ["ROLE_ADMIN"]}
```

**Use Token:**
```bash
curl -H "Authorization: Bearer <token>" http://localhost:8080/api/v1/res/loaders/loaders
```

### Authorization Matrix

| Endpoint | Method | Required Role |
|----------|--------|---------------|
| `/api/v1/res/loaders` | POST/PUT/DELETE | ADMIN |
| `/api/v1/res/signals/**` | POST | ADMIN |
| `/ops/v1/admin/**` | POST | ADMIN or OPERATOR |
| `/ops/v1/admin/**` | PUT/DELETE | ADMIN |
| All other endpoints | GET | Any authenticated user |

## Working with Code

### Startup Flow
1. Load `application.yaml` + profile config
2. Initialize `EncryptionService` (fails fast if `ENCRYPTION_KEY` missing)
3. Create schemas via `schema-dev.sql` (DDL: create-drop in dev)
4. Seed data via `DevDataLoader` (JPA with auto-encryption)
5. `SourceRegistry` loads connection pools on `ApplicationReadyEvent`
6. Publish `SourcesLoadedEvent`

### Adding New API Endpoint
1. Create controller method in `api/` package with `@GetMapping`/`@PostMapping`
2. Inject service via constructor
3. Use DTOs (never expose entities directly)
4. **CRITICAL**: Order endpoints specific → catch-all (avoid `/{id}` matching `/approved`)

### Adding Encrypted Field
```java
@Convert(converter = EncryptedStringConverter.class)
@Column(name = "api_key", length = 512)
private String apiKey;
```
**Note**: Use JPA for inserts (SQL INSERT bypasses encryption)

### Encryption Setup

**Dev Environment:**
```bash
export ENCRYPTION_KEY="e2mWpFHBa3+3DRfZtaNCADsy3E7FArTZ2RFGCRsxod4="
mvn spring-boot:run
```

**Production (Kubernetes):**
```bash
# Generate key (ONE TIME)
openssl rand -base64 32

# Create sealed secret
cd deployment/scripts && ./create-sealed-secret.sh

# Deploy
kubectl apply -f deployment/k8s/sealed-secret.yaml
kubectl apply -f deployment/k8s/deployment.yaml
```

## Deployment

### Backend (Loader Service)

```bash
cd /Volumes/Files/Projects/newLoader/services/loader

# Build, Docker, Deploy
mvn clean package -Dmaven.test.skip=true && \
cp target/loader-0.0.1-SNAPSHOT.jar target/signal-loader-0.0.1-SNAPSHOT.jar && \
docker build -t signal-loader:latest . && \
kubectl rollout restart deployment/signal-loader -n monitoring-app && \
kubectl rollout status deployment/signal-loader -n monitoring-app --timeout=120s
```

### Frontend

```bash
cd /Volumes/Files/Projects/newLoader/frontend

# Build with timestamped tag (NEVER use :latest!)
TIMESTAMP=$(date +%Y%m%d%H%M%S)
npm run build && \
docker build -t loader-frontend:1.2.0-${TIMESTAMP} . && \
sed -i '' "s|image: loader-frontend:.*|image: loader-frontend:1.2.0-${TIMESTAMP}|" k8s_manifist/frontend-deployment.yaml && \
kubectl apply -f k8s_manifist/frontend-deployment.yaml && \
kubectl rollout status deployment/loader-frontend -n monitoring-app --timeout=120s
```

**Why timestamped tags?** Kubernetes `imagePullPolicy: IfNotPresent` won't detect changes with `:latest` tag.

### CRITICAL: Image Tag Mismatch

**Problem**: Code changes not reflected despite deployment.

**Cause**: Deployment YAML expects `signal-loader:0.0.1-SNAPSHOT` but you built `signal-loader:latest`.

**Fix**: Always match tags:
```bash
# Check what deployment expects
EXPECTED_TAG=$(kubectl get deployment signal-loader -n monitoring-app -o jsonpath='{.spec.template.spec.containers[0].image}')

# Build with matching tag
docker build -t $EXPECTED_TAG .

# Force update (rollout restart is NOT enough!)
kubectl set image deployment/signal-loader signal-loader=$EXPECTED_TAG -n monitoring-app
```

## Troubleshooting

### Common Issues

**Issue 1: HTTP 400 on New Endpoint**
- **Symptom**: `Type mismatch | parameter=XXX | expectedType=Long`
- **Cause**: Catch-all `/{id}` path declared before specific paths like `/approved`
- **Fix**: Move specific paths BEFORE catch-all in controller

**Issue 2: Frontend Changes Not Visible**
- **Cause**: Docker image caching (`:latest` tag with `imagePullPolicy: IfNotPresent`)
- **Fix**: Use timestamped tags (see Frontend deployment above)

**Issue 3: JAR Not Found**
- **Cause**: Maven build failed silently
- **Fix**: Check for `BUILD SUCCESS` in Maven output; verify JAR exists before Docker build

**Issue 4: CrashLoopBackOff**
- **Cause**: Missing env vars (`ENCRYPTION_KEY`, `JWT_SECRET`), DB connection failure
- **Fix**: Check logs: `kubectl logs -n monitoring-app -l app=signal-loader --tail=200 | grep -i error`

### Debugging Commands

```bash
# Check all pods
kubectl get pods -n monitoring-app -o wide

# Monitor errors
kubectl logs -n monitoring-app -l app=signal-loader -f | grep -iE "error|warning|fail"

# Check Spring Boot startup
kubectl logs -n monitoring-app -l app=signal-loader --tail=200 | grep "Started LoaderApplication"

# Test DB connectivity
kubectl exec -n monitoring-app deployment/signal-loader -- nc -zv postgres-monitoring-service.monitoring-infra.svc.cluster.local 5432

# Port forward for local testing
kubectl port-forward -n monitoring-app deployment/signal-loader 8080:8080
```

### Rollback

```bash
# Quick rollback
kubectl rollout undo deployment/signal-loader -n monitoring-app

# Rollback to specific revision
kubectl rollout history deployment/signal-loader -n monitoring-app
kubectl rollout undo deployment/signal-loader -n monitoring-app --to-revision=2

# Database rollback
kubectl scale deployment signal-loader -n monitoring-app --replicas=0
kubectl exec -n monitoring-infra postgres-monitoring-0 -- psql -U monitoring monitoringdb < backup-YYYYMMDD.sql
kubectl scale deployment signal-loader -n monitoring-app --replicas=1
```

## Spring Boot Path Mapping

**Problem**: `/{id}` catches ALL paths including `/approved`, `/pending`, etc.

**Solution**: Order endpoints correctly:

```java
@RestController
@RequestMapping("/api/approvals")
public class ApprovalController {
    // 1. Specific paths (no variables)
    @GetMapping("/approved")
    @GetMapping("/pending")

    // 2. Multi-segment paths
    @GetMapping("/pending/count")
    @GetMapping("/pending/{entityType}")

    // 3. Catch-all LAST
    @GetMapping("/{requestId}")  // MUST BE LAST!
}
```

## Dependencies

- Spring Boot 3.5.6
- Resilience4j 2.3.0 (circuit breaker, retry, rate limiting)
- Lombok 1.18.32
- PostgreSQL & MySQL drivers
- HikariCP (connection pooling)

---

**For detailed migration guides, see**: `docs/DATABASE_MIGRATION_WITH_ENCRYPTION.md`
