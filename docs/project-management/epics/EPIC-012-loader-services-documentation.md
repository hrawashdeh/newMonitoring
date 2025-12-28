---
id: "EPIC-012"
title: "Loader Services Architecture Documentation"
status: "backlog"
priority: "high"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "backend-team"
owner: "product-team"
labels: ["documentation", "architecture", "backend", "loaders"]
estimated_points: 21
sprint: "sprint-03"
target_release: "v1.3.0"
dependencies: []
linear_id: ""
jira_id: ""
github_project_id: ""
---

# EPIC-012: Loader Services Architecture Documentation

## Overview

**Brief Description**: Comprehensive documentation of the three core loader services: Signal Loader (main ETL service), Data Generator (test data service), and ETL Initializer (schema and seed data service).

**Business Value**: Development and operations teams need detailed documentation of loader service architecture, functionality, and interaction patterns to maintain, extend, and troubleshoot the monitoring platform.

**Success Criteria**:
- ✅ Signal Loader architecture and features documented
- ✅ Data Generator functionality documented
- ✅ ETL Initializer bootstrap process documented
- ✅ Service interaction diagrams created
- ✅ Configuration parameters explained
- ✅ Embedded loader features detailed

---

## Scope

### In Scope
- Signal Loader (main ETL service) architecture
- Data Generator service design
- ETL Initializer bootstrap process
- Service interaction patterns
- Configuration management
- Embedded loader capabilities
- Database schema initialization
- Test data generation patterns

### Out of Scope
- Implementation changes to existing services
- New feature development
- Performance optimization
- Cloud deployment configurations

---

## Service Architecture Overview

### System Diagram
```
┌─────────────────────────────────────────────────────────────┐
│                     Monitoring Platform                      │
├─────────────────────────────────────────────────────────────┤
│                                                              │
│  ┌──────────────┐      ┌──────────────┐     ┌────────────┐ │
│  │ ETL          │      │ Data         │     │ Signal     │ │
│  │ Initializer  │─────▶│ Generator    │────▶│ Loader     │ │
│  │              │      │              │     │            │ │
│  │ (Bootstrap)  │      │ (Test Data)  │     │ (ETL Core) │ │
│  └──────────────┘      └──────────────┘     └────────────┘ │
│         │                     │                     │       │
│         │                     │                     │       │
│         ▼                     ▼                     ▼       │
│  ┌──────────────────────────────────────────────────────┐  │
│  │           PostgreSQL Database (monitor schema)        │  │
│  │  - loader_config (ETL configurations)                 │  │
│  │  - loader_execution (execution history)               │  │
│  │  - signal_data (collected metrics)                    │  │
│  └──────────────────────────────────────────────────────┘  │
│                                                              │
└─────────────────────────────────────────────────────────────┘
```

---

## 1. ETL Initializer Service

### Purpose
Bootstrap service that initializes database schema, creates tables, and seeds initial data on platform startup.

### Key Responsibilities
1. **Database Schema Initialization**
   - Creates `monitor` schema
   - Creates `auth` schema
   - Applies Flyway migrations

2. **Seed Data Loading**
   - Inserts initial loader configurations
   - Creates default users
   - Inserts reference data
   - Populates message dictionary

3. **Environment Validation**
   - Checks database connectivity
   - Validates schema versions
   - Ensures required tables exist

### Technology Stack
- **Framework**: Spring Boot 3.x
- **Database Migration**: Flyway
- **Language**: Java 17
- **Build Tool**: Maven

### Configuration
```yaml
# application.yaml
spring:
  application:
    name: etl-initializer
  datasource:
    url: jdbc:postgresql://postgres-postgresql.monitoring-infra:5432/postgres
    username: ${DB_USERNAME}
    password: ${DB_PASSWORD}
  flyway:
    enabled: true
    locations: classpath:db/migration
    schemas: monitor,auth
    baseline-on-migrate: true
```

### Flyway Migrations
**Migration Files** (`src/main/resources/db/migration/`):
```
V1__create_monitor_schema.sql          # Create monitor schema
V2__create_loader_tables.sql           # Create loader_config, loader_execution
V3__create_signal_data_table.sql       # Create signal_data table
V4__seed_loader_configs.sql            # Insert initial loaders
V5__add_authentication_schema.sql      # Create auth schema and users table
V6__create_message_dictionary.sql      # Create message reference table
```

