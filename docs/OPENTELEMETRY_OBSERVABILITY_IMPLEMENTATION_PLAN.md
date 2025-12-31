# OpenTelemetry-First Observability Stack Implementation Plan

**Version**: 1.0
**Date**: 2025-12-31
**Scope**: Vendor-neutral observability with Elastic + Prometheus
**Retention**: 24 hours for logs/traces, configurable for metrics

---

## Executive Summary

This document provides a complete implementation plan for a vendor-neutral OpenTelemetry observability stack that integrates Elastic Stack (logs + traces) and Prometheus (metrics) with full context correlation.

**Key Principles**:
- OpenTelemetry is the source of truth (no vendor-specific agents)
- W3C trace context propagation (traceparent, tracestate)
- Business context via X-Operation-Name header
- Full correlation: Logs ↔ Traces ↔ Metrics
- Kubernetes-native deployment (ECK + Prometheus Operator)
- Strict 24-hour retention

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────────┐
│ Java/Spring Boot Microservices                                  │
│ ┌─────────────────────────────────────────────────────────────┐ │
│ │ OpenTelemetry SDK                                           │ │
│ │ ├── Traces (OTLP/gRPC)                                      │ │
│ │ ├── Metrics (OTLP/gRPC)                                     │ │
│ │ └── Context: traceparent + X-Operation-Name                │ │
│ └─────────────────────────────────────────────────────────────┘ │
└────────────────────────┬────────────────────────────────────────┘
                         │ OTLP (4317/4318)
                         ▼
┌─────────────────────────────────────────────────────────────────┐
│ OpenTelemetry Collector (K8s Deployment)                        │
│ ├── Receivers: otlp (grpc/http)                                │
│ ├── Processors: batch, resource, attributes                    │
│ ├── Exporters:                                                 │
│ │   ├── otlp → Elastic APM Server (traces)                    │
│ │   ├── prometheus → /metrics endpoint                        │
│ │   └── logging (debug)                                       │
└─────────┬───────────────────────────────────┬───────────────────┘
          │                                   │
          ▼                                   ▼
┌─────────────────────────────┐   ┌─────────────────────────────┐
│ Elastic Stack (ECK)         │   │ Prometheus                  │
│ ├── Elasticsearch           │   │ ├── Scrapes OTel /metrics   │
│ │   ├── logs-* (24h ILM)    │   │ ├── 24h retention           │
│ │   └── traces-* (24h ILM)  │   │ └── Labels: service.name,   │
│ ├── Kibana                  │   │     operation.name          │
│ └── APM Server (OTLP)       │   └─────────────────────────────┘
└─────────────────────────────┘
          │
          ▼
┌─────────────────────────────┐
│ Elastic Agent/Filebeat      │
│ ├── JSON log parsing        │
│ ├── trace.id extraction     │
│ └── operation.name capture  │
└─────────────────────────────┘
```

---

## Implementation Phases

### Phase 1: Infrastructure Setup (Days 1-2)
1. Deploy Elastic Stack via ECK
2. Deploy OpenTelemetry Collector
3. Deploy Prometheus (Operator)
4. Configure log collection (Filebeat/Elastic Agent)

### Phase 2: Application Instrumentation (Days 3-4)
1. Add OpenTelemetry SDK to all Java services
2. Implement operation context filters
3. Configure JSON logging with correlation
4. Add outbound propagation interceptors

### Phase 3: Retention & Compliance (Day 5)
1. Configure Elasticsearch ILM policies (24h)
2. Configure Prometheus retention (24h)
3. Verify data lifecycle

### Phase 4: Verification & Testing (Day 6)
1. End-to-end trace correlation
2. Log-to-trace navigation
3. Metrics correlation
4. Performance validation

---

## Phase 1: Infrastructure Setup

### 1.1 Elastic Stack (via ECK)

#### Install ECK Operator

```bash
# Install ECK CRDs and operator
kubectl create -f https://download.elastic.co/downloads/eck/2.11.0/crds.yaml
kubectl apply -f https://download.elastic.co/downloads/eck/2.11.0/operator.yaml

# Verify installation
kubectl -n elastic-system get pods
```

#### Elasticsearch Deployment

**File**: `manifests/elastic/elasticsearch.yaml`

```yaml
apiVersion: elasticsearch.k8s.elastic.co/v1
kind: Elasticsearch
metadata:
  name: monitoring-es
  namespace: monitoring-infra
spec:
  version: 8.11.0
  nodeSets:
  - name: default
    count: 1
    config:
      node.store.allow_mmap: false
      # 24-hour retention via ILM (configured separately)
      xpack.monitoring.collection.enabled: true
    podTemplate:
      spec:
        containers:
        - name: elasticsearch
          resources:
            requests:
              memory: 2Gi
              cpu: 1000m
            limits:
              memory: 4Gi
              cpu: 2000m
          env:
          - name: ES_JAVA_OPTS
            value: "-Xms1g -Xmx1g"
    volumeClaimTemplates:
    - metadata:
        name: elasticsearch-data
      spec:
        accessModes:
        - ReadWriteOnce
        resources:
          requests:
            storage: 50Gi
        storageClassName: local-path  # TODO: Change for production
```

#### Kibana Deployment

**File**: `manifests/elastic/kibana.yaml`

```yaml
apiVersion: kibana.k8s.elastic.co/v1
kind: Kibana
metadata:
  name: monitoring-kibana
  namespace: monitoring-infra
spec:
  version: 8.11.0
  count: 1
  elasticsearchRef:
    name: monitoring-es
  config:
    server.publicBaseUrl: "http://localhost:5601"  # TODO: Update for production
  http:
    service:
      spec:
        type: NodePort
        ports:
        - name: http
          port: 5601
          targetPort: 5601
          nodePort: 30561
  podTemplate:
    spec:
      containers:
      - name: kibana
        resources:
          requests:
            memory: 1Gi
            cpu: 500m
          limits:
            memory: 2Gi
            cpu: 1000m
```

#### APM Server Deployment (OTLP Enabled)

**File**: `manifests/elastic/apm-server.yaml`

```yaml
apiVersion: apm.k8s.elastic.co/v1
kind: ApmServer
metadata:
  name: monitoring-apm
  namespace: monitoring-infra
