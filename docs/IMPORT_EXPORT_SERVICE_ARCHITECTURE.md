# Import-Export Service - Microservice Architecture

## Overview

The **loader-import-export-service** is a dedicated Spring Boot microservice responsible for bulk import/export operations of loader configurations. It's designed to scale independently from the core loader-service and handle heavy file processing without impacting scheduler performance.

## Service Responsibilities

### Import Operations
- Parse Excel files (.xlsx) uploaded by ADMIN users
- Validate loader data against business rules
- Create/update loaders via loader-service REST API
- Store uploaded files in PVC for audit trail
- Generate error files for failed rows
- Log all import operations in audit table

### Export Operations
- Currently handled client-side (frontend generates Excel)
- Future enhancement: Server-side export for large datasets

## Architecture Diagram

```
┌─────────────────────────────────────────────────────────────┐
│                         Frontend                             │
│  - ImportExportModal (Export: client-side, Import: upload)  │
└──────────────────┬─────────────────────────────────┬────────┘
                   │                                  │
                   │ Excel Export                     │ Excel Upload
                   │ (Client-side)                    │ (Multipart)
                   │                                  │
                   ▼                                  ▼
         ┌──────────────────┐              ┌─────────────────────────┐
         │  Browser XLSX    │              │  import-export-service  │
         │    Library       │              │   (Port: 8082)          │
         └──────────────────┘              │  ┌───────────────────┐  │
                                           │  │  ImportController │  │
                                           │  │  - POST /import   │  │
                                           │  │  - POST /validate │  │
                                           │  │  - GET  /template │  │
                                           │  └─────────┬─────────┘  │
                                           │            │             │
                                           │            ▼             │
                                           │  ┌───────────────────┐  │
                                           │  │  ImportService    │  │
                                           │  │  - Parse Excel    │  │
                                           │  │  - Validate data  │  │
                                           │  │  - Call loader-   │  │
                                           │  │    service API    │  │
                                           │  └─────────┬─────────┘  │
                                           │            │             │
                                           └────────────┼─────────────┘
                                                        │
                        ┌───────────────────────────────┼───────────────────────────────┐
                        │                               │                               │
                        ▼                               ▼                               ▼
              ┌──────────────────┐          ┌─────────────────────┐         ┌──────────────────┐
              │  loader-service  │          │   PostgreSQL DB     │         │  PVC Storage     │
              │   (Port: 8080)   │          │  - loader schema    │         │  /app/imports    │
              │                  │          │  - loader_version   │         │  - Excel files   │
              │  POST /loaders   │          │  - import_audit_log │         │  - Error files   │
              │  PUT  /loaders   │          └─────────────────────┘         └──────────────────┘
              └──────────────────┘
```

## Technology Stack

### Core Framework
- **Spring Boot**: 3.5.6
- **Java**: 21
- **Maven**: Multi-module project

### Key Dependencies
- **Apache POI**: 5.2.5 (Excel parsing)
- **Spring WebClient**: REST client for loader-service communication
- **Spring Data JPA**: Database access
- **Flyway**: Database migrations
- **Resilience4j**: Circuit breaker for loader-service calls

### Shared Modules
- **loader-common-dto**: Shared DTOs between services (via Maven dependency)

## Project Structure