### Seed Data Examples

**V4: Loader Configurations**
```yaml
# /src/main/resources/etl-data-v1.yaml
loaders:
  - loaderCode: "SIGNAL_LOADER_001"
    enabled: true
    minIntervalSeconds: 60
    maxIntervalSeconds: 300
    maxParallelExecutions: 3
    maxQueryPeriodSeconds: 86400
    queryText: |
      SELECT timestamp, value, unit
      FROM signal_source
      WHERE timestamp > :lastExecutionTime
    description: "Collects signal metrics from primary source"

  - loaderCode: "SIGNAL_LOADER_002"
    enabled: true
    minIntervalSeconds: 120
    maxIntervalSeconds: 600
    maxParallelExecutions: 2
    maxQueryPeriodSeconds: 172800
    queryText: |
      SELECT event_time, signal_value, metadata
      FROM secondary_source
      WHERE event_time > :lastExecutionTime
    description: "Collects signal data from secondary endpoint"
```

**V5: Authentication Data**
```yaml
# /src/main/resources/auth-data-v1.yaml
users:
  - username: "admin"
    password: "admin123"      # Hashed via bcrypt in service
    email: "admin@monitoring.local"
    roles: ["ADMIN", "USER"]
    enabled: true

  - username: "operator"
    password: "operator123"
    email: "operator@monitoring.local"
    roles: ["USER"]
    enabled: true
```

**V6: Message Dictionary**
```yaml
# /src/main/resources/messages-data-v1.yaml
messages:
  - code: "MSG_001"
    category: "INFO"
    template: "Loader {loaderCode} started successfully"
    severity: "INFO"

  - code: "MSG_002"
    category: "ERROR"
    template: "Loader {loaderCode} failed with error: {errorMessage}"
    severity: "ERROR"

  - code: "MSG_003"
    category: "WARNING"
    template: "Loader {loaderCode} execution exceeded max time: {duration}ms"
    severity: "WARNING"
```

### Java Implementation
```java
@Service
public class DataInitializationService {

    @Autowired
    private LoaderConfigRepository loaderConfigRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private MessageRepository messageRepository;

    @PostConstruct
    public void initializeData() {
        log.info("Starting data initialization...");

        // Load loader configurations
        loadLoaderConfigs();

        // Load authentication data
        loadAuthenticationData();

        // Load message dictionary
        loadMessageDictionary();

        log.info("Data initialization completed successfully");
    }

    private void loadLoaderConfigs() {
        // Parse etl-data-v1.yaml and insert into loader_config table
    }

    private void loadAuthenticationData() {
        // Parse auth-data-v1.yaml and insert into users table
        // Hash passwords with bcrypt before storing
    }

    private void loadMessageDictionary() {
        // Parse messages-data-v1.yaml and insert into messages table
    }
}
```

### Deployment
```yaml
# k8s_manifest/etl-initializer-deployment.yaml
apiVersion: apps/v1
kind: Deployment
metadata:
  name: etl-initializer
  namespace: monitoring-app
spec:
  replicas: 1
  template:
    spec:
      containers:
        - name: etl-initializer
          image: etl-initializer:latest
          env:
            - name: DB_USERNAME
              valueFrom:
                secretKeyRef:
                  name: app-secrets
                  key: POSTGRES_USERNAME
            - name: DB_PASSWORD
              valueFrom:
                secretKeyRef:
                  name: app-secrets
                  key: POSTGRES_PASSWORD
          resources:
            limits:
              memory: "512Mi"
              cpu: "500m"
```

---

## 2. Data Generator Service

### Purpose
Generates realistic test data for loaders to process, enabling development and testing without external data sources.

### Key Responsibilities
1. **Signal Data Generation**
   - Creates time-series signal data
   - Simulates realistic patterns (trends, noise, seasonality)
   - Populates `signal_data` table

2. **Configurable Data Patterns**
   - Linear trends
   - Sinusoidal patterns
   - Random noise
   - Anomaly injection

3. **Controlled Data Volume**
   - Configurable record count
   - Configurable time ranges
   - Batch generation

### Technology Stack
- **Framework**: Spring Boot 3.x
- **Language**: Java 17
- **Build Tool**: Maven
- **Scheduling**: Spring @Scheduled

