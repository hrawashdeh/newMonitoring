# Deployment Architecture

**Version**: 1.0
**Date**: 2025-12-31
**Project**: Monitoring Application Platform

---

## Overview

This document describes the systematic deployment architecture for the monitoring application platform, consisting of three main installer scripts that deploy services across two Kubernetes namespaces with centralized secret management.

---

## Deployment Structure

### Three-Tier Installer Architecture

```
┌─────────────────────────────────────────────────────────────────────┐
│ 1. infra_installer.sh                                               │
│    Namespace: monitoring-infra                                      │
│    Secret: infra-secrets (sealed)                                   │
│                                                                      │
│    ┌──────────────────────────────────────────────────────────────┐│
│    │ • Sealed Secrets Controller                                  ││
│    │ • PostgreSQL (Bitnami Helm Chart)                            ││
│    │ • MySQL (StatefulSet)                                        ││
│    │ • Redis (Bitnami Helm Chart)                                 ││
│    │ • Prometheus + Grafana + Alertmanager (kube-prometheus-stack)││
│    │ • Storage Class (local-path-provisioner)                     ││
│    └──────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ 2. app_installer.sh                                                 │
│    Namespace: monitoring-app                                        │
│    Secret: app-secrets (sealed)                                     │
│                                                                      │
│    ┌──────────────────────────────────────────────────────────────┐│
│    │ • ETL Initializer (Flyway DB migrations)                     ││
│    │ • Auth Service (JWT authentication)                          ││
│    │ • Gateway Service (Spring Cloud Gateway)                     ││
│    │ • Signal Loader (Core ETL service)                           ││
│    │ • Import-Export Service (Excel import/export)                ││
│    │ • Data Generator (Test data generation)                      ││
│    └──────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘

┌─────────────────────────────────────────────────────────────────────┐
│ 3. Frontend_installer.sh                                            │
│    Namespace: monitoring-app                                        │
│    Secret: Uses app-secrets                                         │
│                                                                      │
│    ┌──────────────────────────────────────────────────────────────┐│
│    │ • React Frontend (Vite build)                                ││
│    │ • Nginx Static Server                                        ││
│    │ • Build Info Generation                                      ││
│    └──────────────────────────────────────────────────────────────┘│
└─────────────────────────────────────────────────────────────────────┘
```

---

## Namespace Organization

### monitoring-infra (Infrastructure Namespace)

**Purpose**: Shared infrastructure services consumed by all applications

**Central Secret**: `infra-secrets` (sealed)

**Services**:
- **sealed-secrets**: Sealed Secrets Controller (namespace: sealed-secrets)
- **postgres-postgresql**: PostgreSQL database (Bitnami chart)
- **mysql**: MySQL database (StatefulSet)
- **redis-master**: Redis cache (Bitnami chart)
- **prometheus**: Metrics collection (kube-prometheus-stack)
- **grafana**: Metrics visualization
- **alertmanager**: Alert routing

**Secret Keys** (infra-secrets):
```yaml
# PostgreSQL
postgres-admin-password
postgres-app-password

# MySQL
mysql-root-password
mysql-password
MYSQL_APP_USER
MYSQL_APP_DB
MYSQL_GENERATOR_USER
MYSQL_GENERATOR_PASSWORD

# Redis
redis-password
REDIS_PORT

# Grafana
GRAFANA_ADMIN_USER
GRAFANA_ADMIN_PASSWORD

# Alertmanager
ALERTMANAGER_BASIC_AUTH_USER
ALERTMANAGER_BASIC_AUTH_PASSWORD
```

---

### monitoring-app (Application Namespace)

**Purpose**: Business application services

**Central Secret**: `app-secrets` (sealed)

**Services**:
- **etl-initializer**: Database schema initialization (Flyway)
- **auth-service**: JWT authentication service
- **gateway-service**: API gateway (Spring Cloud Gateway)
- **signal-loader**: Core ETL loader service
- **import-export-service**: Excel import/export service
- **data-generator**: Test data generation service
- **loader-frontend**: React frontend with Nginx

