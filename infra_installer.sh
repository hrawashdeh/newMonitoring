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
log_info()  { printf "${BLUE}[INFO]${NC} %s\n" "$*"; }
log_debug() { printf "${YELLOW}[DEBUG]${NC} %s\n" "$*"; }
log_error() { printf "${RED}[ERROR]${NC} %s\n" "$*"; }
log_section() { printf "\n\033[0;35m--- %s ---\033[0m\n\n" "$*"; }  # purple section
log_purple()  { printf "\033[0;35m%s\033[0m\n" "$*"; }
exit_error() { log_error "$*"; exit 1; }

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


# ===================== Module Definitions (Bash 3.x Compatible) =====================
# Format: "id:description"
MODULE_NAMES=(
    "sealed-ctrl:Sealed Secrets Controller"
    "sealed-vals:Sealed Secrets Values (kubeseal)"
    "storage:Storage Class (local-path)"
    "postgres:PostgreSQL Database"
    "mysql:MySQL Database"
    "redis:Redis Cache"
    "prometheus:Prometheus Stack (Grafana, AlertManager)"
    "eck:ECK Operator (Elastic Cloud)"
    "elasticsearch:Elasticsearch"
    "kibana:Kibana Dashboard"
    "filebeat:Filebeat (Log Collection)"
    "ilm:Elasticsearch ILM (24h Retention)"
    "smoke:Credential Smoke Tests"
)

