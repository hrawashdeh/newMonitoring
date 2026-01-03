# Phase 1 Commercial Release - Sprint Plan
## 8-Week Implementation Roadmap

**Epic**: Phase 1 Commercial MVP Release
**Timeline**: 8 weeks (2 sprints × 4 weeks)
**Team**: 4 engineers (Backend, Frontend, DevOps, Technical Writer)
**Goal**: Launch production-ready ETL monitoring platform with 10 paying customers

---

## Sprint Overview

### Sprint 1: Production Dashboards & Infrastructure (Weeks 1-4)
**Focus**: Make monitoring visible and production-ready

**Deliverables**:
- 4 Grafana dashboards (Transaction, Log, Infrastructure, Integration)
- Prometheus metrics exporters for all services
- Elasticsearch deployment with Filebeat
- Statistics capture service (Kibana/Prometheus → Signals)

**Story Points**: 89

---

### Sprint 2: Intelligence & Integrations (Weeks 5-8)
**Focus**: Make monitoring intelligent and actionable

**Deliverables**:
- Incident detection service (4 rule types)
- Jira integration (auto-ticket creation)
- Email/Slack notifications
- Complete documentation & onboarding wizard

**Story Points**: 76

---

## SPRINT 1: Production Dashboards & Infrastructure

### EPIC-1.1: Prometheus Metrics Instrumentation

#### Ticket PLAT-101: Add Prometheus Exporters to All Services
**Type**: Technical Task
**Priority**: Critical
**Story Points**: 8
**Assignee**: Backend Developer

**Description**:
Instrument all microservices (Gateway, Auth, Loader, Import-Export) with Micrometer to expose Prometheus metrics.

**Acceptance Criteria**:
- [ ] All services expose `/actuator/prometheus` endpoint
- [ ] Custom metrics added:
  - `signals_ingested_total` (counter)
  - `signals_validation_failures_total` (counter)
  - `loader_execution_duration_seconds` (histogram)
  - `approval_workflow_transitions_total` (counter)
- [ ] HTTP request metrics include labels: `method`, `uri`, `status`, `service`
- [ ] JVM metrics exposed: memory, GC, threads
- [ ] Database connection pool metrics exposed

**Technical Specifications**:
```java
// LoaderService.java
@Service
public class LoaderService {
    private final Counter signalsIngestedCounter;
    private final Counter signalsFailedCounter;
    private final Timer loaderExecutionTimer;

    public LoaderService(MeterRegistry registry) {
        this.signalsIngestedCounter = Counter.builder("signals_ingested_total")
            .description("Total signals ingested")
            .tag("service", "loader")
            .register(registry);

        this.signalsFailedCounter = Counter.builder("signals_validation_failures_total")
            .description("Total signal validation failures")
            .tag("service", "loader")
            .register(registry);

        this.loaderExecutionTimer = Timer.builder("loader_execution_duration_seconds")
            .description("Loader execution duration")
            .tag("service", "loader")
            .register(registry);
    }

    public void ingestSignals(List<Signal> signals) {
        Timer.Sample sample = Timer.start(registry);
        try {
            // ... ingestion logic
            signalsIngestedCounter.increment(signals.size());
        } catch (ValidationException e) {
            signalsFailedCounter.increment();
            throw e;
        } finally {
            sample.stop(loaderExecutionTimer);
        }
    }
}
```

**Dependencies**: None

**Testing**:
- Verify metrics endpoint returns data: `curl http://localhost:8080/actuator/prometheus`
- Verify custom metrics appear with correct labels
- Load test: 1000 signals/sec, verify counter increments correctly

---

#### Ticket PLAT-102: Deploy Prometheus Server with ServiceMonitor
**Type**: DevOps Task
**Priority**: Critical
**Story Points**: 5
**Assignee**: DevOps Engineer

**Description**:
Deploy Prometheus Operator to Kubernetes cluster and configure ServiceMonitor resources to scrape all services.

**Acceptance Criteria**:
- [ ] Prometheus Operator installed in `monitoring-infra` namespace
- [ ] Prometheus instance deployed with 30-day retention
- [ ] ServiceMonitor resources created for all services:
  - gateway-service
  - auth-service
  - loader-service
  - import-export-service
- [ ] Prometheus UI accessible at http://prometheus.monitoring-app.local
- [ ] All targets showing as "UP" in Prometheus targets page
- [ ] Recording rules configured for common queries (P99 latency, error rate)

**Technical Specifications**:
```yaml
# prometheus-servicemonitor.yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: loader-service-monitor
  namespace: monitoring-app
spec:
  selector:
    matchLabels:
      app: loader-service
  endpoints:
    - port: actuator
      path: /actuator/prometheus
      interval: 30s
      scrapeTimeout: 10s

---
apiVersion: monitoring.coreos.com/v1
kind: PrometheusRule
metadata:
  name: etl-recording-rules
  namespace: monitoring-app
spec:
  groups:
    - name: etl_metrics
      interval: 30s
      rules:
        - record: job:http_request_duration_seconds:p99
          expr: |
            histogram_quantile(0.99,
              sum(rate(http_server_request_duration_seconds_bucket[5m])) by (le, service)
            )

        - record: job:signals_ingestion_rate:5m
          expr: |
            sum(rate(signals_ingested_total[5m])) by (loader_code)
```

**Dependencies**: PLAT-101 (metrics endpoints must exist)

**Testing**:
- Run `kubectl get servicemonitors -n monitoring-app` → All services listed
- Check Prometheus targets: All services "UP" with last scrape <30s ago
- Query test: `http_server_request_duration_seconds_count{service="loader-service"}` returns data

---

### EPIC-1.2: Elasticsearch & Logging Infrastructure

#### Ticket PLAT-103: Deploy Elasticsearch Cluster
**Type**: DevOps Task
**Priority**: Critical
**Story Points**: 8
**Assignee**: DevOps Engineer

**Description**:
Deploy Elasticsearch 8.x cluster using ECK (Elastic Cloud on Kubernetes) for centralized log storage.

**Acceptance Criteria**:
- [ ] ECK Operator installed
- [ ] Elasticsearch cluster deployed with 3 nodes
- [ ] Cluster health status: GREEN
- [ ] 30-day ILM policy configured (rollover every 1 day, delete after 30 days)
- [ ] Index templates created for `logs-*` pattern
- [ ] Kibana deployed and accessible
- [ ] Elasticsearch accessible at http://elasticsearch.monitoring-infra.svc.cluster.local:9200

**Technical Specifications**:
```yaml
# elasticsearch-cluster.yaml
apiVersion: elasticsearch.k8s.elastic.co/v1
kind: Elasticsearch
metadata:
  name: etl-monitoring-logs
  namespace: monitoring-infra
spec:
  version: 8.11.0
  nodeSets:
    - name: default
      count: 3
      config:
        node.store.allow_mmap: false
        # ILM settings
        xpack.ilm.enabled: true
      podTemplate:
        spec:
          containers:
            - name: elasticsearch
              resources:
                requests:
                  memory: 4Gi
                  cpu: 2
                limits:
                  memory: 4Gi
                  cpu: 2
          volumeClaimTemplates:
            - metadata:
                name: elasticsearch-data
              spec:
                accessModes:
                  - ReadWriteOnce
                resources:
                  requests:
                    storage: 100Gi

---
# ILM policy for 30-day retention
apiVersion: v1
kind: ConfigMap
metadata:
  name: elasticsearch-ilm-policy
  namespace: monitoring-infra
data:
  policy.json: |
    {
      "policy": {
        "phases": {
          "hot": {
            "actions": {
              "rollover": {
                "max_age": "1d",
                "max_size": "50gb"
              }
            }
          },
          "delete": {
            "min_age": "30d",
            "actions": {
              "delete": {}
            }
          }
        }
      }
    }
```

**Dependencies**: None

**Testing**:
- Check cluster health: `curl http://elasticsearch:9200/_cluster/health` → status: "green"
- Verify ILM policy: `curl http://elasticsearch:9200/_ilm/policy/logs-30day-policy`
- Test index creation: Create test index, verify it appears in Kibana

---

#### Ticket PLAT-104: Deploy Filebeat for Log Collection
**Type**: DevOps Task
**Priority**: High
**Story Points**: 5
**Assignee**: DevOps Engineer

**Description**:
Deploy Filebeat as DaemonSet to collect logs from all pods and ship to Elasticsearch.

**Acceptance Criteria**:
- [ ] Filebeat DaemonSet deployed (1 pod per node)
- [ ] Autodiscover configured for pods with annotation `co.elastic.logs/enabled: "true"`
- [ ] JSON parsing enabled (all services use LogstashEncoder)
- [ ] Logs indexed in Elasticsearch under `logs-etl-*` pattern
- [ ] Test: Logs from loader-service appear in Kibana within 30 seconds

