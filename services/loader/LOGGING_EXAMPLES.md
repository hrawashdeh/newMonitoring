# Enhanced Logging Examples

## Overview

The ETL Monitoring Platform has a comprehensive logging system with:
- 🔍 **Request/Response logging** with automatic request ID tracking
- 🔗 **Distributed tracing** with correlation IDs
- 📊 **Structured logs** with contextual information (MDC)
- 📁 **5 separate log files** for different purposes
- 🔄 **Automatic log rotation** with compression

---

## 1. API Request/Response Logging

Every API request is automatically logged with comprehensive details:

### Example 1: Successful GET Request

**Request:**
```http
GET /api/v1/res/loaders/loaders HTTP/1.1
Host: localhost:8080
User-Agent: PostmanRuntime/7.32.0
Accept: application/json
```

**Logs Generated:**

**`logs/api.log`:**
```log
2025-11-18 22:10:15.234 [http-nio-8080-exec-1] INFO  ApiLoggingFilter - API_REQUEST | requestId=a1b2c3d4-e5f6-7890-abcd-ef1234567890 | correlationId=x9y8z7w6-v5u4-t3s2-r1q0-p9o8n7m6l5k4 | method=GET | uri=/api/v1/res/loaders/loaders | client=127.0.0.1 | headers={"Host":"localhost:8080","User-Agent":"PostmanRuntime/7.32.0","Accept":"application/json"} | queryParams={} | body=

2025-11-18 22:10:15.456 [http-nio-8080-exec-1] INFO  ApiLoggingFilter - API_RESPONSE | requestId=a1b2c3d4-e5f6-7890-abcd-ef1234567890 | correlationId=x9y8z7w6-v5u4-t3s2-r1q0-p9o8n7m6l5k4 | method=GET | uri=/api/v1/res/loaders/loaders | status=200 | latency=222ms | responseSize=1024 bytes | responseBody={"loaders":[{"loaderCode":"SALES_DAILY","sourceDbCode":"SALES_DB"...
```

**`logs/application.log`** (also includes the above + service logs):
```log
2025-11-18 22:10:15.234 [http-nio-8080-exec-1] INFO  ApiLoggingFilter - [requestId=a1b2c3d4-e5f6-7890-abcd-ef1234567890] [correlationId=x9y8z7w6-v5u4-t3s2-r1q0-p9o8n7m6l5k4] [loaderCode=] - API_REQUEST | method=GET | uri=/api/v1/res/loaders/loaders | client=127.0.0.1

2025-11-18 22:10:15.345 [http-nio-8080-exec-1] DEBUG LoaderService - [requestId=a1b2c3d4-e5f6-7890-abcd-ef1234567890] [correlationId=x9y8z7w6-v5u4-t3s2-r1q0-p9o8n7m6l5k4] [loaderCode=] - Fetching all loaders from repository

2025-11-18 22:10:15.456 [http-nio-8080-exec-1] INFO  ApiLoggingFilter - [requestId=a1b2c3d4-e5f6-7890-abcd-ef1234567890] [correlationId=x9y8z7w6-v5u4-t3s2-r1q0-p9o8n7m6l5k4] [loaderCode=] - API_RESPONSE | status=200 | latency=222ms
```

---

### Example 2: POST Request with Body

**Request:**
```http
POST /api/v1/res/loaders HTTP/1.1
Content-Type: application/json

{
  "loaderCode": "SALES_DAILY",
  "sourceDbCode": "SALES_DB",
  "loaderSql": "SELECT * FROM sales WHERE created_at > ?",
  "intervalMinutes": 60
}
```

**Logs Generated:**