```
newLoader/
├── services/
│   ├── loader/                          # Existing service (Port: 8080)
│   ├── import-export-service/           # New service (Port: 8082)
│   │   ├── pom.xml
│   │   ├── src/
│   │   │   ├── main/
│   │   │   │   ├── java/com/tiqmo/monitoring/importexport/
│   │   │   │   │   ├── ImportExportApplication.java
│   │   │   │   │   ├── api/
│   │   │   │   │   │   └── ImportController.java
│   │   │   │   │   ├── service/
│   │   │   │   │   │   ├── ImportService.java
│   │   │   │   │   │   ├── ExcelParser.java
│   │   │   │   │   │   ├── LoaderServiceClient.java
│   │   │   │   │   │   └── FileStorageService.java
│   │   │   │   │   ├── domain/
│   │   │   │   │   │   ├── entity/
│   │   │   │   │   │   │   ├── ImportAuditLog.java
│   │   │   │   │   │   │   └── LoaderVersion.java
│   │   │   │   │   │   └── repo/
│   │   │   │   │   │       ├── ImportAuditLogRepository.java
│   │   │   │   │   │       └── LoaderVersionRepository.java
│   │   │   │   │   ├── dto/
│   │   │   │   │   │   ├── ImportRequest.java
│   │   │   │   │   │   ├── ImportResponse.java
│   │   │   │   │   │   └── ValidationError.java
│   │   │   │   │   ├── config/
│   │   │   │   │   │   ├── WebClientConfig.java
│   │   │   │   │   │   └── FileStorageProperties.java
│   │   │   │   │   └── exception/
│   │   │   │   │       ├── ExcelParsingException.java
│   │   │   │   │       └── LoaderServiceException.java
│   │   │   │   └── resources/
│   │   │   │       ├── application.yaml
│   │   │   │       └── db/migration/
│   │   │   │           ├── V14__create_import_audit_log.sql
│   │   │   │           └── V15__create_loader_version.sql
│   │   │   └── test/
│   │   ├── k8s_manifist/
│   │   │   └── import-export-deployment.yaml
│   │   └── Dockerfile
│   └── loader-common-dto/               # Shared DTOs
│       ├── pom.xml
│       └── src/main/java/com/tiqmo/monitoring/common/dto/
│           ├── EtlLoaderDto.java
│           ├── SourceDatabaseDto.java
│           └── ApprovalStatus.java
└── infra/
    └── pvc/
        └── loader-imports-pvc.yaml      # PVC for file storage
```

## Database Schema

### New Tables (Created by import-export-service)

#### import_audit_log
```sql
CREATE TABLE loader.import_audit_log (
    id BIGSERIAL PRIMARY KEY,
    file_name VARCHAR(255) NOT NULL,
    file_path VARCHAR(512) NOT NULL,
    file_size_bytes BIGINT NOT NULL,
    import_label VARCHAR(255),
    imported_by VARCHAR(255) NOT NULL,
    imported_at TIMESTAMP NOT NULL DEFAULT NOW(),
    total_rows INT NOT NULL,
    success_count INT NOT NULL DEFAULT 0,
    failure_count INT NOT NULL DEFAULT 0,
    validation_errors TEXT,  -- JSON array
    dry_run BOOLEAN NOT NULL DEFAULT false,
    CONSTRAINT chk_counts CHECK (success_count + failure_count <= total_rows)
);
```

#### loader_version
```sql
CREATE TABLE loader.loader_version (
    id BIGSERIAL PRIMARY KEY,
    loader_code VARCHAR(255) NOT NULL,
    version_number INT NOT NULL,
    version_status VARCHAR(50) NOT NULL,  -- DRAFT, APPROVED, REJECTED
    change_type VARCHAR(50) NOT NULL,     -- IMPORT_UPDATE, MANUAL_EDIT, RESUBMIT

    -- Definition columns (versioned)
    loader_sql TEXT,
    min_interval_seconds INT,
    max_interval_seconds INT,
    max_query_period_seconds INT,
    max_parallel_executions INT,
    purge_strategy VARCHAR(50),
    source_timezone_offset_hours INT,
    aggregation_period_seconds INT,
    source_database_id BIGINT,

    -- Audit columns
    created_by VARCHAR(255) NOT NULL,
    created_at TIMESTAMP NOT NULL DEFAULT NOW(),
    approved_by VARCHAR(255),
    approved_at TIMESTAMP,
    rejected_by VARCHAR(255),
    rejected_at TIMESTAMP,
    rejection_reason TEXT,

    -- Metadata
    import_label VARCHAR(255),
    change_summary TEXT,

    CONSTRAINT fk_loader FOREIGN KEY (loader_code) REFERENCES loader.loader(loader_code),
    CONSTRAINT uk_loader_version UNIQUE (loader_code, version_number),
    CONSTRAINT chk_version_status CHECK (version_status IN ('DRAFT', 'APPROVED', 'REJECTED')),
    CONSTRAINT chk_change_type CHECK (change_type IN ('IMPORT_UPDATE', 'MANUAL_EDIT', 'RESUBMIT'))
);
```

