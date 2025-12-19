# Code Review Checklist - Before Testing

## ✅ LoaderService - COMPLETE

**Enhanced with:**
- ✅ Comprehensive logging (DEBUG, INFO, WARN levels)
- ✅ MDC context (`loaderCode`) in try-finally blocks
- ✅ BusinessException instead of IllegalArgumentException
- ✅ Input validation with proper error codes
- ✅ Javadoc documentation
- ✅ Null safety checks
- ✅ Cross-field validation (min < max intervals)

---

## 🔄 Remaining Services to Enhance

### 1. SignalsIngestService
**Location:** `service/signals/SignalsIngestService.java`

**Current Issues:**
- No logging
- Generic exceptions
- No validation
- No MDC context

**Required Changes:**
```java
@Slf4j
public class SignalsIngestService {

    public SignalsHistory append(SignalsHistory signal) {
        MDC.put("loaderCode", signal.getLoaderCode());
        try {
            log.info("Appending signal | loaderCode={} | timestamp={}",
                signal.getLoaderCode(), signal.getLoadTimeStamp());

            // Validation
            if (signal.getLoaderCode() == null || signal.getLoaderCode().isBlank()) {
                throw new BusinessException(
                    ErrorCode.VALIDATION_REQUIRED_FIELD,
                    "Loader code is required",
                    "loaderCode"
                );
            }

            if (signal.getLoadTimeStamp() == null) {
                throw new BusinessException(
                    ErrorCode.SIGNAL_INVALID_TIMESTAMP,
                    "Load timestamp is required"
                );
            }

            SignalsHistory saved = repository.save(signal);
            log.info("Signal saved | id={}", saved.getId());
            return saved;

        } finally {
            MDC.remove("loaderCode");
        }
    }
}
```

---

### 2. SignalsQueryService
**Location:** `service/signals/SignalsQueryService.java`

**Required Changes:**
- Add logging for query operations
- Add MDC context
- Validate query parameters (fromEpoch < toEpoch)
- Add BusinessException for invalid ranges

---

### 3. BackfillService
**Location:** `service/backfill/DefaultBackfillService.java`

**Required Changes:**
- Enhance logging with job ID and status changes
- Add MDC context for backfillJobId
- Replace any generic exceptions
- Log execution metrics (duration, records processed)

**Example:**
```java
public BackfillJob execute(Long jobId) {
    MDC.put("backfillJobId", jobId.toString());
    try {
        log.info("Starting backfill execution | jobId={}", jobId);

        BackfillJob job = repository.findById(jobId)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.BACKFILL_JOB_NOT_FOUND,
                "Backfill job with ID " + jobId + " not found"
            ));

        if (job.getStatus() != BackfillJobStatus.PENDING) {
            throw new BusinessException(
                ErrorCode.BACKFILL_JOB_NOT_PENDING,
                "Backfill job must be in PENDING status. Current: " + job.getStatus()
            );
        }

        log.info("Backfill job validated | jobId={} | loaderCode={} | timeRange=[{}, {}]",
            jobId, job.getLoaderCode(), job.getFromTimeEpoch(), job.getToTimeEpoch());

        // Execute...

        log.info("Backfill completed | jobId={} | recordsLoaded={} | duration={}ms",
            jobId, job.getRecordsLoaded(), job.getDurationSeconds() * 1000);

        return job;
    } catch (Exception ex) {
        log.error("Backfill execution failed | jobId={}", jobId, ex);
        throw ex;
    } finally {
        MDC.remove("backfillJobId");
    }
}
```

---

### 4. ConfigService
**Location:** `service/config/ConfigService.java`

**Required Changes:**
- Add logging for plan activations
- Add MDC context for parent/planName
- Validate plan names and keys
- Log cache invalidation events

---

### 5. LoadExecutorService
**Location:** `service/execution/DefaultLoadExecutorService.java`

**Required Changes:**
- Already has good logging structure
- Ensure MDC context includes loaderCode
- Add execution metrics logging
- Log time window calculations
- Log data transformation stats

**Example Enhancement:**
```java
public LoadExecutionResult executeLoad(Loader loader) {
    MDC.put("loaderCode", loader.getLoaderCode());
    try {
        Instant startTime = Instant.now();

        log.info("Starting loader execution | interval={}min | lastLoad={}",
            loader.getIntervalMinutes(), loader.getLastLoadTimestamp());

        // Calculate window
        TimeWindow window = calculateTimeWindow(loader);
        log.debug("Time window | from={} | to={} | timezone={}",
            window.getFromTime(), window.getToTime(), loader.getSourceTimezoneOffsetHours());

        // Execute query
        List<Map<String, Object>> rows = sourceDbManager.runQuery(...);
        log.info("Query executed | rowsRetrieved={}", rows.size());

        // Transform
        List<SignalsHistory> signals = dataTransformer.transform(...);
        log.info("Data transformed | signalsGenerated={}", signals.size());

        // Save
        List<SignalsHistory> saved = repository.saveAll(signals);

        Duration duration = Duration.between(startTime, Instant.now());
        log.info("Execution completed | duration={}ms | recordsLoaded={}",
            duration.toMillis(), saved.size());

        return LoadExecutionResult.success(saved.size(), duration);

    } catch (Exception ex) {
        log.error("Execution failed | loaderCode={}", loader.getLoaderCode(), ex);
        throw new BusinessException(
            ErrorCode.LOADER_EXECUTION_FAILED,
            "Loader execution failed for '" + loader.getLoaderCode() + "': " + ex.getMessage(),
            ex
        );
    } finally {
        MDC.remove("loaderCode");
    }
}
```