**Secret Keys** (app-secrets):
```yaml
# Environment
ENV_PROFILE

# MySQL (Read-Only for Loaders)
MYSQL_APP_USER
MYSQL_APP_DB
MYSQL_HOST
MYSQL_PORT
MYSQL_APP_PASSWORD

# MySQL (Data Generator - Read/Write)
MYSQL_GENERATOR_USER
MYSQL_GENERATOR_PASSWORD

# Redis
REDIS_PASSWORD

# PostgreSQL
SPRING_DATASOURCE_URL
SPRING_DATASOURCE_USERNAME
SPRING_DATASOURCE_PASSWORD

# Encryption
ENCRYPTION_KEY  # AES-256-GCM for database column encryption

# JWT
JWT_SECRET
JWT_EXPIRATION_MS

# Spring Boot
SPRING_PROFILES_ACTIVE
SOURCES_MYSQL_POOL_MINIMUM_IDLE
SOURCES_MYSQL_POOL_MAXIMUM_POOL_SIZE
SOURCES_MYSQL_POOL_IDLE_TIMEOUT
```

---

## Secret Management Pattern

### Sealed Secrets Flow

```
1. Developer creates plain YAML
   └── infra/secrets/monitoring-secrets-plain.yaml
   └── services/secrets/app-secrets-plain.yaml

2. kubeseal encrypts the secret
   └── kubeseal --controller-name=sealed-secrets \
              --controller-namespace=sealed-secrets \
              --namespace <target-namespace> \
              --format yaml \
              < plain.yaml \
              > sealed.yaml

3. Sealed secret applied to cluster
   └── kubectl apply -f sealed.yaml

4. Sealed Secrets Controller decrypts
   └── Creates native Kubernetes Secret in target namespace

5. Pods consume the secret
   └── envFrom:
       - secretRef:
           name: app-secrets
```

### Source of Truth Principle

Each namespace has **ONE central secret** that serves as the **single source of truth** for all services in that namespace.

**Benefits**:
- Single point of configuration
- Consistent credential management
- Easy rotation (update one secret)
- Clear security boundary

**DO**:
- ✅ Add new keys to existing central secret
- ✅ Reference secret via `envFrom.secretRef`
- ✅ Keep plain YAML in source control (for regeneration)
- ✅ Keep sealed YAML in source control (for deployment)

**DON'T**:
- ❌ Create multiple secrets per namespace
- ❌ Store secrets in deployment YAMLs
- ❌ Commit decrypted secrets to git (only plain templates)

---

## Installer Script Structure

All three installer scripts follow the **same systematic pattern**:

### Standard Sections (Order Matters)

```bash
1. Colors and Log Helpers
   └── BLUE, YELLOW, GREEN, RED, NC
   └── log_info(), log_debug(), log_error(), log_section(), exit_error()

2. Prompt Helper
   └── prompt_choice() - Interactive Y/n confirmation

3. Configuration
   └── PROJECT_ROOT, NAMESPACE, k_context
   └── Verification prompt

4. kubectl Context
   └── Switch context
   └── Verify current context

5. Namespaces
   └── Create/verify namespaces
   └── kubectl create namespace --dry-run=client

6. Sealed Secrets
   └── kubeseal encryption
   └── kubectl apply sealed secret

7. Service Deployments
   └── Install services in dependency order
   └── Wait for readiness

8. Health Checks
   └── Smoke tests
   └── Credential verification
   └── Endpoint checks

9. Execution Summary
   └── Timing statistics
   └── Success/failure summary
```

### Common Functions

All installers share these helper functions:

**Deployment Validation**:
```bash
validate_deployment()      # Check if deployment exists
monitor_pod_health()       # Wait for pods to be ready
get_pod_status()          # Get deployment health status
```

**Log Analysis**:
```bash
scan_pod_logs()           # Search for errors/warnings in logs
```

**Health Probes**:
```bash
check_health_probe()      # Test actuator endpoints (app_installer)
check_actuator_endpoints() # Test multiple actuator endpoints
check_http_endpoint()     # Test HTTP endpoints (frontend_installer)
```

**Smoke Tests**:
```bash
test_redis()              # Verify Redis authentication
test_pg_admin()           # Verify PostgreSQL admin access
test_pg_app()             # Verify PostgreSQL app user access
test_mysql_root()         # Verify MySQL root access
test_mysql_app()          # Verify MySQL app user access
```