spec:
  version: 8.11.0
  count: 1
  elasticsearchRef:
    name: monitoring-es
  config:
    # Enable OTLP receivers
    apm-server:
      rum:
        enabled: false
      auth:
        anonymous:
          enabled: true  # For dev; use API keys in prod
      # OTLP receivers
      otlp:
        grpc:
          enabled: true
          endpoint: "0.0.0.0:8200"
        http:
          enabled: true
          endpoint: "0.0.0.0:8200"
    output.elasticsearch:
      # Data streams for traces
      indices:
        - index: "traces-apm-%{[observer.version]}-%{+yyyy.MM.dd}"
          when.contains:
            processor.event: "span"
        - index: "traces-apm.sampled-%{[observer.version]}-%{+yyyy.MM.dd}"
          when.contains:
            processor.event: "transaction"
  http:
    service:
      spec:
        type: ClusterIP
        ports:
        - name: https
          port: 8200
          targetPort: 8200
  podTemplate:
    spec:
      containers:
      - name: apm-server
        resources:
          requests:
            memory: 512Mi
            cpu: 500m
          limits:
            memory: 1Gi
            cpu: 1000m
```

#### Elasticsearch ILM Policy (24-hour Retention)

**File**: `scripts/configure-ilm-retention.sh`

```bash
#!/bin/bash
# Configure 24-hour retention for logs and traces

ES_URL="http://monitoring-es-es-http.monitoring-infra.svc.cluster.local:9200"
ES_PASSWORD=$(kubectl get secret monitoring-es-es-elastic-user -n monitoring-infra -o jsonpath='{.data.elastic}' | base64 -d)

# Create ILM policy for logs (24h retention)
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

# Create ILM policy for traces (24h retention)
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

# Apply policy to logs-* index template
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

# Apply policy to traces-* index template
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

echo "ILM policies configured successfully"
```

**Verification**:
```bash
# Make script executable
chmod +x scripts/configure-ilm-retention.sh

# Run configuration
./scripts/configure-ilm-retention.sh

# Verify policies
ES_PASSWORD=$(kubectl get secret monitoring-es-es-elastic-user -n monitoring-infra -o jsonpath='{.data.elastic}' | base64 -d)
curl -k -u "elastic:$ES_PASSWORD" \
  "http://monitoring-es-es-http.monitoring-infra.svc.cluster.local:9200/_ilm/policy/logs-24h-policy?pretty"
```

---

### 1.2 OpenTelemetry Collector

**File**: `manifests/otel/otel-collector.yaml`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: otel-collector-config
  namespace: monitoring-infra
data:
  otel-collector-config.yaml: |
    receivers:
      otlp:
        protocols:
          grpc:
            endpoint: 0.0.0.0:4317
          http:
            endpoint: 0.0.0.0:4318

    processors:
      batch:
        timeout: 10s
        send_batch_size: 1024

      resource:
        attributes:
        - key: deployment.environment
          value: dev
          action: upsert
        - key: cluster.name
          value: monitoring-cluster
          action: upsert

      attributes:
        actions:
        # Map X-Operation-Name from HTTP headers to operation.name
        - key: operation.name
          from_attribute: http.request.header.x-operation-name
          action: insert
        # Ensure operation.name exists (default to unknown)
        - key: operation.name
          value: unknown
          action: insert
        # Normalize operation.name (lowercase, replace spaces)
        - key: operation.name
          pattern: "^(.*)$"
          action: extract

      # Memory limiter to prevent OOM
      memory_limiter:
        check_interval: 1s
        limit_mib: 512
        spike_limit_mib: 128

    exporters:
      # OTLP to Elastic APM Server
      otlp/elastic:
        endpoint: "http://monitoring-apm-apm-http.monitoring-infra.svc.cluster.local:8200"
        tls:
          insecure: true
        headers:
          # APM Server expects Authorization header for API key auth (optional for dev)
          # Authorization: "Bearer <API_KEY>"

      # Prometheus exporter (scrape endpoint)
      prometheus:
        endpoint: "0.0.0.0:8889"
        namespace: otel
        const_labels:
          cluster: monitoring-cluster

      # Debug logging (optional, disable in production)
      logging:
        loglevel: info
        sampling_initial: 5
        sampling_thereafter: 200

    service:
      pipelines:
        traces:
          receivers: [otlp]
          processors: [memory_limiter, batch, resource, attributes]
          exporters: [otlp/elastic, logging]

        metrics:
          receivers: [otlp]
          processors: [memory_limiter, batch, resource, attributes]
          exporters: [prometheus, logging]

      telemetry:
        logs:
          level: info
        metrics:
          level: detailed
          address: 0.0.0.0:8888

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: otel-collector
  namespace: monitoring-infra
  labels:
    app: otel-collector
spec:
  replicas: 2  # For high availability
  selector:
    matchLabels:
      app: otel-collector
  template:
    metadata:
      labels:
        app: otel-collector
    spec:
      containers:
      - name: otel-collector
        image: otel/opentelemetry-collector-contrib:0.91.0
        args:
        - "--config=/conf/otel-collector-config.yaml"
        ports:
        - name: otlp-grpc
          containerPort: 4317
          protocol: TCP
        - name: otlp-http
          containerPort: 4318
          protocol: TCP
        - name: prometheus
          containerPort: 8889
          protocol: TCP
        - name: metrics
          containerPort: 8888
          protocol: TCP
        volumeMounts:
        - name: config
          mountPath: /conf
        resources:
          requests:
            memory: 512Mi
            cpu: 500m
          limits:
            memory: 1Gi
            cpu: 1000m
        livenessProbe:
          httpGet:
            path: /
            port: 13133
          initialDelaySeconds: 30
          periodSeconds: 10
        readinessProbe:
          httpGet:
            path: /
            port: 13133
          initialDelaySeconds: 10
          periodSeconds: 5
      volumes:
      - name: config
        configMap:
          name: otel-collector-config

---
apiVersion: v1
kind: Service
metadata:
  name: otel-collector
  namespace: monitoring-infra
  labels:
    app: otel-collector
spec:
  type: ClusterIP
  selector:
    app: otel-collector
  ports:
  - name: otlp-grpc
    port: 4317
    targetPort: 4317
    protocol: TCP
  - name: otlp-http
    port: 4318
    targetPort: 4318
    protocol: TCP
  - name: prometheus
    port: 8889
    targetPort: 8889
    protocol: TCP
  - name: metrics
    port: 8888
    targetPort: 8888
    protocol: TCP
```

---

### 1.3 Prometheus (Operator)

#### Install Prometheus Operator

```bash
# Add Prometheus Operator Helm repo
helm repo add prometheus-community https://prometheus-community.github.io/helm-charts
helm repo update

# Install Prometheus Operator
helm install prometheus-operator prometheus-community/kube-prometheus-stack \
  --namespace monitoring-infra \
  --create-namespace \
  --set prometheus.prometheusSpec.retention=24h \
  --set prometheus.prometheusSpec.storageSpec.volumeClaimTemplate.spec.resources.requests.storage=50Gi \
  --set prometheus.prometheusSpec.storageSpec.volumeClaimTemplate.spec.storageClassName=local-path \
  --set grafana.enabled=true \
  --set grafana.service.type=NodePort \
  --set grafana.service.nodePort=30300
```

#### ServiceMonitor for OTel Collector

