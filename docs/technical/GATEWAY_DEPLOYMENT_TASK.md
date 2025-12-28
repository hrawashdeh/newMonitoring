# Gateway Deployment Task

**Date Created:** 2024-12-24
**Priority:** HIGH - Required for production-grade, bank-compliant architecture
**Estimated Time:** 4-6 hours
**Status:** Ready to start

---

## Context

### Why Gateway is Needed

Current architecture uses NGINX in frontend pod for routing, which has limitations for a **bank-grade, SAMA/PCI-compliant** solution that will scale horizontally and vertically.

**Current State (NGINX-based):**
```
User Browser (localhost:30080)
    ↓
Frontend NGINX (loader-frontend pod)
    ├─→ /api/v1/auth/* → auth-service:8081
    ├─→ /api/* → signal-loader:8080
    └─→ /* → React static files
```

**Issues with Current Approach:**
- ❌ No service-level circuit breaking
- ❌ No distributed rate limiting
- ❌ No intelligent load balancing (when services scale to multiple pods)
- ❌ Limited request/response transformation
- ❌ No distributed tracing integration
- ❌ CORS configuration duplicated across services
- ❌ HTTP status codes not properly managed at API gateway level

**Target State (Spring Cloud Gateway):**
```
User Browser (localhost:30080)
    ↓
Frontend NGINX (serves React static files only)
    ↓
Spring Cloud Gateway (port 8888)
    ├─→ JWT Validation (before routing)
    ├─→ Rate Limiting (Redis-backed, per-user/per-IP)
    ├─→ Circuit Breakers (Resilience4j)
    ├─→ Distributed Tracing (future)
    └─→ Routes:
        ├─→ /api/v1/auth/** → auth-service:8081 (ClusterIP)
        └─→ /api/** → signal-loader:8080 (ClusterIP)
```

### Benefits for Bank-Grade Solution

**1. Security (SAMA/PCI Compliance):**
- Centralized JWT validation (single point of authentication)
- Rate limiting prevents brute-force attacks (e.g., 100 req/min per user)
- Audit logging at gateway level
- Defense in depth (Gateway validates, then service validates again)

**2. Scalability:**
- Service discovery integration (finds healthy pods automatically)
- Load balancing across multiple service replicas
- Circuit breaking (fail fast when service is down)
- Retry logic for transient failures

**3. Operations:**
- Centralized monitoring (all requests flow through Gateway)
- Canary deployments support (route 90% to v1, 10% to v2)
- API versioning support (route /v1/ vs /v2/ to different services)
- Single CORS configuration instead of per-service

---

## Implementation Tasks

### Prerequisites

**Verify existing services:**
```bash
# Check current deployments
kubectl get deployments,svc -n monitoring-app

# Expected:
# - auth-service (8081)
# - signal-loader (8080)
# - loader-frontend (80)
# - etl-initializer
# - data-generator
```

**Verify Gateway code exists:**
```bash
ls -la /Volumes/Files/Projects/newLoader/services/gateway/

# Should see:
# - pom.xml
# - src/main/resources/application.yaml
# - README.md
```

---

### Task 1: Update Gateway Application Configuration

**File:** `/Volumes/Files/Projects/newLoader/services/gateway/src/main/resources/application.yaml`

**Current configuration needs updates for:**

1. **Route to auth-service:**
```yaml
- id: auth-service
  uri: http://auth-service:8081
  predicates:
    - Path=/api/v1/auth/**
  filters:
    - name: RequestRateLimiter
      args:
        redis-rate-limiter.replenishRate: 10
        redis-rate-limiter.burstCapacity: 20
    # No JWT validation for login endpoint
```

2. **Route to signal-loader:**
```yaml
- id: loader-service
  uri: http://signal-loader:8080
  predicates:
    - Path=/api/**
  filters:
    - name: RequestRateLimiter
      args:
        redis-rate-limiter.replenishRate: 100
        redis-rate-limiter.burstCapacity: 200
    - name: CircuitBreaker
      args:
        name: loaderServiceCircuitBreaker
        fallbackUri: forward:/fallback/loader
```