**Technical Specifications**:
```yaml
# filebeat-daemonset.yaml
apiVersion: apps/v1
kind: DaemonSet
metadata:
  name: filebeat
  namespace: monitoring-infra
spec:
  selector:
    matchLabels:
      app: filebeat
  template:
    metadata:
      labels:
        app: filebeat
    spec:
      serviceAccountName: filebeat
      containers:
        - name: filebeat
          image: docker.elastic.co/beats/filebeat:8.11.0
          args:
            - "-c"
            - "/etc/filebeat.yml"
            - "-e"
          volumeMounts:
            - name: config
              mountPath: /etc/filebeat.yml
              subPath: filebeat.yml
            - name: data
              mountPath: /usr/share/filebeat/data
            - name: varlibdockercontainers
              mountPath: /var/lib/docker/containers
              readOnly: true
            - name: varlog
              mountPath: /var/log
              readOnly: true
      volumes:
        - name: config
          configMap:
            name: filebeat-config
        - name: varlibdockercontainers
          hostPath:
            path: /var/lib/docker/containers
        - name: varlog
          hostPath:
            path: /var/log
        - name: data
          hostPath:
            path: /var/lib/filebeat-data

---
apiVersion: v1
kind: ConfigMap
metadata:
  name: filebeat-config
  namespace: monitoring-infra
data:
  filebeat.yml: |
    filebeat.autodiscover:
      providers:
        - type: kubernetes
          node: ${NODE_NAME}
          hints.enabled: true
          hints.default_config:
            type: container
            paths:
              - /var/log/containers/*${data.kubernetes.container.id}.log

    processors:
      - add_cloud_metadata: ~
      - add_host_metadata: ~
      - decode_json_fields:
          fields: ["message"]
          target: "json"
          overwrite_keys: true

    output.elasticsearch:
      hosts: ['${ELASTICSEARCH_HOST:elasticsearch.monitoring-infra.svc.cluster.local:9200}']
      index: "logs-etl-%{+yyyy.MM.dd}"

    setup.ilm.enabled: false
    setup.template.name: "logs-etl"
    setup.template.pattern: "logs-etl-*"
```

**Dependencies**: PLAT-103 (Elasticsearch must be running)

**Testing**:
- Check Filebeat pods: `kubectl get pods -n monitoring-infra -l app=filebeat` → All Running
- Generate test log: `kubectl exec loader-service-pod -- logger "TEST LOG ENTRY"`
- Query Kibana: Search for "TEST LOG ENTRY", verify it appears within 30s

---

### EPIC-1.3: Grafana Dashboards

#### Ticket PLAT-105: Dashboard 1 - Transaction Monitoring
**Type**: Feature
**Priority**: Critical
**Story Points**: 13
**Assignee**: DevOps Engineer

**Description**:
Create Grafana dashboard for ETL transaction monitoring with signal ingestion funnel, success rates, and P99 latencies.

**Acceptance Criteria**:
- [ ] Dashboard accessible at `/d/etl-transaction-monitoring`
- [ ] 6 panels:
  1. Signal Ingestion Rate (time-series, last 24h)
  2. Loader Success Rate Gauge (last 1h, alert <95%)
  3. Top 5 Failed Loaders (table with drill-down)
  4. P99 Loader Execution Time (heatmap by loader_code)
  5. Signal Validation Failures (bar chart by error type)
  6. Active Loaders Count (stat panel)
- [ ] All panels use variables: `$datasource`, `$time_range`, `$loader_code`
- [ ] Auto-refresh every 30 seconds
- [ ] Export as JSON and commit to Git

**Technical Specifications**:

**Panel 1: Signal Ingestion Rate**
```promql
# Query
sum(rate(signals_ingested_total[5m])) by (loader_code)

# Visualization: Time series
# Legend: {{loader_code}}
# Y-axis: Signals/sec
```

**Panel 2: Loader Success Rate Gauge**
```promql
# Query
(
  sum(rate(signals_ingested_total[1h])) -
  sum(rate(signals_validation_failures_total[1h]))
) / sum(rate(signals_ingested_total[1h])) * 100

# Visualization: Gauge
# Min: 0, Max: 100
# Thresholds:
#   - Red: 0-90
#   - Yellow: 90-95
#   - Green: 95-100
```

**Panel 3: Top 5 Failed Loaders**
```sql
# Query (PostgreSQL data source)
SELECT
  loader_code,
  COUNT(*) as failure_count,
  MAX(captured_at) as last_failure
FROM signals
WHERE validation_status = 'FAILED'
  AND captured_at > NOW() - INTERVAL '24 hours'
GROUP BY loader_code
ORDER BY failure_count DESC
LIMIT 5

# Visualization: Table
# Columns:
#   - Loader Code (link to detail view)
#   - Failure Count
#   - Last Failure (formatted as relative time)
```

**Panel 4: P99 Execution Time**
```promql
# Query
histogram_quantile(0.99,
  sum(rate(loader_execution_duration_seconds_bucket[5m])) by (le, loader_code)
)

# Visualization: Heatmap
# X-axis: Time
# Y-axis: loader_code
# Color: Execution time (seconds)
# Color scheme: Green (0-1s) → Yellow (1-5s) → Red (>5s)
```

**Panel 5: Validation Failures by Type**
```sql
# Query
SELECT
  error_type,
  COUNT(*) as count
FROM signals
WHERE validation_status = 'FAILED'
  AND captured_at > NOW() - INTERVAL '1 hour'
GROUP BY error_type
ORDER BY count DESC

# Visualization: Bar chart (horizontal)
```

**Panel 6: Active Loaders**
```sql
# Query
SELECT COUNT(*)
FROM loader
WHERE enabled = TRUE
  AND version = 'ACTIVE'

# Visualization: Stat panel
# Unit: Loaders
# Color: Blue
```

**Dashboard Variables**:
```json
{
  "templating": {
    "list": [
      {
        "name": "datasource",
        "type": "datasource",
        "query": "prometheus"
      },
      {
        "name": "time_range",
        "type": "interval",
        "options": ["5m", "15m", "1h", "6h", "24h", "7d"]
      },
      {
        "name": "loader_code",
        "type": "query",
        "datasource": "PostgreSQL",
        "query": "SELECT DISTINCT loader_code FROM loader WHERE enabled = TRUE",
        "multi": true,
        "includeAll": true
      }
    ]
  }
}
```

**Dependencies**:
- PLAT-102 (Prometheus data)
- PostgreSQL datasource configured in Grafana

**Testing**:
- Load dashboard → All panels render without errors
- Ingest 1000 test signals → Ingestion rate panel shows spike
- Create failed validation → Appears in "Top 5 Failed Loaders" within 1 minute
- Change time range variable → All panels update

**Deliverable**: `dashboards/transaction-monitoring.json`

---

#### Ticket PLAT-106: Dashboard 2 - Log Monitoring
**Type**: Feature
**Priority**: Critical
**Story Points**: 13
**Assignee**: DevOps Engineer

**Description**:
Create Grafana dashboard for log-based monitoring with error patterns, correlation ID search, and service-level error rates.

**Acceptance Criteria**:
- [ ] Dashboard accessible at `/d/etl-log-monitoring`
- [ ] 7 panels:
  1. Log Volume by Level (time-series: ERROR, WARN, INFO)
  2. Error Rate by Service (bar chart)
  3. Top 10 Error Messages (table with count)
  4. Correlation ID Search (logs panel with variable input)
  5. Error Pattern Heatmap (service × hour)
  6. Recent Critical Errors (logs table, last 100)
  7. Log Growth Rate (stat panel, logs/hour trend)
- [ ] Elasticsearch datasource configured
- [ ] Auto-refresh every 1 minute

**Technical Specifications**:

**Panel 1: Log Volume by Level**
```lucene
# Query (Elasticsearch)
{
  "query": {
    "bool": {
      "must": [
        { "range": { "@timestamp": { "gte": "now-24h" } } }
      ]
    }
  },
  "aggs": {
    "by_time": {
      "date_histogram": {
        "field": "@timestamp",
        "fixed_interval": "5m"
      },
      "aggs": {
        "by_level": {
          "terms": { "field": "level.keyword" }
        }
      }
    }
  }
}

# Visualization: Time series (stacked area)
# Series:
#   - ERROR (red)
#   - WARN (yellow)
#   - INFO (blue)
#   - DEBUG (gray)
```

**Panel 2: Error Rate by Service**
```lucene
# Query
{
  "query": {
    "bool": {
      "must": [
        { "term": { "level.keyword": "ERROR" } },
        { "range": { "@timestamp": { "gte": "now-1h" } } }
      ]
    }
  },
  "aggs": {
    "by_service": {
      "terms": {
        "field": "service.name.keyword",
        "size": 10
      }
    }
  }
}

# Visualization: Bar chart (horizontal)
# Color mapping:
#   - >100 errors: Red
#   - 50-100: Yellow
#   - <50: Green
```

**Panel 3: Top 10 Error Messages**
```lucene
# Query
{
  "query": {
    "bool": {
      "must": [
        { "term": { "level.keyword": "ERROR" } },
        { "range": { "@timestamp": { "gte": "now-24h" } } }
      ]
    }
  },
  "aggs": {
    "top_errors": {
      "terms": {
        "field": "message.keyword",
        "size": 10
      },
      "aggs": {
        "first_occurrence": {
          "min": { "field": "@timestamp" }
        }
      }
    }
  }
}

# Visualization: Table
# Columns:
#   - Error Message (truncate to 100 chars)
#   - Count
#   - First Seen (relative time)
#   - Service (extracted from context)
```

