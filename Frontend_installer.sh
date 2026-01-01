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
FRONTEND_DIR="${PROJECT_ROOT}/frontend"
A_NAMESPACE="monitoring-app"
k_context="docker-desktop"
SERVICE_NAME="loader-frontend"
IMAGE_TAG="loader-frontend:latest"

log_info "Please verify the parameters below before proceeding"

log_debug "PROJECT_ROOT = ${PROJECT_ROOT}"
log_debug "FRONTEND_DIR = ${FRONTEND_DIR}"
log_debug "NAMESPACE    = ${A_NAMESPACE}"
log_debug "k_context    = ${k_context}"
log_debug "SERVICE_NAME = ${SERVICE_NAME}"
log_debug "IMAGE_TAG    = ${IMAGE_TAG}"

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

# ===================== Namespace Verification =====================
log_section "Verifying namespace"

if ! kubectl get namespace "${A_NAMESPACE}" >/dev/null 2>&1; then
   log_warn "Namespace '${A_NAMESPACE}' does not exist. Creating..."
   kubectl create namespace "${A_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -
fi

log_success "Namespace '${A_NAMESPACE}' verified"

# ===================== Common Functions =====================

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

        # Check for warnings (skip nginx access logs)
        local warnings=$(kubectl logs "$pod" -n "$namespace" --tail="$tail_lines" 2>/dev/null | \
            grep -iE "warn|warning" | grep -v "GET\|POST\|PUT\|DELETE" || true)

        if [ -n "$warnings" ]; then
            warn_count=$((warn_count + 1))
            log_warn "Warnings found in pod logs:"
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
        [ $warn_count -gt 0 ] && log_warn "Found warnings in $warn_count pod(s)"
    fi

    return 0
}

check_http_endpoint() {
    local service=$1
    local namespace=$2
    local port=${3:-80}
    local path=${4:-/}

    log_info "Checking HTTP endpoint: $service:$port$path"

    # Port-forward in background
    local local_port=$((8000 + RANDOM % 1000))
    kubectl port-forward -n "$namespace" "svc/$service" "$local_port:$port" &>/dev/null &
    local pf_pid=$!

    # Wait for port-forward to establish
    sleep 3

    # Call HTTP endpoint
    local response=$(curl -s -w "\n%{http_code}" "http://localhost:$local_port$path" 2>/dev/null || echo "FAILED")
    local http_code=$(echo "$response" | tail -1)

    # Kill port-forward
    kill $pf_pid 2>/dev/null || true

    # Evaluate response
    if [ "$http_code" = "200" ]; then
        log_success "HTTP endpoint OK (HTTP $http_code)"
        return 0
    else
        log_error "HTTP endpoint FAILED (HTTP $http_code)"
        return 1
    fi
}

# ===================== Frontend Build =====================
cd "${FRONTEND_DIR}" || exit_error "Missing directory: ${FRONTEND_DIR}"

log_section "Building Frontend Application"

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

# ===================== Generate Build Info =====================
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

# ===================== Docker Image Build =====================
log_section "Building Docker Image"

# Check if Dockerfile exists
if [ ! -f "Dockerfile" ]; then
    log_warn "Dockerfile not found. Please ensure Dockerfile exists in ${FRONTEND_DIR}"
    exit_error "Dockerfile is required for containerization"
fi

# Enterprise-grade deployment: Generate unique version with timestamp
VERSION="1.2.0-$(date +%Y%m%d%H%M%S)"
log_info "Building Docker image with version: ${VERSION}"
log_info "Enterprise deployment: --no-cache --pull ensures fresh build (no cache issues)"

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

# Update deployment YAML with new image tag
log_info "Updating deployment YAML with new image tag: loader-frontend:${VERSION}"
K8S_MANIFEST="${FRONTEND_DIR}/k8s_manifist/frontend-deployment.yaml"
sed -i.bak "s|image: loader-frontend:.*|image: loader-frontend:${VERSION}|g" "$K8S_MANIFEST"
log_success "Deployment YAML updated"

# ===================== Kubernetes Deployment =====================
log_section "Deploying to Kubernetes"

if [ ! -f "$K8S_MANIFEST" ]; then
    exit_error "Kubernetes manifest not found: $K8S_MANIFEST"
fi

log_info "Applying Kubernetes manifest with image: loader-frontend:${VERSION}..."

if ! kubectl apply -f "$K8S_MANIFEST" -n "${A_NAMESPACE}"; then
	log_error "Deployment manifest failed"
	exit 1
else
	log_success "Deployment manifest applied with image: loader-frontend:${VERSION}"
fi

# ===================== Health Monitoring =====================
log_section "Monitoring Deployment Health"

# Monitor health
monitor_pod_health "$SERVICE_NAME" "$A_NAMESPACE" 120

# Check pod status
POD_STATUS=$(get_pod_status "$SERVICE_NAME" "$A_NAMESPACE")
log_info "Pod Status: $POD_STATUS"

# Check logs for errors
scan_pod_logs "$SERVICE_NAME" "$A_NAMESPACE" 100

# ===================== Service Verification =====================
log_section "Verifying Service"

# Check if service exists
if kubectl get service "$SERVICE_NAME" -n "$A_NAMESPACE" >/dev/null 2>&1; then
    log_success "Service '$SERVICE_NAME' exists"

    # Get service details
    log_info "Service details:"
    kubectl get service "$SERVICE_NAME" -n "$A_NAMESPACE"

    # Test HTTP endpoint
    echo
    check_http_endpoint "$SERVICE_NAME" "$A_NAMESPACE" 80 "/"
else
    log_warn "Service '$SERVICE_NAME' not found. Deployment may be using ClusterIP without service."
fi

# ===================== Completion Summary =====================
log_section "Installation Complete"

SCRIPT_END_EPOCH="$(date +%s)"
DURATION=$((SCRIPT_END_EPOCH - SCRIPT_START_EPOCH))

log_success "Frontend deployment completed successfully!"
echo
log_info "Deployment Summary:"
log_info "  • Service: ${SERVICE_NAME}"
log_info "  • Namespace: ${A_NAMESPACE}"
log_info "  • Image: ${IMAGE_TAG}"
log_info "  • Status: ${POD_STATUS}"
log_info "  • Duration: ${DURATION}s"
echo

log_info "Next steps:"
log_info "  1. Check pods: kubectl get pods -n ${A_NAMESPACE} -l app=${SERVICE_NAME}"
log_info "  2. View logs: kubectl logs -n ${A_NAMESPACE} -l app=${SERVICE_NAME} --tail=50"
log_info "  3. Port-forward: kubectl port-forward -n ${A_NAMESPACE} svc/${SERVICE_NAME} 3000:80"
log_info "  4. Access UI: http://localhost:3000"
echo

log_purple "Script started: $SCRIPT_START_STR"
log_purple "Script ended:   $(date +"%Y-%m-%d %H:%M:%S")"
log_purple "Total duration: ${DURATION}s"