### Configuration
```yaml
# data-generator-config.yaml
data-generation:
  enabled: true
  schedule:
    cron: "0 */5 * * * *"      # Every 5 minutes
  signal:
    recordsPerBatch: 100
    timeRangeDays: 7
    signalTypes:
      - type: "temperature"
        minValue: -20.0
        maxValue: 50.0
        unit: "celsius"
        pattern: "sinusoidal"   # Daily temperature cycle
      - type: "pressure"
        minValue: 980.0
        maxValue: 1050.0
        unit: "hPa"
        pattern: "random"
      - type: "humidity"
        minValue: 30.0
        maxValue: 90.0
        unit: "percent"
        pattern: "linear"        # Gradual change
```

### Data Generation Algorithms

**1. Sinusoidal Pattern** (Temperature)
```java
public double generateSinusoidal(long timestamp, SignalConfig config) {
    double hours = (timestamp % 86400000) / 3600000.0;  // Hour of day
    double amplitude = (config.getMaxValue() - config.getMinValue()) / 2;
    double midpoint = (config.getMaxValue() + config.getMinValue()) / 2;
    double value = midpoint + amplitude * Math.sin(2 * Math.PI * hours / 24);
    return value + randomNoise();
}
```

**2. Random Pattern** (Pressure)
```java
public double generateRandom(SignalConfig config) {
    return config.getMinValue()
         + random.nextDouble() * (config.getMaxValue() - config.getMinValue());
}
```

**3. Linear Trend** (Humidity)
```java
public double generateLinear(long timestamp, SignalConfig config) {
    double slope = 0.001;  // Gradual increase
    double baseValue = (config.getMaxValue() + config.getMinValue()) / 2;
    double value = baseValue + slope * (timestamp / 1000);
    return Math.min(Math.max(value, config.getMinValue()), config.getMaxValue());
}
```

### Generated Data Structure
```java
@Entity
@Table(name = "signal_data", schema = "monitor")
public class SignalData {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private Instant timestamp;

    private String signalType;      // "temperature", "pressure", "humidity"

    private Double value;

    private String unit;            // "celsius", "hPa", "percent"

    private String source;          // "data-generator"

    private Map<String, String> metadata;  // Additional context
}
```

### API Endpoints
```java
@RestController
@RequestMapping("/api/v1/generator")
public class DataGeneratorController {

    // Trigger manual data generation
    @PostMapping("/generate")
    public ResponseEntity<GenerationResult> generateData(
        @RequestParam(defaultValue = "100") int recordCount
    );

    // Get generation statistics
    @GetMapping("/stats")
    public ResponseEntity<GenerationStats> getStats();

    // Configure generation parameters
    @PutMapping("/config")
    public ResponseEntity<Void> updateConfig(
        @RequestBody DataGenerationConfig config
    );
}
```

---

## 3. Signal Loader Service (Main ETL Core)

### Purpose
The main ETL service that executes configured loaders, retrieves signal data, and stores it in the monitoring database.

### Key Responsibilities
1. **Loader Execution Management**
   - Loads configurations from `loader_config` table
   - Schedules loader executions based on intervals
   - Manages parallel execution limits
   - Tracks execution history

2. **Query Execution**
   - Executes SQL queries from loader configuration
   - Passes parameters (lastExecutionTime, etc.)
   - Handles query timeouts
   - Manages database connections

3. **Data Persistence**
   - Stores execution results
   - Records execution metadata
   - Logs errors and warnings

4. **Health Monitoring**
   - Exposes health endpoints
   - Tracks execution metrics
   - Reports failures

### Technology Stack
- **Framework**: Spring Boot 3.x
- **Scheduling**: Spring @Scheduled + Dynamic Scheduling
- **Database**: Spring Data JPA + JDBC Template
- **Language**: Java 17
- **Build Tool**: Maven

### Core Architecture

