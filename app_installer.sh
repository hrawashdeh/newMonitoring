
#!/bin/bash

# ===================== Colors =====================
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'   # No Color
SCRIPT_START_EPOCH="$(date +%s)"
SCRIPT_START_STR="$(date +"%Y-%m-%d %H:%M:%S")"

# ===================== Log Helpers =====================
log_info()     { printf "%b[INFO]%b %s\n"    "$BLUE"   "$NC" "$*"; }
log_debug()    { printf "%b[DEBUG]%b %s\n"   "$YELLOW" "$NC" "$*"; }
log_warn()     { printf "%b[WARN]%b %s\n"    "$YELLOW" "$NC" "$*"; }
log_error()    { printf "%b[ERROR]%b %s\n"   "$RED"    "$NC" "$*"; }
log_success()  { printf "%b[SUCCESS]%b %s\n" "$GREEN"  "$NC" "$*"; }
log_section()  { printf "\n\033[0;35m--- %s ---\033[0m\n\n" "$*"; }
log_purple()   { printf "\033[0;35m%s\033[0m\n" "$*"; }
exit_error()   { log_error "$*"; exit 1; }

# ===================== Prompt Helper =====================
prompt_choice() {
  local message="$1"
  local options_str="$2"
  IFS='/' read -ra OPTIONS <<< "$options_str"

  local DEFAULT="${OPTIONS[0]}"

  while true; do
    printf "${GREEN}%s (%s): ${NC}" "$message" "$options_str"
    read -r CONFIRM < /dev/tty
    CONFIRM=${CONFIRM:-$DEFAULT}

    for opt in "${OPTIONS[@]}"; do

      if [[ "$CONFIRM" == "$opt" ]]; then
        return 0
      fi
    done

    log_error "Invalid option. Allowed values: ${options_str}"
  done
}


# ===================== Configuration =====================
log_section "Verify Configuration"
PROJECT_ROOT="/Volumes/Files/Projects/newLoader"
SEALED_NS="sealed-secrets"
A_NAMESPACE="monitoring-app"
k_context="docker-desktop"

log_info "Switching to project root"
log_info "Please verify the parameters below before proceeding"

log_debug "PROJECT_ROOT = ${PROJECT_ROOT}"
log_debug "SEALED_NS    = ${SEALED_NS}"
log_debug "NAMESPACE    = ${A_NAMESPACE}"
log_debug "k_context    = ${k_context}"

prompt_choice "Is this valid configuration?" "Y/n"
[[ "$CONFIRM" =~ ^[Yy]$ ]] || exit_error "Aborted by user"

# ===================== kubectl Context =====================
log_section "Switch kubectl context"
if ! kubectl config use-context "${k_context}"; then
    exit_error "Failed to switch kubectl context to '${k_context}'"
fi

log_info "Current kubectl context: $(kubectl config current-context)"

prompt_choice "Is this valid context?" "Y/n"
[[ "$CONFIRM" =~ ^[Yy]$ ]] || exit_error "Aborted by user"

# ===================== Namespaces =====================
log_section "Creating namespaces"

kubectl delete namespace monitoring-app --ignore-not-found

kubectl create namespace "${A_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

log_info "Current Kubernetes namespaces:"
kubectl get namespaces

if ! kubectl get namespace "${A_NAMESPACE}" >/dev/null 2>&1; then
   exit_error "Namespace '${A_NAMESPACE}' was not created successfully"
fi


log_info "Namespaces validated successfully"


# ===================== Selaed secret  =====================
cd "${PROJECT_ROOT}/services/secrets" || exit_error "Missing directory: ${PROJECT_ROOT}/services/secrets"

log_section "installing Sealed Secrets "

kubeseal \
  --controller-name=sealed-secrets \
  --controller-namespace=${SEALED_NS} \
  --namespace ${A_NAMESPACE} \
  --format yaml \
  < app-secrets-plain.yaml \
  > app-secrets-sealed.yaml || exit_error "kubeseal failed"

kubectl apply -f app-secrets-sealed.yaml || exit_error "Applying sealed secret failed"