**Panel 4: Correlation ID Timeline**
```lucene
# Query (uses $correlation_id variable)
{
  "query": {
    "bool": {
      "must": [
        { "match": { "correlationId": "$correlation_id" } }
      ]
    }
  },
  "sort": [
    { "@timestamp": { "order": "asc" } }
  ]
}

# Visualization: Logs panel
# Columns: timestamp, level, service.name, message
# Color by level:
#   - ERROR: Red
#   - WARN: Yellow
#   - INFO: Blue
# Enable log context (show ±5 lines)
```

**Panel 5: Error Pattern Heatmap**
```lucene
# Query
{
  "query": {
    "bool": {
      "must": [
        { "term": { "level.keyword": "ERROR" } },
        { "range": { "@timestamp": { "gte": "now-7d" } } }
      ]
    }
  },
  "aggs": {
    "by_service": {
      "terms": { "field": "service.name.keyword" },
      "aggs": {
        "by_hour": {
          "date_histogram": {
            "field": "@timestamp",
            "fixed_interval": "1h"
          }
        }
      }
    }
  }
}

# Visualization: Heatmap
# X-axis: Hour of day (0-23)
# Y-axis: Service name
# Color: Error count (white → red scale)
```

**Panel 6: Recent Critical Errors**
```lucene
# Query
{
  "query": {
    "bool": {
      "must": [
        { "term": { "level.keyword": "ERROR" } },
        {
          "query_string": {
            "query": "*CRITICAL* OR *AUTHENTICATION_FAILED* OR *AUTHORIZATION_FAILED*"
          }
        },
        { "range": { "@timestamp": { "gte": "now-1h" } } }
      ]
    }
  },
  "sort": [
    { "@timestamp": { "order": "desc" } }
  ],
  "size": 100
}

# Visualization: Table (with log details expansion)
# Columns:
#   - Timestamp
#   - Service
#   - Error Type (extracted from message)
#   - Message (first 200 chars)
#   - Correlation ID (link to Panel 4 search)
```

**Panel 7: Log Growth Rate**
```lucene
# Query
{
  "query": {
    "range": { "@timestamp": { "gte": "now-2h" } }
  },
  "aggs": {
    "current_hour": {
      "filter": { "range": { "@timestamp": { "gte": "now-1h" } } }
    },
    "previous_hour": {
      "filter": { "range": { "@timestamp": { "gte": "now-2h", "lt": "now-1h" } } }
    }
  }
}

# Transformation: Calculate growth rate
# Formula: ((current - previous) / previous) * 100

# Visualization: Stat panel
# Unit: % change
# Sparkline: Show 24h trend
# Thresholds:
#   - >50% growth: Yellow
#   - >100% growth: Red
```

**Dashboard Variables**:
```json
{
  "templating": {
    "list": [
      {
        "name": "correlation_id",
        "type": "textbox",
        "label": "Correlation ID",
        "description": "Enter correlation ID to trace request"
      },
      {
        "name": "service",
        "type": "query",
        "datasource": "Elasticsearch",
        "query": "{\"find\": \"terms\", \"field\": \"service.name.keyword\"}",
        "multi": true,
        "includeAll": true
      }
    ]
  }
}
```

**Dependencies**:
- PLAT-103 (Elasticsearch)
- PLAT-104 (Filebeat)

**Testing**:
- Generate ERROR log with known correlation ID
- Enter correlation ID in variable → Panel 4 shows full request trace
- Verify error appears in "Top 10 Error Messages" within 1 minute
- Check heatmap shows error in current hour for affected service

**Deliverable**: `dashboards/log-monitoring.json`

---

#### Ticket PLAT-107: Dashboard 3 - Infrastructure Health
**Type**: Feature
**Priority**: High
**Story Points**: 8
**Assignee**: DevOps Engineer

**Description**:
Create Grafana dashboard for Kubernetes infrastructure monitoring with pod health, resource utilization, and node metrics.

**Acceptance Criteria**:
- [ ] Dashboard accessible at `/d/infrastructure-health`
- [ ] 8 panels:
  1. Pod Status Matrix (Running/Pending/Failed by namespace)
  2. CPU Utilization by Pod (gauge per pod)
  3. Memory Utilization by Pod (gauge per pod)
  4. Pod Restart Count (stat panels with alerts)
  5. Node CPU/Memory (time-series per node)
  6. Persistent Volume Usage (bar chart)
  7. Network I/O (time-series, bytes in/out)
  8. Top Resource Consumers (table: top 5 pods by CPU/Memory)
- [ ] Prometheus datasource with kube-state-metrics

**Technical Specifications**:

**Panel 1: Pod Status Matrix**
```promql
# Query
sum(kube_pod_status_phase{namespace=~"monitoring-.*"}) by (namespace, phase)

# Visualization: Stat panels (grid layout)
# Mapping:
#   - Running: Green
#   - Pending: Yellow
#   - Failed: Red
#   - Unknown: Gray
# Layout: 2 rows (monitoring-infra, monitoring-app)
```

**Panel 2: CPU Utilization by Pod**
```promql
# Query
sum(rate(container_cpu_usage_seconds_total{namespace="monitoring-app"}[5m])) by (pod)
/
sum(container_spec_cpu_quota{namespace="monitoring-app"}) by (pod)
* 100

# Visualization: Gauge (one per pod)
# Thresholds:
#   - 0-70%: Green
#   - 70-85%: Yellow
#   - 85-100%: Red
# Alert: >85% for 5 minutes
```

**Panel 3: Memory Utilization by Pod**
```promql
# Query
sum(container_memory_working_set_bytes{namespace="monitoring-app"}) by (pod)
/
sum(container_spec_memory_limit_bytes{namespace="monitoring-app"}) by (pod)
* 100

# Visualization: Gauge (one per pod)
# Thresholds:
#   - 0-75%: Green
#   - 75-90%: Yellow
#   - 90-100%: Red
# Alert: >90% for 5 minutes
```

**Panel 4: Pod Restart Count**
```promql
# Query
sum(kube_pod_container_status_restarts_total{namespace="monitoring-app"}) by (pod)

# Visualization: Stat panel (grid by pod)
# Thresholds:
#   - 0: Green
#   - 1-2: Yellow
#   - >2: Red
# Show delta (restarts in last 1 hour)
```

**Panel 5: Node CPU/Memory**
```promql
# CPU Query
100 - (avg by (node) (irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)

# Memory Query
100 * (1 - (node_memory_MemAvailable_bytes / node_memory_MemTotal_bytes))

# Visualization: Time series (stacked area)
# 2 series per node:
#   - CPU (blue)
#   - Memory (orange)
```

**Panel 6: Persistent Volume Usage**
```promql
# Query
(kubelet_volume_stats_used_bytes / kubelet_volume_stats_capacity_bytes) * 100

# Visualization: Bar chart (horizontal)
# Labels: PVC name
# Thresholds:
#   - 0-70%: Green
#   - 70-85%: Yellow
#   - 85-100%: Red
```

**Panel 7: Network I/O**
```promql
# Bytes In
sum(rate(container_network_receive_bytes_total{namespace="monitoring-app"}[5m])) by (pod)

# Bytes Out
sum(rate(container_network_transmit_bytes_total{namespace="monitoring-app"}[5m])) by (pod)

# Visualization: Time series
# Unit: Bytes/sec
# 2 series per pod (in/out)
```

**Panel 8: Top Resource Consumers**
```promql
# Query (combined CPU + Memory)
topk(5,
  sum(rate(container_cpu_usage_seconds_total{namespace="monitoring-app"}[5m])) by (pod)
)
OR
topk(5,
  sum(container_memory_working_set_bytes{namespace="monitoring-app"}) by (pod)
)

# Visualization: Table
# Columns:
#   - Pod Name
#   - CPU Usage (cores)
#   - Memory Usage (GB)
#   - Network I/O (MB/s)
#   - Restarts (last 24h)
# Sort: CPU desc
```

**Dependencies**:
- PLAT-102 (Prometheus)
- kube-state-metrics deployed

**Testing**:
- Verify all pods show correct status (Running/Pending/Failed)
- Simulate high CPU: `kubectl run cpu-stress` → Dashboard shows >80% within 30s
- Simulate pod crash: `kubectl delete pod X` → Restart count increments
- Check PVC usage matches `kubectl get pvc` output

**Deliverable**: `dashboards/infrastructure-health.json`

---

#### Ticket PLAT-108: Dashboard 4 - Integration Monitoring
**Type**: Feature
**Priority**: High
**Story Points**: 10
**Assignee**: DevOps Engineer

**Description**:
Create Grafana dashboard for service-to-service integration monitoring with service mesh visualization, circuit breaker status, and inter-service latency.

**Acceptance Criteria**:
- [ ] Dashboard accessible at `/d/integration-monitoring`
- [ ] 6 panels:
  1. Service Mesh Diagram (node graph with success/error rates)
  2. Circuit Breaker Status (stat panels by service)
  3. Inter-Service Latency P99 (heatmap: source × target)
  4. HTTP Status Codes (time-series by code: 2xx, 4xx, 5xx)
  5. Database Connection Pool Status (gauge per service)
  6. External API Call Success Rate (stat panel)
- [ ] Use Grafana Node Graph plugin

**Technical Specifications**:

**Panel 1: Service Mesh Diagram**
```promql
# Nodes Query (all services)
group by (service) (
  sum(rate(http_server_requests_seconds_count[5m])) by (service)
)

# Edges Query (service-to-service calls)
sum(rate(http_client_requests_seconds_count[5m]))
by (source_service, target_service, status)

# Visualization: Node Graph
# Node properties:
#   - Title: service name
#   - Subtitle: request rate (req/s)
#   - Color: Health (green/yellow/red based on error rate)
#   - Size: Proportional to traffic volume
# Edge properties:
#   - Color: Success (green) vs Error (red)
#   - Thickness: Request rate
#   - Label: P50 latency
```

**Panel 2: Circuit Breaker Status**
```promql
# Query
resilience4j_circuitbreaker_state{namespace="monitoring-app"}

# Visualization: Stat panel (grid by circuit breaker name)
# Value mapping:
#   - 0 (CLOSED): Green with ✓ icon
#   - 1 (OPEN): Red with ✗ icon
#   - 2 (HALF_OPEN): Yellow with ⚠ icon
# Additional info:
#   - Failure rate: resilience4j_circuitbreaker_failure_rate
#   - Slow call rate: resilience4j_circuitbreaker_slow_call_rate
```

**Panel 3: Inter-Service Latency P99**
```promql
# Query
histogram_quantile(0.99,
  sum(rate(http_client_requests_seconds_bucket[5m]))
  by (le, source_service, target_service)
)

# Visualization: Heatmap
# X-axis: Target service
# Y-axis: Source service
# Color: Latency (ms)
#   - Green: 0-100ms
#   - Yellow: 100-500ms
#   - Red: >500ms
```

**Panel 4: HTTP Status Codes**
```promql
# 2xx Success
sum(rate(http_server_requests_seconds_count{status=~"2.."}[5m])) by (service)

# 4xx Client Errors
sum(rate(http_server_requests_seconds_count{status=~"4.."}[5m])) by (service)

# 5xx Server Errors
sum(rate(http_server_requests_seconds_count{status=~"5.."}[5m])) by (service)

# Visualization: Time series (stacked area)
# Series:
#   - 2xx: Green
#   - 4xx: Yellow
#   - 5xx: Red
```

**Panel 5: Database Connection Pool**
```promql
# Active Connections
hikari_connection_pool_active_connections{namespace="monitoring-app"}

# Max Connections
hikari_connection_pool_max_connections{namespace="monitoring-app"}

# Calculated: Usage %
(active / max) * 100

# Visualization: Gauge (one per service)
# Thresholds:
#   - 0-70%: Green
#   - 70-90%: Yellow
#   - 90-100%: Red
# Label shows: active/max connections
```

**Panel 6: External API Success Rate**
```promql
# Query (for external HTTP calls)
sum(rate(http_client_requests_seconds_count{status=~"2.."}[5m]))
/
sum(rate(http_client_requests_seconds_count[5m]))
* 100

# Visualization: Stat panel
# Unit: % success
# Sparkline: 24h trend
# Threshold: <95% triggers alert
```

**Dependencies**:
- PLAT-102 (Prometheus)
- Resilience4j metrics configured

**Testing**:
- Verify service mesh shows all services (Gateway, Auth, Loader, Import-Export, PostgreSQL, Redis)
- Trigger circuit breaker: Send 50% failed requests → Status changes to OPEN
- Check inter-service latency matches manual curl timing tests
- Verify database connection pool gauge matches actual connections

**Deliverable**: `dashboards/integration-monitoring.json`

---

### EPIC-1.4: Statistics Capture Service

#### Ticket PLAT-109: Build Statistics Capture Service
**Type**: Feature
**Priority**: High
**Story Points**: 13
**Assignee**: Backend Developer

**Description**:
Create scheduled service that captures observability metrics from Prometheus and Elasticsearch and stores them as signals in the database.

**Acceptance Criteria**:
- [ ] StatisticsCaptureService runs every 5 minutes
- [ ] Captures 4 metric types:
  1. Error rates (from Elasticsearch)
  2. Performance metrics (from Prometheus: P99 latency, throughput)
  3. Availability metrics (calculated from health checks)
  4. Infrastructure metrics (CPU, memory, restarts)
- [ ] Stores metrics as signals in `signals` table with `loader_code` prefix "STATS-*"
- [ ] New column `metric_type` added to signals table
- [ ] Pre-configured statistic loaders created (STATS-ERROR-RATE, STATS-PERF, etc.)
- [ ] Queryable via standard signal API

**Technical Specifications**:

```java
// StatisticsCaptureService.java
@Service
@Slf4j
@RequiredArgsConstructor
public class StatisticsCaptureService {

    private final ElasticsearchClient elasticsearchClient;
    private final PrometheusClient prometheusClient;
    private final KubernetesClient kubernetesClient;
    private final SignalRepository signalRepository;

    @Scheduled(cron = "0 */5 * * * *") // Every 5 minutes
    @Transactional
    public void captureStatistics() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);

        log.info("Starting statistics capture | correlationId={}", correlationId);

        try {
            captureErrorRateStatistics();
            capturePerformanceStatistics();
            captureAvailabilityStatistics();
            captureInfrastructureStatistics();

            log.info("Statistics capture completed successfully | correlationId={}", correlationId);
        } catch (Exception e) {
            log.error("STATISTICS_CAPTURE_FAILED | correlationId={} | error={}",
                correlationId, e.getMessage(), e);
        } finally {
            MDC.remove("correlationId");
        }
    }

    private void captureErrorRateStatistics() {
        log.debug("Capturing error rate statistics");

        // Query Elasticsearch for error counts by service (last 5 minutes)
        SearchRequest request = SearchRequest.of(s -> s
            .index("logs-etl-*")
            .size(0)
            .query(q -> q.bool(b -> b
                .must(m -> m.term(t -> t.field("level.keyword").value("ERROR")))
                .must(m -> m.range(r -> r.field("@timestamp")
                    .gte(JsonData.of("now-5m"))))
            ))
            .aggregations("by_service", a -> a
                .terms(t -> t.field("service.name.keyword"))
            )
        );

        SearchResponse<Void> response = elasticsearchClient.search(request, Void.class);

        response.aggregations()
            .get("by_service")
            .sterms()
            .buckets()
            .array()
            .forEach(bucket -> {
                String service = bucket.key();
                long errorCount = bucket.docCount();

                Signal signal = Signal.builder()
                    .loaderCode("STATS-ERROR-RATE")
                    .signalKey(service)
                    .signalValue(String.valueOf(errorCount))
                    .capturedAt(LocalDateTime.now())
                    .metricType("ERROR_COUNT_5MIN")
                    .metricSource("ELASTICSEARCH")
                    .validationStatus("VALID")
                    .build();

                signalRepository.save(signal);

                log.debug("Error rate statistic captured | service={} | errorCount={}",
                    service, errorCount);
            });
    }

    private void capturePerformanceStatistics() {
        log.debug("Capturing performance statistics");

        // Query Prometheus for P99 latency
        String query = "histogram_quantile(0.99, " +
            "sum(rate(http_server_requests_seconds_bucket[5m])) by (le, service))";

        PrometheusQueryResult result = prometheusClient.query(query);

        result.getData().getResult().forEach(metric -> {
            String service = metric.getMetric().get("service");
            double latencySeconds = Double.parseDouble(metric.getValue()[1].toString());
            double latencyMs = latencySeconds * 1000;

            Signal signal = Signal.builder()
                .loaderCode("STATS-PERF")
                .signalKey(service)
                .signalValue(String.format("%.2f", latencyMs))
                .capturedAt(LocalDateTime.now())
                .metricType("LATENCY_P99_MS")
                .metricSource("PROMETHEUS")
                .validationStatus("VALID")
                .build();

            signalRepository.save(signal);

            log.debug("Performance statistic captured | service={} | latencyP99={}ms",
                service, latencyMs);
        });

        // Query for throughput (requests per second)
        query = "sum(rate(http_server_requests_seconds_count[5m])) by (service)";
        result = prometheusClient.query(query);

        result.getData().getResult().forEach(metric -> {
            String service = metric.getMetric().get("service");
            double requestsPerSec = Double.parseDouble(metric.getValue()[1].toString());

            Signal signal = Signal.builder()
                .loaderCode("STATS-PERF")
                .signalKey(service)
                .signalValue(String.format("%.2f", requestsPerSec))
                .capturedAt(LocalDateTime.now())
                .metricType("THROUGHPUT_REQ_PER_SEC")
                .metricSource("PROMETHEUS")
                .validationStatus("VALID")
                .build();

            signalRepository.save(signal);
        });
    }

    private void captureAvailabilityStatistics() {
        log.debug("Capturing availability statistics");

        // Calculate uptime % from Prometheus up metric
        String query = "avg_over_time(up{job=~\".*-service\"}[5m]) * 100";
        PrometheusQueryResult result = prometheusClient.query(query);

        result.getData().getResult().forEach(metric -> {
            String service = metric.getMetric().get("job");
            double uptimePercent = Double.parseDouble(metric.getValue()[1].toString());

            Signal signal = Signal.builder()
                .loaderCode("STATS-AVAIL")
                .signalKey(service)
                .signalValue(String.format("%.2f", uptimePercent))
                .capturedAt(LocalDateTime.now())
                .metricType("UPTIME_PERCENT_5MIN")
                .metricSource("PROMETHEUS")
                .validationStatus("VALID")
                .build();

            signalRepository.save(signal);

            if (uptimePercent < 100.0) {
                log.warn("SERVICE_DOWNTIME_DETECTED | service={} | uptime={}% | " +
                    "suggestion=Check pod status and recent restarts",
                    service, uptimePercent);
            }
        });
    }

    private void captureInfrastructureStatistics() {
        log.debug("Capturing infrastructure statistics");

        // Query Prometheus for CPU usage by pod
        String query = "sum(rate(container_cpu_usage_seconds_total" +
            "{namespace=\"monitoring-app\"}[5m])) by (pod) * 100";
        PrometheusQueryResult result = prometheusClient.query(query);

        result.getData().getResult().forEach(metric -> {
            String pod = metric.getMetric().get("pod");
            double cpuPercent = Double.parseDouble(metric.getValue()[1].toString());

            Signal signal = Signal.builder()
                .loaderCode("STATS-INFRA")
                .signalKey(pod)
                .signalValue(String.format("%.2f", cpuPercent))
                .capturedAt(LocalDateTime.now())
                .metricType("CPU_USAGE_PERCENT")
                .metricSource("PROMETHEUS")
                .validationStatus("VALID")
                .build();

            signalRepository.save(signal);
        });

        // Query for memory usage
        query = "sum(container_memory_working_set_bytes{namespace=\"monitoring-app\"}) " +
            "by (pod) / sum(container_spec_memory_limit_bytes{namespace=\"monitoring-app\"}) " +
            "by (pod) * 100";
        result = prometheusClient.query(query);

        result.getData().getResult().forEach(metric -> {
            String pod = metric.getMetric().get("pod");
            double memoryPercent = Double.parseDouble(metric.getValue()[1].toString());

            Signal signal = Signal.builder()
                .loaderCode("STATS-INFRA")
                .signalKey(pod)
                .signalValue(String.format("%.2f", memoryPercent))
                .capturedAt(LocalDateTime.now())
                .metricType("MEMORY_USAGE_PERCENT")
                .metricSource("PROMETHEUS")
                .validationStatus("VALID")
                .build();

            signalRepository.save(signal);

            if (memoryPercent > 85.0) {
                log.warn("HIGH_MEMORY_USAGE | pod={} | memory={}% | " +
                    "suggestion=Consider scaling or investigating memory leak",
                    pod, memoryPercent);
            }
        });

        // Query for pod restart counts (last 5 minutes)
        query = "sum(increase(kube_pod_container_status_restarts_total" +
            "{namespace=\"monitoring-app\"}[5m])) by (pod)";
        result = prometheusClient.query(query);

        result.getData().getResult().forEach(metric -> {
            String pod = metric.getMetric().get("pod");
            int restarts = (int) Double.parseDouble(metric.getValue()[1].toString());

            if (restarts > 0) {
                Signal signal = Signal.builder()
                    .loaderCode("STATS-INFRA")
                    .signalKey(pod)
                    .signalValue(String.valueOf(restarts))
                    .capturedAt(LocalDateTime.now())
                    .metricType("POD_RESTARTS_5MIN")
                    .metricSource("PROMETHEUS")
                    .validationStatus("VALID")
                    .build();

                signalRepository.save(signal);

                log.warn("POD_RESTART_DETECTED | pod={} | restarts={} | " +
                    "suggestion=Check pod logs for crash reason",
                    pod, restarts);
            }
        });
    }
}
```

