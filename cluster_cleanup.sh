#!/bin/bash

# ===================== Colors =====================
BLUE='\033[0;34m'
YELLOW='\033[1;33m'
GREEN='\033[0;32m'
RED='\033[0;31m'
PURPLE='\033[0;35m'
NC='\033[0m'   # No Color

# ===================== Log Helpers =====================
log_info()  { printf "${BLUE}[INFO]${NC} %s\n" "$*"; }
log_warn()  { printf "${YELLOW}[WARN]${NC} %s\n" "$*"; }
log_error() { printf "${RED}[ERROR]${NC} %s\n" "$*"; }
log_section() { printf "\n${PURPLE}=== %s ===${NC}\n\n" "$*"; }
log_success() { printf "${GREEN}[SUCCESS]${NC} %s\n" "$*"; }

# ===================== Configuration =====================
CONTEXT="docker-desktop"
PV_DATA_PATHS=(
  "/var/lib/k8s-pvs"
  "/opt/local-path-provisioner"
)

# ===================== Confirmation =====================
log_section "Cluster Cleanup Script"
log_warn "This script will:"
log_warn "  1. Reset the Kubernetes cluster (delete all resources)"
log_warn "  2. Remove leftover PV data directories"
log_warn "  3. Prune unused Docker images, containers, and volumes"
log_warn "  4. Free up disk space on your development machine"
echo ""
log_warn "Current context: ${CONTEXT}"
echo ""

read -p "$(printf "${YELLOW}Are you sure you want to proceed? (yes/no): ${NC}")" CONFIRM
if [[ "$CONFIRM" != "yes" ]]; then
  log_error "Cleanup aborted by user"
  exit 1
fi

# ===================== Verify Context =====================
log_section "Verifying Kubernetes Context"
CURRENT_CONTEXT=$(kubectl config current-context)
log_info "Current context: ${CURRENT_CONTEXT}"

if [[ "$CURRENT_CONTEXT" != "$CONTEXT" ]]; then
  log_error "Context mismatch! Expected '${CONTEXT}' but got '${CURRENT_CONTEXT}'"
  read -p "$(printf "${YELLOW}Do you want to switch to ${CONTEXT}? (yes/no): ${NC}")" SWITCH
  if [[ "$SWITCH" == "yes" ]]; then
    kubectl config use-context "${CONTEXT}" || {
      log_error "Failed to switch context"
      exit 1
    }
  else
    log_error "Cleanup aborted"
    exit 1
  fi
fi

# ===================== Delete All Kubernetes Resources =====================
log_section "Deleting All Kubernetes Resources"

# Get all namespaces except system ones
NAMESPACES=$(kubectl get namespaces -o jsonpath='{.items[*].metadata.name}' | tr ' ' '\n' | grep -v '^kube-' | grep -v '^default$')

log_info "Found namespaces to clean: ${NAMESPACES}"

for ns in $NAMESPACES; do
  log_info "Deleting all resources in namespace: ${ns}"

  # Delete helm releases first
  RELEASES=$(helm list -n "$ns" -q 2>/dev/null)
  if [[ -n "$RELEASES" ]]; then
    log_info "Uninstalling Helm releases in ${ns}: ${RELEASES}"
    echo "$RELEASES" | xargs -n1 helm uninstall -n "$ns" 2>&1 | grep -v "not found" || true
  fi

  # Delete all resources in namespace
  kubectl delete all --all -n "$ns" --grace-period=0 --force 2>&1 | grep -v "not found" || true

  # Delete PVCs (important for cleaning up PVs)
  kubectl delete pvc --all -n "$ns" --grace-period=0 --force 2>&1 | grep -v "not found" || true

  # Delete secrets and configmaps
  kubectl delete secret,configmap --all -n "$ns" 2>&1 | grep -v "not found" || true

  # Delete the namespace itself
  log_info "Deleting namespace: ${ns}"
  kubectl delete namespace "$ns" --grace-period=0 --force 2>&1 | grep -v "not found" || true
done

