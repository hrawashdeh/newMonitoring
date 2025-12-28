# Enterprise-Grade Deployment Guide

## Overview

This project uses **centralized installer scripts** that guarantee **zero cache issues** and **reliable deployments** for all services.

## Problem Solved

**Before**: Docker cache issues caused deployments to use old code even after rebuilding
- Using cached Docker layers resulted in stale containers
- Kubernetes couldn't detect new versions
- Users had to hard-refresh browsers (unacceptable for enterprise)

**After**: Enterprise-grade deployment with timestamped versions
- Every deployment creates a unique version (e.g., `1.1.0-1766772210`)
- `--no-cache --pull` ensures fresh builds every time
- Kubernetes automatically detects and deploys new versions
- Zero manual intervention required

## Centralized Installer Scripts

### 1. Infrastructure Installer

**`./infra_installer.sh`** - Install all infrastructure components

Installs:
- Sealed Secrets Controller
- PostgreSQL (with memory optimizations: 512Mi request / 1Gi limit)
- MySQL
- Redis
- Prometheus + Grafana + AlertManager
- Storage class

```bash
./infra_installer.sh
```

**What It Does**:
- Creates `monitoring-infra` namespace
- Deploys all infrastructure services
- Runs credential smoke tests
- Verifies all connections

**When to Use**:
- First-time setup
- After infrastructure changes
- After tearing down cluster

---

### 2. Application Installer

**`./app_installer.sh`** - Install all backend services

Installs (in dependency order):
1. **etl-initializer** - Database schema initialization (Flyway)
2. **auth-service** - Authentication service (JWT + BCrypt)
3. **gateway-service** - Spring Cloud Gateway (routing, rate limiting)
4. **data-generator** - Test data generation utility
5. **signal-loader** - Main ETL loader service

```bash
./app_installer.sh
```

**Enterprise Features**:
- Builds with unique timestamp versions: `1.0.0-1766772210`
- Uses `--no-cache --pull` for guaranteed fresh builds
- Comprehensive health monitoring
- Actuator endpoint verification
- Log scanning for errors
- Pod health tracking

**What It Does**:
- Creates `monitoring-app` namespace
- Loads sealed secrets
- Maven builds all services
- Docker builds with enterprise versioning
- Deploys to Kubernetes
- Verifies health of each service
- Loads ETL configuration YAML
- Loads auth users YAML
- Loads messages dictionary YAML

**When to Use**:
- After infrastructure setup
- After code changes to any backend service
- After configuration changes

---

### 3. Frontend Installer

**`./Frontend_installer.sh`** - Install React frontend

Installs:
- **loader-frontend** - React 18 + TypeScript + Vite

```bash
./Frontend_installer.sh
```

**Enterprise Features**:
- Builds with unique timestamp versions: `1.1.0-1766772210`
- Uses `--no-cache --pull` for guaranteed fresh builds
- Vite production build optimizations
- Nginx serving with proper cache headers
- Health monitoring

**What It Does**:
- Installs npm dependencies
- Runs Vite production build
- Docker builds with enterprise versioning
- Deploys to Kubernetes
- Verifies deployment health
- Tests HTTP endpoints

**When to Use**:
- After frontend code changes
- After React component updates
- After styling changes

---

## How Enterprise Versioning Works

### Timestamp-Based Versions

Each build generates a **unique timestamp-based version**:

```bash
VERSION="1.1.0-$(date +%s)"
# Example: 1.1.0-1766772210
```

This guarantees:
- **Uniqueness**: No two builds have the same version
- **Traceability**: Version = Unix timestamp = exact build time
- **Detection**: Kubernetes sees different image tag = triggers rollout
- **Audit Trail**: Can track exact deployment time

### Cache Busting

Every build uses `--no-cache --pull`:

```bash
docker build --no-cache --pull \
  -t service-name:${VERSION} \
  -t service-name:latest \
  .
```