3. **CORS configuration:**
```yaml
spring:
  cloud:
    gateway:
      globalcors:
        corsConfigurations:
          '[/**]':
            allowedOrigins:
              - "http://localhost:30080"
              - "http://localhost:5173"
            allowedMethods:
              - GET
              - POST
              - PUT
              - DELETE
              - OPTIONS
            allowedHeaders: "*"
            allowCredentials: true
            maxAge: 3600
```

4. **Server port:**
```yaml
server:
  port: 8888
```

5. **Redis connection:**
```yaml
spring:
  data:
    redis:
      host: ${REDIS_HOST:redis-master.monitoring-infra.svc.cluster.local}
      port: ${REDIS_PORT:6379}
      password: ${REDIS_PASSWORD}
```

**Action:** Create complete `application.yaml` with all routes and filters.

---

### Task 2: Create JWT Validation Filter (Optional for Phase 1)

**Note:** For initial deployment, Gateway can route without JWT validation. JWT validation remains in each service.

**For future enhancement (Phase 2):**

**File:** `src/main/java/com/tiqmo/monitoring/gateway/filter/JwtAuthenticationFilter.java`

```java
// Pre-filter that validates JWT before routing to services
// Shares JWT secret with auth-service and loader-service
// Returns 401 if token invalid/missing (for protected routes)
```

**Action:** DEFER to Phase 2. Phase 1 Gateway routes without JWT validation.

---

### Task 3: Deploy Redis for Rate Limiting

**Check if Redis already deployed:**
```bash
kubectl get deployments,svc -n monitoring-infra | grep redis
```

**If Redis NOT deployed, deploy it:**

**File:** Create `/Volumes/Files/Projects/newLoader/infra/redis/redis-deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: redis
  namespace: monitoring-infra
spec:
  replicas: 1
  selector:
    matchLabels:
      app: redis
  template:
    metadata:
      labels:
        app: redis
    spec:
      containers:
      - name: redis
        image: redis:7-alpine
        ports:
        - containerPort: 6379
        env:
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: redis-secret
              key: password
        command:
          - redis-server
          - --requirepass
          - $(REDIS_PASSWORD)
        resources:
          requests:
            memory: "256Mi"
            cpu: "100m"
          limits:
            memory: "512Mi"
            cpu: "200m"
---
apiVersion: v1
kind: Service
metadata:
  name: redis-master
  namespace: monitoring-infra
spec:
  selector:
    app: redis
  ports:
  - port: 6379
    targetPort: 6379
  type: ClusterIP
```

**Create Redis secret:**
```bash
# Generate Redis password
REDIS_PASSWORD=$(openssl rand -base64 32)

# Create secret
kubectl create secret generic redis-secret \
  --from-literal=password="$REDIS_PASSWORD" \
  -n monitoring-infra

# Save password to Gateway sealed secret (will need in Gateway deployment)
echo "REDIS_PASSWORD: $REDIS_PASSWORD"
```

**Action:** Deploy Redis if not exists, create secret, verify connectivity.

---

### Task 4: Create Gateway Dockerfile

**File:** `/Volumes/Files/Projects/newLoader/services/gateway/Dockerfile`

```dockerfile
FROM eclipse-temurin:21-jdk-alpine AS builder

WORKDIR /app

# Copy Maven files
COPY pom.xml .
COPY src ./src

# Build application
RUN apk add --no-cache maven && \
    mvn clean package -DskipTests && \
    mv target/gateway-service-*.jar target/gateway-service.jar

# Runtime stage
FROM eclipse-temurin:21-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -g 1001 -S spring && \
    adduser -u 1001 -S spring -G spring

# Copy JAR from builder
COPY --from=builder /app/target/gateway-service.jar app.jar

# Change ownership
RUN chown -R spring:spring /app

USER spring

EXPOSE 8888

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
  CMD wget --quiet --tries=1 --spider http://localhost:8888/actuator/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
```