---

## Deployment Order (Critical)

Services **must be deployed in this order** due to dependencies:

### Phase 1: Infrastructure (infra_installer.sh)
```
1. Sealed Secrets Controller
   └── Required by: All subsequent secrets

2. Storage Class
   └── Required by: PVCs for databases

3. PostgreSQL
   └── Required by: Application services (Spring DataSource)

4. MySQL
   └── Required by: Loader service (source data)
   └── Post-install: Create users and grants

5. Redis
   └── Required by: Gateway (rate limiting)

6. Prometheus Stack
   └── Required by: Metrics collection
```

### Phase 2: Applications (app_installer.sh)
```
1. ETL Initializer
   └── Runs Flyway migrations
   └── Creates database schema
   └── Loads initial YAML data

2. Auth Service
   └── Provides JWT authentication
   └── Required by: All services (JWT validation)

3. Gateway Service
   └── API gateway and routing
   └── Required by: Frontend (centralized entry point)

4. Data Generator
   └── Populates MySQL test data
   └── Required by: Loaders (test data source)

5. Signal Loader
   └── Core ETL service
   └── Depends on: PostgreSQL, MySQL, Auth

6. Import-Export Service
   └── Excel import/export
   └── Depends on: PostgreSQL, Loader service
```

### Phase 3: Frontend (Frontend_installer.sh)
```
1. npm install
   └── Install dependencies

2. npm run build
   └── Generate build info
   └── Vite production build

3. docker build
   └── Create container image
   └── Tag with version + latest

4. kubectl apply
   └── Deploy to Kubernetes
   └── Wait for pods ready

5. Health checks
   └── Scan logs
   └── Test HTTP endpoint
```

---

## Adding New Services

When adding a new service, follow this **systematic pattern**:

### 1. Determine Namespace

**Infrastructure service** (shared database, cache, etc.)?
→ Add to `infra_installer.sh` → `monitoring-infra` namespace

**Application service** (business logic)?
→ Add to `app_installer.sh` → `monitoring-app` namespace

### 2. Update Central Secret

**If service needs credentials**:

```bash
# Edit plain secret
vim infra/secrets/monitoring-secrets-plain.yaml
# OR
vim services/secrets/app-secrets-plain.yaml

# Add keys:
stringData:
  NEW_SERVICE_USER: "username"
  NEW_SERVICE_PASSWORD: "password"
  NEW_SERVICE_URL: "http://service:port"
```

### 3. Create Deployment Manifest

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: new-service
  namespace: monitoring-app  # or monitoring-infra
spec:
  template:
    spec:
      containers:
      - name: new-service
        env:
        # Option 1: Import all keys (recommended for central secret)
        - name: NEW_SERVICE_USER
          valueFrom:
            secretKeyRef:
              name: app-secrets
              key: NEW_SERVICE_USER
        # Option 2: envFrom (imports entire secret)
        envFrom:
        - secretRef:
            name: app-secrets
```

### 4. Add to Installer Script

**Find insertion point** (respect dependency order):

```bash
# ===================== New Service =====================
cd "${PROJECT_ROOT}/services/new-service"

log_section "Deploying New Service"

# Build Docker image
VERSION="1.0.0-$(date +%s)"
docker build --no-cache --pull \
  -t new-service:${VERSION} \
  -t new-service:latest .

# Apply Kubernetes manifest
kubectl apply -f k8s_manifist/new-service-deployment.yaml

# Wait for readiness
kubectl rollout status deployment/new-service \
  -n monitoring-app --timeout=300s

# Health check
monitor_pod_health "new-service" "monitoring-app" 180
scan_pod_logs "new-service" "monitoring-app" 100

log_success "New service deployed successfully"
```

### 5. Add Smoke Test (Optional)

```bash
# Add to smoke test section
test_new_service() {
  local ns="$1" secret="$2" rid="$3"
  local pod="new-service-check-$rid"

  local service_url
  service_url="$(kubectl -n "$ns" get secret "$secret" \
    -o jsonpath='{.data.NEW_SERVICE_URL}' | base64 -d)"

  local cmd="curl -f $service_url/health"
  local out
  out="$(run_ephemeral_pod_and_get_logs "$ns" "$pod" \
    "curlimages/curl:latest" "$cmd")" || return 1

  echo "$out" | grep -q 'UP' || {
    echo "[ERROR] New service health check failed" >&2
    return 1
  }
  echo "[OK] New service health check passed"
}