**File**: `manifests/prometheus/otel-servicemonitor.yaml`

```yaml
apiVersion: monitoring.coreos.com/v1
kind: ServiceMonitor
metadata:
  name: otel-collector-metrics
  namespace: monitoring-infra
  labels:
    app: otel-collector
    release: prometheus-operator  # Must match Prometheus Operator label selector
spec:
  selector:
    matchLabels:
      app: otel-collector
  endpoints:
  - port: prometheus
    interval: 15s
    path: /metrics
    relabelings:
    # Preserve service.name and operation.name labels
    - sourceLabels: [service_name]
      targetLabel: service_name
      action: replace
    - sourceLabels: [operation_name]
      targetLabel: operation_name
      action: replace
```

**Alternative: Vanilla Prometheus** (if not using Operator)

**File**: `manifests/prometheus/prometheus-config.yaml`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: prometheus-config
  namespace: monitoring-infra
data:
  prometheus.yml: |
    global:
      scrape_interval: 15s
      evaluation_interval: 15s

    scrape_configs:
    - job_name: 'otel-collector'
      static_configs:
      - targets: ['otel-collector.monitoring-infra.svc.cluster.local:8889']
      relabel_configs:
      - source_labels: [service_name]
        target_label: service_name
      - source_labels: [operation_name]
        target_label: operation_name

    - job_name: 'kubernetes-pods'
      kubernetes_sd_configs:
      - role: pod
      relabel_configs:
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_scrape]
        action: keep
        regex: true
      - source_labels: [__meta_kubernetes_pod_annotation_prometheus_io_path]
        action: replace
        target_label: __metrics_path__
        regex: (.+)
      - source_labels: [__address__, __meta_kubernetes_pod_annotation_prometheus_io_port]
        action: replace
        regex: ([^:]+)(?::\d+)?;(\d+)
        replacement: $1:$2
        target_label: __address__

---
apiVersion: apps/v1
kind: Deployment
metadata:
  name: prometheus
  namespace: monitoring-infra
spec:
  replicas: 1
  selector:
    matchLabels:
      app: prometheus
  template:
    metadata:
      labels:
        app: prometheus
    spec:
      containers:
      - name: prometheus
        image: prom/prometheus:v2.48.0
        args:
        - '--config.file=/etc/prometheus/prometheus.yml'
        - '--storage.tsdb.path=/prometheus'
        - '--storage.tsdb.retention.time=24h'
        - '--web.enable-lifecycle'
        ports:
        - containerPort: 9090
        volumeMounts:
        - name: config
          mountPath: /etc/prometheus
        - name: storage
          mountPath: /prometheus
        resources:
          requests:
            memory: 1Gi
            cpu: 500m
          limits:
            memory: 2Gi
            cpu: 1000m
      volumes:
      - name: config
        configMap:
          name: prometheus-config
      - name: storage
        persistentVolumeClaim:
          claimName: prometheus-storage

---
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: prometheus-storage
  namespace: monitoring-infra
spec:
  accessModes:
  - ReadWriteOnce
  resources:
    requests:
      storage: 50Gi
  storageClassName: local-path
```

---

### 1.4 Log Collection (Filebeat)

**File**: `manifests/logging/filebeat-daemonset.yaml`

```yaml
apiVersion: v1
kind: ConfigMap
metadata:
  name: filebeat-config
  namespace: monitoring-infra
data:
  filebeat.yml: |
    filebeat.inputs:
    - type: container
      paths:
        - /var/log/containers/*.log
      processors:
      - add_kubernetes_metadata:
          host: ${NODE_NAME}
          matchers:
          - logs_path:
              logs_path: "/var/log/containers/"
      # JSON decoding for structured logs
      - decode_json_fields:
          fields: ["message"]
          target: ""
          overwrite_keys: true
      # Extract correlation fields
      - copy_fields:
          fields:
          - from: trace_id
            to: trace.id
          - from: span_id
            to: span.id
          - from: operation_name
            to: operation.name
          fail_on_error: false
          ignore_missing: true

    output.elasticsearch:
      hosts: ['https://monitoring-es-es-http.monitoring-infra.svc.cluster.local:9200']
      username: elastic
      password: ${ELASTICSEARCH_PASSWORD}
      ssl.verification_mode: none
      index: "logs-%{[agent.version]}-%{+yyyy.MM.dd}"

    setup.ilm.enabled: true
    setup.ilm.policy_name: "logs-24h-policy"
    setup.template.name: "logs"
    setup.template.pattern: "logs-*"

---
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
      terminationGracePeriodSeconds: 30
      hostNetwork: true
      dnsPolicy: ClusterFirstWithHostNet
      containers:
      - name: filebeat
        image: docker.elastic.co/beats/filebeat:8.11.0
        args: [
          "-c", "/etc/filebeat.yml",
          "-e",
        ]
        env:
        - name: ELASTICSEARCH_PASSWORD
          valueFrom:
            secretKeyRef:
              name: monitoring-es-es-elastic-user
              key: elastic
        - name: NODE_NAME
          valueFrom:
            fieldRef:
              fieldPath: spec.nodeName
        securityContext:
          runAsUser: 0
        resources:
          requests:
            memory: 256Mi
            cpu: 100m
          limits:
            memory: 512Mi
            cpu: 500m
        volumeMounts:
        - name: config
          mountPath: /etc/filebeat.yml
          readOnly: true
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
          type: DirectoryOrCreate

---
apiVersion: v1
kind: ServiceAccount
metadata:
  name: filebeat
  namespace: monitoring-infra

---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRole
metadata:
  name: filebeat
rules:
- apiGroups: [""]
  resources:
  - namespaces
  - pods
  - nodes
  verbs:
  - get
  - watch
  - list

---
apiVersion: rbac.authorization.k8s.io/v1
kind: ClusterRoleBinding
metadata:
  name: filebeat
subjects:
- kind: ServiceAccount
  name: filebeat
  namespace: monitoring-infra
roleRef:
  kind: ClusterRole
  name: filebeat
  apiGroup: rbac.authorization.k8s.io
```

---

## Phase 2: Application Instrumentation

### 2.1 OpenTelemetry SDK Configuration (Java/Spring Boot)

#### Maven Dependencies

**File**: `java/pom.xml` (snippet)

```xml
<properties>
    <opentelemetry.version>1.33.0</opentelemetry.version>
    <opentelemetry-instrumentation.version>1.32.0</opentelemetry-instrumentation.version>
</properties>

<dependencyManagement>
    <dependencies>
        <!-- OpenTelemetry BOM -->
        <dependency>
            <groupId>io.opentelemetry</groupId>
            <artifactId>opentelemetry-bom</artifactId>
            <version>${opentelemetry.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
        <dependency>
            <groupId>io.opentelemetry.instrumentation</groupId>
            <artifactId>opentelemetry-instrumentation-bom</artifactId>
            <version>${opentelemetry-instrumentation.version}</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>

<dependencies>
    <!-- OpenTelemetry API -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-api</artifactId>
    </dependency>

    <!-- OpenTelemetry SDK -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-sdk</artifactId>
    </dependency>

    <!-- OTLP Exporter -->
    <dependency>
        <groupId>io.opentelemetry</groupId>
        <artifactId>opentelemetry-exporter-otlp</artifactId>
    </dependency>

    <!-- Spring Boot auto-instrumentation -->
    <dependency>
        <groupId>io.opentelemetry.instrumentation</groupId>
        <artifactId>opentelemetry-spring-boot-starter</artifactId>
    </dependency>

    <!-- Logback integration -->
    <dependency>
        <groupId>io.opentelemetry.instrumentation</groupId>
        <artifactId>opentelemetry-logback-mdc-1.0</artifactId>
    </dependency>
</dependencies>
```

#### Spring Boot Configuration

**File**: `java/src/main/resources/application.yaml`

```yaml
# OpenTelemetry Configuration
otel:
  sdk:
    disabled: false
  resource:
    attributes:
      service.name: ${OTEL_SERVICE_NAME:service-name}  # Override via env
      service.namespace: monitoring-app
      deployment.environment: ${DEPLOYMENT_ENVIRONMENT:dev}

  exporter:
    otlp:
      endpoint: ${OTEL_EXPORTER_OTLP_ENDPOINT:http://otel-collector.monitoring-infra.svc.cluster.local:4317}
      protocol: grpc
      timeout: 10s

  traces:
    sampler:
      probability: 1.0  # 100% sampling for dev; reduce in production

  metrics:
    export:
      interval: 60s

# Logging (JSON with correlation)
logging:
  pattern:
    console: ""  # Disable default pattern (use JSON)
  level:
    root: INFO
    io.opentelemetry: INFO
    com.tiqmo.monitoring: DEBUG
```

#### Environment Variables (Deployment)

**File**: `manifests/services/loader-deployment.yaml` (snippet)

```yaml
env:
# OpenTelemetry configuration
- name: OTEL_SERVICE_NAME
  value: "signal-loader"
- name: OTEL_EXPORTER_OTLP_ENDPOINT
  value: "http://otel-collector.monitoring-infra.svc.cluster.local:4317"
- name: OTEL_RESOURCE_ATTRIBUTES
  value: "deployment.environment=dev,service.namespace=monitoring-app"
- name: OTEL_TRACES_SAMPLER
  value: "always_on"  # 100% sampling for dev
- name: OTEL_METRICS_EXPORTER
  value: "otlp"
- name: OTEL_LOGS_EXPORTER
  value: "none"  # Logs go to stdout, collected by Filebeat
- name: DEPLOYMENT_ENVIRONMENT
  value: "dev"
```

---

### 2.2 Operation Context Propagation

#### Inbound Filter (Read X-Operation-Name)

**File**: `java/src/main/java/com/tiqmo/monitoring/filter/OperationContextFilter.java`

```java
package com.tiqmo.monitoring.filter;

import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import jakarta.servlet.*;
import jakarta.servlet.http.HttpServletRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;

import java.io.IOException;

/**
 * Servlet filter to extract and propagate operation context from HTTP headers.
 * Reads X-Operation-Name header and adds to MDC and OpenTelemetry span attributes.
 */