**`logs/api.log`:**
```log
2025-11-18 22:15:30.123 [http-nio-8080-exec-2] INFO  ApiLoggingFilter - API_REQUEST | requestId=b2c3d4e5-f6g7-8901-bcde-fg2345678901 | correlationId=y0z1a2b3-c4d5-e6f7-g8h9-i0j1k2l3m4n5 | method=POST | uri=/api/v1/res/loaders | client=192.168.1.100 | headers={"Content-Type":"application/json","Content-Length":"145"} | queryParams={} | body={"loaderCode":"SALES_DAILY","sourceDbCode":"SALES_DB","loaderSql":"SELECT * FROM sales WHERE created_at > ?","intervalMinutes":60}

2025-11-18 22:15:30.234 [http-nio-8080-exec-2] INFO  LoaderService - [requestId=b2c3d4e5-f6g7-8901-bcde-fg2345678901] [correlationId=y0z1a2b3-c4d5-e6f7-g8h9-i0j1k2l3m4n5] [loaderCode=SALES_DAILY] - Creating new loader: SALES_DAILY

2025-11-18 22:15:30.456 [http-nio-8080-exec-2] INFO  LoaderService - [requestId=b2c3d4e5-f6g7-8901-bcde-fg2345678901] [correlationId=y0z1a2b3-c4d5-e6f7-g8h9-i0j1k2l3m4n5] [loaderCode=SALES_DAILY] - Loader created successfully: SALES_DAILY

2025-11-18 22:15:30.567 [http-nio-8080-exec-2] INFO  ApiLoggingFilter - API_RESPONSE | requestId=b2c3d4e5-f6g7-8901-bcde-fg2345678901 | correlationId=y0z1a2b3-c4d5-e6f7-g8h9-i0j1k2l3m4n5 | method=POST | uri=/api/v1/res/loaders | status=201 | latency=444ms | responseSize=256 bytes | responseBody={"loaderCode":"SALES_DAILY","sourceDbCode":"SALES_DB"...
```

---

## 2. Structured Logging with MDC Context

All log messages automatically include contextual information via SLF4J's MDC (Mapped Diagnostic Context):

### Example: Service Layer Logging with Context

**Code:**
```java
@Service
@RequiredArgsConstructor
@Slf4j
public class LoaderService {
    private final LoaderRepository repository;

    public Loader getByCode(String loaderCode) {
        // Add custom MDC context
        MDC.put("loaderCode", loaderCode);

        try {
            log.debug("Fetching loader by code: {}", loaderCode);

            Loader loader = repository.findByLoaderCode(loaderCode)
                .orElseThrow(() -> {
                    log.warn("Loader not found: {}", loaderCode);
                    return new BusinessException(
                        ErrorCode.LOADER_NOT_FOUND,
                        "Loader with code '" + loaderCode + "' not found"
                    );
                });

            log.info("Loader found successfully: {}", loaderCode);
            return loader;

        } finally {
            MDC.remove("loaderCode");  // Clean up
        }
    }

    @Transactional
    public Loader create(CreateLoaderRequest request) {
        MDC.put("loaderCode", request.getLoaderCode());

        try {
            log.info("Creating new loader: {}", request.getLoaderCode());

            // Check for duplicate
            if (repository.existsByLoaderCode(request.getLoaderCode())) {
                log.warn("Loader already exists: {}", request.getLoaderCode());
                throw new BusinessException(
                    ErrorCode.LOADER_ALREADY_EXISTS,
                    "Loader '" + request.getLoaderCode() + "' already exists"
                );
            }

            Loader loader = repository.save(mapToEntity(request));
            log.info("Loader created successfully: {}", loader.getLoaderCode());

            return loader;

        } finally {
            MDC.remove("loaderCode");
        }
    }
}
```

**Generated Logs:**

**`logs/application.log`:**
```log
2025-11-18 22:20:10.123 [http-nio-8080-exec-3] DEBUG LoaderService - [requestId=c3d4e5f6-g7h8-9012-cdef-gh3456789012] [correlationId=z1a2b3c4-d5e6-f7g8-h9i0-j1k2l3m4n5o6] [loaderCode=SALES_DAILY] - Fetching loader by code: SALES_DAILY

2025-11-18 22:20:10.234 [http-nio-8080-exec-3] INFO  LoaderService - [requestId=c3d4e5f6-g7h8-9012-cdef-gh3456789012] [correlationId=z1a2b3c4-d5e6-f7g8-h9i0-j1k2l3m4n5o6] [loaderCode=SALES_DAILY] - Loader found successfully: SALES_DAILY
```

**Notice:** Every log line includes:
- `requestId` - Unique per HTTP request
- `correlationId` - Spans multiple services
- `loaderCode` - Custom business context