**Action:** Create Dockerfile in gateway service directory.

---

### Task 5: Create Gateway Kubernetes Deployment

**File:** `/Volumes/Files/Projects/newLoader/services/gateway/k8s_manifist/gateway-deployment.yaml`

```yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: gateway-service
  namespace: monitoring-app
  labels:
    app: gateway-service
spec:
  replicas: 2
  selector:
    matchLabels:
      app: gateway-service
  template:
    metadata:
      labels:
        app: gateway-service
    spec:
      containers:
      - name: gateway-service
        image: gateway-service:latest
        imagePullPolicy: Never
        ports:
        - containerPort: 8888
          name: http
        env:
        - name: SPRING_PROFILES_ACTIVE
          value: "prod"
        - name: REDIS_HOST
          value: "redis-master.monitoring-infra.svc.cluster.local"
        - name: REDIS_PORT
          value: "6379"
        - name: REDIS_PASSWORD
          valueFrom:
            secretKeyRef:
              name: gateway-secrets
              key: redis-password
        - name: JWT_SECRET
          valueFrom:
            secretKeyRef:
              name: gateway-secrets
              key: jwt-secret
        resources:
          requests:
            memory: "512Mi"
            cpu: "250m"
          limits:
            memory: "1Gi"
            cpu: "500m"
        livenessProbe:
          httpGet:
            path: /actuator/health
            port: 8888
          initialDelaySeconds: 60
          periodSeconds: 10
          timeoutSeconds: 3
          failureThreshold: 3
        readinessProbe:
          httpGet:
            path: /actuator/health
            port: 8888
          initialDelaySeconds: 30
          periodSeconds: 5
          timeoutSeconds: 3
          failureThreshold: 3
---
apiVersion: v1
kind: Service
metadata:
  name: gateway-service
  namespace: monitoring-app
spec:
  selector:
    app: gateway-service
  ports:
  - name: http
    port: 8888
    targetPort: 8888
  type: ClusterIP
---
apiVersion: v1
kind: Service
metadata:
  name: gateway-service-nodeport
  namespace: monitoring-app
spec:
  selector:
    app: gateway-service
  ports:
  - name: http
    port: 8888
    targetPort: 8888
    nodePort: 30088
  type: NodePort
```

**Action:** Create Kubernetes manifests for Gateway deployment and services.

---

### Task 6: Create Gateway Secrets

**File:** `/Volumes/Files/Projects/newLoader/services/gateway/k8s_manifist/gateway-secrets.yaml`

```yaml
apiVersion: v1
kind: Secret
metadata:
  name: gateway-secrets
  namespace: monitoring-app
type: Opaque
stringData:
  redis-password: "<REDIS_PASSWORD_FROM_TASK_3>"
  jwt-secret: "<SAME_JWT_SECRET_AS_AUTH_SERVICE>"
```

**Get JWT secret from auth-service:**
```bash
# Extract JWT secret from auth-service
kubectl get secret auth-secrets -n monitoring-app -o jsonpath='{.data.jwt-secret}' | base64 -d
```

**Action:** Create secrets file with Redis password and JWT secret (same as auth-service).

---

### Task 7: Update Frontend NGINX Configuration

**File:** `/Volumes/Files/Projects/newLoader/frontend/nginx.conf`

**Change from direct service routing to Gateway routing:**

**BEFORE:**
```nginx
# Direct routing to services
location /api/v1/auth/ {
    proxy_pass http://auth-service:8081/api/v1/auth/;
}

location /api/ {
    proxy_pass http://signal-loader:8080/api/;
}
```