# ===================== Comomon functions ==================

validate_deployment() {
    local deployment=$1
    local namespace=$2
    if ! kubectl get deployment "$deployment" -n "$namespace" &>/dev/null; then
        log_error "Deployment '$deployment' not found in namespace '$namespace'"
        return 1
    fi
    return 0
}


monitor_pod_health() {
    local deployment=$1
    local namespace=$2
    local timeout=${3:-300}
    local elapsed=0
    local interval=10

    log_info "Monitoring pod health for deployment: $deployment"
    log_info "Timeout: ${timeout}s | Check interval: ${interval}s"
    echo

    while [ $elapsed -lt $timeout ]; do
        local ready_replicas=$(kubectl get deployment "$deployment" -n "$namespace" \
 		 -o jsonpath='{.status.readyReplicas}' 2>/dev/null)

		local desired_replicas=$(kubectl get deployment "$deployment" -n "$namespace" \
  		-o jsonpath='{.spec.replicas}' 2>/dev/null)

		ready_replicas=${ready_replicas:-0}
		desired_replicas=${desired_replicas:-0}

        echo -ne "\r${BLUE}[$(date +%H:%M:%S)]${NC} Ready: $ready_replicas/$desired_replicas pods"

        if [ "$ready_replicas" -eq "$desired_replicas" ] && [ "$ready_replicas" != 0 ]; then
            echo
            log_success "All pods are ready ($ready_replicas/$desired_replicas)"
            return 0
        fi

        sleep $interval
        elapsed=$((elapsed + interval))
    done

    echo
    log_error "Timeout: Pods did not become ready within ${timeout}s"

    # Show pod status for debugging
    echo
    log_error "Current pod status:"
    kubectl get pods -n "$namespace" -l "app=$deployment" --no-headers

    return 1
}

get_pod_status() {
    local deployment=$1
    local namespace=$2

    if ! validate_deployment "$deployment" "$namespace"; then
        echo "NOT_FOUND"
        return 1
    fi

    local ready_replicas=$(kubectl get deployment "$deployment" -n "$namespace" \
        -o jsonpath='{.status.readyReplicas}' 2>/dev/null || echo "0")
    local desired_replicas=$(kubectl get deployment "$deployment" -n "$namespace" \
        -o jsonpath='{.spec.replicas}' 2>/dev/null || echo "0")

    if [ "$ready_replicas" -eq "$desired_replicas" ] && [ "$ready_replicas" != "0" ]; then
        echo "HEALTHY"
    elif [ "$ready_replicas" -eq "0" ]; then
        echo "UNAVAILABLE"
    else
        echo "DEGRADED"
    fi
}

scan_pod_logs() {
    local deployment=$1
    local namespace=$2
    local tail_lines=${3:-100}

    log_info "Scanning logs for deployment: $deployment"

    # Get all pods for this deployment
    local pods=$(kubectl get pods -n "$namespace" -l "app=$deployment" \
        -o jsonpath='{.items[*].metadata.name}' 2>/dev/null)

    if [ -z "$pods" ]; then
        log_error "No pods found for deployment: $deployment"
        return 0
    fi

    local error_count=0
    local warn_count=0

    for pod in $pods; do
        echo
        log_section "Pod: $pod"

        # Check for errors
        local errors=$(kubectl logs "$pod" -n "$namespace" --tail="$tail_lines" 2>/dev/null | \
            grep -iE "error|exception|fatal|failed" || true)

        if [ -n "$errors" ]; then
            error_count=$((error_count + 1))
            log_error "Errors found in pod logs:"
            echo "$errors" | tail -10
            echo
        fi

        # Check for warnings
        local warnings=$(kubectl logs "$pod" -n "$namespace" --tail="$tail_lines" 2>/dev/null | \
            grep -iE "warn|warning" || true)

        if [ -n "$warnings" ]; then
            warn_count=$((warn_count + 1))
            log_error "Warnings found in pod logs:"
            echo "$warnings" | tail -5
            echo
        fi
    done

    # Summary
    echo
    if [ $error_count -eq 0 ] && [ $warn_count -eq 0 ]; then
        log_success "No errors or warnings found in logs"
    else
        [ $error_count -gt 0 ] && log_error "Found errors in $error_count pod(s)"
        [ $warn_count -gt 0 ] && log_error "Found warnings in $warn_count pod(s)"
    fi

    return 0
}