---

## 3. Error Logging Examples

### Example 1: Business Exception (404 Not Found)

**Code:**
```java
public Loader getByCode(String loaderCode) {
    return repository.findByLoaderCode(loaderCode)
        .orElseThrow(() -> new BusinessException(
            ErrorCode.LOADER_NOT_FOUND,
            "Loader with code '" + loaderCode + "' not found"
        ));
}
```

**Generated Logs:**

**`logs/error.log`:**
```log
2025-11-18 22:25:45.123 [http-nio-8080-exec-4] WARN  GlobalExceptionHandler - [requestId=d4e5f6g7-h8i9-0123-defg-hi4567890123] [correlationId=a2b3c4d5-e6f7-g8h9-i0j1-k2l3m4n5o6p7] [loaderCode=INVALID_CODE] - Business exception | errorCode=LDR-001 | message=Loader with code 'INVALID_CODE' not found
```

**`logs/api.log`:**
```log
2025-11-18 22:25:45.234 [http-nio-8080-exec-4] INFO  ApiLoggingFilter - API_RESPONSE | requestId=d4e5f6g7-h8i9-0123-defg-hi4567890123 | correlationId=a2b3c4d5-e6f7-g8h9-i0j1-k2l3m4n5o6p7 | method=GET | uri=/api/v1/res/loaders/INVALID_CODE | status=404 | latency=111ms | responseBody={"requestId":"d4e5f6g7-h8i9-0123-defg-hi4567890123","timestamp":1700000000,"status":"ERROR","errors":[{"level":"ERROR","errorCode":"LDR-001","codeName":"LOADER_NOT_FOUND","errorMessage":"Loader with code 'INVALID_CODE' not found"}]}
```

---

### Example 2: Validation Errors (400 Bad Request)

**Code:**
```java
@PostMapping
public ResponseEntity<?> create(@RequestBody CreateLoaderRequest request) {
    String requestId = RequestIdFilter.getCurrentRequestId();
    List<ApiError> errors = new ArrayList<>();

    if (request.getLoaderCode() == null) {
        log.warn("Validation failed: loaderCode is required");
        errors.add(ApiError.from(
            ErrorCode.VALIDATION_REQUIRED_FIELD,
            "Loader code is required",
            "loaderCode"
        ));
    }

    if (!errors.isEmpty()) {
        log.warn("Validation failed with {} errors", errors.size());
        ErrorResponse response = ErrorResponse.multipleErrors(requestId, errors);
        return ResponseEntity.badRequest().body(response);
    }

    // ...
}
```

**Generated Logs:**

**`logs/error.log`:**
```log
2025-11-18 22:30:12.123 [http-nio-8080-exec-5] WARN  LoaderController - [requestId=e5f6g7h8-i9j0-1234-efgh-ij5678901234] [correlationId=b3c4d5e6-f7g8-h9i0-j1k2-l3m4n5o6p7q8] [loaderCode=] - Validation failed: loaderCode is required

2025-11-18 22:30:12.234 [http-nio-8080-exec-5] WARN  LoaderController - [requestId=e5f6g7h8-i9j0-1234-efgh-ij5678901234] [correlationId=b3c4d5e6-f7g8-h9i0-j1k2-l3m4n5o6p7q8] [loaderCode=] - Validation failed with 1 errors
```

---

### Example 3: Infrastructure Error with Stack Trace (503 Service Unavailable)

**Code:**
```java
public List<Map<String, Object>> runQuery(String sourceDbCode, String sql) {
    try {
        DataSource dataSource = getDataSource(sourceDbCode);
        return jdbcTemplate.queryForList(sql);
    } catch (SQLException ex) {
        log.error("Database query failed for source: {} | sql: {}", sourceDbCode, sql, ex);
        throw new BusinessException(
            ErrorCode.DATABASE_CONNECTION_ERROR,
            "Failed to execute query on source database: " + sourceDbCode,
            ex
        );
    }
}
```

**Generated Logs:**