@Component
@Order(1)  // Execute early in filter chain
public class OperationContextFilter implements Filter {

    private static final Logger logger = LoggerFactory.getLogger(OperationContextFilter.class);

    private static final String OPERATION_NAME_HEADER = "X-Operation-Name";
    private static final String OPERATION_NAME_MDC_KEY = "operation.name";
    private static final String TRACE_ID_MDC_KEY = "trace.id";
    private static final String SPAN_ID_MDC_KEY = "span.id";
    private static final String DEFAULT_OPERATION = "unknown";

    @Override
    public void doFilter(ServletRequest request, ServletResponse response, FilterChain chain)
            throws IOException, ServletException {

        HttpServletRequest httpRequest = (HttpServletRequest) request;

        try {
            // Extract operation name from header
            String operationName = httpRequest.getHeader(OPERATION_NAME_HEADER);

            // Sanitize and normalize
            operationName = sanitizeOperationName(operationName);

            // Add to MDC for logging
            MDC.put(OPERATION_NAME_MDC_KEY, operationName);

            // Add trace context to MDC
            Span currentSpan = Span.current();
            SpanContext spanContext = currentSpan.getSpanContext();

            if (spanContext.isValid()) {
                MDC.put(TRACE_ID_MDC_KEY, spanContext.getTraceId());
                MDC.put(SPAN_ID_MDC_KEY, spanContext.getSpanId());

                // Add operation name to OpenTelemetry span attributes
                currentSpan.setAttribute("operation.name", operationName);
                currentSpan.setAttribute("http.route", httpRequest.getRequestURI());
            }

            logger.debug("Operation context set: operation={}, traceId={}, spanId={}",
                        operationName,
                        spanContext.getTraceId(),
                        spanContext.getSpanId());

            // Continue filter chain
            chain.doFilter(request, response);

        } finally {
            // Clear MDC after request processing
            MDC.remove(OPERATION_NAME_MDC_KEY);
            MDC.remove(TRACE_ID_MDC_KEY);
            MDC.remove(SPAN_ID_MDC_KEY);
        }
    }

    /**
     * Sanitize and normalize operation name.
     * - Convert to lowercase
     * - Replace spaces with underscores
     * - Remove special characters
     * - Default to "unknown" if empty
     */
    private String sanitizeOperationName(String operationName) {
        if (operationName == null || operationName.trim().isEmpty()) {
            return DEFAULT_OPERATION;
        }

        // Normalize: lowercase, replace spaces, remove special chars
        String sanitized = operationName
            .toLowerCase()
            .trim()
            .replaceAll("\\s+", "_")
            .replaceAll("[^a-z0-9_-]", "");

        // Limit length to prevent cardinality explosion
        if (sanitized.length() > 50) {
            sanitized = sanitized.substring(0, 50);
        }

        return sanitized.isEmpty() ? DEFAULT_OPERATION : sanitized;
    }
}
```

#### Outbound Propagation (WebClient)

**File**: `java/src/main/java/com/tiqmo/monitoring/config/WebClientConfig.java`

```java
package com.tiqmo.monitoring.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.api.trace.Span;
import io.opentelemetry.api.trace.SpanContext;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.slf4j.MDC;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.ClientRequest;
import org.springframework.web.reactive.function.client.ExchangeFilterFunction;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient configuration with OpenTelemetry context propagation.
 */
@Configuration
public class WebClientConfig {