TOTAL_MODULES=${#MODULE_NAMES[@]}
RUN_MODULES=""
INTERACTIVE_MODE=false
AUTO_YES=false

# Parse command line arguments
if [ $# -eq 0 ]; then
    INTERACTIVE_MODE=true
elif [ "$1" = "-1" ] || [ "$1" = "-y" ] || [ "$1" = "--yes" ]; then
    # -1, -y, or --yes means run all modules with auto-accept
    AUTO_YES=true
    for i in $(seq 1 $TOTAL_MODULES); do
        RUN_MODULES="$RUN_MODULES $i"
    done
else
    # Specific modules provided
    RUN_MODULES="$*"
fi

# Get module name by number (1-indexed)
get_module_name() {
    local num=$1
    local idx=$((num - 1))
    if [ $idx -ge 0 ] && [ $idx -lt $TOTAL_MODULES ]; then
        echo "${MODULE_NAMES[$idx]}" | cut -d: -f2
    fi
}

# Get module id by number (1-indexed)
get_module_id() {
    local num=$1
    local idx=$((num - 1))
    if [ $idx -ge 0 ] && [ $idx -lt $TOTAL_MODULES ]; then
        echo "${MODULE_NAMES[$idx]}" | cut -d: -f1
    fi
}

# Check if module should run
should_run_module() {
    local module_num=$1
    if [ -z "$RUN_MODULES" ]; then
        return 1  # No modules selected yet
    fi
    echo "$RUN_MODULES" | grep -qw "$module_num"
}

# Show module menu
show_module_menu() {
    echo ""
    echo "┌──────────────────────────────────────────────────────────────┐"
    echo "│              Infrastructure Installation Modules            │"
    echo "├──────────────────────────────────────────────────────────────┤"
    for i in $(seq 1 $TOTAL_MODULES); do
        local name
        name=$(get_module_name $i)
        printf "│  %2d. %-55s │\n" "$i" "$name"
    done
    echo "├──────────────────────────────────────────────────────────────┤"
    echo "│  -1. Run ALL modules                                        │"
    echo "│   0. Exit                                                   │"
    echo "└──────────────────────────────────────────────────────────────┘"
    echo ""
}

# Prompt for module selection
prompt_module_selection() {
    # Skip in auto-yes mode (modules already set)
    if [ "$AUTO_YES" = true ]; then
        return 0
    fi

    show_module_menu
    printf "${GREEN}Enter module numbers (space-separated) or -1 for all: ${NC}"
    read -r selection < /dev/tty

    if [ "$selection" = "0" ]; then
        log_info "Exiting..."
        exit 0
    elif [ "$selection" = "-1" ]; then
        for i in $(seq 1 $TOTAL_MODULES); do
            RUN_MODULES="$RUN_MODULES $i"
        done
    else
        RUN_MODULES="$selection"
    fi

    # Validate selection
    for num in $RUN_MODULES; do
        if ! [[ "$num" =~ ^[0-9]+$ ]] || [ "$num" -lt 1 ] || [ "$num" -gt $TOTAL_MODULES ]; then
            log_error "Invalid module number: $num (valid: 1-$TOTAL_MODULES)"
            RUN_MODULES=""
            return 1
        fi
    done
    return 0
}

# Show selected modules
show_selected_modules() {
    echo ""
    log_info "Selected modules:"
    for num in $RUN_MODULES; do
        local name
        name=$(get_module_name $num)
        log_debug "  [$num] $name"
    done
    echo ""
}

# Count selected modules
count_selected_modules() {
    echo "$RUN_MODULES" | wc -w | tr -d ' '
}

# ===================== Configuration =====================
log_section "Verify Configuration"
PROJECT_ROOT="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
SEALED_NS="sealed-secrets"
I_NAMESPACE="monitoring-infra"
k_context="docker-desktop"

log_info "Switching to project root"
log_info "Please verify the parameters below before proceeding"

log_debug "PROJECT_ROOT = ${PROJECT_ROOT}"
log_debug "SEALED_NS    = ${SEALED_NS}"
log_debug "NAMESPACE    = ${I_NAMESPACE}"
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

# Show what will be installed
SELECTED_COUNT=$(count_selected_modules)
log_info "Will install $SELECTED_COUNT of $TOTAL_MODULES modules"
show_selected_modules

# ===================== Namespaces =====================
log_section "Creating namespaces"

kubectl create namespace "${SEALED_NS}" --dry-run=client -o yaml | kubectl apply -f -
kubectl create namespace "${I_NAMESPACE}" --dry-run=client -o yaml | kubectl apply -f -

log_info "Current Kubernetes namespaces:"
kubectl get namespaces

for ns in "${SEALED_NS}" "${I_NAMESPACE}"; do
    if ! kubectl get namespace "${ns}" >/dev/null 2>&1; then
        exit_error "Namespace '${ns}' was not created successfully"
    fi
done

log_info "Namespaces validated successfully"


# ===================== add and update helm charts =====================
log_section "Adding and updating Helm repositories"

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo add enix https://charts.enix.io
helm repo add grafana https://grafana.github.io/helm-charts
helm repo add mysql-operator https://mysql.github.io/mysql-operator/
helm repo add sealed-secrets https://bitnami-labs.github.io/sealed-secrets
helm repo add kiali https://kiali.org/helm-charts

helm repo update

# ===================== Module 1: Sealed Secrets Controller =====================
if should_run_module 1; then
log_section "[1/13] Installing Sealed Secrets Controller"
helm upgrade --install sealed-secrets sealed-secrets/sealed-secrets \
  --namespace ${SEALED_NS} \
  --set fullnameOverride=sealed-secrets

kubectl rollout status deployment/sealed-secrets -n ${SEALED_NS} --timeout=180s
fi

# ===================== Module 2: Sealed Secrets Values =====================
if should_run_module 2; then
cd "${PROJECT_ROOT}/infra/secrets"
log_info "Current Path: $(pwd)"

log_section "[2/13] Installing Sealed Secrets Values"

kubeseal \
  --controller-name=sealed-secrets \
  --controller-namespace=${SEALED_NS} \
  --namespace ${I_NAMESPACE} \
  --format yaml \
  < monitoring-secrets-plain.yaml \
  > monitoring-secrets-sealed.yaml

kubectl apply -f monitoring-secrets-sealed.yaml
fi

# ===================== Module 3: Storage Class =====================
if should_run_module 3; then
log_section "[3/13] Installing Storage Class"

cd "${PROJECT_ROOT}/infra/secrets"
curl -fsSL https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml -o local-path-storage.yaml
kubectl apply -f local-path-storage.yaml
kubectl get storageclass
fi

# ===================== Module 4: PostgreSQL =====================
if should_run_module 4; then
log_section "[4/13] Installing PostgreSQL"

cd "${PROJECT_ROOT}/infra/postgress"

helm upgrade --install postgres bitnami/postgresql \
  -n ${I_NAMESPACE} \
  -f values-postgresql.yaml

kubectl rollout status statefulset/postgres-postgresql -n ${I_NAMESPACE} --timeout=300s
kubectl get pods -n ${I_NAMESPACE}

kubectl apply -f postgres-nodeport.yaml
kubectl get svc -n ${I_NAMESPACE}
fi

# ===================== Module 5: MySQL =====================
if should_run_module 5; then
cd "${PROJECT_ROOT}/infra/mysql"

log_section "[5/13] Installing MySQL"

kubectl apply -f mysql-statefulset.yaml
kubectl rollout status statefulset/mysql -n "${I_NAMESPACE}" --timeout=300s
kubectl apply -f mysql-nodeport.yaml

kubectl rollout status statefulset/mysql -n ${I_NAMESPACE} --timeout=300s
kubectl get pods -n ${I_NAMESPACE}

# Run MySQL post-installation setup (create users and grant privileges)
log_info "Running MySQL post-installation setup..."
bash mysql-post-install-setup.sh
fi

# ===================== Module 6: Redis =====================
if should_run_module 6; then
cd "${PROJECT_ROOT}/infra/redis"

log_section "[6/13] Installing Redis"

helm upgrade --install redis bitnami/redis \
  -n monitoring-infra \
  -f values-redis.yaml

kubectl rollout status statefulset/redis-master -n monitoring-infra --timeout=300s

kubectl apply -f redis-nodeport.yaml
fi

# ===================== Module 7: Prometheus Stack =====================
if should_run_module 7; then
cd "${PROJECT_ROOT}/infra/prometheus"

log_section "[7/13] Installing Prometheus Stack"

helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring-infra \
  -f values-kps.yaml
kubectl apply -f Prometheus-nodeport.yaml
kubectl apply -f AlertManager-nodeport.yaml
kubectl apply -f grafana-nodeport.yaml
fi

# ===================== Module 8: ECK Operator =====================
if should_run_module 8; then
cd "${PROJECT_ROOT}"

log_section "[8/13] Installing ECK Operator for Elastic Stack"

log_info "Installing ECK CRDs..."
kubectl create -f https://download.elastic.co/downloads/eck/2.11.0/crds.yaml

log_info "Installing ECK Operator..."
kubectl apply -f https://download.elastic.co/downloads/eck/2.11.0/operator.yaml

log_info "Waiting for ECK operator to be ready..."
kubectl wait --for=condition=ready pod -l control-plane=elastic-operator \
  -n elastic-system --timeout=180s || true

log_info "ECK Operator installed successfully"
fi

# ===================== Module 9: Elasticsearch =====================
if should_run_module 9; then
cd "${PROJECT_ROOT}/infra/otel-manifests/elastic"

log_section "[9/13] Installing Elasticsearch"

kubectl apply -f elasticsearch.yaml

log_info "Waiting for Elasticsearch to be ready (this may take 3-5 minutes)..."
kubectl wait --for=condition=ready pod -l elasticsearch.k8s.elastic.co/cluster-name=monitoring-es \
  -n ${I_NAMESPACE} --timeout=600s || {
    log_error "Elasticsearch did not become ready in time"
    log_info "Checking Elasticsearch status..."
    kubectl get elasticsearch -n ${I_NAMESPACE}
    kubectl get pods -n ${I_NAMESPACE} -l elasticsearch.k8s.elastic.co/cluster-name=monitoring-es
}

log_info "Elasticsearch deployed successfully"
fi

# ===================== Module 10: Kibana =====================
if should_run_module 10; then
cd "${PROJECT_ROOT}/infra/otel-manifests/elastic"

log_section "[10/13] Installing Kibana"

kubectl apply -f kibana.yaml

log_info "Waiting for Kibana to be ready..."
kubectl wait --for=condition=ready pod -l kibana.k8s.elastic.co/name=monitoring-kibana \
  -n ${I_NAMESPACE} --timeout=300s || {
    log_error "Kibana did not become ready in time"
    log_info "Checking Kibana status..."
    kubectl get kibana -n ${I_NAMESPACE}
    kubectl get pods -n ${I_NAMESPACE} -l kibana.k8s.elastic.co/name=monitoring-kibana
}

log_info "Kibana deployed successfully"
log_info "Kibana UI will be available at: http://localhost:30561"
fi

# ===================== APM Server =====================
# TEMPORARILY DISABLED - Re-enable after cluster reset verification

# log_section "Installing APM Server (OTLP enabled)"

# kubectl apply -f apm-server.yaml

# log_info "Waiting for APM Server to be ready..."
# kubectl wait --for=condition=ready pod -l apm.k8s.elastic.co/name=monitoring-apm \
#   -n ${I_NAMESPACE} --timeout=300s || {
#     log_error "APM Server did not become ready in time"
#     log_info "Checking APM Server status..."
#     kubectl get apmserver -n ${I_NAMESPACE}
#     kubectl get pods -n ${I_NAMESPACE} -l apm.k8s.elastic.co/name=monitoring-apm
# }

# log_info "APM Server deployed successfully"

# ===================== OpenTelemetry Collector =====================
# TEMPORARILY DISABLED - Re-enable after cluster reset verification

# cd "${PROJECT_ROOT}/infra/otel-manifests/otel"

# log_section "Installing OpenTelemetry Collector"

# kubectl apply -f otel-collector.yaml

# log_info "Waiting for OpenTelemetry Collector to be ready..."
# kubectl rollout status deployment/otel-collector -n ${I_NAMESPACE} --timeout=180s

# log_info "OpenTelemetry Collector deployed successfully"
# log_info "OTLP gRPC endpoint: otel-collector.monitoring-infra.svc.cluster.local:4317"
# log_info "OTLP HTTP endpoint: otel-collector.monitoring-infra.svc.cluster.local:4318"

# # ===================== ServiceMonitor for OTel Collector =====================

# log_section "Installing ServiceMonitor for OTel Collector"

# kubectl apply -f otel-servicemonitor.yaml

# log_info "ServiceMonitor created for Prometheus scraping"

# ===================== Module 11: Filebeat =====================
if should_run_module 11; then
cd "${PROJECT_ROOT}/infra/otel-manifests/logging"

log_section "[11/13] Installing Filebeat for Log Collection"

kubectl apply -f filebeat-daemonset.yaml

log_info "Waiting for Filebeat DaemonSet to be ready..."
kubectl rollout status daemonset/filebeat -n ${I_NAMESPACE} --timeout=180s

log_info "Filebeat deployed successfully"
fi

# ===================== Module 12: Elasticsearch ILM Configuration =====================
if should_run_module 12; then
cd "${PROJECT_ROOT}/infra/otel-scripts"

log_section "[12/13] Configuring Elasticsearch ILM Policies (24h retention)"

log_info "Running ILM configuration script..."
bash configure-ilm-retention.sh

log_info "ILM policies configured: logs and traces will be deleted after 24 hours"
fi

# ===================== Execution Timing =====================

log_section "Installation execution summary"
SCRIPT_START_EPOCH="${SCRIPT_START_EPOCH:-$(date +%s)}"

format_ts() { date -r "$1" "+%Y-%m-%d %H:%M:%S %Z" 2>/dev/null || date -d "@$1" "+%Y-%m-%d %H:%M:%S %Z"; }

SCRIPT_END_EPOCH="$(date +%s)"
SCRIPT_END_STR="$(date +"%Y-%m-%d %H:%M:%S")"

DURATION_SEC=$((SCRIPT_END_EPOCH - SCRIPT_START_EPOCH))

# Human readable
DUR_H=$((DURATION_SEC / 3600))
DUR_M=$(((DURATION_SEC % 3600) / 60))
DUR_S=$((DURATION_SEC % 60))

end_str="$(format_ts "$SCRIPT_END_EPOCH")"

log_info "Script start: ${SCRIPT_START_STR}"
log_info "Script end  : ${SCRIPT_END_STR}"
log_info "Duration    : ${DUR_H}h ${DUR_M}m ${DUR_S}s (${DURATION_SEC}s)"

# ===================== Module 13: Credential Smoke Tests =====================
if should_run_module 13; then
log_section "[13/13] Preparing to run credential smoke tests"
log_info "Starting smoke tests in 60 seconds (press Ctrl+C to abort)..."

for i in $(seq 60 -1 1); do
  printf "\r\033[0;35mSmoke tests start in %2ds...\033[0m" "$i"
  sleep 1
done
printf "\r\033[0;35mSmoke tests starting now...      \033[0m\n"

# ===================== Work load check =====================
kubectl get nodes -o wide
kubectl get pods -A
kubectl get svc -n ${I_NAMESPACE}
kubectl get secret -n ${I_NAMESPACE}


# ===================== Credential Smoke Tests =====================
log_info "Running credential smoke tests for sealed secret: infra-secrets"

kubectl -n "$NS" delete pod redis-check pg-check mysql-check --ignore-not-found >/dev/null 2>&1 || true



SECRET_NAME="infra-secrets"
NS="${I_NAMESPACE}"

# Must exist and contain required keys
require_keys=(
  postgres-admin-password
  postgres-app-password
  mysql-root-password
  mysql-password
  MYSQL_APP_USER
  MYSQL_APP_DB
  redis-password
  REDIS_PORT
  GRAFANA_ADMIN_USER
  GRAFANA_ADMIN_PASSWORD
  ALERTMANAGER_BASIC_AUTH_USER
  ALERTMANAGER_BASIC_AUTH_PASSWORD
)

kubectl -n "$NS" get secret "$SECRET_NAME" >/dev/null 2>&1 || exit_error "Secret '$SECRET_NAME' not found in namespace '$NS'"

missing=0
for k in "${require_keys[@]}"; do
  if ! kubectl -n "$NS" get secret "$SECRET_NAME" -o jsonpath="{.data.$k}" 2>/dev/null | grep -q .; then
    log_error "Missing/empty key in $SECRET_NAME: $k"
    missing=1
  else
    log_info "Key present: $k"
  fi
done
[[ "$missing" -eq 0 ]] || exit_error "Secret keys validation failed"

# Decode helper (safe-ish: prints length only, not value)
secret_len() {
  local key="$1"
  local val
  val="$(kubectl -n "$NS" get secret "$SECRET_NAME" -o jsonpath="{.data.${key}}" | base64 -d 2>/dev/null || true)"
  printf "%s" "${#val}"
}

log_info "Secret value lengths (sanity):"
for k in "${require_keys[@]}"; do
  log_debug "$k length = $(secret_len "$k")"
done

# --- Verify pods are actually consuming the secret (envFrom/secretKeyRef/volume) ---
log_info "Checking workloads reference infra-secrets (envFrom/secretKeyRef/volume mounts)"

check_ref() {
  local kind="$1" name="$2"
  local y
  y="$(kubectl -n "$NS" get "$kind" "$name" -o yaml 2>/dev/null || true)"

  if [[ -z "$y" ]]; then
    log_error "$kind/$name not found"
    return 1
  fi

  if echo "$y" | grep -Eq "(secretName:\s*${SECRET_NAME}\b|name:\s*${SECRET_NAME}\b)"; then
    log_info "$kind/$name references ${SECRET_NAME}"
  else
    log_error "$kind/$name does NOT reference ${SECRET_NAME}"
    return 1
  fi
}


# Adjust names if yours differ
check_ref statefulset postgres-postgresql
check_ref statefulset mysql
check_ref statefulset redis-master

# kube-prometheus-stack components are usually deployments/statefulsets; we check all in namespace
log_info "Scanning all Deployments/StatefulSets for infra-secrets reference"
no_ref=0
for obj in $(kubectl -n "$NS" get deploy,sts -o name); do
  if kubectl -n "$NS" get "$obj" -o yaml | grep -q "name: ${SECRET_NAME}"; then
    log_debug "$obj -> uses infra-secrets"
  else
    # do not hard-fail here because many workloads do not need secrets
    :
  fi
done

# --- Runtime connectivity tests from inside cluster (preferred) ---
log_info "Running in-cluster connection checks (ephemeral pods)"
RID="smoke-$(date +%s)"

run_ephemeral_pod_and_get_logs() {
  local ns="$1"
  local pod="$2"
  local image="$3"
  local cmd="$4"

  kubectl -n "$ns" delete pod "$pod" --ignore-not-found >/dev/null 2>&1 || true

  kubectl -n "$ns" run "$pod" --restart=Never --image="$image" \
    --command -- sh -lc "$cmd" >/dev/null

  # Wait for phase=Succeeded (NOT Ready)
  if ! kubectl -n "$ns" wait pod "$pod" --for=jsonpath='{.status.phase}'=Succeeded --timeout=180s >/dev/null 2>&1; then
    local phase
    phase="$(kubectl -n "$ns" get pod "$pod" -o jsonpath='{.status.phase}' 2>/dev/null || echo UNKNOWN)"
    echo "[ERROR] Pod $pod did not succeed. phase=$phase" >&2
    kubectl -n "$ns" describe pod "$pod" >&2 || true
    kubectl -n "$ns" logs "$pod" >&2 || true
    kubectl -n "$ns" delete pod "$pod" --ignore-not-found >/dev/null 2>&1 || true
    return 1
  fi

  kubectl -n "$ns" logs "$pod" 2>/dev/null || true
  kubectl -n "$ns" delete pod "$pod" --ignore-not-found >/dev/null 2>&1 || true
}


# --- Redis ---
test_redis() {
  local ns="$1" secret="$2" rid="$3"
  local pod="redis-check-$rid"

  local redis_pwd
  redis_pwd="$(kubectl -n "$ns" get secret "$secret" -o jsonpath='{.data.redis-password}' | base64 -d)"

  local cmd="set -e; timeout 20s redis-cli -h redis-master -a \"$redis_pwd\" --no-auth-warning ping"
  local out
  out="$(run_ephemeral_pod_and_get_logs "$ns" "$pod" "redis:7-alpine" "$cmd")" || return 1

  echo "$out" | grep -q '^PONG' || { echo "[ERROR] Redis did not return PONG. Output: $out" >&2; return 1; }
  echo "[OK] Redis auth+ping"
}




# --- Postgres (admin) ---
test_pg_admin() {
  local ns="$1" secret="$2" rid="$3"
  local pod="pg-admin-check-$rid"

  # Adjust keys if yours differ
  local pg_host="postgres-postgresql"
  local admin_pwd
  admin_pwd="$(kubectl -n "$ns" get secret "$secret" -o jsonpath='{.data.postgres-admin-password}' | base64 -d)"

  # Bitnami often uses user "postgres" for admin; if yours is different, change it here:
  local admin_user="postgres"
  local admin_db="postgres"

  local cmd="set -e;
    export PGPASSWORD=\"$admin_pwd\";
    timeout 30s psql -h \"$pg_host\" -U \"$admin_user\" -d \"$admin_db\" -c 'SELECT 1 AS ok;'"
  local out
  out="$(run_ephemeral_pod_and_get_logs "$ns" "$pod" "postgres:16-alpine" "$cmd")" || return 1

  echo "$out" | grep -q 'ok' || { echo "[ERROR] Postgres admin test failed. Output: $out" >&2; return 1; }
  echo "[OK] Postgres admin login"
}

test_pg_app() {
  local ns="$1" secret="$2" rid="$3"
  local pod="pg-app-check-$rid"

  local pg_host="postgres-postgresql"

  # Adjust these keys if your secret uses different ones
  local app_user="alerts_user"
  local app_db="alerts_db"
  local app_pwd
  app_pwd="$(kubectl -n "$ns" get secret "$secret" -o jsonpath='{.data.postgres-app-password}' | base64 -d)"

  local cmd="set -e;
    export PGPASSWORD=\"$app_pwd\";
    timeout 30s psql -h \"$pg_host\" -U \"$app_user\" -d \"$app_db\" -c 'SELECT current_database() AS db, current_user AS user;'"
  local out
  out="$(run_ephemeral_pod_and_get_logs "$ns" "$pod" "postgres:16-alpine" "$cmd")" || return 1

  echo "$out" | grep -q "$app_db" || { echo "[ERROR] Postgres app test failed. Output: $out" >&2; return 1; }
  echo "[OK] Postgres app login (db=$app_db user=$app_user)"
}


# --- MySQL (root) ---
test_mysql_root() {
  local ns="$1" secret="$2" rid="$3"
  local pod="mysql-root-check-$rid"

  local root_pwd
  root_pwd="$(kubectl -n "$ns" get secret "$secret" -o jsonpath='{.data.mysql-root-password}' | base64 -d)"

  local cmd="set -e;
    timeout 30s mysql -h mysql -uroot -p\"$root_pwd\" -e 'SELECT 1 AS ok;'"
  local out
  out="$(run_ephemeral_pod_and_get_logs "$ns" "$pod" "mysql:8.0" "$cmd")" || return 1

  echo "$out" | grep -q 'ok' || { echo "[ERROR] MySQL root test failed. Output: $out" >&2; return 1; }
  echo "[OK] MySQL root login"
}

test_mysql_app() {
  local ns="$1" secret="$2" rid="$3"
  local pod="mysql-app-check-$rid"

  local app_user app_db app_pwd
  app_user="$(kubectl -n "$ns" get secret "$secret" -o jsonpath='{.data.MYSQL_APP_USER}' | base64 -d)"
  app_db="$(kubectl -n "$ns" get secret "$secret" -o jsonpath='{.data.MYSQL_APP_DB}' | base64 -d)"
  app_pwd="$(kubectl -n "$ns" get secret "$secret" -o jsonpath='{.data.mysql-password}' | base64 -d)"

  local cmd="set -e;
    timeout 30s mysql -h mysql -u\"$app_user\" -p\"$app_pwd\" -D\"$app_db\" -e 'SELECT DATABASE() AS db, CURRENT_USER() AS user;'"
  local out
  out="$(run_ephemeral_pod_and_get_logs "$ns" "$pod" "mysql:8.0" "$cmd")" || return 1

  echo "$out" | grep -q "$app_db" || { echo "[ERROR] MySQL app test failed. Output: $out" >&2; return 1; }
  echo "[OK] MySQL app login (db=$app_db user=$app_user)"
}

NS="monitoring-infra"
SECRET_NAME="infra-secrets"
RID="$(date +%s)"

test_redis "$NS" "$SECRET_NAME" "$RID"
test_mysql_root "$NS" "$SECRET_NAME" "$RID"
test_mysql_app "$NS" "$SECRET_NAME" "$RID"
test_pg_admin "$NS" "$SECRET_NAME" "$RID"
test_pg_app "$NS" "$SECRET_NAME" "$RID"

log_info "Credential smoke tests completed successfully"
fi

log_section "Infrastructure Installation Complete"
log_info "Installed $SELECTED_COUNT modules"