**`logs/error.log`:**
```log
2025-11-18 22:35:20.123 [http-nio-8080-exec-6] ERROR SourceDbManager - [requestId=f6g7h8i9-j0k1-2345-fghi-jk6789012345] [correlationId=c4d5e6f7-g8h9-i0j1-k2l3-m4n5o6p7q8r9] [loaderCode=SALES_DAILY] - Database query failed for source: SALES_DB | sql: SELECT * FROM sales WHERE created_at > '2025-01-01'
java.sql.SQLException: Connection to database 'SALES_DB' refused
    at com.mysql.cj.jdbc.ConnectionImpl.connectOneTryOnly(ConnectionImpl.java:956)
    at com.mysql.cj.jdbc.ConnectionImpl.createNewIO(ConnectionImpl.java:826)
    at com.mysql.cj.jdbc.ConnectionImpl.<init>(ConnectionImpl.java:456)
    ... 42 more

2025-11-18 22:35:20.234 [http-nio-8080-exec-6] WARN  GlobalExceptionHandler - [requestId=f6g7h8i9-j0k1-2345-fghi-jk6789012345] [correlationId=c4d5e6f7-g8h9-i0j1-k2l3-m4n5o6p7q8r9] [loaderCode=SALES_DAILY] - Business exception | errorCode=INF-001 | message=Failed to execute query on source database: SALES_DB
```

---

## 4. Loader Execution Logging

**Code:**
```java
@Service
@Slf4j
public class LoadExecutorService {

    public LoadExecutionResult executeLoad(Loader loader) {
        MDC.put("loaderCode", loader.getLoaderCode());

        try {
            log.info("Starting loader execution | interval={}min | lastLoad={}",
                loader.getIntervalMinutes(), loader.getLastLoadTimestamp());

            Instant startTime = Instant.now();

            // Calculate time window
            TimeWindow window = calculateTimeWindow(loader);
            log.debug("Time window calculated | from={} | to={}", window.getFromTime(), window.getToTime());

            // Execute query
            log.debug("Executing query | sourceDb={} | sql={}",
                loader.getSourceDbCode(), truncateSql(loader.getLoaderSql()));
            List<Map<String, Object>> rows = sourceDbManager.runQuery(loader.getSourceDbCode(), executableSql);
            log.info("Query executed | rowsRetrieved={}", rows.size());

            // Transform data
            log.debug("Transforming data | timezoneOffset={}", loader.getSourceTimezoneOffsetHours());
            List<SignalsHistory> signals = dataTransformer.transform(loader.getLoaderCode(), rows, timezoneOffset);
            log.info("Data transformed | signalsGenerated={}", signals.size());

            // Save to database
            List<SignalsHistory> saved = signalsHistoryRepository.saveAll(signals);
            log.info("Signals saved | recordsInserted={}", saved.size());

            Duration duration = Duration.between(startTime, Instant.now());
            log.info("Loader execution completed | duration={}ms | recordsLoaded={}",
                duration.toMillis(), saved.size());

            return LoadExecutionResult.success(saved.size(), duration);

        } catch (Exception ex) {
            log.error("Loader execution failed | error={}", ex.getMessage(), ex);
            throw ex;
        } finally {
            MDC.remove("loaderCode");
        }
    }
}
```

**Generated Logs:**

**`logs/loader-execution.log`:**
```log
2025-11-18 22:40:00.000 [scheduler-thread-1] INFO  LoadExecutorService - [requestId=] [correlationId=] [loaderCode=SALES_DAILY] - Starting loader execution | interval=60min | lastLoad=2025-11-18T21:40:00Z

2025-11-18 22:40:00.123 [scheduler-thread-1] DEBUG LoadExecutorService - [requestId=] [correlationId=] [loaderCode=SALES_DAILY] - Time window calculated | from=2025-11-18T21:40:00Z | to=2025-11-18T22:40:00Z

2025-11-18 22:40:00.234 [scheduler-thread-1] DEBUG LoadExecutorService - [requestId=] [correlationId=] [loaderCode=SALES_DAILY] - Executing query | sourceDb=SALES_DB | sql=SELECT * FROM sales WHERE created_at BETWEEN ? AND ?

2025-11-18 22:40:02.456 [scheduler-thread-1] INFO  LoadExecutorService - [requestId=] [correlationId=] [loaderCode=SALES_DAILY] - Query executed | rowsRetrieved=1250

2025-11-18 22:40:02.567 [scheduler-thread-1] DEBUG LoadExecutorService - [requestId=] [correlationId=] [loaderCode=SALES_DAILY] - Transforming data | timezoneOffset=+4

2025-11-18 22:40:02.678 [scheduler-thread-1] INFO  LoadExecutorService - [requestId=] [correlationId=] [loaderCode=SALES_DAILY] - Data transformed | signalsGenerated=1250

2025-11-18 22:40:03.123 [scheduler-thread-1] INFO  LoadExecutorService - [requestId=] [correlationId=] [loaderCode=SALES_DAILY] - Signals saved | recordsInserted=1250

2025-11-18 22:40:03.234 [scheduler-thread-1] INFO  LoadExecutorService - [requestId=] [correlationId=] [loaderCode=SALES_DAILY] - Loader execution completed | duration=3234ms | recordsLoaded=1250
```