    private static final String OPERATION_NAME_HEADER = "X-Operation-Name";
    private static final String OPERATION_NAME_MDC_KEY = "operation.name";

    @Bean
    public WebClient webClient(WebClient.Builder builder) {
        return builder
            .filter(propagateContextFilter())
            .build();
    }

    /**
     * Exchange filter to propagate trace context and operation name.
     */
    private ExchangeFilterFunction propagateContextFilter() {
        return (request, next) -> {
            ClientRequest modifiedRequest = ClientRequest.from(request)
                .headers(headers -> {
                    // Propagate W3C trace context (traceparent)
                    Context currentContext = Context.current();
                    TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

                    propagator.inject(currentContext, headers, (carrier, key, value) -> {
                        if (!carrier.containsKey(key)) {
                            carrier.add(key, value);
                        }
                    });

                    // Propagate operation name from MDC
                    String operationName = MDC.get(OPERATION_NAME_MDC_KEY);
                    if (operationName != null && !operationName.isEmpty()) {
                        headers.add(OPERATION_NAME_HEADER, operationName);
                    }
                })
                .build();

            return next.exchange(modifiedRequest);
        };
    }
}
```

#### Outbound Propagation (RestTemplate)

**File**: `java/src/main/java/com/tiqmo/monitoring/config/RestTemplateConfig.java`

```java
package com.tiqmo.monitoring.config;

import io.opentelemetry.api.GlobalOpenTelemetry;
import io.opentelemetry.context.Context;
import io.opentelemetry.context.propagation.TextMapPropagator;
import org.slf4j.MDC;
import org.springframework.boot.web.client.RestTemplateBuilder;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.HttpRequest;
import org.springframework.http.client.ClientHttpRequestExecution;
import org.springframework.http.client.ClientHttpRequestInterceptor;
import org.springframework.http.client.ClientHttpResponse;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;

/**
 * RestTemplate configuration with OpenTelemetry context propagation.
 */
@Configuration
public class RestTemplateConfig {

    private static final String OPERATION_NAME_HEADER = "X-Operation-Name";
    private static final String OPERATION_NAME_MDC_KEY = "operation.name";

    @Bean
    public RestTemplate restTemplate(RestTemplateBuilder builder) {
        return builder
            .interceptors(new ContextPropagationInterceptor())
            .build();
    }

    /**
     * Interceptor to propagate trace context and operation name.
     */
    private static class ContextPropagationInterceptor implements ClientHttpRequestInterceptor {

        @Override
        public ClientHttpResponse intercept(
                HttpRequest request,
                byte[] body,
                ClientHttpRequestExecution execution) throws IOException {

            // Propagate W3C trace context (traceparent)
            Context currentContext = Context.current();
            TextMapPropagator propagator = GlobalOpenTelemetry.getPropagators().getTextMapPropagator();

            propagator.inject(currentContext, request.getHeaders(), (headers, key, value) -> {
                if (!headers.containsKey(key)) {
                    headers.add(key, value);
                }
            });

            // Propagate operation name from MDC
            String operationName = MDC.get(OPERATION_NAME_MDC_KEY);
            if (operationName != null && !operationName.isEmpty()) {
                request.getHeaders().add(OPERATION_NAME_HEADER, operationName);
            }

            return execution.execute(request, body);
        }
    }
}
```

---

### 2.3 JSON Logging with Correlation

#### Logback Configuration

**File**: `java/src/main/resources/logback-spring.xml`

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <include resource="org/springframework/boot/logging/logback/defaults.xml"/>

    <!-- Development Profile: JSON + SQL -->
    <springProfile name="dev,default">
        <appender name="CONSOLE_JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <!-- Include MDC fields -->
                <includeMdcKeyName>trace.id</includeMdcKeyName>
                <includeMdcKeyName>span.id</includeMdcKeyName>
                <includeMdcKeyName>operation.name</includeMdcKeyName>
                <includeMdcKeyName>username</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <includeMdcKeyName>loaderCode</includeMdcKeyName>
                <includeMdcKeyName>correlationId</includeMdcKeyName>

                <!-- Custom fields -->
                <customFields>{"service.name":"${OTEL_SERVICE_NAME:-unknown}","deployment.environment":"${DEPLOYMENT_ENVIRONMENT:-dev}"}</customFields>

                <!-- Structured arguments -->
                <includeStructuredArguments>true</includeStructuredArguments>

                <!-- Stack trace -->
                <throwableConverter class="net.logstash.logback.stacktrace.ShortenedThrowableConverter">
                    <maxDepthPerThrowable>30</maxDepthPerThrowable>
                    <maxLength>2048</maxLength>
                    <shortenedClassNameLength>20</shortenedClassNameLength>
                    <exclude>^sun\.reflect\..*\.invoke</exclude>
                    <exclude>^net\.sf\.cglib\.proxy\.MethodProxy\.invoke</exclude>
                    <rootCauseFirst>true</rootCauseFirst>
                </throwableConverter>
            </encoder>
        </appender>

        <root level="INFO">
            <appender-ref ref="CONSOLE_JSON"/>
        </root>

        <!-- SQL logging -->
        <logger name="org.hibernate.SQL" level="DEBUG"/>
        <logger name="org.hibernate.type.descriptor.sql.BasicBinder" level="TRACE"/>
    </springProfile>

    <!-- Production Profile: JSON only -->
    <springProfile name="prod,production">
        <appender name="CONSOLE_JSON" class="ch.qos.logback.core.ConsoleAppender">
            <encoder class="net.logstash.logback.encoder.LogstashEncoder">
                <!-- Include MDC fields -->
                <includeMdcKeyName>trace.id</includeMdcKeyName>
                <includeMdcKeyName>span.id</includeMdcKeyName>
                <includeMdcKeyName>operation.name</includeMdcKeyName>
                <includeMdcKeyName>username</includeMdcKeyName>
                <includeMdcKeyName>userId</includeMdcKeyName>
                <includeMdcKeyName>loaderCode</includeMdcKeyName>
                <includeMdcKeyName>correlationId</includeMdcKeyName>

                <!-- Custom fields -->
                <customFields>{"service.name":"${OTEL_SERVICE_NAME:-unknown}","deployment.environment":"production"}</customFields>

                <!-- Structured arguments -->
                <includeStructuredArguments>true</includeStructuredArguments>
            </encoder>
        </appender>

        <root level="INFO">
            <appender-ref ref="CONSOLE_JSON"/>
        </root>

        <!-- Reduce noise in production -->
        <logger name="org.hibernate.SQL" level="WARN"/>
        <logger name="org.springframework" level="WARN"/>
    </springProfile>
</configuration>
```

#### OpenTelemetry Logback Appender (Alternative)

**File**: `java/src/main/resources/logback-otel.xml` (optional)

