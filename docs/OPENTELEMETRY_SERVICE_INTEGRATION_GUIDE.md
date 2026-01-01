# OpenTelemetry Service Integration Guide

**Purpose**: Step-by-step guide to add OpenTelemetry instrumentation to Java Spring Boot services

**Date**: 2025-12-31
**Version**: 1.0

---

## Overview

This guide shows how to integrate OpenTelemetry SDK into existing Spring Boot microservices to enable vendor-neutral observability with full correlation across logs, traces, and metrics.

**Benefits**:
- Automatic trace context propagation (W3C traceparent header)
- Business operation tracking via X-Operation-Name header
- Full correlation: logs ↔ traces ↔ metrics via trace.id
- Vendor-neutral: switch from Elastic to Jaeger/Tempo without code changes

---

## Integration Steps

### Step 1: Add OpenTelemetry Dependencies to POM

**Location**: `services/{service-name}/pom.xml`

**Add to `<properties>` section**:
```xml
<properties>
    <opentelemetry.version>1.33.0</opentelemetry.version>
    <opentelemetry-instrumentation.version>1.32.0</opentelemetry-instrumentation.version>
</properties>
```

**Add to `<dependencyManagement>` section**:
```xml
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
```

**Add to `<dependencies>` section**:
```xml
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

<!-- Logback integration (adds trace context to MDC) -->
<dependency>
    <groupId>io.opentelemetry.instrumentation</groupId>
    <artifactId>opentelemetry-logback-mdc-1.0</artifactId>
</dependency>
```

---

### Step 2: Create OperationContextFilter

**Location**: `services/{service-name}/src/main/java/com/tiqmo/monitoring/{service}/filter/OperationContextFilter.java`

**Purpose**: Extracts X-Operation-Name header and adds to MDC + span attributes

Copy from template: `services/OperationContextFilter.java.template`

Replace `{SERVICE_NAME}` with actual service name (loader, gateway, auth, etc.)

**Key Features**:
- Reads `X-Operation-Name` header
- Sanitizes and normalizes operation name (lowercase, max 50 chars)
- Adds to SLF4J MDC for structured logging
- Adds to OpenTelemetry span attributes for trace correlation
- Automatically includes trace.id and span.id in MDC

---

### Step 3: Update application.yaml

**Location**: `services/{service-name}/src/main/resources/application.yaml`

**Add OpenTelemetry configuration**:
```yaml
# OpenTelemetry Configuration
otel:
  sdk:
    disabled: false
  resource:
    attributes:
      service.name: ${OTEL_SERVICE_NAME:service-name}
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
    console: ""  # Disable default pattern (use JSON from logback-spring.xml)
  level:
    root: INFO
    io.opentelemetry: INFO
    com.tiqmo.monitoring: DEBUG
```

---

### Step 4: Update Logback Configuration

**Location**: `services/{service-name}/src/main/resources/logback-spring.xml`

**Ensure MDC fields are included in JSON logs**:

```xml
<encoder class="net.logstash.logback.encoder.LogstashEncoder">
    <!-- Include OpenTelemetry MDC fields -->
    <includeMdcKeyName>trace.id</includeMdcKeyName>
    <includeMdcKeyName>span.id</includeMdcKeyName>
    <includeMdcKeyName>operation.name</includeMdcKeyName>

    <!-- Existing MDC fields -->
    <includeMdcKeyName>username</includeMdcKeyName>
    <includeMdcKeyName>userId</includeMdcKeyName>
    <includeMdcKeyName>loaderCode</includeMdcKeyName>

    <!-- Custom fields -->
    <customFields>{"service.name":"${OTEL_SERVICE_NAME:-unknown}","deployment.environment":"${DEPLOYMENT_ENVIRONMENT:-dev}"}</customFields>
</encoder>
```

**Note**: Loader, gateway, auth services already have logback-spring.xml configured. Just add the trace.id, span.id, and operation.name MDC keys.

---

### Step 5: Update Deployment YAML

**Location**: `services/{service-name}/k8s_manifist/{service}-deployment.yaml`