**AFTER:**
```nginx
# Route ALL /api/* requests to Gateway
location /api/ {
    # Internal Kubernetes service DNS
    proxy_pass http://gateway-service.monitoring-app.svc.cluster.local:8888/api/;

    proxy_set_header Host $host;
    proxy_set_header X-Real-IP $remote_addr;
    proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
    proxy_set_header X-Forwarded-Proto $scheme;
    proxy_set_header Origin $http_origin;

    # Remove CORS headers - Gateway handles CORS now
    # add_header Access-Control-Allow-Origin ... (DELETE THESE)

    # Timeouts
    proxy_connect_timeout 60s;
    proxy_send_timeout 60s;
    proxy_read_timeout 60s;
}
```

**Action:** Update nginx.conf to route all API traffic through Gateway.

---

### Task 8: Change Backend Services to ClusterIP Only

**Files:**
- `/Volumes/Files/Projects/newLoader/services/auth-service/k8s_manifist/auth-deployment.yaml`
- `/Volumes/Files/Projects/newLoader/services/loader/k8s_manifist/loader-deployment.yaml`

**Change:**
```yaml
# BEFORE: NodePort services exposed externally
apiVersion: v1
kind: Service
metadata:
  name: auth-service-nodeport
spec:
  type: NodePort
  ports:
  - port: 8081
    nodePort: 30081
```

**AFTER: Keep only ClusterIP (internal only)**
```yaml
# DELETE the NodePort service entirely
# Keep only:
apiVersion: v1
kind: Service
metadata:
  name: auth-service
spec:
  type: ClusterIP
  ports:
  - port: 8081
    targetPort: 8081
```

**Action:** Remove NodePort services for auth-service and signal-loader. Services should only be accessible via Gateway.

---

### Task 9: Update app_installer.sh

**File:** `/Volumes/Files/Projects/newLoader/app_installer.sh`

**Add Gateway build and deployment section after auth-service:**

```bash
# ===================== Build Gateway Service =====================
log_section "Building Gateway Service"

GATEWAY_DIR="${PROJECT_ROOT}/services/gateway"

if [ ! -d "$GATEWAY_DIR" ]; then
    log_error "Gateway service directory not found: $GATEWAY_DIR"
    exit 1
fi

log_info "Building gateway-service JAR..."
cd "$GATEWAY_DIR"

if ! mvn clean package -DskipTests; then
    log_error "Maven build failed for gateway-service"
    exit 1
fi

GATEWAY_JAR="${GATEWAY_DIR}/target/gateway-service-0.0.1-SNAPSHOT.jar"
if [ ! -f "$GATEWAY_JAR" ]; then
    log_error "Gateway JAR not found: $GATEWAY_JAR"
    exit 1
fi

log_success "Gateway service JAR built successfully"

# Build Docker image
log_info "Building gateway-service Docker image..."
if ! docker build -t gateway-service:latest "$GATEWAY_DIR"; then
    log_error "Docker build failed for gateway-service"
    exit 1
fi

log_success "Gateway service Docker image built"

# ===================== Deploy Gateway Service =====================
log_section "Deploying Gateway Service"

log_info "Applying gateway secrets..."
kubectl apply -f "${GATEWAY_DIR}/k8s_manifist/gateway-secrets.yaml"

log_info "Deploying gateway service..."
kubectl apply -f "${GATEWAY_DIR}/k8s_manifist/gateway-deployment.yaml"

log_info "Waiting for gateway service to be ready..."
kubectl wait --for=condition=available --timeout=120s deployment/gateway-service -n "${A_NAMESPACE}"

GATEWAY_POD=$(kubectl get pods -n "${A_NAMESPACE}" -l app=gateway-service -o jsonpath='{.items[0].metadata.name}')
log_info "Gateway pod: $GATEWAY_POD"

log_info "Checking gateway service health..."
sleep 10
kubectl logs -n "${A_NAMESPACE}" "$GATEWAY_POD" --tail=50 || true

log_success "Gateway service deployed successfully"
```

**Action:** Add Gateway build and deployment steps to app_installer.sh.

---

### Task 10: Testing Plan