---

## 5. SQL Query Logging

**`logs/sql.log`:** (When Hibernate SQL logging is enabled)
```log
2025-11-18 22:45:10.123 [http-nio-8080-exec-7] DEBUG org.hibernate.SQL - [requestId=g7h8i9j0-k1l2-3456-ghij-kl7890123456] [correlationId=d5e6f7g8-h9i0-j1k2-l3m4-n5o6p7q8r9s0] [loaderCode=] -
    select
        l1_0.id,
        l1_0.loader_code,
        l1_0.source_db_code,
        l1_0.loader_sql,
        l1_0.interval_minutes,
        l1_0.source_timezone_offset_hours,
        l1_0.last_load_timestamp
    from
        loader.loader l1_0
    where
        l1_0.loader_code=?

2025-11-18 22:45:10.234 [http-nio-8080-exec-7] TRACE org.hibernate.orm.jdbc.bind - [requestId=g7h8i9j0-k1l2-3456-ghij-kl7890123456] [correlationId=d5e6f7g8-h9i0-j1k2-l3m4-n5o6p7q8r9s0] [loaderCode=] - binding parameter [1] as [VARCHAR] - [SALES_DAILY]
```

---

## 6. Log File Organization

The system automatically separates logs into 5 different files:

### **`logs/application.log`** - All application logs
- All INFO, WARN, ERROR logs from entire application
- Request/response logs
- Service logs
- Infrastructure logs
- **Rotation:** Daily + 100MB size limit
- **Retention:** 30 days (max 10GB)

### **`logs/api.log`** - API request/response only
- Filtered to only API_REQUEST and API_RESPONSE log messages
- Useful for API monitoring and debugging
- **Rotation:** Daily + 100MB size limit
- **Retention:** 30 days (max 20GB)

### **`logs/error.log`** - Errors and warnings only
- All WARN and ERROR level logs
- Stack traces included
- **Rotation:** Daily + 100MB size limit
- **Retention:** 30 days (max 5GB)

### **`logs/loader-execution.log`** - Scheduler execution logs
- Loader execution lifecycle
- ETL job progress
- Data loading metrics
- **Rotation:** Daily + 100MB size limit
- **Retention:** 30 days (max 10GB)

### **`logs/sql.log`** - Database query logs
- Hibernate SQL statements
- Query parameters
- Execution times
- **Rotation:** Daily + 50MB size limit
- **Retention:** 7 days (max 2GB)

### **Archived Logs:**
```
logs/
  application.log              # Current day
  api.log
  error.log
  loader-execution.log
  sql.log
  archive/
    application-2025-11-17.0.log.gz    # Yesterday
    application-2025-11-17.1.log.gz    # Yesterday (2nd file)
    application-2025-11-16.0.log.gz
    api-2025-11-17.0.log.gz
    error-2025-11-17.0.log.gz
    ...
```

---

## 7. Complete Request Flow Example

**Request:** `POST /api/v1/res/loaders`

**Complete Log Trace:**