**Database Migration**:
```sql
-- V18__add_metric_columns_to_signals.sql
ALTER TABLE signals ADD COLUMN metric_type VARCHAR(50);
ALTER TABLE signals ADD COLUMN metric_source VARCHAR(50);

CREATE INDEX idx_signals_metric_type ON signals(metric_type);
CREATE INDEX idx_signals_captured_at_desc ON signals(captured_at DESC);

COMMENT ON COLUMN signals.metric_type IS 'Type of metric: ERROR_COUNT_5MIN, LATENCY_P99_MS, etc.';
COMMENT ON COLUMN signals.metric_source IS 'Source: PROMETHEUS, ELASTICSEARCH, KUBERNETES, CALCULATED';

-- Create predefined statistic loaders
INSERT INTO loader (loader_code, loader_name, loader_description, enabled, version, created_at) VALUES
('STATS-ERROR-RATE', 'Error Rate Statistics',
 'Captures error log counts from Elasticsearch every 5 minutes',
 TRUE, 'ACTIVE', CURRENT_TIMESTAMP),

('STATS-PERF', 'Performance Statistics',
 'Captures P99 latency and throughput from Prometheus every 5 minutes',
 TRUE, 'ACTIVE', CURRENT_TIMESTAMP),

('STATS-AVAIL', 'Availability Statistics',
 'Calculates service uptime percentage from health checks',
 TRUE, 'ACTIVE', CURRENT_TIMESTAMP),

('STATS-INFRA', 'Infrastructure Statistics',
 'Captures CPU, memory, and pod restart metrics from Kubernetes',
 TRUE, 'ACTIVE', CURRENT_TIMESTAMP);
```

**Prometheus Client**:
```java
// PrometheusClient.java
@Component
@RequiredArgsConstructor
public class PrometheusClient {

    private final WebClient prometheusWebClient;
    private final String prometheusBaseUrl;

    public PrometheusQueryResult query(String promqlQuery) {
        try {
            return prometheusWebClient.get()
                .uri(uriBuilder -> uriBuilder
                    .path("/api/v1/query")
                    .queryParam("query", promqlQuery)
                    .build())
                .retrieve()
                .bodyToMono(PrometheusQueryResult.class)
                .block(Duration.ofSeconds(10));
        } catch (Exception e) {
            log.error("PROMETHEUS_QUERY_FAILED | query={} | error={}",
                promqlQuery, e.getMessage(), e);
            throw new PrometheusClientException("Failed to query Prometheus", e);
        }
    }

    public PrometheusQueryResult queryRange(String promqlQuery,
                                            Instant start,
                                            Instant end,
                                            String step) {
        return prometheusWebClient.get()
            .uri(uriBuilder -> uriBuilder
                .path("/api/v1/query_range")
                .queryParam("query", promqlQuery)
                .queryParam("start", start.getEpochSecond())
                .queryParam("end", end.getEpochSecond())
                .queryParam("step", step)
                .build())
            .retrieve()
            .bodyToMono(PrometheusQueryResult.class)
            .block(Duration.ofSeconds(10));
    }
}

// Configuration
@Configuration
public class ObservabilityClientsConfig {

    @Bean
    public WebClient prometheusWebClient(
            @Value("${prometheus.base-url}") String prometheusBaseUrl) {
        return WebClient.builder()
            .baseUrl(prometheusBaseUrl)
            .defaultHeader(HttpHeaders.ACCEPT, MediaType.APPLICATION_JSON_VALUE)
            .build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(
            @Value("${elasticsearch.base-url}") String elasticsearchBaseUrl) {
        RestClient restClient = RestClient.builder(
            HttpHost.create(elasticsearchBaseUrl)
        ).build();

        ElasticsearchTransport transport = new RestClientTransport(
            restClient, new JacksonJsonpMapper()
        );

        return new ElasticsearchClient(transport);
    }
}
```

**Configuration (application.yaml)**:
```yaml
prometheus:
  base-url: ${PROMETHEUS_URL:http://prometheus.monitoring-infra.svc.cluster.local:9090}

elasticsearch:
  base-url: ${ELASTICSEARCH_URL:http://elasticsearch.monitoring-infra.svc.cluster.local:9200}

statistics:
  capture:
    enabled: ${STATISTICS_CAPTURE_ENABLED:true}
    cron: "0 */5 * * * *"  # Every 5 minutes
    retention-days: 30  # Keep statistics for 30 days
```

**Dependencies**:
- PLAT-102 (Prometheus)
- PLAT-103 (Elasticsearch)

**Testing**:
- Deploy service → Verify @Scheduled runs every 5 minutes
- Generate 50 ERROR logs → Next capture cycle creates signal with errorCount=50
- Query API: `GET /api/ldr/sig?loaderCode=STATS-ERROR-RATE` → Returns statistics signals
- Verify SQL queries work:
  ```sql
  SELECT signal_key as service,
         AVG(CAST(signal_value AS DECIMAL)) as avg_error_count
  FROM signals
  WHERE loader_code = 'STATS-ERROR-RATE'
    AND captured_at > NOW() - INTERVAL '24 hours'
  GROUP BY signal_key
  ORDER BY avg_error_count DESC;
  ```

**Deliverable**:
- StatisticsCaptureService.java
- PrometheusClient.java + ElasticsearchClient.java
- V18 database migration
- Configuration updates

---

## END OF SPRINT 1

**Sprint 1 Total Story Points**: 89
**Sprint 1 Duration**: 4 weeks
**Sprint 1 Deliverables**:
- ✅ 4 Production Grafana dashboards
- ✅ Prometheus + Elasticsearch infrastructure
- ✅ Statistics capture service (observability metrics → signals)
- ✅ All services instrumented with metrics

**Sprint 1 Demo**:
- Show all 4 dashboards with live data
- Ingest 10,000 signals → Watch transaction dashboard update in real-time
- Generate error → Show in log dashboard within 30 seconds
- Query statistics via API: `GET /api/ldr/sig?loaderCode=STATS-PERF&metricType=LATENCY_P99_MS`