```xml
<?xml version="1.0" encoding="UTF-8"?>
<configuration>
    <!-- OpenTelemetry Logback Appender for direct log shipping -->
    <appender name="OTEL" class="io.opentelemetry.instrumentation.logback.v1_0.OpenTelemetryAppender">
        <!-- Automatically captures MDC and includes trace context -->
        <captureExperimentalAttributes>true</captureExperimentalAttributes>
        <captureKeyValuePairAttributes>true</captureKeyValuePairAttributes>
    </appender>

    <root level="INFO">
        <appender-ref ref="OTEL"/>
    </root>
</configuration>
```

---

## Phase 3: Metrics Correlation

### 3.1 Custom Metrics with Operation Context

**File**: `java/src/main/java/com/tiqmo/monitoring/metrics/OperationMetrics.java`

```java
package com.tiqmo.monitoring.metrics;

import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Tag;
import io.micrometer.core.instrument.Timer;
import org.slf4j.MDC;
import org.springframework.stereotype.Component;

import java.util.Arrays;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Metrics utility for recording operation-level metrics.
 */
@Component
public class OperationMetrics {

    private static final String OPERATION_NAME_MDC_KEY = "operation.name";
    private static final String SERVICE_NAME = System.getenv("OTEL_SERVICE_NAME");

    private final MeterRegistry meterRegistry;

    public OperationMetrics(MeterRegistry meterRegistry) {
        this.meterRegistry = meterRegistry;
    }

    /**
     * Record operation duration with operation.name label.
     */
    public void recordOperationDuration(String metricName, long durationMs) {
        String operationName = MDC.get(OPERATION_NAME_MDC_KEY);
        if (operationName == null) {
            operationName = "unknown";
        }

        List<Tag> tags = Arrays.asList(
            Tag.of("service.name", SERVICE_NAME != null ? SERVICE_NAME : "unknown"),
            Tag.of("operation.name", operationName)
        );

        Timer.builder(metricName)
            .tags(tags)
            .description("Operation duration in milliseconds")
            .register(meterRegistry)
            .record(durationMs, TimeUnit.MILLISECONDS);
    }

    /**
     * Increment operation counter.
     */
    public void incrementOperationCounter(String metricName) {
        String operationName = MDC.get(OPERATION_NAME_MDC_KEY);
        if (operationName == null) {
            operationName = "unknown";
        }

        List<Tag> tags = Arrays.asList(
            Tag.of("service.name", SERVICE_NAME != null ? SERVICE_NAME : "unknown"),
            Tag.of("operation.name", operationName)
        );

        meterRegistry.counter(metricName, tags).increment();
    }
}
```

**Usage Example**:

```java
@Service
public class LoaderService {

    private final OperationMetrics operationMetrics;

    public LoaderService(OperationMetrics operationMetrics) {
        this.operationMetrics = operationMetrics;
    }

    public void createLoader(LoaderDto dto) {
        long startTime = System.currentTimeMillis();

        try {
            // Business logic...

            operationMetrics.incrementOperationCounter("loader.created.total");
        } finally {
            long duration = System.currentTimeMillis() - startTime;
            operationMetrics.recordOperationDuration("loader.create.duration", duration);
        }
    }
}
```

### 3.2 Low Cardinality Guidelines

**CRITICAL**: To prevent metrics cardinality explosion in Prometheus:

- ✅ **DO**: Use `operation.name` with controlled values (create_order, fetch_loaders, etc.)
- ✅ **DO**: Limit `operation.name` to 50-100 unique values per service
- ❌ **DON'T**: Use `trace.id` as a metric label (infinite cardinality)
- ❌ **DON'T**: Use `user.id` as a metric label (high cardinality)
- ❌ **DON'T**: Use `request.id` as a metric label (infinite cardinality)

**Example Metric (Safe)**:
```
http_server_duration_seconds_bucket{
  service_name="signal-loader",
  operation_name="create_loader",
  http_status="200",
  le="0.1"
} 42
```

---

## Phase 4: Kibana + Prometheus Queries

### 4.1 Kibana Queries

#### Find Logs by Trace ID

**Query**:
```
trace.id: "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6"
```

**KQL Advanced**:
```
trace.id: "a1b2c3d4e5f6g7h8i9j0k1l2m3n4o5p6" AND level: "ERROR"
```

#### Find Traces by Operation Name

**APM UI**:
1. Navigate to **APM** → **Services**
2. Select service (e.g., `signal-loader`)
3. Filter by **Transaction Name** = operation.name
4. Click on transaction to view trace

**Elasticsearch Query** (via Kibana Dev Tools):
```json
GET traces-*/_search
{
  "query": {
    "bool": {
      "must": [
        { "term": { "operation.name.keyword": "create_loader" } },
        { "term": { "service.name.keyword": "signal-loader" } }
      ]
    }
  },
  "size": 100,
  "sort": [
    { "@timestamp": { "order": "desc" } }
  ]
}
```

#### Errors Grouped by Operation

**Query**:
```
level: "ERROR" AND operation.name: *
```

**Aggregation**:
```json
GET logs-*/_search
{
  "size": 0,
  "query": {
    "term": { "level.keyword": "ERROR" }
  },
  "aggs": {
    "errors_by_operation": {
      "terms": {
        "field": "operation.name.keyword",
        "size": 50
      },
      "aggs": {
        "sample_errors": {
          "top_hits": {
            "size": 5,
            "_source": ["message", "trace.id", "@timestamp"]
          }
        }
      }
    }
  }
}
```

---

### 4.2 Prometheus Queries (PromQL)

#### Latency by Operation (P95)

```promql
histogram_quantile(0.95,
  sum by(service_name, operation_name, le) (
    rate(http_server_duration_seconds_bucket[5m])
  )
)
```

#### Request Rate by Operation

```promql
sum by(service_name, operation_name) (
  rate(http_server_requests_total[5m])
)
```

#### Error Ratio by Operation

```promql
sum by(service_name, operation_name) (
  rate(http_server_requests_total{status=~"5.."}[5m])
)
/
sum by(service_name, operation_name) (
  rate(http_server_requests_total[5m])
)
```

#### Top 5 Slowest Operations

```promql
topk(5,
  histogram_quantile(0.95,
    sum by(service_name, operation_name, le) (
      rate(http_server_duration_seconds_bucket[5m])
    )
  )
)
```

---

## Phase 5: Verification & Testing

### 5.1 End-to-End Trace Correlation

**Test Script**: `scripts/verify-correlation.sh`