# Call in smoke test section
test_new_service "$NS" "$SECRET_NAME" "$RID"
```

---

## Service Communication Patterns

### Cross-Namespace Service Discovery

Services in **monitoring-app** access infrastructure in **monitoring-infra**:

```yaml
# Full DNS format: service.namespace.svc.cluster.local
SPRING_DATASOURCE_URL: "jdbc:postgresql://postgres-postgresql.monitoring-infra.svc.cluster.local:5432/alerts_db"
MYSQL_HOST: "mysql.monitoring-infra.svc.cluster.local"
REDIS_HOST: "redis-master.monitoring-infra.svc.cluster.local"
```

### Same-Namespace Communication

Services in **monitoring-app** communicate via short DNS:

```yaml
# Just service name
LOADER_SERVICE_HOST: "signal-loader"
AUTH_SERVICE_URL: "http://auth-service:8080"
```

---

## Health Check Patterns

### Deployment Health

```bash
# Wait for pods to be ready
kubectl rollout status deployment/service-name \
  -n namespace --timeout=300s

# Custom health monitoring (shows progress)
monitor_pod_health "service-name" "namespace" 300
```

### Application Health

```bash
# Spring Boot Actuator
check_health_probe "service-name" "namespace" 8080 "/actuator/health"

# Custom HTTP endpoint
check_http_endpoint "service-name" "namespace" 80 "/"
```

### Database Connectivity

```bash
# PostgreSQL
test_pg_admin "$NAMESPACE" "$SECRET_NAME" "$RID"
test_pg_app "$NAMESPACE" "$SECRET_NAME" "$RID"

# MySQL
test_mysql_root "$NAMESPACE" "$SECRET_NAME" "$RID"
test_mysql_app "$NAMESPACE" "$SECRET_NAME" "$RID"

# Redis
test_redis "$NAMESPACE" "$SECRET_NAME" "$RID"
```

---

## Version Management

### Docker Image Tagging Strategy

**Dual tagging** (timestamped + latest):

```bash
VERSION="1.0.0-$(date +%s)"

docker build --no-cache --pull \
  -t service-name:${VERSION} \
  -t service-name:latest .
```

**Benefits**:
- `latest` tag: Easy local development
- Timestamped tag: Audit trail, rollback capability
- `--no-cache --pull`: Ensures fresh build (no cache issues)

### Frontend Build Info

```typescript
// Auto-generated by Frontend_installer.sh
export const BUILD_INFO = {
  buildNumber: '1735653015',
  buildDate: '2025-12-31T12:30:15Z',
  version: '1.0.0',
};
```

---

## Verification Checklist

After running any installer, verify:

### Infrastructure (infra_installer.sh)

- [ ] All pods in `monitoring-infra` are Running
- [ ] PostgreSQL accepts admin and app user connections
- [ ] MySQL accepts root and app user connections
- [ ] Redis PING returns PONG
- [ ] Grafana UI accessible on NodePort 30300
- [ ] Prometheus UI accessible on NodePort 30090
- [ ] All smoke tests pass

```bash
kubectl get pods -n monitoring-infra
kubectl get svc -n monitoring-infra
kubectl get secret -n monitoring-infra
```

### Application (app_installer.sh)

- [ ] All pods in `monitoring-app` are Running
- [ ] Flyway migrations completed (check etl-initializer logs)
- [ ] Auth service returns JWT token on login
- [ ] Gateway service accessible on NodePort 30088
- [ ] Loader service can query MySQL
- [ ] Import-export service can upload Excel files
- [ ] All actuator health endpoints return UP

```bash
kubectl get pods -n monitoring-app
kubectl get svc -n monitoring-app
kubectl logs -n monitoring-app -l app=etl-initializer --tail=50
```

### Frontend (Frontend_installer.sh)

- [ ] Frontend pod is Running
- [ ] Nginx serves static files
- [ ] Build info displays correct version
- [ ] HTTP endpoint returns 200
- [ ] No errors in pod logs
- [ ] Can access UI via port-forward

```bash
kubectl get pods -n monitoring-app -l app=loader-frontend
kubectl port-forward -n monitoring-app svc/loader-frontend 3000:80
# Open http://localhost:3000
```

---

## Troubleshooting Guide

### Common Issues

**Sealed secret not decrypting**:
```bash
# Check controller is running
kubectl get pods -n sealed-secrets