---

## SPRINT 2: Intelligence & Integrations

### EPIC-2.1: Incident Detection Service

#### Ticket PLAT-201: Create Incidents Table & Domain Model
**Type**: Technical Task
**Priority**: Critical
**Story Points**: 5
**Assignee**: Backend Developer

**Description**:
Create database schema and JPA entities for incident management.

**Acceptance Criteria**:
- [ ] `incidents` table created with all required columns
- [ ] `incident_detection_rules` table created
- [ ] JPA entities: Incident, IncidentDetectionRule
- [ ] Repository interfaces with custom queries
- [ ] Pre-populated with 10 default detection rules

**Technical Specifications**:

```sql
-- V19__create_incidents_tables.sql

CREATE TABLE incidents (
    id BIGSERIAL PRIMARY KEY,
    title VARCHAR(200) NOT NULL,
    description TEXT,
    severity VARCHAR(20) NOT NULL, -- CRITICAL, HIGH, MEDIUM, LOW
    category VARCHAR(50) NOT NULL, -- TRANSACTION, LOG, INTEGRATION, INFRASTRUCTURE
    status VARCHAR(20) DEFAULT 'OPEN', -- OPEN, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, CLOSED

    -- Context
    service_name VARCHAR(100),
    correlation_id VARCHAR(36),
    affected_component VARCHAR(100),

    -- Metrics snapshot
    metric_name VARCHAR(100),
    metric_value DECIMAL(15,2),
    threshold_value DECIMAL(15,2),

    -- Resolution
    impact TEXT,
    suggestion TEXT,
    resolution_notes TEXT,

    -- External integrations
    jira_ticket_key VARCHAR(50),
    jira_ticket_url VARCHAR(500),

    -- Timestamps
    detected_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    acknowledged_at TIMESTAMP,
    resolved_at TIMESTAMP,
    closed_at TIMESTAMP,

    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,

    CONSTRAINT chk_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_status CHECK (status IN ('OPEN', 'ACKNOWLEDGED', 'IN_PROGRESS', 'RESOLVED', 'CLOSED')),
    CONSTRAINT chk_category CHECK (category IN ('TRANSACTION', 'LOG', 'INTEGRATION', 'INFRASTRUCTURE'))
);

CREATE INDEX idx_incidents_status ON incidents(status);
CREATE INDEX idx_incidents_severity ON incidents(severity);
CREATE INDEX idx_incidents_detected_at_desc ON incidents(detected_at DESC);
CREATE INDEX idx_incidents_correlation_id ON incidents(correlation_id);
CREATE INDEX idx_incidents_service_name ON incidents(service_name);

COMMENT ON TABLE incidents IS 'Stores detected incidents from automated monitoring';
COMMENT ON COLUMN incidents.correlation_id IS 'Links incident to originating request trace';
COMMENT ON COLUMN incidents.jira_ticket_key IS 'External Jira ticket reference (e.g., ETLOPS-123)';

---

CREATE TABLE incident_detection_rules (
    id BIGSERIAL PRIMARY KEY,
    rule_name VARCHAR(100) NOT NULL UNIQUE,
    rule_type VARCHAR(50) NOT NULL, -- TRANSACTION, LOG, INTEGRATION, INFRASTRUCTURE
    description TEXT,

    -- Detection configuration
    metric_query TEXT NOT NULL, -- SQL, PromQL, or Lucene query
    query_type VARCHAR(20) NOT NULL, -- SQL, PROMETHEUS, ELASTICSEARCH
    threshold_value DECIMAL(10,2),
    threshold_operator VARCHAR(10), -- GT, LT, EQ, GTE, LTE, EXISTS

    -- Incident properties
    severity VARCHAR(20) NOT NULL,
    alert_message_template TEXT NOT NULL,
    suggestion_template TEXT,

    -- Execution
    enabled BOOLEAN DEFAULT TRUE,
    evaluation_interval_seconds INT DEFAULT 60,
    cooldown_period_seconds INT DEFAULT 300, -- Prevent duplicate alerts

    -- Metadata
    created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
    last_triggered_at TIMESTAMP,
    trigger_count INT DEFAULT 0,

    CONSTRAINT chk_rule_type CHECK (rule_type IN ('TRANSACTION', 'LOG', 'INTEGRATION', 'INFRASTRUCTURE')),
    CONSTRAINT chk_rule_severity CHECK (severity IN ('CRITICAL', 'HIGH', 'MEDIUM', 'LOW')),
    CONSTRAINT chk_threshold_operator CHECK (threshold_operator IN ('GT', 'LT', 'EQ', 'GTE', 'LTE', 'EXISTS'))
);

CREATE INDEX idx_detection_rules_enabled ON incident_detection_rules(enabled);
CREATE INDEX idx_detection_rules_rule_type ON incident_detection_rules(rule_type);

COMMENT ON TABLE incident_detection_rules IS 'Configurable rules for automated incident detection';
COMMENT ON COLUMN incident_detection_rules.cooldown_period_seconds IS 'Minimum seconds between triggering same rule to prevent alert fatigue';

---

-- Pre-populate with default detection rules
INSERT INTO incident_detection_rules
(rule_name, rule_type, description, metric_query, query_type, threshold_value, threshold_operator,
 severity, alert_message_template, suggestion_template, evaluation_interval_seconds)
VALUES

-- TRANSACTION-based rules
('signal_failure_rate_high', 'TRANSACTION',
 'Detects when signal validation failure rate exceeds 5%',
 'SELECT (COUNT(*) FILTER (WHERE validation_status = ''FAILED'')::FLOAT / COUNT(*)::FLOAT) * 100 as failure_rate FROM signals WHERE captured_at > NOW() - INTERVAL ''5 minutes''',
 'SQL', 5.0, 'GT', 'HIGH',
 'Signal failure rate exceeded {{threshold}}% (current: {{value}}%)',
 'Check loader configurations, database connectivity, and recent schema changes',
 60),

('zero_signals_ingested', 'TRANSACTION',
 'Detects when no signals ingested in last 10 minutes',
 'SELECT COUNT(*) as signal_count FROM signals WHERE captured_at > NOW() - INTERVAL ''10 minutes''',
 'SQL', 1, 'LT', 'CRITICAL',
 'No signals ingested in last 10 minutes',
 'Check if scheduled loaders are running, verify database connectivity',
 300),

-- LOG-based rules
('error_log_spike', 'LOG',
 'Detects abnormal ERROR log volume',
 '{"query": {"bool": {"must": [{"term": {"level.keyword": "ERROR"}}, {"range": {"@timestamp": {"gte": "now-5m"}}}]}}}',
 'ELASTICSEARCH', 50, 'GT', 'MEDIUM',
 'ERROR log count exceeded {{threshold}} in 5 minutes (current: {{value}})',
 'Review recent deployments, check for cascading failures, examine error patterns',
 60),

('authentication_failure_cluster', 'LOG',
 'Detects potential brute force attack (5+ auth failures from same IP)',
 'SELECT client_ip, COUNT(*) as failure_count FROM login_attempts WHERE success = FALSE AND attempted_at > NOW() - INTERVAL ''5 minutes'' GROUP BY client_ip HAVING COUNT(*) >= 5',
 'SQL', 5, 'GTE', 'HIGH',
 'Multiple authentication failures from IP {{client_ip}} ({{value}} attempts)',
 'Investigate potential brute force attack, consider IP blocking',
 120),

-- INTEGRATION-based rules
('circuit_breaker_open', 'INTEGRATION',
 'Detects when any circuit breaker enters OPEN state',
 'resilience4j_circuitbreaker_state{state="OPEN"}',
 'PROMETHEUS', NULL, 'EXISTS', 'CRITICAL',
 'Circuit breaker OPEN for {{service_name}}',
 'Check downstream service health, review error logs, verify network connectivity',
 30),

('database_connection_pool_exhausted', 'INTEGRATION',
 'Detects when connection pool usage exceeds 90%',
 '(hikari_connection_pool_active_connections / hikari_connection_pool_max_connections) * 100',
 'PROMETHEUS', 90, 'GT', 'HIGH',
 'Database connection pool usage: {{value}}% (threshold: {{threshold}}%)',
 'Increase pool size, check for connection leaks, review long-running transactions',
 60),

-- INFRASTRUCTURE-based rules
('high_memory_usage', 'INFRASTRUCTURE',
 'Detects pods with memory usage >85%',
 '(container_memory_working_set_bytes / container_spec_memory_limit_bytes) * 100',
 'PROMETHEUS', 85, 'GT', 'HIGH',
 'High memory usage in {{pod_name}}: {{value}}%',
 'Consider scaling pod, investigate memory leak, review heap dumps',
 120),

('pod_restart_loop', 'INFRASTRUCTURE',
 'Detects pods restarting more than 3 times in 5 minutes',
 'increase(kube_pod_container_status_restarts_total[5m])',
 'PROMETHEUS', 3, 'GT', 'CRITICAL',
 'Pod {{pod_name}} restarted {{value}} times in 5 minutes',
 'Check pod logs for crash reason (OOMKilled, CrashLoopBackOff), review resource limits',
 60),

('pvc_storage_critical', 'INFRASTRUCTURE',
 'Detects when PVC usage exceeds 90%',
 '(kubelet_volume_stats_used_bytes / kubelet_volume_stats_capacity_bytes) * 100',
 'PROMETHEUS', 90, 'GT', 'HIGH',
 'Persistent volume {{pvc_name}} usage: {{value}}% (threshold: {{threshold}}%)',
 'Expand volume size, archive old data, review retention policies',
 300),

('node_cpu_exhaustion', 'INFRASTRUCTURE',
 'Detects when node CPU usage exceeds 90% for 5 minutes',
 '100 - (avg by (node) (irate(node_cpu_seconds_total{mode="idle"}[5m])) * 100)',
 'PROMETHEUS', 90, 'GT', 'CRITICAL',
 'Node {{node_name}} CPU usage: {{value}}% sustained for 5 minutes',
 'Scale cluster, identify resource-intensive pods, review workload distribution',
 120);
```

