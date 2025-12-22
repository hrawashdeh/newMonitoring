# ETL Initializer Service

## Overview

The ETL Initializer is a **long-running scheduled service** that manages both database schema and configuration data:

1. **Schema Management** - Flyway migrations run automatically on startup (DDL)
2. **Data Loading** - YAML files uploaded at runtime via kubectl cp (DML)
3. **Version Tracking** - Incremental loading without duplicates
4. **File Monitoring** - Scans for new YAML files every 60 seconds

**Architecture Change**: Refactored from one-time Kubernetes Job to continuous Deployment for runtime configuration updates.

---

## Quick Links

📚 **Documentation**:
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Complete architecture and design
- **[FLYWAY_VS_YAML.md](FLYWAY_VS_YAML.md)** - ⭐ **What goes where? Flyway vs YAML explained**
- **[DIRECTORY_STRUCTURE.md](DIRECTORY_STRUCTURE.md)** - Quick start guide
- **[test-data/README.md](test-data/README.md)** - Test data files and SQL queries

---

## Quick Start

```bash
# 1. Build JAR
mvn clean package -DskipTests

# 2. Build Docker image
docker build -t etl-initializer:0.0.1-SNAPSHOT .

# 3. Deploy to Kubernetes (Deployment, not Job!)
kubectl apply -f k8s_manifist/etl-initializer-deployment.yaml

# 4. Wait for pod to be ready
kubectl get pods -n monitoring-app -l app=etl-initializer

# 5. Upload version 1 data
POD=$(kubectl get pods -n monitoring-app -l app=etl-initializer -o jsonpath='{.items[0].metadata.name}')
kubectl cp test-data/etl-data-v1.yaml monitoring-app/$POD:/data/uploads/

# 6. Monitor logs (file processed in ~60 seconds)
kubectl logs -f -n monitoring-app $POD

# 7. Upload version 2 (incremental update)
kubectl cp test-data/etl-data-v2.yaml monitoring-app/$POD:/data/uploads/
```

---

## Architecture

### Dual-Purpose Service

```
┌─────────────────────────────────────────────────────────────┐
│                  ETL Initializer Service                     │
│                                                              │
│  ┌────────────────────┐         ┌─────────────────────┐    │
│  │  Flyway Migrations │         │  YAML File Loader   │    │
│  │   (Auto-Start)     │         │   (@Scheduled)      │    │
│  └────────────────────┘         └─────────────────────┘    │
│           │                              │                   │
│           │ DDL (Schema)                 │ DML (Data)       │
│           ▼                              ▼                   │
│  ┌──────────────────────────────────────────────────────┐  │
│  │              PostgreSQL Database                      │  │
│  │  ┌──────────┐  ┌──────────┐  ┌──────────┐           │  │
│  │  │  loader  │  │ signals  │  │ general  │           │  │
│  │  └──────────┘  └──────────┘  └──────────┘           │  │
│  └──────────────────────────────────────────────────────┘  │
└─────────────────────────────────────────────────────────────┘

External Interaction: kubectl cp *.yaml pod:/data/uploads/
```

### 1. Flyway Migrations (Schema + System Config)

**Purpose**: Create database schema + insert system/reference configuration

**Location**: `src/main/resources/db/migration/`

**Execution**: Automatic on service startup, before Spring Boot initializes

**What Flyway Handles**:
- **DDL**: Tables, indexes, constraints, schemas
- **System Config**: Config plans, default settings, lookup data
- **Initialization Data**: Version tracking, system flags

**Migrations**:
- V1: Initial schema (source_database, loader tables)
- V2: Loader scheduling (load_history, scheduling_state tables)
- V3: Config management (config_plan, config_value + **system config modes**)
- V4: Zero-record tracking (add columns)
- V5: Timezone offset support (add columns)
- V6: Backfill jobs (backfill_job table)
- V7: General schema (initialization_log, system_config + **version tracking**)

See **[FLYWAY_VS_YAML.md](FLYWAY_VS_YAML.md)** for detailed explanation of what goes where.

### 2. YAML File Loader (Business Data)

**Purpose**: Load business/runtime data (source databases, ETL loaders)

**Execution**: @Scheduled every 60 seconds, scans `/data/uploads/`

**What YAML Handles**:
- **Source Databases**: MySQL/PostgreSQL connection details
- **Loaders**: ETL job definitions, SQL queries
- **Credentials**: Passwords (encrypted in DB)
- **Environment-specific**: Different data for dev/staging/prod

**Features**:
- Version-aware (only loads version > current)
- SHA-256 hashing (prevents duplicate processing)
- CSV metadata generation (audit trail)
- File lifecycle (uploads → processed/failed)

---

## Configuration

### Environment Variables (from app-secrets)

```yaml
SPRING_DATASOURCE_URL: jdbc:postgresql://postgres-postgresql.monitoring-infra.svc.cluster.local:5432/alerts_db
SPRING_DATASOURCE_USERNAME: alerts_user
SPRING_DATASOURCE_PASSWORD: HaAirK101348App
ENCRYPTION_KEY: <256-bit-base64-key>
```