**Test 1: Verify Redis Connectivity**
```bash
# Get Redis pod
REDIS_POD=$(kubectl get pods -n monitoring-infra -l app=redis -o jsonpath='{.items[0].metadata.name}')

# Test Redis connection
kubectl exec -n monitoring-infra "$REDIS_POD" -- redis-cli -a "$REDIS_PASSWORD" ping
# Expected: PONG
```

**Test 2: Verify Gateway Health**
```bash
# Check Gateway pod is running
kubectl get pods -n monitoring-app -l app=gateway-service

# Check Gateway logs
GATEWAY_POD=$(kubectl get pods -n monitoring-app -l app=gateway-service -o jsonpath='{.items[0].metadata.name}')
kubectl logs -n monitoring-app "$GATEWAY_POD" --tail=100

# Test health endpoint
kubectl exec -n monitoring-app "$GATEWAY_POD" -- wget -qO- http://localhost:8888/actuator/health
# Expected: {"status":"UP"}
```

**Test 3: Test Gateway Routing - Auth Service**
```bash
# Test login via Gateway (from outside cluster)
curl -X POST http://localhost:30088/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -H "Origin: http://localhost:30080" \
  -d '{"username":"admin","password":"HaAdmin123"}'

# Expected: {"token":"eyJ...","username":"admin","roles":["ROLE_ADMIN"]}
```

**Test 4: Test Gateway Routing - Loader Service**
```bash
# Get JWT token first
TOKEN=$(curl -s -X POST http://localhost:30088/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -d '{"username":"admin","password":"HaAdmin123"}' | jq -r '.token')

# Test loaders endpoint via Gateway
curl -X GET http://localhost:30088/api/v1/res/loaders/loaders \
  -H "Authorization: Bearer $TOKEN" \
  -H "Origin: http://localhost:30080"

# Expected: {"loaders":[...]}
```

**Test 5: Test Rate Limiting**
```bash
# Send 25 requests rapidly (should hit rate limit)
for i in {1..25}; do
  curl -s -X POST http://localhost:30088/api/v1/auth/login \
    -H "Content-Type: application/json" \
    -d '{"username":"admin","password":"wrong"}' \
    -w "Request $i: %{http_code}\n"
  sleep 0.1
done

# Expected: First ~20 requests return 401/403, later requests return 429 (Too Many Requests)
```

**Test 6: Test Frontend via Gateway**
```bash
# Rebuild frontend with new NGINX config
cd /Volumes/Files/Projects/newLoader/frontend
docker build --no-cache -t loader-frontend:latest .

# Delete frontend pods to recreate with new image
kubectl delete pod -l app=loader-frontend -n monitoring-app

# Wait for pods to be ready
kubectl wait --for=condition=ready pod -l app=loader-frontend -n monitoring-app --timeout=120s

# Test from browser
# Open: http://localhost:30080
# Login with: admin / HaAdmin123
# Verify: Can view loaders list, all API calls go through Gateway
```

**Test 7: Verify Backend Services NOT Directly Accessible**
```bash
# These should FAIL (NodePort removed):
curl http://localhost:30081/api/v1/auth/login
curl http://localhost:30082/api/v1/res/loaders/loaders

# Expected: Connection refused or timeout (services only accessible via Gateway)
```

---

## Deployment Sequence

**Execute in this order:**

1. ✅ Deploy Redis (if not exists)
2. ✅ Update Gateway application.yaml
3. ✅ Create Gateway Dockerfile
4. ✅ Create Gateway secrets (with Redis password + JWT secret)
5. ✅ Build Gateway Docker image
6. ✅ Deploy Gateway to Kubernetes
7. ✅ Verify Gateway health and connectivity to Redis
8. ✅ Update frontend nginx.conf to route to Gateway
9. ✅ Rebuild and redeploy frontend
10. ✅ Remove NodePort services from auth-service and signal-loader
11. ✅ Test all flows via Gateway
12. ✅ Update app_installer.sh with Gateway steps

---

## Rollback Plan