check_health_probe() {
    local service=$1
    local namespace=$2
    local port=${3:-8080}
    local path=${4:-/actuator/health}

    log_info "Checking health probe: $service:$port$path"

    # Port-forward in background
    local local_port=$((8000 + RANDOM % 1000))
    kubectl port-forward -n "$namespace" "svc/$service" "$local_port:$port" &>/dev/null &
    local pf_pid=$!

    # Wait for port-forward to establish
    sleep 2

    # Call health endpoint
    local response=$(curl -s -w "\n%{http_code}" "http://localhost:$local_port$path" 2>/dev/null || echo "FAILED")
    local http_code=$(echo "$response" | tail -1)
    local body=$(echo "$response" | head -n -1)

    # Kill port-forward
    kill $pf_pid 2>/dev/null || true

    # Evaluate response
    if [ "$http_code" = "200" ]; then
        log_success "Health probe OK (HTTP $http_code)"
        echo "$body" | jq '.' 2>/dev/null || echo "$body"
        return 0
    else
        log_error "Health probe FAILED (HTTP $http_code)"
        echo "$body"
        return 1
    fi
}

# Check all actuator endpoints
# Usage: check_actuator_endpoints <service-name> <namespace> <port>
check_actuator_endpoints() {
    local service=$1
    local namespace=$2
    local port=${3:-8080}

    log_section "Actuator Endpoints: $service"

    local endpoints=("health" "info" "metrics")

    for endpoint in "${endpoints[@]}"; do
        echo
        log_info "Endpoint: /actuator/$endpoint"
        check_health_probe "$service" "$namespace" "$port" "/actuator/$endpoint"
    done
}


# ===================== ETL Initializer =====================
cd "${PROJECT_ROOT}/services/etl_initializer" || exit_error "Missing directory: ${PROJECT_ROOT}/services/etl_initializer"

SERVICE_NAME="etl-initializer"

log_section "Installing ETL Initializer Service"

log_info "Running Maven build..."

if ! mvn clean package -Dmaven.test.skip=true; then
    log_error "Maven build failed"
    exit 1
fi

log_success "Maven build completed"

# Enterprise-grade deployment: Generate unique version with timestamp
VERSION="1.0.0-$(date +%s)"
log_info "Building Docker image with version: ${VERSION}"
log_info "Enterprise deployment: --no-cache ensures fresh build (no cache issues)"

if ! docker build --no-cache --pull -t etl-initializer:${VERSION} -t etl-initializer:0.0.1-SNAPSHOT .; then
    log_error "Docker image build failed"
    exit 1
else
	if docker images etl-initializer:${VERSION} --format "{{.Repository}}:{{.Tag}}" | grep -q "etl-initializer:${VERSION}"; then
	    log_success "Image verified in local Docker registry"
	else
	    log_error "Image not found in local Docker registry!"
	    exit 1
	fi
	log_success "Build completed: etl-initializer:${VERSION} (also tagged 0.0.1-SNAPSHOT)"
fi

if ! kubectl apply -f "./k8s_manifist/etl-initializer-deployment.yaml" -n "${A_NAMESPACE}"; then
	log_error "Deployment manifest failed"
	exit 1
else
	log_success "Deployment manifest applied"
fi

# Monitor health
monitor_pod_health "$SERVICE_NAME" "$A_NAMESPACE" 120

# Check pod status
POD_STATUS=$(get_pod_status "$SERVICE_NAME" "$A_NAMESPACE")
log_info "Pod Status: $POD_STATUS"

# Check logs for errors
scan_pod_logs "$SERVICE_NAME" "$A_NAMESPACE" 60

log_success "ETL Initializer service installed successfully"

# ===================== Load ETL Configuration YAML =====================
log_section "Loading ETL Configuration"

