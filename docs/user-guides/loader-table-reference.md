# Loader Table User Guide

## Table of Contents
1. [Overview](#overview)
2. [Table Structure](#table-structure)
3. [Column Reference](#column-reference)
4. [Column Categories](#column-categories)
5. [Execution Lifecycle](#execution-lifecycle)
6. [Configuration Best Practices](#configuration-best-practices)
7. [Common Scenarios](#common-scenarios)

---

## Overview

The `loader` table is the core entity in the ETL monitoring system. Each row represents a data loader configuration that:
- Executes SQL queries against source databases
- Runs on configurable intervals
- Manages distributed execution across multiple replicas
- Handles timezone normalization
- Tracks execution state and history

**Schema**: `loader.loader`
**Total Columns**: 18
**Primary Key**: `id` (bigint, auto-increment)
**Unique Constraint**: `loader_code`

---

## Table Structure

```sql
Table "loader.loader"
┌──────────────────────────────┬──────────────────────────┬─────────────┬──────────┐
│ Column                       │ Type                     │ Nullable    │ Default  │
├──────────────────────────────┼──────────────────────────┼─────────────┼──────────┤
│ id                           │ bigint                   │ NOT NULL    │ IDENTITY │
│ loader_code                  │ character varying(50)    │ NOT NULL    │ -        │
│ loader_sql                   │ text                     │ NULL        │ -        │
│ source_database_id           │ bigint                   │ NULL        │ -        │
│ load_status                  │ character varying(50)    │ NULL        │ 'IDLE'   │
│ last_load_timestamp          │ timestamp with time zone │ NULL        │ -        │
│ last_success_timestamp       │ timestamp with time zone │ NULL        │ -        │
│ failed_since                 │ timestamp with time zone │ NULL        │ -        │
│ min_interval_seconds         │ integer                  │ NULL        │ -        │
│ max_interval_seconds         │ integer                  │ NULL        │ -        │
│ max_query_period_seconds     │ integer                  │ NULL        │ -        │
│ max_parallel_executions      │ integer                  │ NULL        │ -        │
│ purge_strategy               │ character varying(50)    │ NULL        │ 'FAIL_*' │
│ consecutive_zero_record_runs │ integer                  │ NULL        │ 0        │
│ source_timezone_offset_hours │ integer                  │ NULL        │ -        │
│ enabled                      │ boolean                  │ NULL        │ -        │
│ created_at                   │ timestamp with time zone │ NULL        │ -        │
│ updated_at                   │ timestamp with time zone │ NULL        │ -        │
└──────────────────────────────┴──────────────────────────┴─────────────┴──────────┘

Indexes:
  "loader_pkey" PRIMARY KEY, btree (id)
  "uk_loader_code" UNIQUE CONSTRAINT, btree (loader_code)
  "idx_loader_status" btree (load_status)
  "idx_loader_source_database" btree (source_database_id)

Foreign Keys:
  "fk_loader_source_database" FOREIGN KEY (source_database_id)
    REFERENCES loader.source_databases(id)

Encryption:
  "loader_sql" - AES-256-GCM encrypted at application layer
```

---

## Column Reference

### 1. Identification Columns

#### `id` (bigint, PRIMARY KEY)
- **Purpose**: Unique internal identifier for the loader
- **Type**: Auto-incrementing sequence
- **Usage**: Database internal use, not exposed to users
- **Example**: `1`, `2`, `3`
- **Code Reference**: `Loader.java:23`

#### `loader_code` (varchar(50), UNIQUE NOT NULL)
- **Purpose**: Human-readable unique identifier for the loader
- **Usage**:
  - Used in URLs: `/loaders/{loaderCode}`
  - Used in API endpoints
  - Displayed in UI tables and cards
  - Used for logging and monitoring
- **Constraints**: Must be unique across all loaders
- **Example**: `"ALARMS_LOADER"`, `"SIGNALS_LOADER"`, `"EVENTS_LOADER"`
- **Best Practice**: Use UPPER_SNAKE_CASE with descriptive names
- **Code Reference**: `Loader.java:27-30`

```java
@Column(name = "loader_code", nullable = false, unique = true, length = 50)
private String loaderCode;
```

---

### 2. Configuration Columns

#### `loader_sql` (text, ENCRYPTED)
- **Purpose**: SQL query executed against the source database to fetch data
- **Encryption**: AES-256-GCM at application layer via `EncryptedStringConverter`
- **Storage**: Encrypted in database, decrypted in memory when needed
- **Usage**:
  - Executed by LoaderExecutor during each run
  - Must include `WHERE timestamp_field >= :lastLoadTimestamp` for incremental loading
  - Should include `WHERE timestamp_field <= :currentTimestamp` for bounded queries
- **Example**:
```sql
SELECT
    alarm_id,
    alarm_timestamp,
    alarm_message,
    severity
FROM alarms
WHERE alarm_timestamp >= :lastLoadTimestamp
  AND alarm_timestamp <= :currentTimestamp
ORDER BY alarm_timestamp ASC
```
- **Security**: Never logged in plaintext, only shown to authorized users
- **Code Reference**: `Loader.java:35-39`

#### `source_database_id` (bigint, FOREIGN KEY)
- **Purpose**: References the source database configuration to execute queries against
- **Foreign Key**: `loader.source_databases(id)`
- **Usage**:
  - LoaderExecutor uses this to establish JDBC connection
  - Contains connection URL, credentials, driver class
- **Relationship**: Many loaders can use the same source database
- **Example**: `1` (references PostgreSQL production DB)
- **Code Reference**: `Loader.java:119-121`

```java
@ManyToOne(fetch = FetchType.LAZY)
@JoinColumn(name = "source_database_id")
private SourceDatabase sourceDatabase;
```

---

### 3. Scheduling Columns

#### `min_interval_seconds` (integer)
- **Purpose**: Minimum wait time between END of one execution and START of next
- **Type**: End-to-start interval (cooldown period)
- **Usage**:
  - After loader finishes at 10:00:30, and min_interval is 300s (5 min)
  - Next execution cannot start before 10:05:30
- **Use Case**: Prevent overloading source database with frequent queries
- **Example**: `300` (5 minutes), `600` (10 minutes), `3600` (1 hour)
- **Code Reference**: `Loader.java:56-62`

```
Timeline Example (min_interval_seconds = 300):
Run 1: [10:00:00 =====> 10:00:30] (duration: 30s)
Wait:                   [======= 5 min wait =======]
Run 2:                              [10:05:30 =====> 10:06:00]
```

#### `max_interval_seconds` (integer)
- **Purpose**: Maximum time between START of consecutive executions
- **Type**: Start-to-start frequency (execution cadence)
- **Usage**:
  - If loader starts at 10:00:00 and max_interval is 3600s (1 hour)
  - Next execution MUST start by 11:00:00, regardless of when previous ended
- **Use Case**: Guarantee minimum execution frequency
- **Example**: `3600` (hourly), `7200` (every 2 hours), `86400` (daily)
- **Priority**: Overrides min_interval if conflicts occur
- **Code Reference**: `Loader.java:64-72`

```
Timeline Example (max_interval_seconds = 3600):
Run 1: [10:00:00 =====> 10:45:00] (duration: 45 min - long query!)
Run 2: [11:00:00 =====> 11:02:00] (MUST start at 11:00, can't wait for min_interval)
```

#### `max_query_period_seconds` (integer)
- **Purpose**: Maximum time range of historical data to fetch in a single execution
- **Type**: Time window for SQL query
- **Usage**:
  - Limits: `:currentTimestamp - :lastLoadTimestamp <= max_query_period_seconds`
  - If lastLoadTimestamp is very old (e.g., after downtime), prevents fetching too much data
  - Example: If last run was 3 days ago but max_query_period is 1 hour (3600s):
    - First run fetches: [3 days ago + 3600s]
    - Second run fetches: [3 days ago + 7200s]
    - Continues in 1-hour chunks until caught up
- **Use Case**: Prevent memory overflow, database timeouts, or massive result sets
- **Example**: `3600` (1 hour), `7200` (2 hours), `86400` (1 day)
- **Code Reference**: `Loader.java:74-82`

```sql
-- LoaderExecutor modifies query to:
WHERE timestamp_field >= :lastLoadTimestamp
  AND timestamp_field <= LEAST(
        :currentTimestamp,
        :lastLoadTimestamp + INTERVAL '3600 seconds'
      )
```

#### `max_parallel_executions` (integer)
- **Purpose**: Maximum number of this loader running simultaneously across ALL replicas
- **Type**: Global distributed concurrency limit
- **Usage**:
  - LoaderExecutor uses pessimistic locking (SELECT FOR UPDATE) on loader row
  - Checks: `COUNT(executions WHERE status=RUNNING) < max_parallel_executions`
  - If limit reached, execution deferred to next scheduling cycle
- **Use Case**:
  - Prevent overloading source database
  - Control resource usage
  - Typical values: 1 (sequential), 2-5 (moderate parallel), 10+ (high throughput)
- **Example**: `1` (no parallelism), `3` (max 3 concurrent runs)
- **Code Reference**: `Loader.java:84-91`

```
Scenario: 3 replicas, max_parallel_executions = 2

Replica 1: [RUNNING] - acquires lock
Replica 2: [RUNNING] - acquires lock
Replica 3: [WAITING] - max limit reached (2/2), must wait
```

---

### 4. Timezone Handling Columns

#### `source_timezone_offset_hours` (integer)
- **Purpose**: Source database timezone offset from GMT for timestamp normalization
- **Type**: Hours offset from GMT (can be negative)
- **Usage**:
  - When fetching timestamps from source DB, converts to UTC before storing
  - Formula: `utc_timestamp = source_timestamp - source_timezone_offset_hours`
  - Example: If source DB is GMT-5 (EST), offset = -5
    - Source timestamp: 2025-12-27 10:00:00 (EST)
    - Stored as UTC: 2025-12-27 15:00:00 (UTC)
- **Use Case**:
  - Normalize timestamps across multiple source databases in different timezones
  - Ensure consistent time-based processing
- **Example**: `-5` (EST), `0` (GMT/UTC), `+8` (Singapore)
- **Code Reference**: `Loader.java:107-113`

```java
// LoaderExecutor applies offset:
Instant normalizedTimestamp = sourceTimestamp
    .minus(Duration.ofHours(sourceTimezoneOffsetHours));
```

---

### 5. Runtime State Columns

#### `load_status` (varchar(50), ENUM)
- **Purpose**: Current execution state of the loader
- **Type**: Enum with 4 possible values
- **Values**:
  - `IDLE` - Not currently executing, waiting for next scheduled run
  - `RUNNING` - Actively executing a query
  - `FAILED` - Last execution failed, requires attention
  - `PAUSED` - Manually disabled by user
- **Usage**:
  - LoaderScheduler checks: `load_status = IDLE AND enabled = true`
  - LoaderExecutor updates: `IDLE -> RUNNING -> IDLE` (success) or `-> FAILED` (error)
  - UI displays status badges
- **Transitions**:
```
IDLE ──(schedule)──> RUNNING ──(success)──> IDLE
                              └─(error)────> FAILED

FAILED ──(auto-recovery after 20 min)──> IDLE

Any ──(user disable)──> PAUSED
PAUSED ──(user enable)──> IDLE
```
- **Default**: `IDLE`
- **Code Reference**: `Loader.java:41-45`, `LoadStatus.java`

#### `last_load_timestamp` (timestamp with time zone)
- **Purpose**: Last timestamp successfully processed (upper bound of last query)
- **Type**: Instant (UTC timezone)
- **Usage**:
  - Next execution starts from this timestamp: `WHERE timestamp >= :lastLoadTimestamp`
  - Updated after successful execution to max timestamp in result set
  - If NULL, loader never ran (first run fetches from epoch or configured start)
- **Example**: `2025-12-27 10:05:30+00`
- **Critical**: This is the "watermark" for incremental loading
- **Code Reference**: `Loader.java:47-51`

```java
// LoaderExecutor after successful run:
Instant maxTimestampInResults = resultSet.stream()
    .map(Record::getTimestamp)
    .max(Comparator.naturalOrder())
    .orElse(lastLoadTimestamp);
loader.setLastLoadTimestamp(maxTimestampInResults);
```

#### `last_success_timestamp` (timestamp with time zone)
- **Purpose**: Timestamp of the last successful execution completion
- **Type**: Instant (UTC timezone)
- **Usage**:
  - Updated to current time when execution completes successfully
  - Used for monitoring: "Last successful run was 2 hours ago"
  - UI displays: "Last Success: 2025-12-27 10:05:30 UTC"
- **Difference from last_load_timestamp**:
  - `last_load_timestamp` = data timestamp (from source records)
  - `last_success_timestamp` = execution completion time (system clock)
- **Example**: `2025-12-27 12:15:45+00`
- **Code Reference**: `Loader.java:93-97`

#### `failed_since` (timestamp with time zone)
- **Purpose**: Timestamp when loader first entered FAILED status
- **Type**: Instant (UTC timezone), nullable
- **Usage**:
  - Set when execution fails: `failed_since = Instant.now()`
  - Cleared when execution succeeds: `failed_since = null`
  - Auto-recovery mechanism: If `Instant.now() - failed_since > 20 minutes`, reset to IDLE
- **Auto-Recovery Logic**:
```java
if (loader.getLoadStatus() == LoadStatus.FAILED
    && loader.getFailedSince() != null
    && Duration.between(loader.getFailedSince(), Instant.now()).toMinutes() > 20) {
    loader.setLoadStatus(LoadStatus.IDLE);
    loader.setFailedSince(null);
    log.info("Auto-recovered loader {} after 20 minutes", loader.getLoaderCode());
}
```
- **Example**: `2025-12-27 08:30:00+00` (if currently in FAILED state)
- **NULL**: Loader has never failed or was cleared
- **Code Reference**: `Loader.java:99-105`

#### `consecutive_zero_record_runs` (integer)
- **Purpose**: Counter for consecutive executions that returned zero records
- **Type**: Integer counter
- **Usage**:
  - Incremented when execution succeeds but returns 0 rows
  - Reset to 0 when execution returns > 0 rows
  - Used for anomaly detection: "Source system may be down, no new data for 50 consecutive runs"
- **Alert Threshold**: Typically alert if > 100 consecutive zero-record runs
- **Example**: `0` (normal), `5` (maybe slow source), `150` (source down or no new data)
- **Default**: `0`
- **Code Reference**: `Loader.java:115-117`

```java
// LoaderExecutor logic:
if (recordCount == 0) {
    loader.setConsecutiveZeroRecordRuns(loader.getConsecutiveZeroRecordRuns() + 1);
} else {
    loader.setConsecutiveZeroRecordRuns(0);
}
```

---

### 6. Data Management Columns

#### `purge_strategy` (varchar(50), ENUM)
- **Purpose**: Defines how to handle duplicate data when reloading
- **Type**: Enum with 3 possible values
- **Values**:
  - `FAIL_ON_DUPLICATE` - Abort execution if duplicate key constraint violation occurs
  - `PURGE_AND_RELOAD` - Delete existing data in time range before inserting new data
  - `SKIP_DUPLICATES` - Ignore duplicate key errors, continue processing
- **Usage Scenarios**:

**1. FAIL_ON_DUPLICATE** (Default, Safest)
```java
// Use when: Data should never duplicate, any duplicate indicates a bug
try {
    targetRepo.saveAll(records);
} catch (DataIntegrityViolationException e) {
    loader.setLoadStatus(LoadStatus.FAILED);
    throw new LoaderException("Duplicate data detected", e);
}
```

**2. PURGE_AND_RELOAD** (Complete Refresh)
```java
// Use when: Source data can change (updates/deletes), need accurate snapshot
Instant startTime = lastLoadTimestamp;
Instant endTime = currentTimestamp;

// Delete existing data in time range
targetRepo.deleteByTimestampBetween(startTime, endTime);

// Insert fresh data
targetRepo.saveAll(records);
```

**3. SKIP_DUPLICATES** (Idempotent)
```java
// Use when: Re-running same time range is safe, duplicates expected
for (Record record : records) {
    try {
        targetRepo.save(record);
    } catch (DataIntegrityViolationException e) {
        log.debug("Skipping duplicate record: {}", record.getId());
        continue; // Skip, don't fail
    }
}
```

- **Best Practice**:
  - Start with `FAIL_ON_DUPLICATE` in development
  - Use `PURGE_AND_RELOAD` for dimension tables or data that can change
  - Use `SKIP_DUPLICATES` for idempotent event streams
- **Default**: `FAIL_ON_DUPLICATE`
- **Code Reference**: `Loader.java:53-54`, `PurgeStrategy.java`

---

### 7. Control Columns

#### `enabled` (boolean)
- **Purpose**: Master on/off switch for the loader
- **Type**: Boolean flag
- **Usage**:
  - LoaderScheduler checks: `enabled = true` before scheduling
  - UI toggle: "Pause" / "Resume" button
  - When `enabled = false`, loader is completely skipped
- **State Interaction**:
  - `enabled = false` does NOT change `load_status`
  - `load_status = PAUSED` is set when user manually pauses
  - `enabled = false` AND `load_status = IDLE` = typical paused state
- **Example**: `true` (active), `false` (paused by user)
- **Code Reference**: `Loader.java:123-124`

```sql
-- LoaderScheduler query:
SELECT * FROM loader.loader
WHERE enabled = true
  AND load_status = 'IDLE'
  AND (last_load_timestamp IS NULL
       OR EXTRACT(EPOCH FROM (NOW() - last_load_timestamp)) >= max_interval_seconds)
FOR UPDATE SKIP LOCKED;
```

---

### 8. Audit Columns

#### `created_at` (timestamp with time zone)
- **Purpose**: Timestamp when loader record was first created
- **Type**: Instant (UTC timezone)
- **Usage**: Audit trail, reporting, "Loader age" metrics
- **Set**: Automatically by JPA `@PrePersist`
- **Example**: `2025-12-25 14:30:00+00`
- **Code Reference**: `Loader.java:126-127`

#### `updated_at` (timestamp with time zone)
- **Purpose**: Timestamp of last modification to loader configuration
- **Type**: Instant (UTC timezone)
- **Usage**:
  - Audit trail, change tracking
  - Updated on ANY field change (except last_load_timestamp)
- **Set**: Automatically by JPA `@PreUpdate`
- **Example**: `2025-12-27 09:15:30+00`
- **Code Reference**: `Loader.java:128-129`

---

## Column Categories

### Primary Identification
- `id` - Internal database key
- `loader_code` - User-facing unique identifier

### Security & Configuration
- `loader_sql` - **ENCRYPTED** query definition
- `source_database_id` - Source connection reference

### Execution Scheduling
- `min_interval_seconds` - Cooldown period (end-to-start)
- `max_interval_seconds` - Maximum frequency (start-to-start)
- `max_query_period_seconds` - Historical data chunk size
- `max_parallel_executions` - Distributed concurrency limit

### Timezone Management
- `source_timezone_offset_hours` - GMT offset for normalization

### Runtime State Tracking
- `load_status` - Current state (IDLE, RUNNING, FAILED, PAUSED)
- `last_load_timestamp` - Data watermark (last processed timestamp)
- `last_success_timestamp` - Last successful execution time
- `failed_since` - Failure tracking for auto-recovery
- `consecutive_zero_record_runs` - Anomaly detection counter

### Data Quality
- `purge_strategy` - Duplicate handling strategy

### Control & Audit
- `enabled` - Master on/off switch
- `created_at` - Creation timestamp
- `updated_at` - Last modification timestamp

---

## Execution Lifecycle

### 1. Loader Creation
```sql
INSERT INTO loader.loader (
    loader_code,
    loader_sql,
    source_database_id,
    min_interval_seconds,
    max_interval_seconds,
    max_query_period_seconds,
    max_parallel_executions,
    source_timezone_offset_hours,
    purge_strategy,
    enabled,
    load_status
) VALUES (
    'ALARMS_LOADER',
    'SELECT * FROM alarms WHERE timestamp >= :lastLoadTimestamp',
    1,  -- PostgreSQL production DB
    300,  -- 5 min cooldown
    3600,  -- Run at least every hour
    7200,  -- Fetch max 2 hours per run
    1,  -- No parallelism
    -5,  -- EST timezone
    'FAIL_ON_DUPLICATE',
    true,  -- Enabled
    'IDLE'  -- Initial state
);
```

### 2. Scheduling Decision
LoaderScheduler runs every 30 seconds:

```java
List<Loader> candidates = loaderRepo.findAll().stream()
    .filter(loader -> loader.isEnabled())  // Must be enabled
    .filter(loader -> loader.getLoadStatus() == LoadStatus.IDLE)  // Not running or failed
    .filter(loader -> {
        // Check if max_interval_seconds elapsed since last start
        if (loader.getLastSuccessTimestamp() == null) return true;
        long secondsSinceLastStart = Duration.between(
            loader.getLastSuccessTimestamp(),
            Instant.now()
        ).toSeconds();
        return secondsSinceLastStart >= loader.getMaxIntervalSeconds();
    })
    .filter(loader -> {
        // Check if min_interval_seconds elapsed since last end
        if (loader.getLastSuccessTimestamp() == null) return true;
        long secondsSinceLastEnd = Duration.between(
            loader.getLastSuccessTimestamp(),
            Instant.now()
        ).toSeconds();
        return secondsSinceLastEnd >= loader.getMinIntervalSeconds();
    })
    .collect(Collectors.toList());
```

### 3. Execution Acquisition (Distributed Lock)
```java
// Pessimistic lock to prevent multiple replicas running same loader
Loader loader = entityManager.find(
    Loader.class,
    loaderId,
    LockModeType.PESSIMISTIC_WRITE
);

// Check parallel execution limit
int runningCount = executionRepo.countByLoaderAndStatus(loader, ExecutionStatus.RUNNING);
if (runningCount >= loader.getMaxParallelExecutions()) {
    log.info("Max parallel limit reached: {}/{}",
        runningCount, loader.getMaxParallelExecutions());
    return; // Skip this execution
}

// Acquire execution slot
loader.setLoadStatus(LoadStatus.RUNNING);
loaderRepo.save(loader);
```

### 4. Query Execution
```java
// Calculate time range
Instant startTimestamp = loader.getLastLoadTimestamp() != null
    ? loader.getLastLoadTimestamp()
    : Instant.EPOCH;

Instant endTimestamp = Instant.now();

// Apply max_query_period_seconds limit
long periodSeconds = Duration.between(startTimestamp, endTimestamp).toSeconds();
if (periodSeconds > loader.getMaxQueryPeriodSeconds()) {
    endTimestamp = startTimestamp.plusSeconds(loader.getMaxQueryPeriodSeconds());
}

// Apply timezone offset
if (loader.getSourceTimezoneOffsetHours() != null) {
    startTimestamp = startTimestamp.minus(
        Duration.ofHours(loader.getSourceTimezoneOffsetHours())
    );
    endTimestamp = endTimestamp.minus(
        Duration.ofHours(loader.getSourceTimezoneOffsetHours())
    );
}

// Execute query
String sql = decryptSql(loader.getLoaderSql());
List<Record> records = jdbcTemplate.query(
    sql,
    Map.of(
        "lastLoadTimestamp", startTimestamp,
        "currentTimestamp", endTimestamp
    ),
    recordMapper
);
```

### 5. Data Persistence (Purge Strategy)
```java
switch (loader.getPurgeStrategy()) {
    case FAIL_ON_DUPLICATE:
        try {
            targetRepo.saveAll(records);
        } catch (DataIntegrityViolationException e) {
            loader.setLoadStatus(LoadStatus.FAILED);
            loader.setFailedSince(Instant.now());
            throw new LoaderException("Duplicate detected", e);
        }
        break;

    case PURGE_AND_RELOAD:
        targetRepo.deleteByTimestampBetween(startTimestamp, endTimestamp);
        targetRepo.saveAll(records);
        break;

    case SKIP_DUPLICATES:
        records.forEach(record -> {
            try {
                targetRepo.save(record);
            } catch (DataIntegrityViolationException e) {
                // Silently skip duplicates
            }
        });
        break;
}
```

### 6. Success Handling
```java
// Update watermark
Instant maxTimestamp = records.stream()
    .map(Record::getTimestamp)
    .max(Comparator.naturalOrder())
    .orElse(endTimestamp);
loader.setLastLoadTimestamp(maxTimestamp);

// Update success timestamp
loader.setLastSuccessTimestamp(Instant.now());

// Reset state
loader.setLoadStatus(LoadStatus.IDLE);
loader.setFailedSince(null);

// Update zero-record counter
if (records.isEmpty()) {
    loader.setConsecutiveZeroRecordRuns(
        loader.getConsecutiveZeroRecordRuns() + 1
    );
} else {
    loader.setConsecutiveZeroRecordRuns(0);
}

loaderRepo.save(loader);
```

### 7. Failure Handling
```java
loader.setLoadStatus(LoadStatus.FAILED);
loader.setFailedSince(Instant.now());
loaderRepo.save(loader);

// Auto-recovery runs on next scheduler cycle
if (Duration.between(loader.getFailedSince(), Instant.now()).toMinutes() > 20) {
    loader.setLoadStatus(LoadStatus.IDLE);
    loader.setFailedSince(null);
    log.info("Auto-recovered loader: {}", loader.getLoaderCode());
}
```

---

## Configuration Best Practices

### Scheduling Configuration

**For High-Frequency Real-Time Data** (e.g., IoT sensors, live metrics):
```
min_interval_seconds: 60        (1 minute cooldown)
max_interval_seconds: 300       (run at least every 5 minutes)
max_query_period_seconds: 600   (fetch 10 minutes per run)
max_parallel_executions: 3      (allow some parallelism)
```

**For Hourly Batch Data** (e.g., hourly aggregations):
```
min_interval_seconds: 600       (10 minute cooldown)
max_interval_seconds: 3600      (run every hour)
max_query_period_seconds: 7200  (fetch 2 hours per run)
max_parallel_executions: 1      (sequential only)
```

**For Daily Batch Data** (e.g., daily reports):
```
min_interval_seconds: 3600      (1 hour cooldown)
max_interval_seconds: 86400     (run daily)
max_query_period_seconds: 172800 (fetch 2 days per run)
max_parallel_executions: 1      (sequential only)
```

**For Recovery from Long Downtime**:
```
min_interval_seconds: 30        (minimal cooldown)
max_interval_seconds: 300       (run frequently)
max_query_period_seconds: 3600  (small chunks to avoid timeout)
max_parallel_executions: 5      (high parallelism)
```

### Timezone Configuration

**Source DB in EST (GMT-5)**:
```
source_timezone_offset_hours: -5
```
Source: `2025-12-27 10:00:00` → Stored as UTC: `2025-12-27 15:00:00`

**Source DB in Singapore (GMT+8)**:
```
source_timezone_offset_hours: 8
```
Source: `2025-12-27 18:00:00` → Stored as UTC: `2025-12-27 10:00:00`

**Source DB already in UTC**:
```
source_timezone_offset_hours: 0
```

### Purge Strategy Selection

**Use FAIL_ON_DUPLICATE when**:
- Data is append-only (events, logs, transactions)
- Duplicates indicate a bug or misconfiguration
- Need strict data integrity guarantees

**Use PURGE_AND_RELOAD when**:
- Source data can be updated or deleted
- Need accurate snapshot of source state
- Reprocessing same time range is necessary (e.g., dimension tables)

**Use SKIP_DUPLICATES when**:
- Loader may re-run same time range intentionally
- Duplicates are expected and harmless
- Idempotent event processing

### Parallelism Guidelines

**max_parallel_executions = 1** (Sequential):
- Default safe choice
- Use when source DB can't handle concurrent queries
- Use when target DB has strict ordering requirements

**max_parallel_executions = 2-5** (Moderate):
- Good for catching up after downtime
- Source DB can handle moderate load
- Each execution processes independent time ranges

**max_parallel_executions = 10+** (High):
- Use only for recovery scenarios
- Source DB is highly scalable
- Monitor for database connection pool exhaustion

---

## Common Scenarios

### Scenario 1: Loader Stuck in RUNNING State

**Symptoms**:
```sql
SELECT loader_code, load_status, last_success_timestamp
FROM loader.loader
WHERE load_status = 'RUNNING'
  AND last_success_timestamp < NOW() - INTERVAL '1 hour';
```

**Causes**:
1. Executor pod crashed mid-execution
2. Database connection timeout
3. Query hanging on source DB

**Solution**:
```sql
-- Manual reset to IDLE
UPDATE loader.loader
SET load_status = 'IDLE',
    updated_at = NOW()
WHERE loader_code = 'STUCK_LOADER';
```

### Scenario 2: Loader Failing with Duplicates

**Symptoms**:
```
load_status = 'FAILED'
failed_since = 2025-12-27 08:00:00
Error: "Duplicate key value violates unique constraint"
```

**Diagnosis**:
```sql
-- Check purge strategy
SELECT loader_code, purge_strategy, last_load_timestamp
FROM loader.loader
WHERE loader_code = 'FAILING_LOADER';
```

**Solutions**:

A. **Change purge strategy** (if reprocessing is expected):
```sql
UPDATE loader.loader
SET purge_strategy = 'PURGE_AND_RELOAD',
    load_status = 'IDLE',
    failed_since = NULL
WHERE loader_code = 'FAILING_LOADER';
```

B. **Reset watermark** (if data range needs reprocessing):
```sql
UPDATE loader.loader
SET last_load_timestamp = '2025-12-26 00:00:00+00',
    load_status = 'IDLE',
    failed_since = NULL
WHERE loader_code = 'FAILING_LOADER';
```

### Scenario 3: Loader Not Running Frequently Enough

**Symptoms**: Last run was 3 hours ago, but max_interval_seconds = 3600 (1 hour)

**Diagnosis**:
```sql
SELECT
    loader_code,
    enabled,
    load_status,
    last_success_timestamp,
    EXTRACT(EPOCH FROM (NOW() - last_success_timestamp)) as seconds_since_last_run,
    max_interval_seconds
FROM loader.loader
WHERE loader_code = 'SLOW_LOADER';
```

**Possible Causes**:
1. `enabled = false` → Enable loader
2. `load_status = FAILED` → Reset to IDLE
3. `min_interval_seconds` too high → Reduce value
4. All replicas busy with other loaders → Add more replicas or reduce other loaders' parallelism

**Solution**:
```sql
-- Enable and reset
UPDATE loader.loader
SET enabled = true,
    load_status = 'IDLE',
    failed_since = NULL,
    min_interval_seconds = 60  -- Reduce cooldown
WHERE loader_code = 'SLOW_LOADER';
```

### Scenario 4: Source Database Downtime Recovery

**Situation**: Source DB was down for 48 hours, need to backfill data quickly

**Step 1**: Check current watermark
```sql
SELECT loader_code, last_load_timestamp, max_query_period_seconds
FROM loader.loader
WHERE loader_code = 'BACKFILL_LOADER';

-- Result:
-- last_load_timestamp = 2025-12-25 10:00:00 (48 hours ago)
-- max_query_period_seconds = 7200 (2 hours)
```

**Step 2**: Optimize for backfill
```sql
UPDATE loader.loader
SET max_query_period_seconds = 3600,  -- Smaller chunks (1 hour)
    min_interval_seconds = 30,         -- Minimal cooldown
    max_interval_seconds = 60,         -- Run every minute
    max_parallel_executions = 5        -- High parallelism
WHERE loader_code = 'BACKFILL_LOADER';
```

**Step 3**: Monitor progress
```sql
SELECT
    loader_code,
    last_load_timestamp,
    EXTRACT(EPOCH FROM (NOW() - last_load_timestamp)) / 3600 as hours_behind,
    consecutive_zero_record_runs
FROM loader.loader
WHERE loader_code = 'BACKFILL_LOADER';
```

**Step 4**: Restore normal config after caught up
```sql
UPDATE loader.loader
SET max_query_period_seconds = 7200,
    min_interval_seconds = 300,
    max_interval_seconds = 3600,
    max_parallel_executions = 1
WHERE loader_code = 'BACKFILL_LOADER';
```

### Scenario 5: Detecting Source System Issues

**Use Case**: Source system stopped producing data, but loader executes successfully

**Detection Query**:
```sql
SELECT
    loader_code,
    consecutive_zero_record_runs,
    last_success_timestamp,
    last_load_timestamp
FROM loader.loader
WHERE consecutive_zero_record_runs > 50
ORDER BY consecutive_zero_record_runs DESC;
```

**Alert Threshold**: `consecutive_zero_record_runs > 100` for 1+ hours

**Interpretation**:
- `consecutive_zero_record_runs = 150` → Source system likely down or no new data for extended period
- `last_success_timestamp` recent → Loader is executing successfully
- `last_load_timestamp` not advancing → No new data being processed

**Actions**:
1. Check source system health
2. Verify SQL query is correct
3. Check if data is being produced in source DB
4. Review timezone configuration

---

## SQL Query Examples

### Create New Loader
```sql
INSERT INTO loader.loader (
    loader_code,
    loader_sql,
    source_database_id,
    min_interval_seconds,
    max_interval_seconds,
    max_query_period_seconds,
    max_parallel_executions,
    source_timezone_offset_hours,
    purge_strategy,
    enabled,
    load_status,
    consecutive_zero_record_runs
) VALUES (
    'SIGNALS_LOADER',
    'SELECT signal_id, signal_timestamp, signal_value FROM signals WHERE signal_timestamp >= :lastLoadTimestamp AND signal_timestamp <= :currentTimestamp ORDER BY signal_timestamp',
    1,
    300,
    3600,
    7200,
    1,
    0,
    'FAIL_ON_DUPLICATE',
    true,
    'IDLE',
    0
);
```

### Pause Loader
```sql
UPDATE loader.loader
SET enabled = false,
    load_status = 'PAUSED',
    updated_at = NOW()
WHERE loader_code = 'SIGNALS_LOADER';
```

### Resume Loader
```sql
UPDATE loader.loader
SET enabled = true,
    load_status = 'IDLE',
    failed_since = NULL,
    updated_at = NOW()
WHERE loader_code = 'SIGNALS_LOADER';
```

### Check Loader Health
```sql
SELECT
    loader_code,
    load_status,
    enabled,
    last_success_timestamp,
    EXTRACT(EPOCH FROM (NOW() - last_success_timestamp)) / 60 as minutes_since_success,
    consecutive_zero_record_runs,
    failed_since
FROM loader.loader
ORDER BY
    CASE load_status
        WHEN 'FAILED' THEN 1
        WHEN 'RUNNING' THEN 2
        WHEN 'PAUSED' THEN 3
        WHEN 'IDLE' THEN 4
    END,
    last_success_timestamp DESC NULLS LAST;
```

### Find Loaders Behind Schedule
```sql
SELECT
    loader_code,
    max_interval_seconds,
    EXTRACT(EPOCH FROM (NOW() - last_success_timestamp)) as seconds_since_last_run,
    EXTRACT(EPOCH FROM (NOW() - last_success_timestamp)) - max_interval_seconds as seconds_overdue
FROM loader.loader
WHERE enabled = true
  AND load_status != 'RUNNING'
  AND EXTRACT(EPOCH FROM (NOW() - last_success_timestamp)) > max_interval_seconds
ORDER BY seconds_overdue DESC;
```

---

## Related Documentation

- **Loader Entity**: `services/loader/src/main/java/com/tiqmo/monitoring/loader/domain/loader/entity/Loader.java`
- **LoadStatus Enum**: `services/loader/src/main/java/com/tiqmo/monitoring/loader/domain/loader/entity/LoadStatus.java`
- **PurgeStrategy Enum**: `services/loader/src/main/java/com/tiqmo/monitoring/loader/domain/loader/entity/PurgeStrategy.java`
- **LoaderService**: `services/loader/src/main/java/com/tiqmo/monitoring/loader/service/loader/LoaderService.java`
- **LoaderController**: `services/loader/src/main/java/com/tiqmo/monitoring/loader/api/loader/LoaderController.java`
- **Frontend UI**: `frontend/src/pages/LoadersListPage.tsx`
- **Known Issues**: `KNOWN_ISSUES.md`

---

**Document Version**: 1.0
**Last Updated**: 2025-12-27
**Author**: ETL Monitoring System Documentation