This ensures:
- **No Docker cache**: Fresh build from source every time
- **Latest base images**: `--pull` fetches newest base image layers
- **Dual tagging**: Version tag for deployment + latest for convenience

### Automatic Detection

Kubernetes `imagePullPolicy: Always` ensures:
- Always check for new image versions
- Even with `:latest` tag, pulls if digest changed
- No stale containers served

---

## Full Deployment Sequence

### Initial Setup (Fresh Cluster)

```bash
# 1. Install infrastructure
./infra_installer.sh

# Wait for infrastructure to stabilize (about 5 minutes)

# 2. Install backend services
./app_installer.sh

# Wait for services to stabilize (about 2 minutes)

# 3. Install frontend
./Frontend_installer.sh
```

**Total Time**: ~10-15 minutes

### After Code Changes

**Frontend changes only**:
```bash
./Frontend_installer.sh
```

**Backend service changes**:
```bash
./app_installer.sh
```

**Infrastructure changes**:
```bash
./infra_installer.sh
```

---

## Deployment Verification

Each installer includes comprehensive verification:

### Health Checks
- Pod readiness monitoring
- Deployment rollout status
- Resource allocation verification

### Log Scanning
- Automatic error detection
- Warning identification
- Recent log review

### Endpoint Testing
- Actuator health probes
- HTTP endpoint verification
- Redis connectivity tests
- Database connection tests

### Smoke Tests
- Credential validation
- Service connectivity
- Cross-service communication

---

## Common Scenarios

### Scenario 1: Update LoadersOverviewPage Component

```bash
# Make your changes to frontend/src/pages/LoadersOverviewPage.tsx
vim frontend/src/pages/LoadersOverviewPage.tsx

# Deploy
./Frontend_installer.sh
```

**Result**: New code deployed, users see changes immediately (no hard refresh needed)

### Scenario 2: Update Auth Service Logic

```bash
# Make your changes to services/auth-service/src/...
vim services/auth-service/src/main/java/com/tiqmo/monitoring/auth/service/AuthService.java

# Deploy
./app_installer.sh
```

**Result**: All services rebuilt with fresh versions, rolled out with zero downtime

### Scenario 3: Change PostgreSQL Memory

```bash
# Edit infra/postgress/values-postgresql.yaml
vim infra/postgress/values-postgresql.yaml

# Deploy
./infra_installer.sh
```

**Result**: PostgreSQL redeployed with new configuration

---

## Access Points

After successful deployment:

| Service | URL | Purpose |
|---------|-----|---------|
| **Frontend** | http://localhost:30080 | Main UI |
| **Gateway** | http://localhost:30088 | API Gateway |
| **Grafana** | http://localhost:30300 | Metrics Dashboard |
| **Prometheus** | http://localhost:30900 | Metrics Collection |
| **AlertManager** | http://localhost:31000 | Alert Management |

### Test Endpoints

```bash
# Frontend health
curl http://localhost:30080/health

# Gateway health
curl http://localhost:30088/actuator/health

# Test login
curl -X POST http://localhost:30080/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"admin123"}'

# Get loaders stats
curl http://localhost:30080/api/v1/res/loaders/stats

# Access new Loaders Overview Page
open http://localhost:30080/loaders
```

---

## Troubleshooting

### Build Fails

**Issue**: Maven or npm build fails

**Solution**:
1. Check Java version: `java -version` (should be 17+)
2. Check Node version: `node -v` (should be 20+)
3. Review build logs for specific error
4. Clean build: `mvn clean` or `rm -rf node_modules`

### Deployment Timeout

**Issue**: Pods don't become ready

**Solution**:
1. Check pod status: `kubectl get pods -n monitoring-app`
2. View pod logs: `kubectl logs -n monitoring-app <pod-name>`
3. Check resources: `kubectl describe pod -n monitoring-app <pod-name>`
4. Verify secrets exist: `kubectl get secrets -n monitoring-app`

### PostgreSQL OOMKilled

**Issue**: PostgreSQL keeps restarting