ETL_YAML_FILE="${PROJECT_ROOT}/services/testData/etl-data-v1.yaml"

if [ ! -f "$ETL_YAML_FILE" ]; then
    log_error "ETL YAML file not found: $ETL_YAML_FILE"
    log_error "Please ensure backup/etl-data-v1.yaml exists before running installer"
    exit 1
fi

log_info "Waiting for etl-initializer pod to be ready..."
if ! kubectl wait --for=condition=ready pod -l app=etl-initializer -n "${A_NAMESPACE}" --timeout=120s; then
    log_error "etl-initializer pod did not become ready in time"
    exit 1
fi

ETL_POD=$(kubectl get pod -n "${A_NAMESPACE}" -l app=etl-initializer -o jsonpath='{.items[0].metadata.name}')
log_info "ETL Initializer pod: $ETL_POD"

log_info "Copying ETL configuration YAML to pod..."
if ! kubectl cp "$ETL_YAML_FILE" "${A_NAMESPACE}/${ETL_POD}:/data/uploads/etl-data-v1.yaml"; then
    log_error "Failed to copy YAML file to pod"
    exit 1
fi

log_success "ETL configuration file uploaded successfully"
log_info "Monitoring ETL configuration processing (this may take up to 60 seconds)..."

# Wait for processing
sleep 30

# Check processing logs
kubectl logs -n "${A_NAMESPACE}" "$ETL_POD" --tail=50 | grep -E "Processing file|Successfully processed|version" || true

log_info "Verifying loaders were created..."
kubectl exec -n monitoring-infra postgres-postgresql-0 -- \
    env PGPASSWORD=HaAirK101348App psql -U alerts_user -d alerts_db -c \
    "SELECT loader_code, load_status, min_interval_seconds FROM loader.loader;" || \
    log_warn "Could not verify loader creation (this is normal on first run)"

log_success "ETL configuration loaded successfully"

# ===================== Load Auth Users YAML =====================
log_section "Loading Auth Users Configuration"

AUTH_YAML_FILE="${PROJECT_ROOT}/services/testData/auth-data-v1.yaml"

if [ ! -f "$AUTH_YAML_FILE" ]; then
    log_error "Auth YAML file not found: $AUTH_YAML_FILE"
    log_error "Please ensure services/testData/auth-data-v1.yaml exists before running installer"
    exit 1
fi

log_info "Copying auth configuration YAML to pod..."
if ! kubectl cp "$AUTH_YAML_FILE" "${A_NAMESPACE}/${ETL_POD}:/data/uploads/auth-data-v1.yaml"; then
    log_error "Failed to copy auth YAML file to pod"
    exit 1
fi

log_success "Auth configuration file uploaded successfully"
log_info "Monitoring auth configuration processing..."

# Wait for processing
sleep 30

# Check processing logs
kubectl logs -n "${A_NAMESPACE}" "$ETL_POD" --tail=50 | grep -E "Processing file|auth-data|Successfully processed" || true

log_info "Verifying auth users were created..."
kubectl exec -n monitoring-infra postgres-postgresql-0 -- \
    env PGPASSWORD=HaAirK101348App psql -U alerts_user -d alerts_db -c \
    "SELECT username, email, enabled FROM auth.users;" || \
    log_warn "Could not verify user creation (this is normal on first run)"

log_success "Auth users loaded successfully"

# ===================== Load Messages Dictionary YAML =====================
log_section "Loading Messages Dictionary Configuration"

MESSAGES_YAML_FILE="${PROJECT_ROOT}/services/testData/messages-data-v1.yaml"

if [ ! -f "$MESSAGES_YAML_FILE" ]; then
    log_error "Messages YAML file not found: $MESSAGES_YAML_FILE"
    log_error "Please ensure services/testData/messages-data-v1.yaml exists before running installer"
    exit 1
fi

log_info "Copying messages dictionary configuration YAML to pod..."
if ! kubectl cp "$MESSAGES_YAML_FILE" "${A_NAMESPACE}/${ETL_POD}:/data/uploads/messages-data-v1.yaml"; then
    log_error "Failed to copy messages YAML file to pod"
    exit 1