```log
# 1. REQUEST RECEIVED
2025-11-18 22:50:00.000 [http-nio-8080-exec-8] INFO  ApiLoggingFilter - [requestId=h8i9j0k1-l2m3-4567-hijk-lm8901234567] [correlationId=e6f7g8h9-i0j1-k2l3-m4n5-o6p7q8r9s0t1] [loaderCode=] - API_REQUEST | method=POST | uri=/api/v1/res/loaders | client=192.168.1.100 | body={"loaderCode":"NEW_LOADER"...}

# 2. SERVICE LAYER - VALIDATION
2025-11-18 22:50:00.100 [http-nio-8080-exec-8] INFO  LoaderService - [requestId=h8i9j0k1-l2m3-4567-hijk-lm8901234567] [correlationId=e6f7g8h9-i0j1-k2l3-m4n5-o6p7q8r9s0t1] [loaderCode=NEW_LOADER] - Creating new loader: NEW_LOADER

# 3. DATABASE QUERY
2025-11-18 22:50:00.200 [http-nio-8080-exec-8] DEBUG org.hibernate.SQL - [requestId=h8i9j0k1-l2m3-4567-hijk-lm8901234567] [correlationId=e6f7g8h9-i0j1-k2l3-m4n5-o6p7q8r9s0t1] [loaderCode=NEW_LOADER] -
    select count(*) from loader.loader where loader_code=?

# 4. SERVICE LAYER - SAVING
2025-11-18 22:50:00.300 [http-nio-8080-exec-8] DEBUG org.hibernate.SQL - [requestId=h8i9j0k1-l2m3-4567-hijk-lm8901234567] [correlationId=e6f7g8h9-i0j1-k2l3-m4n5-o6p7q8r9s0t1] [loaderCode=NEW_LOADER] -
    insert into loader.loader (loader_code, source_db_code, loader_sql, ...) values (?, ?, ?, ...)

# 5. SERVICE LAYER - SUCCESS
2025-11-18 22:50:00.400 [http-nio-8080-exec-8] INFO  LoaderService - [requestId=h8i9j0k1-l2m3-4567-hijk-lm8901234567] [correlationId=e6f7g8h9-i0j1-k2l3-m4n5-o6p7q8r9s0t1] [loaderCode=NEW_LOADER] - Loader created successfully: NEW_LOADER

# 6. RESPONSE SENT
2025-11-18 22:50:00.500 [http-nio-8080-exec-8] INFO  ApiLoggingFilter - [requestId=h8i9j0k1-l2m3-4567-hijk-lm8901234567] [correlationId=e6f7g8h9-i0j1-k2l3-m4n5-o6p7q8r9s0t1] [loaderCode=] - API_RESPONSE | status=201 | latency=500ms | responseSize=512 bytes
```

**You can trace the entire request using the `requestId`!**

---

## 8. Best Practices

### ✅ DO:

```java
// Use SLF4J parameterized logging (more efficient)
log.info("Processing loader: {}", loaderCode);
log.debug("Query executed in {}ms with {} rows", duration, rowCount);

// Add MDC context for business logic
MDC.put("loaderCode", loaderCode);
try {
    // ... business logic
} finally {
    MDC.remove("loaderCode");
}

// Log at appropriate levels
log.debug("Internal details");    // Development debugging
log.info("Business events");       // Normal operations
log.warn("Recoverable issues");    // Something unusual but handled
log.error("Failures", exception);  // Actual errors with stack traces
```

### ❌ DON'T:

```java
// Don't use string concatenation (wastes resources)
log.info("Processing loader: " + loaderCode);

// Don't log sensitive data
log.info("User password: {}", password);  // ❌ NEVER!
log.info("Encryption key: {}", encryptionKey);  // ❌ NEVER!

// Don't log entire objects (may contain sensitive data)
log.info("Request: {}", request);  // Could contain passwords

// Don't forget to clean up MDC
MDC.put("loaderCode", loaderCode);
// ... if exception happens, MDC leaks to other threads!
```

---

## Summary

The enhanced logging system provides:
- ✅ **Automatic request tracking** with unique IDs
- ✅ **Distributed tracing** with correlation IDs
- ✅ **Structured logs** with contextual information (MDC)
- ✅ **Organized log files** for different purposes
- ✅ **Automatic rotation** with compression
- ✅ **Complete audit trail** for every API request
- ✅ **Easy debugging** with stack traces and context
