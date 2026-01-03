#!/bin/bash
# Configure 24-hour retention for logs and traces in Elasticsearch

set -e

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
RED='\033[0;31m'
NC='\033[0m'

log_info()  { printf "${BLUE}[INFO]${NC} %s\n" "$*"; }
log_success() { printf "${GREEN}[SUCCESS]${NC} %s\n" "$*"; }
log_error() { printf "${RED}[ERROR]${NC} %s\n" "$*"; }

ES_URL="http://monitoring-es-es-http.monitoring-infra.svc.cluster.local:9200"

log_info "Waiting for Elasticsearch secret to be created..."
kubectl wait --for=condition=Ready secret/monitoring-es-es-elastic-user \
  -n monitoring-infra --timeout=180s

ES_PASSWORD=$(kubectl get secret monitoring-es-es-elastic-user \
  -n monitoring-infra \
  -o jsonpath='{.data.elastic}' | base64 -d)

log_info "Waiting for Elasticsearch to be ready..."
for i in {1..60}; do
  if curl -k -s -u "elastic:$ES_PASSWORD" "$ES_URL/_cluster/health" | grep -q '"status":"green\|yellow"'; then
    log_success "Elasticsearch is ready"
    break
  fi
  if [ $i -eq 60 ]; then
    log_error "Elasticsearch did not become ready in time"
    exit 1
  fi
  echo -n "."
  sleep 5
done

# Create ILM policy for logs (24h retention)
log_info "Creating ILM policy for logs (24h retention)..."
curl -k -X PUT "$ES_URL/_ilm/policy/logs-24h-policy" \
  -u "elastic:$ES_PASSWORD" \
  -H 'Content-Type: application/json' \
  -d '{
  "policy": {
    "phases": {
      "hot": {
        "min_age": "0ms",
        "actions": {
          "rollover": {
            "max_age": "1h",
            "max_size": "1GB"
          }
        }
      },
      "delete": {
        "min_age": "24h",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}'

log_success "Logs ILM policy created"

# Create ILM policy for traces (24h retention)
log_info "Creating ILM policy for traces (24h retention)..."
curl -k -X PUT "$ES_URL/_ilm/policy/traces-24h-policy" \
  -u "elastic:$ES_PASSWORD" \
  -H 'Content-Type: application/json' \
  -d '{
  "policy": {
    "phases": {
      "hot": {
        "min_age": "0ms",
        "actions": {
          "rollover": {
            "max_age": "1h",
            "max_size": "5GB"
          }
        }
      },
      "delete": {
        "min_age": "24h",
        "actions": {
          "delete": {}
        }
      }
    }
  }
}'

log_success "Traces ILM policy created"

# Apply policy to logs-* index template
log_info "Applying ILM policy to logs index template..."
curl -k -X PUT "$ES_URL/_index_template/logs-template" \
  -u "elastic:$ES_PASSWORD" \
  -H 'Content-Type: application/json' \
  -d '{
  "index_patterns": ["logs-*"],
  "template": {
    "settings": {
      "index.lifecycle.name": "logs-24h-policy",
      "index.lifecycle.rollover_alias": "logs"
    }
  }
}'

log_success "Logs index template configured"

# Apply policy to traces-* index template
log_info "Applying ILM policy to traces index template..."
curl -k -X PUT "$ES_URL/_index_template/traces-template" \
  -u "elastic:$ES_PASSWORD" \
  -H 'Content-Type: application/json' \
  -d '{
  "index_patterns": ["traces-*"],
  "template": {
    "settings": {
      "index.lifecycle.name": "traces-24h-policy",
      "index.lifecycle.rollover_alias": "traces"
    }
  }
}'

log_success "Traces index template configured"

log_success "ILM policies configured successfully"
log_info "Logs and traces will be automatically deleted after 24 hours"