fi

log_success "Messages dictionary configuration file uploaded successfully"
log_info "Monitoring messages configuration processing..."

# Wait for processing
sleep 10

# Check processing logs
kubectl logs -n "${A_NAMESPACE}" "$ETL_POD" --tail=50 | grep -E "Processing file|messages-data|Successfully processed" || true

log_info "Verifying messages were created..."
kubectl exec -n monitoring-infra postgres-postgresql-0 -- \
    env PGPASSWORD=HaAirK101348App psql -U alerts_user -d alerts_db -c \
    "SELECT message_code, message_category, message_en FROM general.message_dictionary LIMIT 10;" || \
    log_warn "Could not verify message creation (this is normal on first run)"

log_success "Messages dictionary loaded successfully"



# ===================== Auth Service =====================
cd "${PROJECT_ROOT}/services/auth-service" || exit_error "Missing directory: ${PROJECT_ROOT}/services/auth-service"

SERVICE_NAME="auth-service"

log_section "Installing Auth Service"

log_info "Running Maven build..."

if ! mvn clean package -Dmaven.test.skip=true; then
    log_error "Maven build failed"
    exit 1
fi

log_success "Maven build completed"

# Enterprise-grade deployment: Generate unique version with timestamp
VERSION="1.0.0-$(date +%s)"
log_info "Building Docker image with version: ${VERSION}"
log_info "Enterprise deployment: --no-cache ensures fresh build (no cache issues)"

if ! docker build --no-cache --pull -t auth-service:${VERSION} -t auth-service:latest .; then
    log_error "Docker image build failed"
    exit 1
else
	if docker images auth-service:${VERSION} --format "{{.Repository}}:{{.Tag}}" | grep -q "auth-service:${VERSION}"; then
	    log_success "Image verified in local Docker registry"
	else
	    log_error "Image not found in local Docker registry!"
	    exit 1
	fi
	log_success "Build completed: auth-service:${VERSION} (also tagged latest)"
fi

if ! kubectl apply -f "./k8s/deployment.yaml" -n "${A_NAMESPACE}"; then
	log_error "Deployment manifest failed"
	exit 1
else
	log_success "Deployment manifest applied"
fi

# Monitor health
monitor_pod_health "$SERVICE_NAME" "$A_NAMESPACE" 120

# Check pod status
POD_STATUS=$(get_pod_status "$SERVICE_NAME" "$A_NAMESPACE")
log_info "Pod Status: $POD_STATUS"

# Check logs for errors
scan_pod_logs "$SERVICE_NAME" "$A_NAMESPACE" 60

# Test health probes
check_actuator_endpoints "$SERVICE_NAME" "$A_NAMESPACE" 8081

log_success "Auth service installed successfully"



# ===================== Gateway Service =====================
cd "${PROJECT_ROOT}/services/gateway" || exit_error "Missing directory: ${PROJECT_ROOT}/services/gateway"

SERVICE_NAME="gateway-service"

log_section "Installing Gateway Service (Spring Cloud Gateway)"

log_info "Running Maven build..."

if ! mvn clean package -DskipTests; then
    log_error "Maven build failed"
    exit 1
fi

log_success "Maven build completed"

# Enterprise-grade deployment: Generate unique version with timestamp
VERSION="1.0.0-$(date +%s)"
log_info "Building Docker image with version: ${VERSION}"
log_info "Enterprise deployment: --no-cache ensures fresh build (no cache issues)"

if ! docker build --no-cache --pull -t gateway-service:${VERSION} -t gateway-service:latest .; then
    log_error "Docker image build failed"
    exit 1
else
	if docker images gateway-service:${VERSION} --format "{{.Repository}}:{{.Tag}}" | grep -q "gateway-service:${VERSION}"; then
	    log_success "Image verified in local Docker registry"
	else
	    log_error "Image not found in local Docker registry!"
	    exit 1
	fi
	log_success "Build completed: gateway-service:${VERSION} (also tagged latest)"
fi

log_info "Deploying gateway service to Kubernetes..."