---

### 6. SourceDbManager
**Location:** `infra/source/SourceDbManager.java`

**Required Changes:**
- Add logging for query executions
- Add MDC context for sourceDbCode
- Wrap SQLException in BusinessException
- Log connection pool metrics

---

### 7. SegmentService
**Location:** `service/segments/SegmentService.java`

**Status:** ✅ Already has logging
**Check:** Verify MDC usage and error handling

---

## Validation Patterns to Apply

### Pattern 1: Required Field Validation
```java
if (value == null || value.isBlank()) {
    throw new BusinessException(
        ErrorCode.VALIDATION_REQUIRED_FIELD,
        "Field name is required",
        "fieldName"
    );
}
```

### Pattern 2: Numeric Range Validation
```java
if (value <= 0) {
    throw new BusinessException(
        ErrorCode.VALIDATION_INVALID_VALUE,
        "Value must be greater than 0",
        "fieldName"
    );
}
```

### Pattern 3: Time Range Validation
```java
if (fromTime >= toTime) {
    throw new BusinessException(
        ErrorCode.VALIDATION_INVALID_VALUE,
        "From time must be before to time"
    );
}
```

### Pattern 4: Cross-Field Validation
```java
if (minValue != null && maxValue != null && minValue > maxValue) {
    throw new BusinessException(
        ErrorCode.VALIDATION_INVALID_VALUE,
        "Min value cannot be greater than max value",
        "minValue"
    );
}
```

---

## Logging Patterns to Apply

### Pattern 1: Method Entry (DEBUG)
```java
log.debug("Fetching resource by id: {}", id);
```

### Pattern 2: Business Operation (INFO)
```java
log.info("Creating new resource | name={}", name);
log.info("Resource created successfully | id={} | name={}", id, name);
```

### Pattern 3: Warning Condition (WARN)
```java
log.warn("Resource not found | id={}", id);
log.warn("Validation failed | field={} | value={}", field, value);
```

### Pattern 4: Error with Exception (ERROR)
```java
log.error("Operation failed | context={}", context, exception);
```

### Pattern 5: Metrics Logging (INFO)
```java
log.info("Operation completed | duration={}ms | recordsProcessed={}",
    duration, count);
```

---

## MDC Context Patterns

### Pattern 1: Single Business Entity
```java
MDC.put("loaderCode", loaderCode);
try {
    // Business logic
} finally {
    MDC.remove("loaderCode");
}
```

### Pattern 2: Multiple Context Values
```java
MDC.put("loaderCode", loaderCode);
MDC.put("backfillJobId", jobId.toString());
try {
    // Business logic
} finally {
    MDC.remove("loaderCode");
    MDC.remove("backfillJobId");
}
```

### Pattern 3: Auto-Cleanup with Try-With-Resources
```java
// If you create a custom MDCContext class:
try (MDCContext ctx = MDCContext.of("loaderCode", loaderCode)) {
    // Business logic - auto cleanup
}
```

---

## Testing Checklist

Before testing tomorrow:

### Unit Tests
- [ ] Test validation logic (null, blank, invalid values)
- [ ] Test BusinessException error codes
- [ ] Test MDC context cleanup
- [ ] Test cross-field validation

### Integration Tests
- [ ] Test API endpoints with invalid data
- [ ] Verify error response structure
- [ ] Verify requestId in responses
- [ ] Verify logging output

### Manual Testing
- [ ] Test GET /loaders with invalid code
- [ ] Test POST /loaders with missing fields
- [ ] Test POST /loaders with invalid values
- [ ] Test DELETE /loaders with non-existent code
- [ ] Check logs/application.log for proper MDC context
- [ ] Check logs/error.log for exceptions
- [ ] Check logs/api.log for request/response logs

---

## Build Verification

After all enhancements:

```bash
# Clean build
mvn clean compile -DskipTests

# Run tests
mvn test

# Check for compilation errors
# Check for missing imports
# Verify all logs use SLF4J (not System.out.println)
```

---

## Quick Reference: ErrorCode Usage

| Scenario | ErrorCode | HTTP Status |
|----------|-----------|-------------|
| Resource not found | `LOADER_NOT_FOUND` | 404 |
| Resource already exists | `LOADER_ALREADY_EXISTS` | 409 |
| Required field missing | `VALIDATION_REQUIRED_FIELD` | 400 |
| Invalid field value | `VALIDATION_INVALID_VALUE` | 400 |
| Invalid field format | `VALIDATION_INVALID_FORMAT` | 400 |
| Database connection error | `DATABASE_CONNECTION_ERROR` | 503 |
| SQL execution error | `LOADER_INVALID_SQL` | 400 |
| Config not found | `CONFIG_PLAN_NOT_FOUND` | 404 |
| Backfill job not found | `BACKFILL_JOB_NOT_FOUND` | 404 |
| Invalid timestamp | `SIGNAL_INVALID_TIMESTAMP` | 400 |

---

## Summary

**LoaderService:** ✅ **COMPLETE** - Fully enhanced with logging, validation, and proper exceptions

**Remaining:** 6 services need similar treatment
- SignalsIngestService
- SignalsQueryService
- BackfillService (DefaultBackfillService)
- ConfigService
- LoadExecutorService (enhance existing)
- SourceDbManager

**Estimated Time:** 2-3 hours for all services

**Priority:**
1. **HIGH**: SignalsIngestService, BackfillService (used in APIs)
2. **MEDIUM**: LoadExecutorService, SourceDbManager (used in background)
3. **LOW**: ConfigService, SignalsQueryService (less frequently used)