**Solution**:
- Already fixed! `infra_installer.sh` now sets memory to 512Mi request / 1Gi limit
- If still failing, increase in `infra/postgress/values-postgresql.yaml`

### Login Fails

**Issue**: Login returns 401 or 403

**Solution**:
1. Check auth-service logs: `kubectl logs -n monitoring-app -l app=auth-service`
2. Verify users exist: See database verification commands below
3. Check auth schema initialized: `app_installer.sh` loads `auth-data-v1.yaml`

---

## Database Verification

### Check Loaders

```bash
kubectl exec -n monitoring-infra postgres-postgresql-0 -- \
  env PGPASSWORD=HaAirK101348App psql -U alerts_user -d alerts_db -c \
  "SELECT loader_code, enabled FROM loader.loader;"
```

### Check Auth Users

```bash
kubectl exec -n monitoring-infra postgres-postgresql-0 -- \
  env PGPASSWORD=HaAirK101348App psql -U alerts_user -d alerts_db -c \
  "SELECT username, email, enabled FROM auth.users;"
```

### Check Messages Dictionary

```bash
kubectl exec -n monitoring-infra postgres-postgresql-0 -- \
  env PGPASSWORD=HaAirK101348App psql -U alerts_user -d alerts_db -c \
  "SELECT message_code, message_category FROM general.message_dictionary LIMIT 10;"
```

---

## Architecture

### Namespaces

- **sealed-secrets**: Sealed Secrets Controller
- **monitoring-infra**: Infrastructure (PostgreSQL, MySQL, Redis, Prometheus)
- **monitoring-app**: Application services (auth, gateway, loader, frontend)

### Service Dependencies

```
Infrastructure (monitoring-infra)
  ├─ PostgreSQL (main database)
  ├─ MySQL (source database for ETL)
  └─ Redis (rate limiting, caching)

Application (monitoring-app)
  ├─ etl-initializer (schema setup, YAML loading)
  ├─ auth-service (JWT authentication)
  ├─ gateway-service (API routing, uses Redis)
  ├─ signal-loader (ETL processing)
  ├─ data-generator (test data)
  └─ loader-frontend (React UI)
```

### Deployment Order

1. **Infrastructure First**: Must be stable before apps
2. **etl-initializer**: Creates schemas, loads YAML data
3. **auth-service**: Authentication layer
4. **gateway-service**: API gateway (depends on auth, Redis)
5. **Backend Services**: signal-loader, data-generator
6. **Frontend Last**: Depends on gateway

---

## Best Practices

### Development Workflow

1. **Make code changes** in your IDE
2. **Run appropriate installer**: Frontend, app, or infra
3. **Installer handles everything**:
   - Maven/npm build
   - Docker build with unique version
   - Kubernetes deployment
   - Health verification
4. **Access application**: No cache clearing needed!

### CI/CD Integration

```yaml
# Example: GitHub Actions
deploy-frontend:
  runs-on: ubuntu-latest
  steps:
    - uses: actions/checkout@v3
    - name: Deploy Frontend
      run: ./Frontend_installer.sh
```

### Production Checklist

Before production deployment:

- [ ] Use Docker registry (not local images)
- [ ] Configure image pull secrets
- [ ] Update resource requests/limits
- [ ] Enable TLS/HTTPS
- [ ] Configure ingress controller
- [ ] Set up monitoring alerts
- [ ] Configure backup strategy
- [ ] Document rollback procedure

---

## Version History

| Date | Version | Changes |
|------|---------|---------|
| 2025-12-26 | 1.0.0 | Enterprise-grade deployment system implemented |
| | | - Timestamp-based versioning |
| | | - --no-cache --pull for all builds |
| | | - imagePullPolicy: Always |
| | | - Comprehensive health monitoring |
| | | - Centralized installer scripts |

---

**Last Updated**: December 26, 2025
**Maintained By**: DevOps Team
**Support**: Check pod logs, deployment events, or service status