# Check secret exists in target namespace
kubectl get secret <secret-name> -n <namespace>

# Regenerate sealed secret
kubeseal --controller-name=sealed-secrets \
  --controller-namespace=sealed-secrets \
  --namespace <target-namespace> \
  --format yaml \
  < plain.yaml > sealed.yaml
```

**Pod CrashLoopBackOff**:
```bash
# Check logs
kubectl logs <pod-name> -n <namespace>

# Check events
kubectl describe pod <pod-name> -n <namespace>

# Common causes:
# - Missing secret keys
# - Database connection failed
# - Port already in use
# - Image pull failed
```

**Service not accessible**:
```bash
# Check service exists
kubectl get svc <service-name> -n <namespace>

# Check endpoints exist
kubectl get endpoints <service-name> -n <namespace>

# Port forward to test
kubectl port-forward -n <namespace> svc/<service-name> 8080:8080
```

**Database connection failed**:
```bash
# Check secret keys exist
kubectl get secret <secret-name> -n <namespace> \
  -o jsonpath='{.data}' | jq

# Run ephemeral pod to test connection
kubectl run -it --rm debug --image=postgres:16-alpine \
  --restart=Never -- \
  psql -h postgres-postgresql.monitoring-infra.svc.cluster.local \
  -U alerts_user -d alerts_db
```

---

## File Structure Reference

```
/Volumes/Files/Projects/newLoader/
├── infra_installer.sh           # Infrastructure installer
├── app_installer.sh              # Application installer
├── Frontend_installer.sh         # Frontend installer
│
├── infra/                        # Infrastructure configs
│   ├── secrets/
│   │   ├── monitoring-secrets-plain.yaml
│   │   └── monitoring-secrets-sealed.yaml
│   ├── postgress/
│   │   ├── values-postgresql.yaml
│   │   └── postgres-nodeport.yaml
│   ├── mysql/
│   │   ├── mysql-statefulset.yaml
│   │   ├── mysql-nodeport.yaml
│   │   └── mysql-post-install-setup.sh
│   ├── redis/
│   │   ├── values-redis.yaml
│   │   └── redis-nodeport.yaml
│   └── prometheus/
│       ├── values-kps.yaml
│       ├── Prometheus-nodeport.yaml
│       ├── AlertManager-nodeport.yaml
│       └── grafana-nodeport.yaml
│
├── services/                     # Application services
│   ├── secrets/
│   │   ├── app-secrets-plain.yaml
│   │   └── app-secrets-sealed.yaml
│   ├── etl_initializer/
│   ├── auth-service/
│   ├── gateway/
│   ├── loader/
│   ├── import-export-service/
│   └── dataGenerator/
│
└── frontend/                     # Frontend application
    ├── src/
    ├── public/
    ├── Dockerfile
    ├── nginx.conf
    └── k8s_manifist/
        └── frontend-deployment.yaml
```

---

## Best Practices Summary

1. **One Secret Per Namespace**: Central secret as single source of truth
2. **Sealed Secrets**: Never commit plain secrets to git
3. **Dependency Order**: Respect deployment order (infra → app → frontend)
4. **Health Checks**: Always verify pod health and run smoke tests
5. **Image Tagging**: Use dual tags (versioned + latest)
6. **No Cache Builds**: `--no-cache --pull` for reproducible builds
7. **Logging**: Use consistent log helpers for debugging
8. **Interactive Prompts**: Confirm critical operations
9. **Execution Summary**: Always show timing and status
10. **Smoke Tests**: Verify credentials and connectivity before completion

---

**End of Deployment Architecture Document**
