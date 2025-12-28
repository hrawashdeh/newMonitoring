---
id: "US-052"
title: "Add Aggregation Period Column to Loader List"
epic: "EPIC-010"
status: "in_progress"
priority: "high"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "fullstack-team"
reviewer: ""
labels: ["frontend", "backend", "ui", "table", "aggregation"]
estimated_points: 5
actual_hours: 0
sprint: "sprint-02"
dependencies: []
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-052: Add Aggregation Period Column to Loader List

## User Story

**As an** operations engineer,
**I want** to see the aggregation period for each loader in the list,
**So that** I understand the time granularity at which data is being aggregated from the source.

---

## Acceptance Criteria

- [ ] Given I am on the Loaders List page, when I view the table, then I see an "Aggregation Period" column
- [ ] Given a loader has aggregation period of 60 seconds, when I view the column, then it displays "1m"
- [ ] Given a loader has aggregation period of 300 seconds, when I view the column, then it displays "5m"
- [ ] Given a loader has aggregation period of 3600 seconds, when I view the column, then it displays "1h"
- [ ] Given a loader has no aggregation period set, when I view the column, then it displays "-" (no default)
- [ ] Given I hover over the value, when I pause, then I see tooltip explaining "Data is aggregated in X minute intervals based on the first column (load_time_stamp)"

---

## Business Value

**Problem**: Operations engineers need to know:
- How granular the collected data is (1 minute vs 5 minutes vs 1 hour)
- Whether loaders are aggregating data or collecting raw records
- Time resolution for downstream analytics and reporting

**Value**:
- Better understanding of data quality and resolution
- Helps troubleshoot missing data issues (aggregation vs collection frequency)
- Informs query performance expectations (larger aggregation = fewer records)

---

## Technical Background

### What is Aggregation Period?

**Aggregation Period** is the time window over which source data is grouped/summarized before being stored.

**Example SQL Query** with 1-minute aggregation:
```sql
SELECT
    DATE_TRUNC('minute', load_time_stamp) AS time_bucket,  -- Aggregate by minute
    COUNT(*) AS record_count,
    AVG(signal_value) AS avg_value,
    MAX(signal_value) AS max_value,
    MIN(signal_value) AS min_value
FROM signal_source
WHERE load_time_stamp > :lastExecutionTime
GROUP BY time_bucket
ORDER BY time_bucket
```

**Key Points**:
- First column in SELECT (e.g., `load_time_stamp`) determines aggregation granularity
- `DATE_TRUNC('minute', ...)` → 1-minute aggregation
- `DATE_TRUNC('hour', ...)` → 1-hour aggregation
- No `DATE_TRUNC` → Raw data (no aggregation)

**Current State**:
- All loaders currently use **1-minute aggregation** (60 seconds)
- This is NOT a default value (must be explicitly configured)
- Aggregation period is derived from the SQL query first column

---

## Technical Implementation

### Frontend

**Type Definition** (`/frontend/src/types/loader.ts`):
```typescript
export interface Loader {
  // ... existing fields
  aggregationPeriodSeconds?: number; // Time window for data aggregation (60 = 1 minute)
}
```

**Column Definition** (`/frontend/src/pages/LoadersListPage.tsx`):
```typescript
{
  accessorKey: 'aggregationPeriodSeconds',
  header: 'Aggregation Period',
  cell: ({ row }) => {
    const seconds = row.getValue('aggregationPeriodSeconds') as number | undefined;

    if (!seconds) {
      return <span className="text-muted-foreground">-</span>;
    }

    const formatted = formatSeconds(seconds);
    return (
      <span className="font-medium" title={`Data aggregated in ${formatted} intervals`}>
        {formatted}
      </span>
    );
  },
}
```

---

### Backend

**Database Schema**:
```sql
-- Add aggregation_period_seconds column to loader_config
ALTER TABLE monitor.loader_config
ADD COLUMN aggregation_period_seconds INTEGER;

COMMENT ON COLUMN monitor.loader_config.aggregation_period_seconds IS
'Time window (in seconds) for data aggregation. Extracted from SQL query first column (DATE_TRUNC). NULL if no aggregation.';
```

**Migration**: `V8__add_aggregation_period.sql`