**Add OpenTelemetry environment variables**:
```yaml
env:
# OpenTelemetry configuration
- name: OTEL_SERVICE_NAME
  value: "signal-loader"  # Change per service
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

## Service-Specific Implementation Status

### ✅ Loader Service (signal-loader)

**Status**: ✅ COMPLETE - Implementation finished (2025-12-31)

**Files Modified**:
1. ✅ `services/loader/pom.xml` - Added OpenTelemetry dependencies (versions 1.33.0 / 1.32.0)
2. ✅ `services/loader/src/main/java/com/tiqmo/monitoring/loader/filter/OperationContextFilter.java` - Created
3. ✅ `services/loader/src/main/resources/application.yaml` - Added OTel config
4. ✅ `services/loader/src/main/resources/logback-spring.xml` - Added trace MDC keys (trace.id, span.id, operation.name)
5. ✅ `services/loader/k8s_manifist/loader-deployment.yaml` - Added OTel env vars

**Implementation Details**:
- OpenTelemetry SDK 1.33.0 with OTLP gRPC exporter
- OperationContextFilter extracts X-Operation-Name header
- Automatic trace.id and span.id injection to MDC
- 100% trace sampling (always_on) for development
- Service name: signal-loader
- OTLP endpoint: otel-collector.monitoring-infra.svc.cluster.local:4317

**Commands to deploy**:
```bash
cd /Volumes/Files/Projects/newLoader/services/loader
mvn clean package -Dmaven.test.skip=true
cp target/loader-0.0.1-SNAPSHOT.jar target/signal-loader-0.0.1-SNAPSHOT.jar
docker build -t signal-loader:latest .
kubectl rollout restart deployment/signal-loader -n monitoring-app
kubectl rollout status deployment/signal-loader -n monitoring-app --timeout=120s
```

---

### ✅ Gateway Service

**Status**: ✅ COMPLETE - Implementation finished (2025-12-31)

**Files Modified**:
1. ✅ `services/gateway/pom.xml` - Added OpenTelemetry dependencies (versions 1.33.0 / 1.32.0)
2. ✅ `services/gateway/src/main/java/com/tiqmo/monitoring/gateway/filter/OperationContextWebFilter.java` - Created (Reactive WebFilter)
3. ✅ `services/gateway/src/main/resources/application.yaml` - Added OTel config
4. ✅ `services/gateway/src/main/resources/logback-spring.xml` - Added trace MDC keys (trace.id, span.id, operation.name)
5. ✅ `services/gateway/k8s_manifist/gateway-deployment.yaml` - Added OTel env vars

**Implementation Details**:
- OpenTelemetry SDK 1.33.0 with OTLP gRPC exporter
- **OperationContextWebFilter** (Reactive WebFilter for Spring Cloud Gateway/WebFlux)
- Key difference from loader: Uses `Mono<Void> filter()` instead of `void doFilter()`
- Automatic trace.id and span.id injection to MDC
- 100% trace sampling (always_on) for production
- Service name: gateway-service
- OTLP endpoint: otel-collector.monitoring-infra.svc.cluster.local:4317
- Deployment: 2 replicas for high availability

**Commands to deploy**:
```bash
cd /Volumes/Files/Projects/newLoader/services/gateway
docker build -t gateway-service:latest .
kubectl rollout restart deployment/gateway-service -n monitoring-app
kubectl rollout status deployment/gateway-service -n monitoring-app --timeout=120s
```

**Service Name**: `gateway-service`

---

### ⏳ Auth Service

**Status**: Pending

**Required Changes**:
1. Add OpenTelemetry dependencies to `services/auth-service/pom.xml`
2. Create `services/auth-service/src/main/java/com/tiqmo/monitoring/auth/filter/OperationContextFilter.java`
3. Update `services/auth-service/src/main/resources/application.yaml`
4. Update `services/auth-service/src/main/resources/logback-spring.xml` (add trace MDC keys)
5. Update `services/auth-service/k8s/deployment.yaml`

**Service Name**: `auth-service`

---

### ⏳ Import-Export Service

**Status**: Pending

**Required Changes**:
1. Add OpenTelemetry dependencies to `services/import-export-service/pom.xml`
2. Create `services/import-export-service/src/main/java/com/tiqmo/monitoring/importexport/filter/OperationContextFilter.java`
3. Update `services/import-export-service/src/main/resources/application.yaml`
4. Update `services/import-export-service/src/main/resources/logback-spring.xml` (add trace MDC keys)
5. Update `services/import-export-service/k8s_manifist/import-export-deployment.yaml`

**Service Name**: `import-export-service`

---

### ⏳ Data Generator

**Status**: Pending (Low Priority - test data service)

**Required Changes**:
1. Add OpenTelemetry dependencies to `services/dataGenerator/pom.xml`
2. Create `services/dataGenerator/src/main/java/com/tiqmo/monitoring/datagenerator/filter/OperationContextFilter.java`
3. Update `services/dataGenerator/src/main/resources/application.yaml`
4. Update `services/dataGenerator/k8s_manifist/data-generator-deployment.yaml`

**Service Name**: `data-generator`

---

## Testing OpenTelemetry Integration

### Verify Traces

**1. Generate test traffic**:
```bash
# Login to get JWT token
TOKEN=$(curl -X POST http://localhost:30088/api/v1/auth/login \
  -H "Content-Type: application/json" \
  -H "X-Operation-Name: user_login" \
  -d '{"username":"admin","password":"admin123"}' | jq -r '.token')

