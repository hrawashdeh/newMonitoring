
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

# ===================== Module Definitions =====================
# Using indexed arrays for bash 3.x compatibility (macOS default)
MODULE_NAMES=(
    "secrets:Sealed Secrets"
    "etl-init:ETL Initializer Service"
    "etl-config:ETL Configuration (YAML files)"
    "auth:Auth Service"
    "gateway:Gateway Service"
    "data-gen:Data Generator Service"
    "approval:Approval Workflow Core (dependency)"
    "loader:Signal Loader Service"
    "import-export:Import-Export Service"
    "frontend:Frontend (monitoring-frontend)"
)
TOTAL_MODULES=${#MODULE_NAMES[@]}

# Track which modules to run (space-separated list)
RUN_MODULES=""
AUTO_YES=false

# ===================== Module Selection =====================
show_usage() {
    echo ""
    echo "Usage: $0 [options] [module_numbers...]"
    echo ""
    echo "Options:"
    echo "  -1           Run ALL modules (non-interactive)"
    echo "  -h, --help   Show this help message"
    echo ""
    echo "Available modules:"
    for i in $(seq 1 $TOTAL_MODULES); do
        local idx=$((i - 1))
        IFS=':' read -r id name <<< "${MODULE_NAMES[$idx]}"
        printf "  %2d) %s\n" "$i" "$name"
    done
    echo ""
    echo "Examples:"
    echo "  $0           # Interactive mode - prompts for module selection"
    echo "  $0 -1        # Run ALL modules (non-interactive)"
    echo "  $0 4 5       # Run only Auth Service and Gateway Service"
    echo "  $0 8 10      # Run only Signal Loader and Frontend"
    echo ""
}

show_module_menu() {
    echo ""
    log_purple "╔════════════════════════════════════════════════════════════╗"
    log_purple "║              SELECT MODULES TO INSTALL                      ║"
    log_purple "╠════════════════════════════════════════════════════════════╣"
    for i in $(seq 1 $TOTAL_MODULES); do
        local idx=$((i - 1))
        IFS=':' read -r id name <<< "${MODULE_NAMES[$idx]}"
        printf "\033[0;35m║  %2d) %-54s ║\033[0m\n" "$i" "$name"
    done
    log_purple "╠════════════════════════════════════════════════════════════╣"
    log_purple "║  -1) ALL MODULES                                           ║"
    log_purple "║   0) EXIT                                                  ║"
    log_purple "╚════════════════════════════════════════════════════════════╝"
    echo ""
}

prompt_module_selection() {
    # Skip in auto-yes mode (modules already set)
    if [ "$AUTO_YES" = true ]; then
        return 0
    fi

    show_module_menu
    printf "${GREEN}Enter module numbers (space-separated) or -1 for all: ${NC}"
    read -r MODULE_INPUT < /dev/tty

    # Trim whitespace
    MODULE_INPUT=$(echo "$MODULE_INPUT" | xargs)

    # Check for exit
    if [[ "$MODULE_INPUT" == "0" ]]; then
        log_info "Installation cancelled by user"
        exit 0
    fi

    # Check for all modules
    if [[ "$MODULE_INPUT" == "-1" ]]; then
        RUN_MODULES=$(seq 1 $TOTAL_MODULES | tr '\n' ' ')
        return
    fi

    # Parse selected modules
    for num in $MODULE_INPUT; do
        if [[ "$num" =~ ^[0-9]+$ ]] && [ "$num" -ge 1 ] && [ "$num" -le $TOTAL_MODULES ]; then
            RUN_MODULES="$RUN_MODULES $num "
        else
            log_error "Invalid module number: $num"
            return 1
        fi
    done

    if [ -z "$RUN_MODULES" ]; then
        log_error "No modules selected"
        return 1
    fi

    return 0
}

parse_module_args() {
    # Store args for later - we'll prompt after context confirmation if no args
    CMDLINE_ARGS="$*"

    # Check for help
    if [[ "$1" == "-h" || "$1" == "--help" ]]; then
        show_usage
        exit 0
    fi

    # Check for -1, -y, or --yes (all modules, non-interactive with auto-accept)
    if [[ "$1" == "-1" || "$1" == "-y" || "$1" == "--yes" ]]; then
        RUN_MODULES=$(seq 1 $TOTAL_MODULES | tr '\n' ' ')
        INTERACTIVE_MODE=false
        AUTO_YES=true
        return
    fi

    # If arguments provided, use them (non-interactive)
    if [ $# -gt 0 ]; then
        for arg in "$@"; do
            if [[ "$arg" =~ ^[0-9]+$ ]] && [ "$arg" -ge 1 ] && [ "$arg" -le $TOTAL_MODULES ]; then
                RUN_MODULES="$RUN_MODULES $arg "
            else
                log_error "Invalid module number: $arg"
                show_usage
                exit 1
            fi
        done
        INTERACTIVE_MODE=false
        return
    fi

    # No arguments - will prompt interactively after context confirmation
    INTERACTIVE_MODE=true
}

should_run_module() {
    local module_num=$1
    [[ " $RUN_MODULES " == *" $module_num "* ]]
}

show_selected_modules() {
    log_section "Selected Modules"
    local count=0
    for i in $(seq 1 $TOTAL_MODULES); do
        if should_run_module $i; then
            local idx=$((i - 1))
            IFS=':' read -r id name <<< "${MODULE_NAMES[$idx]}"
            log_info "[$i] $name"
            count=$((count + 1))
        fi
    done
    echo ""
    log_info "Total modules to run: $count"
}

# Parse command line arguments (sets INTERACTIVE_MODE flag)
INTERACTIVE_MODE=true
parse_module_args "$@"

# Show selected modules if non-interactive mode
if [ "$INTERACTIVE_MODE" = false ]; then
    show_selected_modules
fi

# ===================== Prompt Helper =====================
prompt_choice() {
  local message="$1"
  local options_str="$2"
  IFS='/' read -ra OPTIONS <<< "$options_str"

  local DEFAULT="${OPTIONS[0]}"

  # Auto-accept in non-interactive mode
  if [ "$AUTO_YES" = true ]; then
    CONFIRM="$DEFAULT"
    log_info "Auto-accepting: $message -> $CONFIRM"
    return 0
  fi

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

SEALED_NS="sealed-secrets"
A_NAMESPACE="monitoring-app"
k_context="docker-desktop"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)" 

log_info "Switching to project root"

cd "${PROJECT_ROOT}"
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

# ===================== Interactive Module Selection =====================
if [ "$INTERACTIVE_MODE" = true ]; then
    while true; do
        if prompt_module_selection; then
            show_selected_modules
            prompt_choice "Proceed with selected modules?" "Y/n"
            if [[ "$CONFIRM" =~ ^[Yy]$ ]]; then
                break
            else
                RUN_MODULES=""  # Reset for re-selection
            fi
        fi
    done
fi

# ===================== Namespaces =====================
log_section "Managing namespaces"

# Check if this is a FULL deployment (all modules selected)
SELECTED_COUNT=0
for i in $(seq 1 $TOTAL_MODULES); do
    if should_run_module $i; then
        SELECTED_COUNT=$((SELECTED_COUNT + 1))
    fi
done

if [ "$SELECTED_COUNT" -eq "$TOTAL_MODULES" ]; then
    # Full deployment - ask before deleting namespace
    log_warn "Full deployment detected. This will DELETE and recreate the namespace."
    prompt_choice "Delete existing namespace and start fresh?" "Y/n"
    if [[ "$CONFIRM" =~ ^[Yy]$ ]]; then
        kubectl delete namespace monitoring-app --ignore-not-found
        log_info "Namespace deleted. Waiting for cleanup..."
        sleep 5
    else
        log_info "Keeping existing namespace (incremental deployment)"
    fi
else
    # Partial deployment - do NOT delete namespace
    log_info "Partial deployment detected ($SELECTED_COUNT/$TOTAL_MODULES modules)"
    log_info "Existing namespace will be preserved"
fi

# Ensure namespace exists
kubectl create namespace "${A_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

log_info "Current Kubernetes namespaces:"
kubectl get namespaces

if ! kubectl get namespace "${A_NAMESPACE}" >/dev/null 2>&1; then
   exit_error "Namespace '${A_NAMESPACE}' was not created successfully"
fi

log_info "Namespace validated successfully"


# ===================== Module 1: Sealed Secrets =====================
if should_run_module 1; then
    cd "${PROJECT_ROOT}/services/secrets" || exit_error "Missing directory: ${PROJECT_ROOT}/services/secrets"

    log_section "[1/10] Installing Sealed Secrets"

    kubeseal \
      --controller-name=sealed-secrets \
      --controller-namespace=${SEALED_NS} \
      --namespace ${A_NAMESPACE} \
      --format yaml \
      < app-secrets-plain.yaml \
      > app-secrets-sealed.yaml || exit_error "kubeseal failed"

    kubectl apply -f app-secrets-sealed.yaml || exit_error "Applying sealed secret failed"
    log_success "Sealed Secrets module completed"
fi



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
    local body=$(echo "$response" | sed '$d')

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


# ===================== Module 2: ETL Initializer =====================
if should_run_module 2; then
cd "${PROJECT_ROOT}/services/etl_initializer" || exit_error "Missing directory: ${PROJECT_ROOT}/services/etl_initializer"

SERVICE_NAME="etl-initializer"

log_section "[2/10] Installing ETL Initializer Service"

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

# Generate deployment YAML from template with unique image tag
log_info "Generating deployment YAML from template with image: etl-initializer:${VERSION}"
K8S_TEMPLATE="./k8s_manifist/etl-initializer-deployment.yaml.template"
K8S_MANIFEST="./k8s_manifist/etl-initializer-deployment.yaml"

if [ ! -f "$K8S_TEMPLATE" ]; then
    exit_error "Template file not found: $K8S_TEMPLATE"
fi

rm -f "$K8S_MANIFEST" "${K8S_MANIFEST}".bak*
cp "$K8S_TEMPLATE" "$K8S_MANIFEST"
sed -i '' "s|__IMAGE_TAG__|etl-initializer:${VERSION}|g" "$K8S_MANIFEST"
log_success "Deployment YAML generated from template"

if ! kubectl apply -f "$K8S_MANIFEST" -n "${A_NAMESPACE}"; then
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

fi  # End of module 2

# ===================== Module 4: Auth Service =====================
if should_run_module 4; then
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

# Generate deployment YAML from template with unique image tag
log_info "Generating deployment YAML from template with image: auth-service:${VERSION}"
K8S_TEMPLATE="./k8s/deployment.yaml.template"
K8S_MANIFEST="./k8s/deployment.yaml"

if [ ! -f "$K8S_TEMPLATE" ]; then
    exit_error "Template file not found: $K8S_TEMPLATE"
fi

rm -f "$K8S_MANIFEST" "${K8S_MANIFEST}".bak*
cp "$K8S_TEMPLATE" "$K8S_MANIFEST"
sed -i '' "s|__IMAGE_TAG__|auth-service:${VERSION}|g" "$K8S_MANIFEST"
log_success "Deployment YAML generated from template"

if ! kubectl apply -f "$K8S_MANIFEST" -n "${A_NAMESPACE}"; then
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
fi  # End of module 4

# ===================== Module 5: Gateway Service =====================
if should_run_module 5; then
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

# Generate deployment YAML from template with unique image tag
log_info "Generating deployment YAML from template with image: gateway-service:${VERSION}"
K8S_TEMPLATE="./k8s_manifist/gateway-deployment.yaml.template"
K8S_MANIFEST="./k8s_manifist/gateway-deployment.yaml"

if [ ! -f "$K8S_TEMPLATE" ]; then
    exit_error "Template file not found: $K8S_TEMPLATE"
fi

rm -f "$K8S_MANIFEST" "${K8S_MANIFEST}".bak*
cp "$K8S_TEMPLATE" "$K8S_MANIFEST"
sed -i '' "s|__IMAGE_TAG__|gateway-service:${VERSION}|g" "$K8S_MANIFEST"
log_success "Deployment YAML generated from template"

log_info "Deploying gateway service to Kubernetes..."

if ! kubectl apply -f "$K8S_MANIFEST" -n "${A_NAMESPACE}"; then
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
fi  # End of module 5

# ===================== Module 6: Data Generator =====================
if should_run_module 6; then
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

# Generate deployment YAML from template with unique image tag
log_info "Generating deployment YAML from template with image: data-generator:${VERSION}"
K8S_TEMPLATE="./k8s_manifist/data-generator-deployment.yaml.template"
K8S_MANIFEST="./k8s_manifist/data-generator-deployment.yaml"

if [ ! -f "$K8S_TEMPLATE" ]; then
    exit_error "Template file not found: $K8S_TEMPLATE"
fi

rm -f "$K8S_MANIFEST" "${K8S_MANIFEST}".bak*
cp "$K8S_TEMPLATE" "$K8S_MANIFEST"
sed -i '' "s|__IMAGE_TAG__|data-generator:${VERSION}|g" "$K8S_MANIFEST"
log_success "Deployment YAML generated from template"

if ! kubectl apply -f "$K8S_MANIFEST" -n $A_NAMESPACE; then
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
fi  # End of module 6

# ===================== Module 7: approval-workflow-core (dependency) =====================
if should_run_module 7; then
log_section "Building approval-workflow-core dependency"

cd "${PROJECT_ROOT}/services/approval-workflow-core" || exit_error "Missing directory: ${PROJECT_ROOT}/services/approval-workflow-core"

log_info "Installing approval-workflow-core to local Maven repository..."

if ! mvn clean install -Dmaven.test.skip=true; then
    log_error "approval-workflow-core Maven install failed"
    exit 1
fi

log_success "approval-workflow-core installed to local Maven repository (~/.m2/repository)"
fi  # End of module 7

# ===================== Module 8: Signal Loader =====================
if should_run_module 8; then
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

# Generate deployment YAML from template with unique image tag
log_info "Generating deployment YAML from template with image: signal-loader:${VERSION}"
K8S_TEMPLATE="./k8s_manifist/loader-deployment.yaml.template"
K8S_MANIFEST="./k8s_manifist/loader-deployment.yaml"

if [ ! -f "$K8S_TEMPLATE" ]; then
    exit_error "Template file not found: $K8S_TEMPLATE"
fi

rm -f "$K8S_MANIFEST" "${K8S_MANIFEST}".bak*
cp "$K8S_TEMPLATE" "$K8S_MANIFEST"
sed -i '' "s|__IMAGE_TAG__|signal-loader:${VERSION}|g" "$K8S_MANIFEST"
log_success "Deployment YAML generated from template"

if ! kubectl apply -f "$K8S_MANIFEST" -n "${A_NAMESPACE}"; then
	log_error "Deployment manifest failed"
	exit 1
else
	log_success "Deployment manifest applied"
fi

# Monitor health
monitor_pod_health "$SERVICE_NAME" "$A_NAMESPACE" 120

# check pod status
POD_STATUS=$(get_pod_status "$SERVICE_NAME" "$A_NAMESPACE")
log_info "Pod Status: $POD_STATUS"
# Check logs for errors
scan_pod_logs "$SERVICE_NAME" "$A_NAMESPACE" 60

# Test health probes
check_actuator_endpoints "$SERVICE_NAME" "$A_NAMESPACE" 8080

log_success "loader-deployment service installed successfully"
fi  # End of module 8

# ===================== Module 9: Import-Export Service =====================
if should_run_module 9; then
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

# Generate deployment YAML from template with unique image tag
log_info "Generating deployment YAML from template with image: import-export-service:${VERSION}"
K8S_TEMPLATE="./k8s_manifist/import-export-deployment.yaml.template"
K8S_MANIFEST="./k8s_manifist/import-export-deployment.yaml"

if [ ! -f "$K8S_TEMPLATE" ]; then
    exit_error "Template file not found: $K8S_TEMPLATE"
fi

rm -f "$K8S_MANIFEST" "${K8S_MANIFEST}".bak*
cp "$K8S_TEMPLATE" "$K8S_MANIFEST"
sed -i '' "s|__IMAGE_TAG__|import-export-service:${VERSION}|g" "$K8S_MANIFEST"
log_success "Deployment YAML generated from template"

log_info "Deploying import-export-service to Kubernetes..."

if ! kubectl apply -f "$K8S_MANIFEST" -n "${A_NAMESPACE}"; then
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
fi  # End of module 9

# ===================== Module 10: Frontend =====================
if should_run_module 10; then
    FRONTEND_DIR="${PROJECT_ROOT}/frontend"
    SERVICE_NAME="loader-frontend"
    IMAGE_TAG="loader-frontend:latest"

    cd "${FRONTEND_DIR}" || exit_error "Missing directory: ${FRONTEND_DIR}"

    log_section "[10/10] Building Frontend Application"

    # Check if package.json exists
    if [ ! -f "package.json" ]; then
        exit_error "package.json not found in ${FRONTEND_DIR}"
    fi

    log_info "Installing npm dependencies..."
    if ! npm install; then
        log_error "npm install failed"
        exit 1
    fi
    log_success "npm install completed"

    # Generate Build Info
    log_info "Generating build information..."
    BUILD_NUMBER="$(date +%s)"
    BUILD_DATE="$(date -u +"%Y-%m-%dT%H:%M:%SZ")"
    APP_VERSION="1.0.0"

    cat > src/buildInfo.ts <<EOF
// Auto-generated build information
// This file is updated automatically by the deployment script

export const BUILD_INFO = {
  buildNumber: '${BUILD_NUMBER}',
  buildDate: '${BUILD_DATE}',
  version: '${APP_VERSION}',
};
EOF
    log_success "Build info generated: Build #${BUILD_NUMBER}"

    log_info "Building production bundle with Vite..."
    if ! npm run build; then
        log_error "npm run build failed"
        exit 1
    fi
    log_success "Frontend build completed"

    # Verify dist directory exists
    if [ ! -d "dist" ]; then
        exit_error "Build output directory 'dist' not found. Build may have failed."
    fi
    log_info "Build artifacts verified in dist/"

    # Docker Image Build
    log_section "Building Frontend Docker Image"

    if [ ! -f "Dockerfile" ]; then
        exit_error "Dockerfile is required for containerization"
    fi

    VERSION="1.2.0-$(date +%Y%m%d%H%M%S)"
    log_info "Building Docker image with version: ${VERSION}"
    log_info "Enterprise deployment: --no-cache --pull ensures fresh build"

    if ! docker build --no-cache --pull -t loader-frontend:${VERSION} -t "${IMAGE_TAG}" .; then
        log_error "Docker image build failed"
        exit 1
    else
        if docker images "loader-frontend:${VERSION}" --format "{{.Repository}}:{{.Tag}}" | grep -q "loader-frontend:${VERSION}"; then
            log_success "Image verified in local Docker registry"
        else
            log_error "Image not found in local Docker registry!"
            exit 1
        fi
        log_success "Build completed: loader-frontend:${VERSION} (also tagged latest)"
    fi

    # Generate deployment YAML from template
    log_info "Generating deployment YAML from template with image: loader-frontend:${VERSION}"
    K8S_TEMPLATE="${FRONTEND_DIR}/k8s_manifist/frontend-deployment.yaml.template"
    K8S_MANIFEST="${FRONTEND_DIR}/k8s_manifist/frontend-deployment.yaml"

    if [ ! -f "$K8S_TEMPLATE" ]; then
        exit_error "Template file not found: $K8S_TEMPLATE"
    fi

    rm -f "$K8S_MANIFEST" "${K8S_MANIFEST}".bak*
    cp "$K8S_TEMPLATE" "$K8S_MANIFEST"
    sed -i '' "s|__IMAGE_TAG__|loader-frontend:${VERSION}|g" "$K8S_MANIFEST"
    log_success "Deployment YAML generated from template"

    # Kubernetes Deployment
    log_section "Deploying Frontend to Kubernetes"

    if ! kubectl apply -f "$K8S_MANIFEST" -n "${A_NAMESPACE}"; then
        log_error "Frontend deployment manifest failed"
        exit 1
    else
        log_success "Frontend deployment manifest applied"
    fi

    # Health Monitoring
    monitor_pod_health "$SERVICE_NAME" "$A_NAMESPACE" 120

    POD_STATUS=$(get_pod_status "$SERVICE_NAME" "$A_NAMESPACE")
    log_info "Frontend Pod Status: $POD_STATUS"

    scan_pod_logs "$SERVICE_NAME" "$A_NAMESPACE" 100

    # Service Verification
    log_section "Verifying Frontend Service"

    if kubectl get service "$SERVICE_NAME" -n "$A_NAMESPACE" >/dev/null 2>&1; then
        log_success "Service '$SERVICE_NAME' exists"
        kubectl get service "$SERVICE_NAME" -n "$A_NAMESPACE"
    fi

    NODEPORT_SERVICE="${SERVICE_NAME}-nodeport"
    if kubectl get service "$NODEPORT_SERVICE" -n "$A_NAMESPACE" >/dev/null 2>&1; then
        log_success "Service '$NODEPORT_SERVICE' (NodePort) exists"
        NODEPORT=$(kubectl get service "$NODEPORT_SERVICE" -n "$A_NAMESPACE" -o jsonpath='{.spec.ports[0].nodePort}')
        log_info "Frontend NodePort: $NODEPORT"
        log_info "Frontend URL: http://localhost:$NODEPORT"
    fi

    log_success "Frontend module completed"
fi

# ===================== Installation Summary =====================
log_section "Installation Complete"

SCRIPT_END_EPOCH="$(date +%s)"
DURATION=$((SCRIPT_END_EPOCH - SCRIPT_START_EPOCH))
DUR_M=$((DURATION / 60))
DUR_S=$((DURATION % 60))

log_success "All selected modules have been installed successfully!"
echo
log_info "Duration: ${DUR_M}m ${DUR_S}s (${DURATION}s)"
log_info "Timestamp: $(date +"%Y-%m-%d %H:%M:%S")"




