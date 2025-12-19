# Error Handling Guide

## Overview

This guide shows practical examples of using the standardized error handling system in the ETL Monitoring Platform.

## Key Components

1. **ErrorCode** - Enum with error codes, messages, and severity levels
2. **BusinessException** - Typed exception for business logic errors
3. **ApiError** - DTO representing a single error
4. **ErrorResponse** - DTO for unified error responses
5. **GlobalExceptionHandler** - Converts exceptions to ErrorResponse

---

## Pattern 1: Service Layer - Throwing Typed Exceptions

### Example 1: Simple Not Found Error

```java
@Service
@RequiredArgsConstructor
public class LoaderService {
    private final LoaderRepository repository;

    public Loader getByCode(String loaderCode) {
        return repository.findByLoaderCode(loaderCode)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.LOADER_NOT_FOUND,
                "Loader with code '" + loaderCode + "' not found"
            ));
    }
}
```

**Resulting JSON Response (404 Not Found):**
```json
{
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": 1700000000,
  "status": "ERROR",
  "errors": [{
    "level": "ERROR",
    "errorCode": "LDR-001",
    "codeName": "LOADER_NOT_FOUND",
    "errorMessage": "Loader with code 'INVALID_CODE' not found"
  }]
}
```

### Example 2: Validation Error with Field

```java
@Service
public class LoaderService {

    public Loader create(CreateLoaderRequest request) {
        // Validate loader code format
        if (request.getLoaderCode() == null || request.getLoaderCode().isBlank()) {
            throw new BusinessException(
                ErrorCode.VALIDATION_REQUIRED_FIELD,
                "Loader code is required",
                "loaderCode"  // Field name
            );
        }

        // Check for duplicate
        if (repository.existsByLoaderCode(request.getLoaderCode())) {
            throw new BusinessException(
                ErrorCode.LOADER_ALREADY_EXISTS,
                "Loader with code '" + request.getLoaderCode() + "' already exists",
                "loaderCode"
            );
        }

        return repository.save(mapToEntity(request));
    }
}
```

**Resulting JSON Response (409 Conflict):**
```json
{
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": 1700000000,
  "status": "ERROR",
  "errors": [{
    "level": "ERROR",
    "errorCode": "LDR-002",
    "codeName": "LOADER_ALREADY_EXISTS",
    "errorMessage": "Loader with code 'SALES_DAILY' already exists",
    "field": "loaderCode"
  }]
}
```

### Example 3: Infrastructure Error with Cause

```java
@Service
@RequiredArgsConstructor
public class SourceDbManager {

    public List<Map<String, Object>> runQuery(String sourceDbCode, String sql) {
        try {
            DataSource dataSource = getDataSource(sourceDbCode);
            // Execute query...
            return results;
        } catch (SQLException ex) {
            throw new BusinessException(
                ErrorCode.DATABASE_CONNECTION_ERROR,
                "Failed to execute query on source database: " + sourceDbCode,
                ex  // Original exception as cause
            );
        }
    }
}
```

**Resulting JSON Response (503 Service Unavailable):**
```json
{
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": 1700000000,
  "status": "ERROR",
  "errors": [{
    "level": "ERROR",
    "errorCode": "INF-001",
    "codeName": "DATABASE_CONNECTION_ERROR",
    "errorMessage": "Failed to execute query on source database: SALES_DB"
  }]
}
```

---

## Pattern 2: Controller Layer - Direct Error Responses

### Example 1: Returning Error Response Directly

```java
@RestController
@RequestMapping("/api/v1/res/backfill")
@RequiredArgsConstructor
public class BackfillAdminController {

    @PostMapping("/{id}/execute")
    public ResponseEntity<?> executeBackfillJob(@PathVariable Long id) {
        String requestId = RequestIdFilter.getCurrentRequestId();

        BackfillJob job = backfillService.findById(id)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.BACKFILL_JOB_NOT_FOUND,
                "Backfill job with ID " + id + " not found"
            ));

        // Check if job is in correct status
        if (job.getStatus() != BackfillJobStatus.PENDING) {
            ApiError error = ApiError.from(
                ErrorCode.BACKFILL_JOB_NOT_PENDING,
                "Backfill job must be in PENDING status to execute. Current status: " + job.getStatus()
            );
            ErrorResponse response = ErrorResponse.singleError(requestId, error);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Execute the job
        BackfillJobResult result = backfillService.execute(job);
        return ResponseEntity.ok(result);
    }
}
```

### Example 2: Multiple Validation Errors