**Seed Data Update** (`/services/testData/etl-data-v1.yaml`):
```yaml
loaders:
  - loaderCode: "SIGNAL_LOADER_001"
    enabled: true
    aggregationPeriodSeconds: 60    # 1 minute aggregation
    queryText: |
      SELECT
        DATE_TRUNC('minute', load_time_stamp) AS time_bucket,
        COUNT(*) AS count,
        AVG(value) AS avg_value
      FROM signal_source
      WHERE load_time_stamp > :lastExecutionTime
      GROUP BY time_bucket

  - loaderCode: "SIGNAL_LOADER_002"
    enabled: true
    aggregationPeriodSeconds: 300   # 5 minute aggregation
    queryText: |
      SELECT
        DATE_TRUNC('minute', load_time_stamp) AS time_bucket,
        COUNT(*) AS count
      FROM signal_source
      WHERE load_time_stamp > :lastExecutionTime
      GROUP BY time_bucket
```

**Java Entity Update**:
```java
@Entity
@Table(name = "loader_config", schema = "monitor")
public class LoaderConfig {
    // ... existing fields

    @Column(name = "aggregation_period_seconds")
    private Integer aggregationPeriodSeconds;

    // Getters and setters
}
```

**LoaderResponse DTO Update**:
```java
@Data
public class LoaderResponse {
    // ... existing fields
    private Integer aggregationPeriodSeconds;
}
```

---

## How to Determine Aggregation Period

### Automatic Detection (Future Enhancement)

Parse the SQL query `queryText` to extract aggregation period:

```java
public class QueryAnalyzer {
    public Integer detectAggregationPeriod(String sqlQuery) {
        // Pattern to match DATE_TRUNC('unit', column)
        Pattern pattern = Pattern.compile("DATE_TRUNC\\('(\\w+)',");
        Matcher matcher = pattern.matcher(sqlQuery);

        if (matcher.find()) {
            String unit = matcher.group(1);

            switch (unit.toLowerCase()) {
                case "second": return 1;
                case "minute": return 60;
                case "hour": return 3600;
                case "day": return 86400;
                case "week": return 604800;
                default: return null;
            }
        }

        return null; // No aggregation detected
    }
}
```

### Manual Configuration (Current Approach)

Operations team manually sets `aggregationPeriodSeconds` when creating/editing loaders.

---

## UI/UX Design

### Column Display

| Aggregation Period (seconds) | Display |
|------------------------------|---------|
| 60 | 1m |
| 300 | 5m |
| 600 | 10m |
| 3600 | 1h |
| 7200 | 2h |
| 86400 | 1d |
| null/undefined | - |

### Tooltip Text
"Data is aggregated in [X] intervals based on the first column timestamp"

---

## Definition of Done

- [ ] `aggregationPeriodSeconds` added to `Loader` type (frontend)
- [ ] Aggregation Period column added to table
- [ ] Format helper function implemented (seconds → 1m, 5m, 1h)
- [ ] Tooltip added explaining aggregation
- [ ] Database migration V8 created
- [ ] `aggregation_period_seconds` column added to `loader_config` table
- [ ] Seed data updated with aggregation periods
- [ ] `LoaderConfig` entity updated (backend)
- [ ] `LoaderResponse` DTO updated (backend)
- [ ] Built and deployed to cluster
- [ ] Manual testing completed

---

## Backend Tasks

- [ ] **TASK-026**: Create V8 migration adding `aggregation_period_seconds` column
- [ ] **TASK-027**: Update `LoaderConfig` entity with new field
- [ ] **TASK-028**: Update `LoaderResponse` DTO with new field
- [ ] **TASK-029**: Update seed data (`etl-data-v1.yaml`) with aggregation periods
- [ ] **TASK-030**: Redeploy ETL Initializer to apply migration
- [ ] **TASK-031**: (Future) Implement automatic aggregation period detection from SQL

---

## Related Documentation

- SQL Query Analysis: How `DATE_TRUNC` determines aggregation granularity
- Aggregation Best Practices: When to use 1m vs 5m vs 1h
- Performance Impact: Larger aggregation = fewer records = faster queries

---

**Status**: 🚧 IN PROGRESS
**Frontend**: Ready to implement
**Backend**: Migration needed