### Application Configuration

```yaml
# application.yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: loader,signals

etl:
  file:
    upload-path: /data/uploads
    processed-path: /data/processed
    failed-path: /data/failed
    scan-interval-seconds: 60
```

---

## YAML File Format

```yaml
etl:
  metadata:
    load_version: 1  # REQUIRED - version number
    description: "Initial ETL configuration"

  sources:
    - db_code: MYSQL_TEST
      version: 1  # Added in version 1
      ip: mysql.monitoring-infra.svc.cluster.local
      port: 3306
      db_type: MYSQL
      db_name: test_data
      user_name: test_user
      pass_word: password  # Encrypted in DB with AES-256-GCM

  loaders:
    - loader_code: TEST_LOADER_01
      version: 1  # Added in version 1
      source_db_code: MYSQL_TEST
      loader_sql: |
        SELECT 'TEST_LOADER_01' as loader_code,
               NOW() as event_timestamp,
               COUNT(*) as record_count
        FROM DUAL
      min_interval_seconds: 30
      max_interval_seconds: 300
      max_query_period_seconds: 86400
      max_parallel_executions: 1
      load_status: IDLE
      purge_strategy: FAIL_ON_DUPLICATE
```

**Note**: All field names use **snake_case** (not camelCase)

---

## Version-Based Incremental Loading

### Problem
How to add new loaders without reinserting existing ones?

### Solution
Multi-level version tracking:

```sql
-- System tracks current version
SELECT config_value FROM general.system_config
WHERE config_key = 'CURRENT_ETL_VERSION';
-- Returns: '1'
```

```yaml
# File specifies its version
metadata:
  load_version: 2  # This is version 2

# Each source/loader has its own version
sources:
  - db_code: MYSQL_TEST
    version: 1  # Added in v1 - will be skipped
  - db_code: POSTGRES_PROD
    version: 2  # Added in v2 - will be loaded

loaders:
  - loader_code: LOADER_01
    version: 1  # Will be skipped
  - loader_code: LOADER_02
    version: 2  # Will be loaded
```

**Loading Logic**:
- Only loads sources/loaders with `version > current_version`
- Checks if record already exists (by code)
- Updates `CURRENT_ETL_VERSION` after successful load

---

## File Processing Flow

```
1. File uploaded to /data/uploads/ (via kubectl cp)
   ↓
2. Scheduled task detects file (every 60s)
   ↓
3. Calculate SHA-256 hash
   ↓
4. Check if already processed (hash in initialization_log)
   ↓
5. Parse YAML, extract load_version
   ↓
6. Compare: if file_version <= current_version → SKIP
   ↓
7. Create log entry (status=PROCESSING)
   ↓
8. Load sources (only version > current)
   ↓
9. Load loaders (only version > current)
   ↓
10. Generate CSV metadata
    ↓
11. Update log (status=COMPLETED)
    ↓
12. Update CURRENT_ETL_VERSION
    ↓
13. Move file to /data/processed/

If error: → status=FAILED → /data/failed/
```

---

## Monitoring & Observability

### Check Schema Version

```sql
-- Flyway migration status
SELECT version, description, installed_on, success
FROM flyway_schema_history
ORDER BY installed_rank DESC;
```

### Check Data Version

```sql
-- Current ETL data version
SELECT config_value FROM general.system_config
WHERE config_key = 'CURRENT_ETL_VERSION';
```

### Check File Processing History

```sql
-- All processed files
SELECT file_name, load_version, sources_loaded, loaders_loaded,
       status, processed_at, error_message
FROM general.initialization_log
ORDER BY processed_at DESC;
```

### Loader Execution History

```sql
-- Recent executions for a loader
SELECT loader_code, status, start_time, duration_seconds,
       records_loaded, records_ingested
FROM loader.load_history
WHERE loader_code = 'TEST_LOADER_01'
ORDER BY start_time DESC
LIMIT 10;
```

### Service Logs

```bash
# Watch for file processing
kubectl logs -f -n monitoring-app <pod> | grep "Processing file"

# Check version updates
kubectl logs -n monitoring-app <pod> | grep "File version"

# Check for errors
kubectl logs -n monitoring-app <pod> | grep ERROR
```

---

## Security

### Password Encryption

**In YAML**: Plaintext
```yaml
pass_word: MyPassword123  # Plaintext in file
```

**In Database**: Encrypted
```
Encrypted with AES-256-GCM
Key from ENCRYPTION_KEY environment variable
Format: AES256$iv$ciphertext$tag
```

### Recommendations

1. **Delete YAML files** after successful upload
2. **Use Kubernetes secrets** for ENCRYPTION_KEY
3. **Restrict pod exec access** to prevent file viewing
4. **Monitor processed/ directory** and clean periodically

---

## Troubleshooting

### Flyway Migration Failed