# Make API call with operation name
curl -H "Authorization: Bearer $TOKEN" \
  -H "X-Operation-Name: fetch_loaders" \
  http://localhost:30088/api/v1/res/loaders/loaders
```

**2. Check logs for trace correlation**:
```bash
kubectl logs -n monitoring-app -l app=signal-loader --tail=20 | grep -o 'trace.id":"[^"]*"' | head -1
```

**3. View traces in Kibana APM**:
```
http://localhost:30561/app/apm/services
```

**4. Query Elasticsearch for logs with trace.id**:
```bash
ES_PASSWORD=$(kubectl get secret monitoring-es-es-elastic-user -n monitoring-infra -o jsonpath='{.data.elastic}' | base64 -d)

curl -k -u "elastic:$ES_PASSWORD" \
  "http://monitoring-es-es-http.monitoring-infra.svc.cluster.local:9200/logs-*/_search?pretty" \
  -H 'Content-Type: application/json' \
  -d '{
    "size": 5,
    "query": {
      "bool": {
        "must": [
          {"exists": {"field": "trace.id"}},
          {"term": {"operation.name.keyword": "fetch_loaders"}}
        ]
      }
    }
  }'
```

---

## Troubleshooting

### Issue: No traces appearing in APM

**Check**:
```bash
# 1. Verify OTel Collector is running
kubectl get pods -n monitoring-infra -l app=otel-collector

# 2. Check OTel Collector logs
kubectl logs -n monitoring-infra -l app=otel-collector --tail=50

# 3. Verify service has OTEL_EXPORTER_OTLP_ENDPOINT
kubectl exec -n monitoring-app deployment/signal-loader -- env | grep OTEL

# 4. Check service logs for OpenTelemetry initialization
kubectl logs -n monitoring-app -l app=signal-loader | grep -i opentelemetry
```

### Issue: trace.id not in logs

**Check**:
```bash
# 1. Verify logback-spring.xml has MDC keys
kubectl exec -n monitoring-app deployment/signal-loader -- \
  cat /app/classes/logback-spring.xml | grep "trace.id"

# 2. Check if OpenTelemetry Logback appender is loaded
kubectl logs -n monitoring-app -l app=signal-loader | grep "LogbackMdc"
```

### Issue: operation.name not appearing

**Check**:
```bash
# 1. Verify OperationContextFilter is loaded
kubectl logs -n monitoring-app -l app=signal-loader | grep "OperationContextFilter"

# 2. Check if X-Operation-Name header is sent
curl -v -H "X-Operation-Name: test" http://localhost:30088/api/v1/res/loaders/loaders

# 3. Check filter order
kubectl logs -n monitoring-app -l app=signal-loader | grep "@Order"
```

---

## Rollout Strategy

**Phase 1** (Complete): Infrastructure deployment
- ECK Operator, Elasticsearch, Kibana, APM Server
- OpenTelemetry Collector
- Filebeat
- ILM policies

**Phase 2** (In Progress): Application instrumentation
- Loader service (example implementation)
- Gateway service
- Auth service
- Import-Export service

**Phase 3** (Future): Advanced features
- Custom metrics with operation.name labels
- Performance monitoring dashboards
- Alert rules based on operation.name

---

## Next Steps

1. **Test loader service** with OpenTelemetry enabled
2. **Verify traces** appear in Kibana APM
3. **Confirm correlation** between logs and traces
4. **Roll out to gateway** service
5. **Roll out to auth** service
6. **Roll out to import-export** service
7. **Create dashboards** in Kibana for operation-level metrics

---

## References

- OpenTelemetry Java SDK: https://opentelemetry.io/docs/instrumentation/java/
- Spring Boot Integration: https://opentelemetry.io/docs/instrumentation/java/automatic/spring-boot/
- Elastic APM OTLP: https://www.elastic.co/guide/en/apm/guide/current/otlp.html
- W3C Trace Context: https://www.w3.org/TR/trace-context/

---

**End of Integration Guide**