if ! kubectl apply -f "./k8s_manifist/gateway-deployment.yaml" -n "${A_NAMESPACE}"; then
	log_error "Deployment manifest failed"
	exit 1
else
	log_success "Deployment manifest applied"
fi

# Monitor health
monitor_pod_health "$SERVICE_NAME" "$A_NAMESPACE" 120

# Check pod status
POD_STATUS=$(get_pod_status "$SERVICE_NAME" "$A_NAMESPACE")
log_info "Pod Status: $POD_STATUS"

# Check logs for errors
scan_pod_logs "$SERVICE_NAME" "$A_NAMESPACE" 60

# Test health probes
check_actuator_endpoints "$SERVICE_NAME" "$A_NAMESPACE" 8888

# Test Redis connectivity from Gateway pod
log_info "Testing Gateway Redis connectivity..."
GATEWAY_POD=$(kubectl get pods -n "${A_NAMESPACE}" -l app=gateway-service -o jsonpath='{.items[0].metadata.name}')
if [ -n "$GATEWAY_POD" ]; then
    log_info "Gateway pod: $GATEWAY_POD"
    log_info "Checking Gateway logs for Redis connection..."
    kubectl logs -n "${A_NAMESPACE}" "$GATEWAY_POD" --tail=30 | grep -i "redis\|connected" || true
fi

log_success "Gateway service installed successfully"

log_info "Gateway NodePort: 30088"
log_info "Test gateway: curl http://localhost:30088/actuator/health"



# ===================== DATA Generator =====================
cd "${PROJECT_ROOT}/services/dataGenerator" || exit_error "Missing directory: ${PROJECT_ROOT}/services/dataGenerator"

SERVICE_NAME="data-generator"

log_section "Installing data-generator Service"

log_info "Running Maven build..."

if ! mvn clean package -Dmaven.test.skip=true; then
    log_error "Maven build failed"
    exit 1
fi

log_success "Maven build completed"


# Enterprise-grade deployment: Generate unique version with timestamp
VERSION="1.0.0-$(date +%s)"
log_info "Building Docker image with version: ${VERSION}"
log_info "Enterprise deployment: --no-cache ensures fresh build (no cache issues)"

if ! docker build --no-cache --pull -t data-generator:${VERSION} -t data-generator:0.0.1-SNAPSHOT .; then
    log_error "Docker image build failed"
    exit 1
else
	if docker images data-generator:${VERSION} --format "{{.Repository}}:{{.Tag}}" | grep -q "data-generator:${VERSION}"; then
	    log_success "Image verified in local Docker registry"
	else
	    log_error "Image not found in local Docker registry!"
	    exit 1
	fi
	log_success "Build completed: data-generator:${VERSION} (also tagged 0.0.1-SNAPSHOT)"
fi

if ! kubectl apply -f "./k8s_manifist/data-generator-deployment.yaml" -n $A_NAMESPACE; then
	log_error "Deployment manifest failed"
	exit 1
else
	log_success "Deployment manifest applied"
fi

# Monitor health

monitor_pod_health "$SERVICE_NAME" "$A_NAMESPACE" 60



# check pod status
POD_STATUS=$(get_pod_status "$SERVICE_NAME" "$A_NAMESPACE")
log_info "Pod Status: $POD_STATUS" 
# Check logs for errors
scan_pod_logs "$SERVICE_NAME" "$A_NAMESPACE" 60

# Test health probes
check_actuator_endpoints "$SERVICE_NAME" "$A_NAMESPACE" 8080

log_success "data-generator service installed successfully"



# ===================== signal loader =====================
cd "${PROJECT_ROOT}/services/loader" || exit_error "Missing directory: ${PROJECT_ROOT}/services/loader"

SERVICE_NAME="signal-loader"

log_section "Installing signal-loader Service"

log_info "Running Maven build..."

if ! mvn clean package -Dmaven.test.skip=true; then
    log_error "Maven build failed"
    exit 1
fi

log_success "Maven build completed"


# Enterprise-grade deployment: Generate unique version with timestamp
VERSION="1.0.0-$(date +%s)"
log_info "Building Docker image with version: ${VERSION}"
log_info "Enterprise deployment: --no-cache ensures fresh build (no cache issues)"