```bash
# Check migration status
kubectl exec -n monitoring-app <pod> -- sh -c \
  'psql $SPRING_DATASOURCE_URL -c "SELECT * FROM flyway_schema_history WHERE success = false;"'

# View pod logs
kubectl logs -n monitoring-app <pod> | grep -i flyway
```

### YAML File Not Processing

```bash
# Check if file is in uploads
kubectl exec -n monitoring-app <pod> -- ls /data/uploads/

# Check for errors in failed directory
kubectl exec -n monitoring-app <pod> -- ls /data/failed/

# View error message
psql -c "SELECT file_name, error_message FROM general.initialization_log WHERE status = 'FAILED' ORDER BY created_at DESC LIMIT 1;"
```

### Version Too Low

```
Log message: "File version 1 is not greater than current version 1. Skipping."
```

**Solution**: Increment version number in YAML metadata

### Duplicate File

```
Log message: "File already processed (duplicate hash). Skipping."
```

**Solution**: File was already processed (by SHA-256 hash). Either:
- This is expected (file moved to processed/)
- Modify file content if you need to reprocess

### Reset System (Development Only!)

```sql
-- Reset version to 0
UPDATE general.system_config
SET config_value = '0'
WHERE config_key = 'CURRENT_ETL_VERSION';

-- Clear initialization log
DELETE FROM general.initialization_log;

-- Delete all loaders and sources (dangerous!)
DELETE FROM loader.loader;
DELETE FROM loader.source_database;
```

---

## Files Structure

```
etl_initializer/
├── README.md                           # This file
├── ARCHITECTURE.md                     # Complete architecture docs
├── DIRECTORY_STRUCTURE.md              # Quick start guide
├── Dockerfile                          # Container image
├── pom.xml                            # Maven configuration
│
├── src/main/
│   ├── java/com/tiqmo/monitoring/initializer/
│   │   ├── EtlInitializerApplication.java     # Main app + FileMonitorService
│   │   ├── config/LoaderData.java             # YAML binding model
│   │   ├── domain/                            # JPA entities
│   │   │   ├── Loader.java
│   │   │   └── SourceDatabase.java
│   │   ├── repository/                        # JPA repositories
│   │   └── infra/security/                    # AES-256-GCM encryption
│   │
│   └── resources/
│       ├── application.yaml                   # Spring Boot config
│       └── db/migration/                      # Flyway migrations (V1-V7)
│
├── test-data/                          # NOT in resources!
│   ├── README.md                       # Test data documentation
│   ├── etl-data-v1.yaml               # Version 1 (for testing)
│   ├── etl-data-v2.yaml               # Version 2 (incremental)
│   └── current-data-export.yaml       # Complete system backup
│
└── k8s_manifist/
    └── etl-initializer-deployment.yaml  # Kubernetes Deployment

/data/ (in pod - runtime only)
├── uploads/         # Upload zone (kubectl cp)
├── processed/       # Successfully loaded
└── failed/          # Processing errors
```

---

## Migration from Old Job-Based System

If upgrading from the old one-time Job architecture:

1. **Delete old Job**:
   ```bash
   kubectl delete job etl-initializer -n monitoring-app
   ```

2. **Deploy new Deployment**:
   ```bash
   kubectl apply -f k8s_manifist/etl-initializer-deployment.yaml
   ```

3. **Check current version**:
   ```sql
   SELECT config_value FROM general.system_config WHERE config_key = 'CURRENT_ETL_VERSION';
   ```

4. **Upload new data** (version must be > current):
   ```bash
   kubectl cp etl-data-v2.yaml monitoring-app/<pod>:/data/uploads/
   ```

---

## Best Practices

### Flyway Migrations

✅ **DO**:
- Use sequential version numbers (V1, V2, V3...)
- Include descriptive comments
- Test on dev before production
- Make migrations idempotent where possible

❌ **DON'T**:
- Modify existing migrations after deployment
- Include data (DML) in schema migrations
- Use out-of-order migrations

### YAML Files

✅ **DO**:
- Increment version for each file
- Test with v1 first, then incremental versions
- Include descriptive metadata
- Monitor initialization_log after upload
- Delete files after successful processing

❌ **DON'T**:
- Reuse version numbers
- Skip version numbers
- Upload same file twice
- Leave password files in uploads/

---

## See Also

- **[FLYWAY_VS_YAML.md](FLYWAY_VS_YAML.md)** - ⭐ **Decision matrix: what goes where?**
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Complete architecture documentation
- **[test-data/README.md](test-data/README.md)** - Query examples and test data
- **[DIRECTORY_STRUCTURE.md](DIRECTORY_STRUCTURE.md)** - File processing workflow


## Todo

- ✅ encrypt and store upload yaml file in database (COMPLETED - V8 migration)
- create yml file to upload mocking loaders
- view data from grafana
- create GUI for setting up data sources, loaders, and view history data
- build user access and authintication logic
- read from promithuse
- read from kibana
- integrate with jira
- develop dynamic report RCA report (can it be done on grafana??)