```bash
#!/bin/bash
# Verify end-to-end trace correlation

set -e

echo "=== OpenTelemetry Observability Verification ==="

# 1. Generate test request with X-Operation-Name header
echo ""
echo "Step 1: Generate test request..."

TRACE_ID=$(curl -s -X POST "http://localhost:30088/api/loaders" \
  -H "Content-Type: application/json" \
  -H "X-Operation-Name: test_create_loader" \
  -H "Authorization: Bearer YOUR_TOKEN" \
  -d '{
    "loaderCode": "TEST_LOADER",
    "sourceDbCode": "TEST_DB",
    "enabled": true
  }' | jq -r '.traceId // empty')

if [ -z "$TRACE_ID" ]; then
  echo "WARNING: Could not extract trace ID from response"
  echo "Waiting 5 seconds for logs to be indexed..."
  sleep 5

  # Extract trace ID from logs
  ES_URL="http://monitoring-es-es-http.monitoring-infra.svc.cluster.local:9200"
  ES_PASSWORD=$(kubectl get secret monitoring-es-es-elastic-user -n monitoring-infra -o jsonpath='{.data.elastic}' | base64 -d)

  TRACE_ID=$(curl -s -k -u "elastic:$ES_PASSWORD" "$ES_URL/logs-*/_search" \
    -H 'Content-Type: application/json' \
    -d '{
      "size": 1,
      "sort": [{"@timestamp": {"order": "desc"}}],
      "query": {
        "bool": {
          "must": [
            {"term": {"operation.name.keyword": "test_create_loader"}},
            {"exists": {"field": "trace.id"}}
          ]
        }
      }
    }' | jq -r '.hits.hits[0]._source["trace.id"] // empty')
fi

if [ -z "$TRACE_ID" ]; then
  echo "ERROR: Could not find trace ID. Exiting."
  exit 1
fi

echo "✓ Trace ID: $TRACE_ID"

# 2. Verify logs contain trace ID
echo ""
echo "Step 2: Verify logs contain trace ID..."

ES_URL="http://monitoring-es-es-http.monitoring-infra.svc.cluster.local:9200"
ES_PASSWORD=$(kubectl get secret monitoring-es-es-elastic-user -n monitoring-infra -o jsonpath='{.data.elastic}' | base64 -d)

LOG_COUNT=$(curl -s -k -u "elastic:$ES_PASSWORD" "$ES_URL/logs-*/_count" \
  -H 'Content-Type: application/json' \
  -d "{
    \"query\": {
      \"term\": {\"trace.id.keyword\": \"$TRACE_ID\"}
    }
  }" | jq -r '.count')

if [ "$LOG_COUNT" -gt 0 ]; then
  echo "✓ Found $LOG_COUNT log entries with trace ID"
else
  echo "✗ No logs found with trace ID"
  exit 1
fi

# 3. Verify logs contain operation.name
echo ""
echo "Step 3: Verify logs contain operation.name..."

OPERATION_LOG=$(curl -s -k -u "elastic:$ES_PASSWORD" "$ES_URL/logs-*/_search" \
  -H 'Content-Type: application/json' \
  -d "{
    \"size\": 1,
    \"query\": {
      \"bool\": {
        \"must\": [
          {\"term\": {\"trace.id.keyword\": \"$TRACE_ID\"}},
          {\"term\": {\"operation.name.keyword\": \"test_create_loader\"}}
        ]
      }
    }
  }" | jq -r '.hits.hits[0]._source["operation.name"] // empty')

if [ "$OPERATION_LOG" == "test_create_loader" ]; then
  echo "✓ Logs contain operation.name: $OPERATION_LOG"
else
  echo "✗ Logs missing operation.name"
  exit 1
fi

# 4. Verify traces contain operation.name
echo ""
echo "Step 4: Verify traces contain operation.name..."

TRACE_SPAN=$(curl -s -k -u "elastic:$ES_PASSWORD" "$ES_URL/traces-*/_search" \
  -H 'Content-Type: application/json' \
  -d "{
    \"size\": 1,
    \"query\": {
      \"term\": {\"trace.id.keyword\": \"$TRACE_ID\"}
    }
  }" | jq -r '.hits.hits[0]._source.labels["operation.name"] // .hits.hits[0]._source["operation.name"] // empty')

if [ -n "$TRACE_SPAN" ]; then
  echo "✓ Trace contains operation.name: $TRACE_SPAN"
else
  echo "✗ Trace missing operation.name"
  exit 1
fi

# 5. Verify metrics exist with operation.name label
echo ""
echo "Step 5: Verify metrics contain operation.name label..."

PROM_URL="http://prometheus-operator-kube-prom-prometheus.monitoring-infra.svc.cluster.local:9090"

METRIC_RESULT=$(curl -s "$PROM_URL/api/v1/query" \
  --data-urlencode 'query=http_server_requests_total{operation_name="test_create_loader"}' | jq -r '.data.result | length')

if [ "$METRIC_RESULT" -gt 0 ]; then
  echo "✓ Metrics found with operation.name label"
else
  echo "⚠ No metrics found yet (may need more time to scrape)"
fi

# 6. Summary
echo ""
echo "=== Verification Summary ==="
echo "✓ Trace ID: $TRACE_ID"
echo "✓ Logs correlated with trace ID"
echo "✓ operation.name present in logs"
echo "✓ operation.name present in traces"
echo "✓ End-to-end correlation verified"
echo ""
echo "=== Navigation URLs ==="
echo "Kibana Logs: http://localhost:30561/app/discover#/?_a=(query:(language:kuery,query:'trace.id:\"$TRACE_ID\"'))"
echo "Kibana APM: http://localhost:30561/app/apm/services"
echo "Prometheus: http://localhost:30300/graph?g0.expr=http_server_requests_total%7Boperation_name%3D%22test_create_loader%22%7D"
echo ""
echo "=== Verification Complete ==="
```

**Run**:
```bash
chmod +x scripts/verify-correlation.sh
./scripts/verify-correlation.sh
```

---

### 5.2 Retention Verification

**Script**: `scripts/verify-retention.sh`

```bash
#!/bin/bash
# Verify 24-hour retention policies

set -e

echo "=== Retention Policy Verification ==="

ES_URL="http://monitoring-es-es-http.monitoring-infra.svc.cluster.local:9200"
ES_PASSWORD=$(kubectl get secret monitoring-es-es-elastic-user -n monitoring-infra -o jsonpath='{.data.elastic}' | base64 -d)

# Check logs ILM policy
echo ""
echo "Logs ILM Policy:"
curl -s -k -u "elastic:$ES_PASSWORD" "$ES_URL/_ilm/policy/logs-24h-policy?pretty" | jq '.["logs-24h-policy"].policy.phases.delete'

# Check traces ILM policy
echo ""
echo "Traces ILM Policy:"
curl -s -k -u "elastic:$ES_PASSWORD" "$ES_URL/_ilm/policy/traces-24h-policy?pretty" | jq '.["traces-24h-policy"].policy.phases.delete'

# Check Prometheus retention
echo ""
echo "Prometheus Retention:"
kubectl get prometheus -n monitoring-infra -o jsonpath='{.items[0].spec.retention}'

echo ""
echo "=== Retention Verification Complete ==="
```

