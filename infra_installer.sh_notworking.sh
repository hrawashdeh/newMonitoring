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

# ===================== Sealed Secrets Controller =====================
log_section "installing Sealed Secrets Controller"
helm upgrade --install sealed-secrets sealed-secrets/sealed-secrets \
  --namespace ${SEALED_NS} \
  --set fullnameOverride=sealed-secrets

kubectl rollout status deployment/sealed-secrets -n ${SEALED_NS} --timeout=180s



# ===================== Sealed Secrets Values =====================
cd "${PROJECT_ROOT}/infra/secrets"
log_info "Current Path: $(pwd)"

log_section "installing Sealed Secrets "

kubeseal \
  --controller-name=sealed-secrets \
  --controller-namespace=${SEALED_NS} \
  --namespace ${I_NAMESPACE} \
  --format yaml \
  < monitoring-secrets-plain.yaml \
  > monitoring-secrets-sealed.yaml

kubectl apply -f monitoring-secrets-sealed.yaml


# ===================== storage class =====================

log_section "installing storage class"

curl -fsSL https://raw.githubusercontent.com/rancher/local-path-provisioner/master/deploy/local-path-storage.yaml -o local-path-storage.yaml
kubectl apply -f local-path-storage.yaml
kubectl get storageclass


# ===================== PostgresSQL =====================

log_section "installing PostgresSQL"

cd "${PROJECT_ROOT}/infra/postgress"
helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm upgrade --install postgres bitnami/postgresql \
  -n ${I_NAMESPACE} \
  -f values-postgresql.yaml


kubectl rollout status statefulset/postgres-postgresql -n ${I_NAMESPACE} --timeout=300s
kubectl get pods -n ${I_NAMESPACE}


kubectl apply -f postgres-nodeport.yaml
kubectl get svc -n ${I_NAMESPACE}


# ===================== mysql =====================
cd "${PROJECT_ROOT}/infra/mysql"

log_section "installing mysql"


kubectl apply -f mysql-statefulset.yaml
kubectl rollout status statefulset/mysql -n "${I_NAMESPACE}" --timeout=300s
kubectl apply -f mysql-nodeport.yaml

kubectl rollout status statefulset/mysql -n ${I_NAMESPACE} --timeout=300s
kubectl get pods -n ${I_NAMESPACE}

# Run MySQL post-installation setup (create users and grant privileges)
log_info "Running MySQL post-installation setup..."
bash mysql-post-install-setup.sh


# ===================== Redis =====================
cd "${PROJECT_ROOT}/infra/redis"

log_section "installing Redis"

helm repo add bitnami https://charts.bitnami.com/bitnami
helm repo update

helm upgrade --install redis bitnami/redis \
  -n monitoring-infra \
  -f values-redis.yaml


kubectl rollout status statefulset/redis-master -n monitoring-infra --timeout=300s


kubectl apply -f redis-nodeport.yaml


# ===================== prometheus =====================
cd "${PROJECT_ROOT}/infra/prometheus"

log_section "installing prometheus stack"

helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

helm upgrade --install monitoring prometheus-community/kube-prometheus-stack \
  -n monitoring-infra \
  -f values-kps.yaml
kubectl apply -f Prometheus-nodeport.yaml
kubectl apply -f AlertManager-nodeport.yaml
kubectl apply -f grafana-nodeport.yaml

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

# ===================== 60s Countdown Before Smoke Test =====================
log_section "Preparing to run credential smoke tests"
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