**JPA Entities**:
```java
// Incident.java
@Entity
@Table(name = "incidents")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Incident {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, length = 200)
    private String title;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private IncidentSeverity severity;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private IncidentCategory category;

    @Enumerated(EnumType.STRING)
    @Column(length = 20)
    private IncidentStatus status = IncidentStatus.OPEN;

    // Context
    @Column(length = 100)
    private String serviceName;

    @Column(length = 36)
    private String correlationId;

    @Column(length = 100)
    private String affectedComponent;

    // Metrics
    @Column(length = 100)
    private String metricName;

    @Column(precision = 15, scale = 2)
    private BigDecimal metricValue;

    @Column(precision = 15, scale = 2)
    private BigDecimal thresholdValue;

    // Resolution
    @Column(columnDefinition = "TEXT")
    private String impact;

    @Column(columnDefinition = "TEXT")
    private String suggestion;

    @Column(columnDefinition = "TEXT")
    private String resolutionNotes;

    // External integrations
    @Column(length = 50)
    private String jiraTicketKey;

    @Column(length = 500)
    private String jiraTicketUrl;

    // Timestamps
    @Column(nullable = false)
    private LocalDateTime detectedAt = LocalDateTime.now();

    private LocalDateTime acknowledgedAt;
    private LocalDateTime resolvedAt;
    private LocalDateTime closedAt;

    @Column(nullable = false)
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime updatedAt = LocalDateTime.now();

    public enum IncidentSeverity {
        CRITICAL, HIGH, MEDIUM, LOW
    }

    public enum IncidentCategory {
        TRANSACTION, LOG, INTEGRATION, INFRASTRUCTURE
    }

    public enum IncidentStatus {
        OPEN, ACKNOWLEDGED, IN_PROGRESS, RESOLVED, CLOSED
    }

    public boolean isOpen() {
        return status == IncidentStatus.OPEN || status == IncidentStatus.ACKNOWLEDGED;
    }

    public boolean isCritical() {
        return severity == IncidentSeverity.CRITICAL;
    }
}

// IncidentDetectionRule.java
@Entity
@Table(name = "incident_detection_rules")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class IncidentDetectionRule {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false, unique = true, length = 100)
    private String ruleName;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 50)
    private RuleType ruleType;

    @Column(columnDefinition = "TEXT")
    private String description;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String metricQuery;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private QueryType queryType;

    @Column(precision = 10, scale = 2)
    private BigDecimal thresholdValue;

    @Enumerated(EnumType.STRING)
    @Column(length = 10)
    private ThresholdOperator thresholdOperator;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private Incident.IncidentSeverity severity;

    @Column(nullable = false, columnDefinition = "TEXT")
    private String alertMessageTemplate;

    @Column(columnDefinition = "TEXT")
    private String suggestionTemplate;

    private Boolean enabled = true;
    private Integer evaluationIntervalSeconds = 60;
    private Integer cooldownPeriodSeconds = 300;

    private LocalDateTime createdAt = LocalDateTime.now();
    private LocalDateTime updatedAt = LocalDateTime.now();
    private LocalDateTime lastTriggeredAt;
    private Integer triggerCount = 0;

    public enum RuleType {
        TRANSACTION, LOG, INTEGRATION, INFRASTRUCTURE
    }

    public enum QueryType {
        SQL, PROMETHEUS, ELASTICSEARCH
    }

    public enum ThresholdOperator {
        GT, LT, EQ, GTE, LTE, EXISTS
    }

    public boolean shouldEvaluate() {
        if (!enabled) return false;
        if (lastTriggeredAt == null) return true;

        Duration cooldown = Duration.ofSeconds(cooldownPeriodSeconds);
        return LocalDateTime.now().isAfter(lastTriggeredAt.plus(cooldown));
    }
}
```

**Repositories**:
```java
//IncidentRepository.java
@Repository
public interface IncidentRepository extends JpaRepository<Incident, Long> {

    List<Incident> findByStatusIn(List<Incident.IncidentStatus> statuses);

    List<Incident> findByServiceNameAndStatusNot(String serviceName, Incident.IncidentStatus status);

    @Query("SELECT i FROM Incident i WHERE i.status IN ('OPEN', 'ACKNOWLEDGED') " +
           "AND i.severity = :severity ORDER BY i.detectedAt DESC")
    List<Incident> findOpenIncidentsBySeverity(@Param("severity") Incident.IncidentSeverity severity);

    @Query("SELECT i FROM Incident i WHERE i.detectedAt > :since ORDER BY i.detectedAt DESC")
    List<Incident> findRecentIncidents(@Param("since") LocalDateTime since);

    boolean existsByCorrelationIdAndStatus(String correlationId, Incident.IncidentStatus status);
}

// IncidentDetectionRuleRepository.java
@Repository
public interface IncidentDetectionRuleRepository extends JpaRepository<IncidentDetectionRule, Long> {

    List<IncidentDetectionRule> findByEnabledTrue();

    List<IncidentDetectionRule> findByRuleTypeAndEnabledTrue(IncidentDetectionRule.RuleType ruleType);

    Optional<IncidentDetectionRule> findByRuleName(String ruleName);
}
```

**Dependencies**: None

**Testing**:
- Run migration → Tables created successfully
- Insert test incident → All fields save correctly
- Query `findOpenIncidentsBySeverity(CRITICAL)` → Returns only CRITICAL+OPEN incidents
- Test `shouldEvaluate()` → Respects cooldown period

**Deliverable**:
- V19 database migration
- Incident.java + IncidentDetectionRule.java
- IncidentRepository.java + IncidentDetectionRuleRepository.java

---

#### Ticket PLAT-202: Build Incident Detection Service
**Type**: Feature
**Priority**: Critical
**Story Points**: 21
**Assignee**: Backend Developer

**Description**:
Create service that evaluates detection rules every minute and creates incidents when thresholds are exceeded.

**Acceptance Criteria**:
- [ ] IncidentDetectionService runs every 60 seconds
- [ ] Evaluates all enabled rules from `incident_detection_rules` table
- [ ] Supports 3 query types: SQL, PROMETHEUS, ELASTICSEARCH
- [ ] Creates incidents when thresholds exceeded
- [ ] Respects cooldown period (no duplicate incidents within 5 minutes)
- [ ] Updates rule `last_triggered_at` and `trigger_count`
- [ ] Triggers notifications (Email/Slack) for CRITICAL/HIGH severity
- [ ] Comprehensive logging with ERROR_TYPE patterns

**Technical Specifications**:

```java
// IncidentDetectionService.java
@Service
@Slf4j
@RequiredArgsConstructor
public class IncidentDetectionService {

    private final IncidentDetectionRuleRepository ruleRepository;
    private final IncidentRepository incidentRepository;
    private final JdbcTemplate jdbcTemplate;
    private final PrometheusClient prometheusClient;
    private final ElasticsearchClient elasticsearchClient;
    private final NotificationService notificationService;

    @Scheduled(fixedRate = 60000) // Every 60 seconds
    @Transactional
    public void detectIncidents() {
        String correlationId = UUID.randomUUID().toString();
        MDC.put("correlationId", correlationId);
        MDC.put("processId", "incident-detection");

        log.trace("Entering detectIncidents() | correlationId={}", correlationId);
        log.info("Starting incident detection cycle | correlationId={}", correlationId);

        try {
            List<IncidentDetectionRule> enabledRules = ruleRepository.findByEnabledTrue();
            log.debug("Found {} enabled detection rules | correlationId={}",
                enabledRules.size(), correlationId);

            int incidentsCreated = 0;
            int rulesEvaluated = 0;

            for (IncidentDetectionRule rule : enabledRules) {
                if (!rule.shouldEvaluate()) {
                    log.trace("Skipping rule {} (cooldown period) | lastTriggered={} | cooldown={}s",
                        rule.getRuleName(), rule.getLastTriggeredAt(),
                        rule.getCooldownPeriodSeconds());
                    continue;
                }

                try {
                    boolean violated = evaluateRule(rule, correlationId);
                    rulesEvaluated++;

                    if (violated) {
                        incidentsCreated++;
                        log.info("Rule violated, incident created | rule={} | severity={} | correlationId={}",
                            rule.getRuleName(), rule.getSeverity(), correlationId);
                    }

                } catch (Exception e) {
                    log.error("RULE_EVALUATION_FAILED | rule={} | error={} | correlationId={} | " +
                                "reason=Exception during rule evaluation | " +
                                "suggestion=Check query syntax and data source connectivity",
                            rule.getRuleName(), e.getMessage(), correlationId, e);
                }
            }

            log.info("Incident detection cycle completed | rulesEvaluated={} | incidentsCreated={} | " +
                    "correlationId={}",
                rulesEvaluated, incidentsCreated, correlationId);

        } catch (Exception e) {
            log.error("INCIDENT_DETECTION_CYCLE_FAILED | error={} | correlationId={} | " +
                        "reason=Fatal error in detection cycle",
                    e.getMessage(), correlationId, e);
        } finally {
            MDC.remove("correlationId");
            MDC.remove("processId");
            log.trace("Exiting detectIncidents()");
        }
    }

    private boolean evaluateRule(IncidentDetectionRule rule, String correlationId) {
        log.trace("Evaluating rule | rule={} | queryType={} | correlationId={}",
            rule.getRuleName(), rule.getQueryType(), correlationId);

        QueryResult result = switch (rule.getQueryType()) {
            case SQL -> executeSqlQuery(rule);
            case PROMETHEUS -> executePrometheusQuery(rule);
            case ELASTICSEARCH -> executeElasticsearchQuery(rule);
        };

        if (result.isEmpty()) {
            log.trace("Rule query returned no results | rule={}", rule.getRuleName());
            return false;
        }

        boolean thresholdViolated = checkThreshold(result, rule);

        if (thresholdViolated) {
            createIncident(rule, result, correlationId);
            updateRuleTriggerInfo(rule);
        }

        return thresholdViolated;
    }

    private QueryResult executeSqlQuery(IncidentDetectionRule rule) {
        log.trace("Executing SQL query | rule={}", rule.getRuleName());

        try {
            List<Map<String, Object>> rows = jdbcTemplate.queryForList(rule.getMetricQuery());

            if (rows.isEmpty()) {
                return QueryResult.empty();
            }

            Map<String, Object> firstRow = rows.get(0);
            Object value = firstRow.values().iterator().next();

            return QueryResult.of(value, firstRow);

        } catch (Exception e) {
            log.error("SQL_QUERY_FAILED | rule={} | query={} | error={}",
                rule.getRuleName(), rule.getMetricQuery(), e.getMessage(), e);
            return QueryResult.empty();
        }
    }

    private QueryResult executePrometheusQuery(IncidentDetectionRule rule) {
        log.trace("Executing Prometheus query | rule={}", rule.getRuleName());

        try {
            PrometheusQueryResult response = prometheusClient.query(rule.getMetricQuery());

            if (response.getData().getResult().isEmpty()) {
                return QueryResult.empty();
            }

            PrometheusMetric metric = response.getData().getResult().get(0);
            double value = Double.parseDouble(metric.getValue()[1].toString());

            Map<String, Object> context = new HashMap<>();
            context.put("value", value);
            context.putAll(metric.getMetric());

            return QueryResult.of(value, context);

        } catch (Exception e) {
            log.error("PROMETHEUS_QUERY_FAILED | rule={} | query={} | error={}",
                rule.getRuleName(), rule.getMetricQuery(), e.getMessage(), e);
            return QueryResult.empty();
        }
    }

    private QueryResult executeElasticsearchQuery(IncidentDetectionRule rule) {
        log.trace("Executing Elasticsearch query | rule={}", rule.getRuleName());

        try {
            // Parse JSON query
            ObjectMapper mapper = new ObjectMapper();
            JsonNode queryJson = mapper.readTree(rule.getMetricQuery());

            // Execute count query
            SearchRequest request = SearchRequest.of(s -> s
                .index("logs-etl-*")
                .size(0)
                .query(q -> q.bool(b -> {
                    JsonNode mustNode = queryJson.get("query").get("bool").get("must");
                    // Build query from JSON (simplified for example)
                    return b;
                }))
            );

            SearchResponse<Void> response = elasticsearchClient.search(request, Void.class);
            long count = response.hits().total().value();

            Map<String, Object> context = new HashMap<>();
            context.put("count", count);

            return QueryResult.of(count, context);

        } catch (Exception e) {
            log.error("ELASTICSEARCH_QUERY_FAILED | rule={} | query={} | error={}",
                rule.getRuleName(), rule.getMetricQuery(), e.getMessage(), e);
            return QueryResult.empty();
        }
    }

    private boolean checkThreshold(QueryResult result, IncidentDetectionRule rule) {
        if (rule.getThresholdOperator() == IncidentDetectionRule.ThresholdOperator.EXISTS) {
            return !result.isEmpty();
        }

        double value = result.getNumericValue();
        double threshold = rule.getThresholdValue().doubleValue();

        boolean violated = switch (rule.getThresholdOperator()) {
            case GT -> value > threshold;
            case GTE -> value >= threshold;
            case LT -> value < threshold;
            case LTE -> value <= threshold;
            case EQ -> Math.abs(value - threshold) < 0.001;
            default -> false;
        };

        log.trace("Threshold check | rule={} | value={} | operator={} | threshold={} | violated={}",
            rule.getRuleName(), value, rule.getThresholdOperator(), threshold, violated);

        return violated;
    }

    private void createIncident(IncidentDetectionRule rule, QueryResult result, String correlationId) {
        log.trace("Creating incident | rule={} | correlationId={}", rule.getRuleName(), correlationId);

        // Build alert message from template
        String alertMessage = buildMessageFromTemplate(
            rule.getAlertMessageTemplate(), result.getContext()
        );

        String suggestion = buildMessageFromTemplate(
            rule.getSuggestionTemplate(), result.getContext()
        );

        // Extract service name from context
        String serviceName = result.getContext().getOrDefault("service", "unknown").toString();

        Incident incident = Incident.builder()
            .title(alertMessage)
            .description(String.format("Detection rule '%s' triggered", rule.getRuleName()))
            .severity(rule.getSeverity())
            .category(Incident.IncidentCategory.valueOf(rule.getRuleType().name()))
            .status(Incident.IncidentStatus.OPEN)
            .serviceName(serviceName)
            .correlationId(correlationId)
            .metricName(rule.getRuleName())
            .metricValue(BigDecimal.valueOf(result.getNumericValue()))
            .thresholdValue(rule.getThresholdValue())
            .suggestion(suggestion)
            .detectedAt(LocalDateTime.now())
            .build();

        incident = incidentRepository.save(incident);

        log.info("INCIDENT_CREATED | incidentId={} | severity={} | category={} | service={} | " +
                "correlationId={} | alertMessage={}",
            incident.getId(), incident.getSeverity(), incident.getCategory(),
            incident.getServiceName(), correlationId, alertMessage);

        // Send notifications for CRITICAL/HIGH severity
        if (incident.getSeverity() == Incident.IncidentSeverity.CRITICAL ||
            incident.getSeverity() == Incident.IncidentSeverity.HIGH) {

            try {
                notificationService.sendIncidentNotification(incident);
                log.info("Incident notification sent | incidentId={} | channels=email,slack",
                    incident.getId());
            } catch (Exception e) {
                log.error("NOTIFICATION_FAILED | incidentId={} | error={}",
                    incident.getId(), e.getMessage(), e);
            }
        }
    }

    private void updateRuleTriggerInfo(IncidentDetectionRule rule) {
        rule.setLastTriggeredAt(LocalDateTime.now());
        rule.setTriggerCount(rule.getTriggerCount() + 1);
        ruleRepository.save(rule);

        log.debug("Rule trigger info updated | rule={} | triggerCount={} | lastTriggered={}",
            rule.getRuleName(), rule.getTriggerCount(), rule.getLastTriggeredAt());
    }

    private String buildMessageFromTemplate(String template, Map<String, Object> context) {
        if (template == null) return "";

        String message = template;
        for (Map.Entry<String, Object> entry : context.entrySet()) {
            message = message.replace("{{" + entry.getKey() + "}}", entry.getValue().toString());
        }
        return message;
    }

    // Query result wrapper
    @Data
    @AllArgsConstructor
    private static class QueryResult {
        private Object value;
        private Map<String, Object> context;

        public static QueryResult of(Object value, Map<String, Object> context) {
            return new QueryResult(value, context);
        }

        public static QueryResult empty() {
            return new QueryResult(null, Map.of());
        }

        public boolean isEmpty() {
            return value == null;
        }

        public double getNumericValue() {
            if (value instanceof Number) {
                return ((Number) value).doubleValue();
            }
            return Double.parseDouble(value.toString());
        }
    }
}
```

**Dependencies**:
- PLAT-201 (incidents tables)
- PLAT-109 (Prometheus/Elasticsearch clients)

**Testing**:
- Deploy service → Scheduled task runs every 60s
- Create test rule: `signal_count > 0` (should always trigger)
- Wait 60s → Verify incident created with correct severity/category
- Trigger rule again within 5 minutes → No duplicate incident (cooldown works)
- Check notification sent for CRITICAL severity

**Deliverable**:
- IncidentDetectionService.java
- Unit tests with mocked query clients
- Integration test with test database

---

*Due to character limits, I'll continue with the remaining tickets in the next file...*

**Deliverable**: `docs/sprints/PHASE1_SPRINT_PLAN.md` (Part 1 of 2)

Would you like me to continue with:
- Part 2: Jira Integration + Notifications + Documentation (remaining 10 tickets)
- OR create a separate summary document with all tickets in a table format?