```java
@RestController
@RequestMapping("/api/v1/res/loaders")
public class LoaderController {

    @PostMapping
    public ResponseEntity<?> create(@RequestBody CreateLoaderRequest request) {
        String requestId = RequestIdFilter.getCurrentRequestId();
        List<ApiError> errors = new ArrayList<>();

        // Validate multiple fields
        if (request.getLoaderCode() == null || request.getLoaderCode().isBlank()) {
            errors.add(ApiError.from(
                ErrorCode.VALIDATION_REQUIRED_FIELD,
                "Loader code is required",
                "loaderCode"
            ));
        }

        if (request.getIntervalMinutes() == null || request.getIntervalMinutes() <= 0) {
            errors.add(ApiError.from(
                ErrorCode.VALIDATION_INVALID_VALUE,
                "Interval must be greater than 0",
                "intervalMinutes"
            ));
        }

        if (request.getLoaderSql() == null || request.getLoaderSql().isBlank()) {
            errors.add(ApiError.from(
                ErrorCode.VALIDATION_REQUIRED_FIELD,
                "SQL query is required",
                "loaderSql"
            ));
        }

        // Return all validation errors if any
        if (!errors.isEmpty()) {
            ErrorResponse response = ErrorResponse.multipleErrors(requestId, errors);
            return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(response);
        }

        // Proceed with creation
        Loader created = loaderService.create(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }
}
```

**Resulting JSON Response (400 Bad Request):**
```json
{
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": 1700000000,
  "status": "ERROR",
  "errors": [
    {
      "level": "ERROR",
      "errorCode": "VAL-001",
      "codeName": "VALIDATION_REQUIRED_FIELD",
      "errorMessage": "Loader code is required",
      "field": "loaderCode"
    },
    {
      "level": "ERROR",
      "errorCode": "VAL-003",
      "codeName": "VALIDATION_INVALID_VALUE",
      "errorMessage": "Interval must be greater than 0",
      "field": "intervalMinutes"
    }
  ]
}
```

---

## Pattern 3: Configuration Service - Custom Error Handling

### Example: Config Plan Activation

```java
@Service
@RequiredArgsConstructor
public class ConfigService {

    public ConfigPlan activatePlan(String parent, String planName) {
        // Find the plan
        ConfigPlan plan = configPlanRepository.findByParentAndPlanName(parent, planName)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.CONFIG_PLAN_NOT_FOUND,
                String.format("Configuration plan '%s' for parent '%s' not found", planName, parent)
            ));

        // Check if already active
        if (plan.isActive()) {
            throw new BusinessException(
                ErrorCode.CONFIG_PLAN_ALREADY_ACTIVE,
                String.format("Configuration plan '%s' is already active", planName)
            );
        }

        // Deactivate current active plan
        configPlanRepository.findByParentAndIsActive(parent, true)
            .ifPresent(activePlan -> {
                activePlan.setActive(false);
                configPlanRepository.save(activePlan);
            });

        // Activate new plan
        plan.setActive(true);
        plan.setActivatedAt(LocalDateTime.now());
        ConfigPlan activated = configPlanRepository.save(plan);

        // Clear cache and publish event
        clearCache(parent);
        publishConfigChangedEvent(parent, planName);

        return activated;
    }
}
```

---

## Pattern 4: Using Default Error Messages

```java
@Service
public class SignalsService {

    public SignalsHistory validateAndSave(SignalsHistory signal) {
        // Use default message from ErrorCode enum
        if (signal.getLoadTimeStamp() == null) {
            throw new BusinessException(ErrorCode.SIGNAL_INVALID_TIMESTAMP);
            // Uses default message: "Invalid timestamp"
        }

        if (signal.getLoaderCode() == null || signal.getLoaderCode().isBlank()) {
            throw new BusinessException(ErrorCode.SIGNAL_MISSING_REQUIRED_FIELD);
            // Uses default message: "Missing required field"
        }

        return signalsRepository.save(signal);
    }
}
```

---

## Pattern 5: WARNING Level Errors (Non-Fatal)

```java
@Service
public class SecurityAuditService {

    public PermissionReport auditSourceDatabase(String sourceDbCode) {
        SourceDatabase sourceDb = findByCode(sourceDbCode);

        // Check read-only mode
        boolean isReadOnly = checkReadOnlyMode(sourceDb);

        if (!isReadOnly) {
            // This is a WARNING, not a fatal ERROR
            // The ErrorLevel.WARNING is automatically used from ErrorCode definition
            throw new BusinessException(
                ErrorCode.SOURCE_DATABASE_NOT_READONLY,
                String.format("Source database '%s' is not in read-only mode. This poses a data integrity risk.", sourceDbCode)
            );
        }

        return PermissionReport.builder()
            .sourceDbCode(sourceDbCode)
            .isReadOnly(true)
            .build();
    }
}
```