**Loader Configuration Model**
```java
@Entity
@Table(name = "loader_config", schema = "monitor")
public class LoaderConfig {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(unique = true, nullable = false)
    private String loaderCode;           // "SIGNAL_LOADER_001"

    private Boolean enabled;              // true/false

    private Integer minIntervalSeconds;   // Minimum time between executions

    private Integer maxIntervalSeconds;   // Maximum time between executions

    private Integer maxParallelExecutions; // Concurrent execution limit

    private Integer maxQueryPeriodSeconds; // Max time range for queries

    @Column(columnDefinition = "TEXT")
    private String queryText;             // SQL query to execute

    private String description;

    private Instant createdAt;

    private Instant updatedAt;
}
```

**Loader Execution Model**
```java
@Entity
@Table(name = "loader_execution", schema = "monitor")
public class LoaderExecution {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String loaderCode;

    private Instant startTime;

    private Instant endTime;

    private String status;               // "SUCCESS", "FAILED", "RUNNING"

    private Integer recordsProcessed;

    private Long executionDurationMs;

    @Column(columnDefinition = "TEXT")
    private String errorMessage;

    private Instant createdAt;
}
```

### Loader Execution Engine

**Main Scheduler**
```java
@Service
public class LoaderExecutionService {

    @Autowired
    private LoaderConfigRepository loaderConfigRepository;

    @Autowired
    private LoaderExecutionRepository executionRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    // Execute all enabled loaders
    @Scheduled(fixedDelay = 10000)  // Check every 10 seconds
    public void executeLoaders() {
        List<LoaderConfig> enabledLoaders = loaderConfigRepository
            .findByEnabledTrue();

        for (LoaderConfig loader : enabledLoaders) {
            if (shouldExecuteLoader(loader)) {
                executeLoaderAsync(loader);
            }
        }
    }

    private boolean shouldExecuteLoader(LoaderConfig loader) {
        // Check if enough time has passed since last execution
        Optional<LoaderExecution> lastExecution = executionRepository
            .findTopByLoaderCodeOrderByStartTimeDesc(loader.getLoaderCode());

        if (lastExecution.isEmpty()) {
            return true;  // First execution
        }

        long secondsSinceLastExecution = Duration
            .between(lastExecution.get().getStartTime(), Instant.now())
            .getSeconds();

        return secondsSinceLastExecution >= loader.getMinIntervalSeconds();
    }

    @Async
    public void executeLoaderAsync(LoaderConfig loader) {
        LoaderExecution execution = new LoaderExecution();
        execution.setLoaderCode(loader.getLoaderCode());
        execution.setStartTime(Instant.now());
        execution.setStatus("RUNNING");
        executionRepository.save(execution);

        try {
            // Execute query
            List<Map<String, Object>> results = jdbcTemplate.queryForList(
                loader.getQueryText(),
                getQueryParameters(loader)
            );

            // Process results
            int recordsProcessed = processResults(results, loader);

            // Update execution status
            execution.setStatus("SUCCESS");
            execution.setRecordsProcessed(recordsProcessed);
            execution.setEndTime(Instant.now());
            execution.setExecutionDurationMs(
                Duration.between(execution.getStartTime(), execution.getEndTime())
                    .toMillis()
            );

        } catch (Exception e) {
            execution.setStatus("FAILED");
            execution.setErrorMessage(e.getMessage());
            execution.setEndTime(Instant.now());
            log.error("Loader execution failed: {}", loader.getLoaderCode(), e);
        } finally {
            executionRepository.save(execution);
        }
    }

    private Map<String, Object> getQueryParameters(LoaderConfig loader) {
        // Get last successful execution time
        Optional<LoaderExecution> lastSuccess = executionRepository
            .findTopByLoaderCodeAndStatusOrderByStartTimeDesc(
                loader.getLoaderCode(), "SUCCESS"
            );

        Instant lastExecutionTime = lastSuccess
            .map(LoaderExecution::getEndTime)
            .orElse(Instant.now().minus(
                loader.getMaxQueryPeriodSeconds(), ChronoUnit.SECONDS
            ));

        return Map.of("lastExecutionTime", lastExecutionTime);
    }

    private int processResults(List<Map<String, Object>> results, LoaderConfig loader) {
        // Store results in signal_data table or process as needed
        return results.size();
    }
}
```

### Embedded Loader Features

**1. Dynamic Configuration Reloading**
```java
@Service
public class LoaderConfigWatcher {

    @Scheduled(fixedDelay = 60000)  // Check every minute
    public void reloadConfigurations() {
        // Reload loader configurations from database
        // Apply changes without restart
    }
}
```