**If Gateway deployment fails:**

1. **Revert frontend nginx.conf** to direct service routing
2. **Rebuild frontend** with old config
3. **Re-add NodePort services** to auth-service and signal-loader
4. **Delete Gateway deployment**: `kubectl delete deployment gateway-service -n monitoring-app`
5. **System returns to working state**

**Rollback commands:**
```bash
# Restore frontend nginx.conf from git
cd /Volumes/Files/Projects/newLoader/frontend
git checkout nginx.conf

# Rebuild frontend
docker build --no-cache -t loader-frontend:latest .

# Delete frontend pods
kubectl delete pod -l app=loader-frontend -n monitoring-app

# Restore auth-service NodePort
kubectl apply -f services/auth-service/k8s_manifist/auth-deployment.yaml

# Delete Gateway
kubectl delete deployment,svc gateway-service -n monitoring-app
```

---

## Success Criteria

**Gateway deployment is successful when:**

- ✅ Gateway pod is running and healthy
- ✅ Gateway actuator/health returns 200 OK
- ✅ Login works via Gateway (http://localhost:30088/api/v1/auth/login)
- ✅ Loaders API works via Gateway with JWT token
- ✅ Frontend login works (routes through Gateway)
- ✅ Frontend loaders page loads (routes through Gateway)
- ✅ Rate limiting triggers after excessive requests
- ✅ Backend services NOT accessible directly (NodePort removed)
- ✅ CORS works properly (no browser errors)
- ✅ Gateway logs show routed requests

---

## Prompt to Start Work Tomorrow

**Copy and paste this to Claude Code:**

```
I need to deploy Spring Cloud Gateway to replace NGINX-based routing in my Kubernetes cluster.

Context:
- Current: Frontend NGINX routes directly to auth-service (8081) and signal-loader (8080)
- Target: All API traffic should flow through Spring Cloud Gateway (8888)
- Reason: Bank-grade solution requiring centralized security, rate limiting, circuit breaking

Project:
- Location: /Volumes/Files/Projects/newLoader
- Gateway code exists in: services/gateway/
- Current deployment: Kubernetes cluster, namespace monitoring-app

Requirements:
1. Deploy Redis for rate limiting backend (monitoring-infra namespace)
2. Update Gateway application.yaml with:
   - Routes to auth-service:8081 (/api/v1/auth/**)
   - Routes to signal-loader:8080 (/api/**)
   - CORS configuration
   - Rate limiting filters
   - Circuit breaker filters
3. Create Gateway Dockerfile (multi-stage build with Maven)
4. Create Gateway Kubernetes deployment (2 replicas, ClusterIP + NodePort 30088)
5. Create Gateway secrets (Redis password + JWT secret from auth-service)
6. Update frontend nginx.conf to route ALL /api/* to Gateway
7. Remove NodePort from auth-service and signal-loader (ClusterIP only)
8. Add Gateway to app_installer.sh build/deploy process
9. Test all flows via Gateway

Reference document: /Volumes/Files/Projects/newLoader/GATEWAY_DEPLOYMENT_TASK.md

Start with Task 1: Update Gateway application.yaml configuration.
```

---

## Additional Notes

**After Gateway is deployed:**

1. Monitor Gateway metrics at: http://localhost:30088/actuator/prometheus
2. Check rate limiting in Redis:
   ```bash
   kubectl exec -n monitoring-infra redis-pod -- redis-cli -a PASSWORD KEYS "request_rate_limiter*"
   ```
3. Consider adding:
   - Grafana dashboard for Gateway metrics
   - Alerting on Gateway circuit breaker state
   - Distributed tracing (Spring Cloud Sleuth + Zipkin)

**Future enhancements (Phase 2):**
- JWT validation filter in Gateway
- Response caching filter
- Request/response transformation filters
- Custom fallback handlers for circuit breakers
- Canary deployment routing

---

**End of Document**

This task is ready to execute. All details, code snippets, and testing procedures are included.