**Resulting JSON Response (with WARNING level):**
```json
{
  "requestId": "a1b2c3d4-e5f6-7890-abcd-ef1234567890",
  "timestamp": 1700000000,
  "status": "ERROR",
  "errors": [{
    "level": "WARNING",
    "errorCode": "SDB-003",
    "codeName": "SOURCE_DATABASE_NOT_READONLY",
    "errorMessage": "Source database 'SALES_DB' is not in read-only mode. This poses a data integrity risk."
  }]
}
```

---

## HTTP Status Code Mapping

The `GlobalExceptionHandler` automatically maps error codes to appropriate HTTP status codes:

| Error Code Pattern | HTTP Status | Example |
|-------------------|-------------|---------|
| `*-001` (Not Found) | 404 | `LOADER_NOT_FOUND`, `CONFIG_PLAN_NOT_FOUND` |
| `LDR-002` (Already Exists) | 409 | `LOADER_ALREADY_EXISTS` |
| `VAL-*` (Validation) | 400 | `VALIDATION_REQUIRED_FIELD` |
| `INF-*` (Infrastructure) | 503 | `DATABASE_CONNECTION_ERROR` |
| `SIG-*`, `LDR-003` | 400 | `SIGNAL_INVALID_TIMESTAMP` |
| `GEN-007` (Conflict) | 409 | `CONFLICT` |
| `GEN-001` (Internal) | 500 | `INTERNAL_ERROR` |
| Default | 400 | All other business errors |

---

## Best Practices

### ✅ DO:
- Use `BusinessException` for all business logic errors
- Include specific error messages with context (IDs, names, values)
- Add field names for validation errors
- Use default error messages when they're sufficient
- Include original exception as cause for infrastructure errors

### ❌ DON'T:
- Throw generic `IllegalArgumentException` or `RuntimeException`
- Return error responses manually (let GlobalExceptionHandler handle it)
- Mix error levels (the ErrorCode already defines the level)
- Log sensitive data in error messages

---

## Example: Complete Service with Error Handling

```java
@Service
@RequiredArgsConstructor
@Slf4j
public class LoaderService {
    private final LoaderRepository repository;
    private final SourceDatabaseRepository sourceDbRepository;

    @Transactional(readOnly = true)
    public Loader getByCode(String loaderCode) {
        log.debug("Fetching loader by code: {}", loaderCode);

        return repository.findByLoaderCode(loaderCode)
            .orElseThrow(() -> new BusinessException(
                ErrorCode.LOADER_NOT_FOUND,
                "Loader with code '" + loaderCode + "' not found"
            ));
    }

    @Transactional
    public Loader create(CreateLoaderRequest request) {
        log.info("Creating new loader: {}", request.getLoaderCode());

        // Validation
        if (repository.existsByLoaderCode(request.getLoaderCode())) {
            throw new BusinessException(
                ErrorCode.LOADER_ALREADY_EXISTS,
                "Loader with code '" + request.getLoaderCode() + "' already exists",
                "loaderCode"
            );
        }

        // Verify source database exists
        SourceDatabase sourceDb = sourceDbRepository.findByCode(request.getSourceDbCode())
            .orElseThrow(() -> new BusinessException(
                ErrorCode.SOURCE_DATABASE_NOT_FOUND,
                "Source database '" + request.getSourceDbCode() + "' not found",
                "sourceDbCode"
            ));

        // Create loader
        Loader loader = Loader.builder()
            .loaderCode(request.getLoaderCode())
            .sourceDbCode(request.getSourceDbCode())
            .loaderSql(request.getLoaderSql())
            .intervalMinutes(request.getIntervalMinutes())
            .build();

        Loader saved = repository.save(loader);
        log.info("Loader created successfully: {}", saved.getLoaderCode());

        return saved;
    }

    @Transactional
    public void deleteByCode(String loaderCode) {
        log.info("Deleting loader: {}", loaderCode);

        if (!repository.existsByLoaderCode(loaderCode)) {
            throw new BusinessException(
                ErrorCode.LOADER_NOT_FOUND,
                "Cannot delete: Loader with code '" + loaderCode + "' not found"
            );
        }

        repository.deleteByLoaderCode(loaderCode);
        log.info("Loader deleted successfully: {}", loaderCode);
    }
}
```

---

## Summary

Use `BusinessException` with `ErrorCode` throughout your application:
1. **Service Layer**: Throw `BusinessException` for business logic violations
2. **Controller Layer**: Let `GlobalExceptionHandler` convert to `ErrorResponse`
3. **Direct Error Responses**: Use `ApiError.from()` for complex validation scenarios
4. **Error Level**: Automatically determined from `ErrorCode` definition
5. **HTTP Status**: Automatically mapped by `GlobalExceptionHandler`

The system ensures:
- ✅ Consistent error format across all endpoints
- ✅ Automatic request ID tracking
- ✅ Proper HTTP status codes
- ✅ Comprehensive logging
- ✅ Error code enum name in trace for debugging