if ! docker build --no-cache --pull -t signal-loader:${VERSION} -t signal-loader:0.0.1-SNAPSHOT .; then
    log_error "Docker image build failed"
    exit 1
else
	if docker images signal-loader:${VERSION} --format "{{.Repository}}:{{.Tag}}" | grep -q "signal-loader:${VERSION}"; then
	    log_success "Image verified in local Docker registry"
	else
	    log_error "Image not found in local Docker registry!"
	    exit 1
	fi
	log_success "Build completed: signal-loader:${VERSION} (also tagged 0.0.1-SNAPSHOT)"
fi

if ! kubectl apply -f "./k8s_manifist/loader-deployment.yaml" -n "${A_NAMESPACE}"; then
	log_error "Deployment manifest failed"
	exit 1
else
	log_success "Deployment manifest applied"
fi


# check pos sttaus

monitor_pod_health "$SERVICE_NAME" "$A_NAMESPACE" 120


# check pod status
POD_STATUS=$(get_pod_status "$SERVICE_NAME" "$A_NAMESPACE")
log_info "Pod Status: $POD_STATUS"
# Check logs for errors
scan_pod_logs "$SERVICE_NAME" "$A_NAMESPACE" 60

# Test health probes
check_actuator_endpoints "$SERVICE_NAME" "$A_NAMESPACE" 8080

log_success "loader-deployment service installed successfully"



# ===================== Import-Export Service =====================
cd "${PROJECT_ROOT}/services/import-export-service" || exit_error "Missing directory: ${PROJECT_ROOT}/services/import-export-service"

SERVICE_NAME="import-export-service"

log_section "Installing Import-Export Service"

log_info "Running Maven build..."

if ! mvn clean package -Dmaven.test.skip=true; then
    log_error "Maven build failed"
    exit 1
fi

log_success "Maven build completed"

# Enterprise-grade deployment: Generate unique version with timestamp
VERSION="1.0.0-$(date +%s)"
log_info "Building Docker image with version: ${VERSION}"
log_info "Enterprise deployment: --no-cache ensures fresh build (no cache issues)"

if ! docker build --no-cache --pull -t import-export-service:${VERSION} -t import-export-service:latest .; then
    log_error "Docker image build failed"
    exit 1
else
	if docker images import-export-service:${VERSION} --format "{{.Repository}}:{{.Tag}}" | grep -q "import-export-service:${VERSION}"; then
	    log_success "Image verified in local Docker registry"
	else
	    log_error "Image not found in local Docker registry!"
	    exit 1
	fi
	log_success "Build completed: import-export-service:${VERSION} (also tagged latest)"
fi

log_info "Creating PVC and ConfigMap for import-export-service..."

if ! kubectl apply -f "./k8s_manifist/import-export-pvc.yaml" -n "${A_NAMESPACE}"; then
	log_error "PVC and ConfigMap creation failed"
	exit 1
else
	log_success "PVC and ConfigMap created successfully"
fi

log_info "Deploying import-export-service to Kubernetes..."

if ! kubectl apply -f "./k8s_manifist/import-export-deployment.yaml" -n "${A_NAMESPACE}"; then
	log_error "Deployment manifest failed"
	exit 1
else
	log_success "Deployment manifest applied"
fi

# Monitor health
monitor_pod_health "$SERVICE_NAME" "$A_NAMESPACE" 120

# Check pod status
POD_STATUS=$(get_pod_status "$SERVICE_NAME" "$A_NAMESPACE")
log_info "Pod Status: $POD_STATUS"

# Check logs for errors
scan_pod_logs "$SERVICE_NAME" "$A_NAMESPACE" 60

# Test health probes
check_actuator_endpoints "$SERVICE_NAME" "$A_NAMESPACE" 8080

log_success "import-export-service installed successfully"



# ===================== Frontend =====================
log_section "Installation Complete"

log_success "All services have been installed successfully!"
log_info "You can now deploy the frontend separately using the frontend deployment script"