## API Endpoints

### Import-Export Service (Port: 8082)

| Method | Endpoint | Auth | Description |
|--------|----------|------|-------------|
| POST | `/api/v1/import/upload` | ADMIN | Upload Excel file for import |
| POST | `/api/v1/import/validate` | ADMIN | Validate Excel file (dry-run) |
| GET | `/api/v1/import/template` | Any | Download Excel template |
| GET | `/api/v1/import/audit` | ADMIN | Get import audit trail |
| GET | `/api/v1/import/audit/{id}/file` | ADMIN | Download original import file |
| GET | `/api/v1/import/audit/{id}/errors` | ADMIN | Download error report CSV |

### Request/Response Examples

#### POST /api/v1/import/upload
```json
// Request: multipart/form-data
{
  "file": "<Excel file>",
  "importLabel": "2024-12-Migration",
  "dryRun": false
}

// Response: 200 OK
{
  "auditId": 123,
  "totalRows": 100,
  "successCount": 95,
  "failureCount": 5,
  "createdLoaders": ["LOADER_001", "LOADER_002", ...],
  "validationErrors": [
    {
      "row": 5,
      "field": "loaderCode",
      "error": "Loader code cannot be empty"
    },
    {
      "row": 12,
      "field": "sourceDatabaseId",
      "error": "Source database ID 999 not found"
    }
  ],
  "errorFileDownloadUrl": "/api/v1/import/audit/123/errors"
}
```

## Inter-Service Communication

### Import-Export → Loader Service

The import-export-service communicates with loader-service via REST API using Spring WebClient.

**Configuration:**
```yaml
loader-service:
  base-url: http://signal-loader:8080
  timeout:
    connect: 5s
    read: 30s
  retry:
    max-attempts: 3
    backoff-delay: 1s
```

**Circuit Breaker:**
```java
@CircuitBreaker(name = "loader-service", fallbackMethod = "createLoaderFallback")
public EtlLoaderDto createLoader(EtlLoaderDto loaderDto) {
    return webClient.post()
        .uri("/api/v1/res/loaders")
        .bodyValue(loaderDto)
        .retrieve()
        .bodyToMono(EtlLoaderDto.class)
        .block();
}
```

## File Storage (PVC)

### PVC Configuration
```yaml
apiVersion: v1
kind: PersistentVolumeClaim
metadata:
  name: loader-imports-pvc
  namespace: monitoring-app
spec:
  accessModes:
    - ReadWriteOnce
  resources:
    requests:
      storage: 10Gi
  storageClassName: standard
```

### File Naming Convention
```
/app/imports/
├── import-1767024268-admin-loaders-2024-12.xlsx
├── import-1767024300-admin-loaders-2025-01.xlsx
└── errors/
    ├── import-errors-1767024268.xlsx
    └── import-errors-1767024300.xlsx
```

### File Retention
- Import files: Retained indefinitely for audit compliance
- Error files: Retained for 90 days (configurable)
- Manual cleanup by DBA if storage exceeds threshold

## Deployment

### Kubernetes Resources

**Service:**
- Name: `loader-import-export`
- Port: 8082 (HTTP)
- Type: ClusterIP (internal only)

**Deployment:**
- Replicas: 2 (for HA)
- Resource Requests: CPU 200m, Memory 512Mi
- Resource Limits: CPU 1000m, Memory 1Gi
- Liveness Probe: `/actuator/health/liveness`
- Readiness Probe: `/actuator/health/readiness`

**Volume Mounts:**
```yaml
volumeMounts:
  - name: imports-storage
    mountPath: /app/imports
volumes:
  - name: imports-storage
    persistentVolumeClaim:
      claimName: loader-imports-pvc
```

## Security

### Authentication & Authorization
- JWT token validation (shared secret with loader-service)
- Role-based access control: **ADMIN only** for import operations
- Protected fields filtered during import based on user role

### Input Validation
- File size limit: 10MB per upload
- File type validation: Only .xlsx accepted
- Excel structure validation: Required columns must exist
- Business rule validation: loaderCode unique, sourceDatabaseId exists, etc.