# ===================== Clean up cluster-wide resources =====================
log_section "Cleaning Cluster-Wide Resources"

log_info "Deleting all PVs"
kubectl delete pv --all --grace-period=0 --force 2>&1 | grep -v "not found" || true

log_info "Deleting all StorageClasses (except defaults)"
kubectl get storageclass -o name | grep -v "hostpath" | xargs -r kubectl delete 2>&1 | grep -v "not found" || true

log_info "Deleting CRDs from Helm charts"
kubectl get crd -o name | grep -E "(bitnami|prometheus|grafana|alertmanager)" | xargs -r kubectl delete 2>&1 | grep -v "not found" || true

# ===================== Wait for finalizers =====================
log_section "Waiting for Resources to Terminate"
log_info "Waiting up to 60 seconds for namespaces to be deleted..."

for i in {1..60}; do
  REMAINING=$(kubectl get namespaces -o jsonpath='{.items[*].metadata.name}' | tr ' ' '\n' | grep -v '^kube-' | grep -v '^default$' | wc -l)
  if [[ "$REMAINING" -eq 0 ]]; then
    log_success "All namespaces deleted"
    break
  fi
  printf "."
  sleep 1
done
echo ""

# Force remove stuck namespaces
STUCK_NS=$(kubectl get namespaces -o jsonpath='{.items[*].metadata.name}' | tr ' ' '\n' | grep -v '^kube-' | grep -v '^default$')
if [[ -n "$STUCK_NS" ]]; then
  log_warn "Some namespaces are stuck, attempting to force remove..."
  for ns in $STUCK_NS; do
    kubectl get namespace "$ns" -o json | jq '.spec.finalizers = []' | kubectl replace --raw "/api/v1/namespaces/$ns/finalize" -f - 2>&1 || true
  done
fi

# ===================== Clean Docker Desktop PV Data =====================
log_section "Cleaning Persistent Volume Data"

log_info "Accessing Docker Desktop VM to clean PV directories..."

for pv_path in "${PV_DATA_PATHS[@]}"; do
  log_info "Cleaning: ${pv_path}"

  # For Docker Desktop on macOS, we need to access the VM
  docker run --rm --privileged --pid=host alpine nsenter -t 1 -m -u -n -i sh -c "
    if [ -d '${pv_path}' ]; then
      echo 'Directory ${pv_path} exists, removing...'
      rm -rf '${pv_path}'/*
      echo 'Cleaned ${pv_path}'
    else
      echo 'Directory ${pv_path} does not exist'
    fi
  " 2>&1
done

# ===================== Docker Cleanup =====================
log_section "Docker Cleanup"

log_info "Stopping and removing all containers..."
docker ps -aq | xargs -r docker stop 2>&1 || true
docker ps -aq | xargs -r docker rm -f 2>&1 || true

log_info "Removing unused images..."
docker image prune -af 2>&1 | grep -E "(deleted|freed)" || log_info "No images to remove"

log_info "Removing unused volumes..."
docker volume prune -f 2>&1 | grep -E "(deleted|freed)" || log_info "No volumes to remove"

log_info "Removing unused networks..."
docker network prune -f 2>&1 | grep -E "(deleted)" || log_info "No networks to remove"

log_info "Running system prune..."
docker system prune -af --volumes 2>&1 | grep -E "(deleted|freed)" || log_info "Nothing to prune"

# ===================== Disk Space Report =====================
log_section "Disk Space Report"

log_info "Docker disk usage:"
docker system df

log_info "Docker Desktop VM disk usage:"
docker run --rm --privileged --pid=host alpine nsenter -t 1 -m -u -n -i df -h / 2>&1 | grep -v "Filesystem"

# ===================== Summary =====================
log_section "Cleanup Complete"

log_success "Kubernetes cluster has been reset"
log_success "Persistent volume data has been cleaned"
log_success "Docker resources have been pruned"
log_success "Your development machine is ready for a fresh installation"

echo ""
log_info "You can now run './infra_installer.sh' to set up infrastructure"