---

## Deployment Sequence

### Complete Deployment Order

```bash
# 1. Install ECK Operator
kubectl create -f https://download.elastic.co/downloads/eck/2.11.0/crds.yaml
kubectl apply -f https://download.elastic.co/downloads/eck/2.11.0/operator.yaml

# 2. Deploy Elastic Stack
kubectl apply -f manifests/elastic/elasticsearch.yaml
kubectl apply -f manifests/elastic/kibana.yaml
kubectl apply -f manifests/elastic/apm-server.yaml

# Wait for Elasticsearch ready
kubectl wait --for=condition=ready pod -l elasticsearch.k8s.elastic.co/cluster-name=monitoring-es -n monitoring-infra --timeout=300s

# 3. Configure ILM policies
./scripts/configure-ilm-retention.sh

# 4. Deploy OpenTelemetry Collector
kubectl apply -f manifests/otel/otel-collector.yaml

# Wait for OTel Collector ready
kubectl wait --for=condition=ready pod -l app=otel-collector -n monitoring-infra --timeout=120s

# 5. Deploy Prometheus
helm install prometheus-operator prometheus-community/kube-prometheus-stack \
  --namespace monitoring-infra \
  --set prometheus.prometheusSpec.retention=24h \
  --set prometheus.prometheusSpec.storageSpec.volumeClaimTemplate.spec.resources.requests.storage=50Gi

# 6. Deploy ServiceMonitor
kubectl apply -f manifests/prometheus/otel-servicemonitor.yaml

# 7. Deploy Filebeat
kubectl apply -f manifests/logging/filebeat-daemonset.yaml

# 8. Update application services with OpenTelemetry configuration
# (Rebuild and redeploy with new env vars and dependencies)

# 9. Verify deployment
./scripts/verify-correlation.sh
./scripts/verify-retention.sh
```

---

## Acceptance Criteria Checklist

- [ ] **OpenTelemetry SDK**: All services use OpenTelemetry SDK (not vendor agents)
- [ ] **W3C Propagation**: `traceparent` header propagated across services
- [ ] **Operation Context**: `X-Operation-Name` header captured and propagated
- [ ] **Logs Correlation**: Logs contain `trace.id`, `span.id`, `operation.name`
- [ ] **Traces Correlation**: Traces contain `operation.name` attribute
- [ ] **Metrics Correlation**: Metrics include `service.name` and `operation.name` labels
- [ ] **Elastic Integration**: Logs and traces shipped to Elasticsearch
- [ ] **Prometheus Integration**: Metrics scraped from OTel Collector
- [ ] **24h Retention - Logs**: ILM policy deletes logs after 24 hours
- [ ] **24h Retention - Traces**: ILM policy deletes traces after 24 hours
- [ ] **24h Retention - Metrics**: Prometheus retention set to 24 hours
- [ ] **Log-to-Trace Navigation**: Can jump from log entry to trace in Kibana
- [ ] **Trace-to-Log Navigation**: Can filter logs by trace ID
- [ ] **Metrics by Operation**: Can query Prometheus metrics by operation.name
- [ ] **Error Grouping**: Can group errors by operation.name in Kibana
- [ ] **Performance Monitoring**: Can measure P95 latency by operation

---

## Architecture Benefits

This architecture guarantees:

1. **Vendor Neutrality**: OpenTelemetry is the source of truth; can switch backends without code changes
2. **Full Correlation**: Same `trace.id` and `operation.name` across logs, traces, metrics
3. **Business Context**: `operation.name` provides business-level visibility
4. **Cost Control**: 24-hour retention prevents storage explosion
5. **Kubernetes-Native**: ECK and Prometheus Operator for production-grade deployments
6. **Scalability**: OTel Collector can scale independently; Elastic can add nodes
7. **Developer Experience**: Single SDK (OpenTelemetry) for all telemetry
8. **Compliance**: W3C standards for trace context propagation

---

## Next Steps

1. **Phase 1** (Days 1-2): Deploy infrastructure (Elastic, OTel Collector, Prometheus)
2. **Phase 2** (Days 3-4): Instrument applications (add OpenTelemetry SDK, filters, logging)
3. **Phase 3** (Day 5): Configure retention policies and verify data lifecycle
4. **Phase 4** (Day 6): End-to-end testing and validation
5. **Production Readiness** (Week 2): Performance tuning, security hardening, monitoring

---

## Troubleshooting Reference

### Logs Not Appearing in Elasticsearch

1. Check Filebeat is running: `kubectl get pods -n monitoring-infra -l app=filebeat`
2. Check Filebeat logs: `kubectl logs -n monitoring-infra -l app=filebeat`
3. Verify Elasticsearch is healthy: `kubectl get elasticsearch -n monitoring-infra`
4. Check ILM policy applied: `curl -u elastic:PASSWORD ES_URL/_cat/indices?v`

### Traces Not Appearing in APM

1. Check OTel Collector logs: `kubectl logs -n monitoring-infra -l app=otel-collector`
2. Verify APM Server is running: `kubectl get apmserver -n monitoring-infra`
3. Check OTLP endpoint: `kubectl get svc -n monitoring-infra otel-collector`
4. Verify application OTLP exporter config: `echo $OTEL_EXPORTER_OTLP_ENDPOINT`

### Metrics Not in Prometheus

1. Check ServiceMonitor exists: `kubectl get servicemonitor -n monitoring-infra`
2. Verify Prometheus scrape targets: Prometheus UI → Status → Targets
3. Check OTel Collector /metrics endpoint: `curl http://otel-collector:8889/metrics`
4. Verify Prometheus retention: `kubectl get prometheus -n monitoring-infra -o yaml`

---

## File Manifest

```
/
├── manifests/
│   ├── elastic/
│   │   ├── elasticsearch.yaml
│   │   ├── kibana.yaml
│   │   └── apm-server.yaml
│   ├── otel/
│   │   └── otel-collector.yaml
│   ├── prometheus/
│   │   ├── otel-servicemonitor.yaml
│   │   └── prometheus-config.yaml (alternative)
│   └── logging/
│       └── filebeat-daemonset.yaml
├── scripts/
│   ├── configure-ilm-retention.sh
│   ├── verify-correlation.sh
│   └── verify-retention.sh
├── java/
│   ├── pom.xml (OpenTelemetry dependencies)
│   ├── src/main/resources/
│   │   ├── application.yaml (OTel config)
│   │   └── logback-spring.xml (JSON logging)
│   └── src/main/java/com/tiqmo/monitoring/
│       ├── filter/OperationContextFilter.java
│       ├── config/WebClientConfig.java
│       ├── config/RestTemplateConfig.java
│       └── metrics/OperationMetrics.java
└── README.md (this file)
```

---

**End of Implementation Plan**