### Audit Trail
- All import operations logged in `import_audit_log`
- Original files retained in PVC
- Error files generated for failed imports
- No PII in logs or error messages

## Error Handling

### Excel Parsing Errors
```json
{
  "error": "EXCEL_PARSING_ERROR",
  "message": "Invalid Excel structure",
  "details": "Missing required column: loaderCode"
}
```

### Validation Errors
```json
{
  "error": "VALIDATION_ERROR",
  "message": "5 validation errors found",
  "validationErrors": [
    {
      "row": 5,
      "field": "loaderCode",
      "error": "Loader code cannot be empty"
    }
  ]
}
```

### Loader Service Errors
```json
{
  "error": "LOADER_SERVICE_ERROR",
  "message": "Failed to create loader",
  "details": "Circuit breaker OPEN - loader-service unavailable"
}
```

## Monitoring & Observability

### Metrics (Actuator + Prometheus)
- `import.requests.total` - Total import requests
- `import.success.count` - Successful imports
- `import.failure.count` - Failed imports
- `import.validation.errors.count` - Validation errors
- `loader.service.calls.total` - Calls to loader-service
- `loader.service.calls.failed` - Failed calls to loader-service

### Health Checks
- `/actuator/health` - Overall service health
- `/actuator/health/liveness` - Liveness probe
- `/actuator/health/readiness` - Readiness probe (checks loader-service connectivity)

### Logging
- Import operations: INFO level
- Validation errors: WARN level
- Excel parsing errors: ERROR level
- Loader service errors: ERROR level with stack trace

## Performance Considerations

### Scalability
- Horizontal scaling: Increase replicas for high import volume
- Vertical scaling: Increase memory for large Excel files
- Async processing: Consider RabbitMQ/Kafka for very large imports (future)

### Resource Limits
- Max file size: 10MB (configurable)
- Max rows per import: 10,000 (configurable)
- Timeout: 60s per import operation

### Optimization
- Stream processing for large Excel files (Apache POI streaming)
- Batch loader creation (bulk API calls to loader-service)
- Caching of source database IDs (reduce DB queries)

## Future Enhancements

1. **Async Import**: Queue-based processing for large files
2. **Server-Side Export**: Generate Excel server-side for large datasets
3. **Import Scheduling**: Schedule imports from SFTP/S3
4. **Data Transformation**: Apply transformations during import (e.g., SQL templates)
5. **Multi-Tenancy**: Support imports for different organizations
6. **Version Comparison**: UI for comparing loader versions side-by-side

## Migration Strategy

### Phase 1: Infrastructure (Current)
- Create import-export-service project
- Create database migrations (V14, V15)
- Deploy PVC for file storage

### Phase 2: Import Backend
- Implement Excel parsing
- Implement validation logic
- Implement loader-service client
- Implement file storage

### Phase 3: Import Frontend
- Update ImportExportModal with file upload UI
- Implement import summary modal
- Implement error file download

### Phase 4: Deployment & Testing
- Deploy import-export-service to K8s
- End-to-end testing
- Performance testing
- Security audit

### Phase 5: Production Rollout
- Blue-green deployment
- Monitoring setup
- Documentation update
- User training

## Rollback Plan

If import-export-service fails in production:
1. Scale down import-export-service deployment
2. Frontend automatically disables Import tab (service unavailable)
3. Manual loader creation via UI still works
4. Export functionality unaffected (client-side)
5. Fix issue, redeploy, scale up

## Dependencies Matrix

| Service | Depends On | Communication | Fallback |
|---------|------------|---------------|----------|
| import-export-service | loader-service | REST (WebClient) | Circuit breaker returns error |
| import-export-service | PostgreSQL | JDBC | Connection pool retry |
| import-export-service | PVC | File I/O | Fail fast, return error |
| Frontend | import-export-service | REST (fetch) | Show error toast |
| Frontend | Browser XLSX lib | Client-side | Export disabled if lib fails |

---

**Author**: Hassan Rawashdeh
**Date**: 2025-12-29
**Version**: 1.0.0