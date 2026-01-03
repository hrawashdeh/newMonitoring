#!/bin/bash
set -euo pipefail

###############################################################################
# Variables – must match the install script
###############################################################################
NAMESPACE="loader-infra"
POSTGRES_RELEASE="postgres"

SEALED_NS="sealed-secrets"
SEALED_RELEASE="sealed-secrets"

###############################################################################
# Helper
###############################################################################
run() {
  echo
  echo ">>> $*"
  eval "$@"
}

echo "This script will:"
echo "  - Uninstall Helm release '${POSTGRES_RELEASE}' in namespace '${NAMESPACE}' (if exists)"
echo "  - Delete namespace '${NAMESPACE}'"
echo "  - Uninstall Sealed Secrets controller '${SEALED_RELEASE}' in '${SEALED_NS}' (optional section)"
echo "  - Delete local YAML files: postgres-auth-plain.yaml, postgres-auth-sealed.yaml,"
echo "    values-postgresql.yaml, postgres-nodeport.yaml, local-path-storage.yaml (if present)"
echo

###############################################################################
# 1. Uninstall PostgreSQL Helm release (if exists)
###############################################################################
if helm status "${POSTGRES_RELEASE}" -n "${NAMESPACE}" >/dev/null 2>&1; then
  run "helm uninstall ${POSTGRES_RELEASE} -n ${NAMESPACE}"
else
  echo "Helm release '${POSTGRES_RELEASE}' not found in namespace '${NAMESPACE}', skipping."
fi

###############################################################################
# 2. Delete application namespace (loader-infra)
###############################################################################
run "kubectl delete namespace ${NAMESPACE} --ignore-not-found"

###############################################################################
# 3. OPTIONAL: Uninstall Sealed Secrets controller and namespace
#    Comment this block out if you want to keep Sealed Secrets for other uses.
###############################################################################
if helm status "${SEALED_RELEASE}" -n "${SEALED_NS}" >/dev/null 2>&1; then
  run "helm uninstall ${SEALED_RELEASE} -n ${SEALED_NS}"
else
  echo "Helm release '${SEALED_RELEASE}' not found in namespace '${SEALED_NS}', skipping."
fi

run "kubectl delete namespace ${SEALED_NS} --ignore-not-found"

# If you also want to remove the CRD, uncomment:
# run 'kubectl delete crd sealedsecrets.bitnami.com --ignore-not-found'

###############################################################################
# 4. OPTIONAL: Remove local-path provisioner (only if you are sure!)
#    If you used this cluster ONLY for your tests, you can remove it.
#    Otherwise, better leave it as-is.
###############################################################################
if [ -f "local-path-storage.yaml" ]; then
  echo
  echo "Removing local-path-storage objects from file local-path-storage.yaml"
  run "kubectl delete -f local-path-storage.yaml --ignore-not-found"
else
  echo "local-path-storage.yaml not found in current directory, skipping local-path cleanup."
fi

###############################################################################
# 5. Delete local YAML files created by the install script
###############################################################################
run "rm -f postgres-auth-plain.yaml postgres-auth-sealed.yaml values-postgresql.yaml postgres-nodeport.yaml"

echo
echo "======================================================================"
echo "Cleanup completed."
echo
echo "To rebuild everything:"
echo "  1) Run your install script again (the one that installs:"
echo "     - Sealed Secrets controller"
echo "     - postgres-auth sealed secret"
echo "     - Bitnami PostgreSQL"
echo "     - NodePort service on 30432"
echo "  2) Verify with:"
echo "       kubectl get pods -n ${NAMESPACE}"
echo "       kubectl get svc  -n ${NAMESPACE}"
echo "======================================================================"