**2. Pause/Resume Capability**
```java
@RestController
@RequestMapping("/api/v1/loaders")
public class LoaderControlController {

    @PutMapping("/{loaderCode}/toggle")
    public ResponseEntity<LoaderConfig> toggleLoader(
        @PathVariable String loaderCode
    ) {
        LoaderConfig loader = loaderRepository.findByLoaderCode(loaderCode)
            .orElseThrow();
        loader.setEnabled(!loader.getEnabled());
        return ResponseEntity.ok(loaderRepository.save(loader));
    }
}
```

**3. Execution History API**
```java
@GetMapping("/{loaderCode}/executions")
public ResponseEntity<Page<LoaderExecution>> getExecutionHistory(
    @PathVariable String loaderCode,
    @RequestParam(defaultValue = "0") int page,
    @RequestParam(defaultValue = "20") int size
) {
    Pageable pageable = PageRequest.of(page, size,
        Sort.by("startTime").descending());
    return ResponseEntity.ok(
        executionRepository.findByLoaderCode(loaderCode, pageable)
    );
}
```

**4. Health Monitoring**
```java
@Component
public class LoaderHealthIndicator implements HealthIndicator {

    @Override
    public Health health() {
        List<LoaderConfig> enabledLoaders = loaderRepository.findByEnabledTrue();

        for (LoaderConfig loader : enabledLoaders) {
            Optional<LoaderExecution> lastExecution = executionRepository
                .findTopByLoaderCodeOrderByStartTimeDesc(loader.getLoaderCode());

            if (lastExecution.isPresent() &&
                "FAILED".equals(lastExecution.get().getStatus())) {
                return Health.down()
                    .withDetail("failedLoader", loader.getLoaderCode())
                    .build();
            }
        }

        return Health.up().build();
    }
}
```

---

## Service Interaction Patterns

### Startup Sequence
1. **ETL Initializer** starts
   - Connects to PostgreSQL
   - Applies Flyway migrations (V1 → V6)
   - Seeds loader configurations
   - Seeds authentication data
   - Seeds message dictionary
   - Exits (one-time bootstrap)

2. **Data Generator** starts
   - Waits for database schema to exist
   - Begins generating test data every 5 minutes
   - Populates `signal_data` table

3. **Signal Loader** starts
   - Loads enabled loader configurations
   - Begins execution scheduling
   - Processes data from `signal_data` (or external sources)
   - Records execution history

### Runtime Interaction
```
┌─────────────────┐
│  Data Generator │
│  (Every 5 min)  │
└────────┬────────┘
         │
         ▼
   ┌─────────────────┐
   │  signal_data    │
   │  (test data)    │
   └────────┬────────┘
            │
            ▼
   ┌─────────────────┐
   │  Signal Loader  │
   │  (queries data) │
   └────────┬────────┘
            │
            ▼
   ┌─────────────────┐
   │ loader_execution│
   │ (execution log) │
   └─────────────────┘
```

---

## Configuration Management

### Environment Variables
All services use environment variables for configuration:

```yaml
# Shared configuration
DB_HOST: postgres-postgresql.monitoring-infra.svc.cluster.local
DB_PORT: 5432
DB_NAME: postgres
DB_USERNAME: <from-sealed-secret>
DB_PASSWORD: <from-sealed-secret>
DB_SCHEMA: monitor

# Service-specific
FLYWAY_ENABLED: true
DATA_GENERATION_ENABLED: true
LOADER_EXECUTION_ENABLED: true
```

---

## Future Enhancements

1. **ETL Initializer**:
   - Add migration rollback support
   - Implement schema versioning API
   - Add data validation checks

2. **Data Generator**:
   - Add ML-based pattern generation
   - Implement anomaly injection
   - Add real-time streaming mode

3. **Signal Loader**:
   - Add distributed execution (multiple instances)
   - Implement leader election
   - Add execution prioritization
   - Implement retry logic with exponential backoff

---

## Related Documentation

- [Database Schema Documentation](../../database/schema-documentation.md)
- [Loader Configuration Guide](../../guides/loader-configuration.md)
- [API Reference](../../api/loader-api-reference.md)

---

**Created By**: backend-team
**Status**: 📋 BACKLOG (Documentation in progress)
