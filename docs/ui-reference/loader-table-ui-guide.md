# Loader Database Table - UI Presentation Reference

## Purpose

This document describes the `loader.loader` database table structure with specific guidance on how each field should be **presented and consumed in the UI**. Use this as a reference when designing UI components, forms, and displays.

**Database Table**: `loader.loader`
**Schema**: `loader`
**Total Fields**: 18

---

## Quick Reference Table

| Field | Type | Nullable | UI Component | Display Priority | Notes |
|-------|------|----------|--------------|------------------|-------|
| id | bigint | NO | Hidden | N/A | Internal use only |
| loader_code | varchar(50) | NO | Text (bold/prominent) | HIGH | Primary identifier |
| loader_sql | text (encrypted) | YES | Code editor / Read-only | HIGH | Sensitive, show on details only |
| source_database_id | bigint | YES | Dropdown/Select | MEDIUM | FK reference |
| load_status | varchar(50) | YES | Badge/Pill | HIGH | Color-coded |
| last_load_timestamp | timestamptz | YES | Formatted date/time | MEDIUM | Data watermark |
| last_success_timestamp | timestamptz | YES | Relative time | HIGH | "5 mins ago" |
| failed_since | timestamptz | YES | Relative time + alert | HIGH | Show only if FAILED |
| min_interval_seconds | integer | YES | Time input / display | MEDIUM | Format as h/m/s |
| max_interval_seconds | integer | YES | Time input / display | MEDIUM | Format as h/m/s |
| max_query_period_seconds | integer | YES | Time input / display | MEDIUM | Format as d/h/s |
| max_parallel_executions | integer | YES | Number input / display | MEDIUM | Show as number |
| purge_strategy | varchar(50) | YES | Select / Badge | LOW | Enum with 3 values |
| consecutive_zero_record_runs | integer | YES | Number / Alert indicator | LOW | Alert if > 100 |
| source_timezone_offset_hours | integer | YES | Number / Timezone picker | LOW | Format as GMT±N |
| enabled | boolean | YES | Toggle switch / Badge | HIGH | Green/Red indicator |
| created_at | timestamptz | YES | Formatted date | LOW | Audit only |
| updated_at | timestamptz | YES | Relative time | LOW | "Modified 2h ago" |

---

## Field-by-Field UI Reference

### 1. id
**Type**: `bigint` (auto-increment)
**Nullable**: NO
**Example Values**: `1`, `2`, `3`, `125`

**UI Presentation**:
- ❌ **DO NOT DISPLAY** in user-facing UI
- Used only for internal API calls and database relations
- Never show in forms, tables, or details pages

**API Usage**: Include in API responses but hide from UI

---

### 2. loader_code
**Type**: `varchar(50)`
**Nullable**: NO (UNIQUE constraint)
**Example Values**: `"ALARMS_LOADER"`, `"SIGNALS_LOADER"`, `"EVENTS_LOADER_V2"`

**UI Presentation Options**:

**List View**:
```
┌─────────────────────────────────┐
│ ALARMS_LOADER                   │ ← Bold, larger font
│ SIGNALS_LOADER                  │
│ EVENTS_LOADER_V2                │
└─────────────────────────────────┘
```
- Bold text, prominent font size
- Use `font-medium` or `font-semibold`
- Make it clickable (primary navigation element)

**Details View**:
```
┌─────────────────────────────────────────┐
│  ALARMS_LOADER                          │ ← Large heading
│  ━━━━━━━━━━━━━━                         │
│                                         │
│  [Configuration details below...]       │
└─────────────────────────────────────────┘
```
- Use as page title/heading (`<h1>` or `<h2>`)
- Consider adding icon or status badge next to it

**Form Input** (Create/Edit):
```
Loader Code *
┌─────────────────────────────────┐
│ ALARMS_LOADER                   │
└─────────────────────────────────┘
Unique identifier (UPPER_SNAKE_CASE recommended)
```
- Text input field
- Validation: Required, unique, max 50 chars
- Helper text: "Use UPPER_SNAKE_CASE (e.g., ALARMS_LOADER)"
- Show error if duplicate

**Best Practice**: Use as breadcrumb, URL slug, and primary identifier throughout UI

---

### 3. loader_sql
**Type**: `text` (encrypted with AES-256-GCM)
**Nullable**: YES
**Example Value**:
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

**UI Presentation Options**:

**List View**:
- ❌ **DO NOT SHOW** in list/table view (too large, sensitive)
- Show indicator only: "SQL Query Configured ✓"

**Details View** - Read-Only Display:
```
┌────────────────────────────────────────────────┐
│ SQL Query                            [Copy]    │
│ ┌────────────────────────────────────────────┐│
│ │ SELECT                                     ││
│ │     alarm_id,                              ││
│ │     alarm_timestamp,                       ││
│ │     alarm_message,                         ││
│ │     severity                               ││
│ │ FROM alarms                                ││
│ │ WHERE alarm_timestamp >= :lastLoadTimestamp││
│ │   AND alarm_timestamp <= :currentTimestamp ││
│ │ ORDER BY alarm_timestamp ASC               ││
│ └────────────────────────────────────────────┘│
│                                                │
│ ⓘ This query is encrypted in the database     │
└────────────────────────────────────────────────┘
```
**Component**: Code block with syntax highlighting
**Features**:
- Monospace font (`font-mono`)
- Syntax highlighting for SQL
- Copy to clipboard button
- Background: Light gray or dark mode equivalent
- Scrollable if long
- Security note: "Encrypted in database"

**Form Input** (Create/Edit):
```
SQL Query *
┌────────────────────────────────────────────────┐
│ SELECT                                         │
│     signal_id,                                 │
│     signal_timestamp,                          │
│     signal_value                               │
│ FROM signals                                   │
│ WHERE signal_timestamp >= :lastLoadTimestamp   │
│   AND signal_timestamp <= :currentTimestamp    │
│ ORDER BY signal_timestamp ASC                  │
└────────────────────────────────────────────────┘

Required placeholders:
• :lastLoadTimestamp - Starting point for incremental load
• :currentTimestamp - Ending point for current run
```
**Component**: Code editor or large textarea
**Features**:
- SQL syntax highlighting
- Auto-indent
- Line numbers
- Validation:
  - Must include `:lastLoadTimestamp`
  - Must include `:currentTimestamp`
  - Must have valid SQL syntax (if possible)
- Helper text showing required placeholders

**Security**:
- Show lock icon 🔒 to indicate encryption
- Add tooltip: "This query is encrypted using AES-256-GCM before storage"

---

### 4. source_database_id
**Type**: `bigint` (Foreign Key → `loader.source_databases.id`)
**Nullable**: YES
**Example Values**: `1`, `2`, `5`

**UI Presentation Options**:

**List View**:
- ❌ **PROBABLY SKIP** (show in details only, or show database name if joined)
- Alternative: Show source database name if API provides it

**Details View**:
```
Source Database
┌─────────────────────────────────┐
│ PostgreSQL Production           │ ← Display name, not ID
│ (postgres-prod.example.com)     │
└─────────────────────────────────┘
```
**Component**: Read-only text showing database name
**Data**: Requires JOIN or nested API response to get database details

**Form Input** (Create/Edit):
```
Source Database *
┌─────────────────────────────────────────┐
│ PostgreSQL Production                  ▼│
└─────────────────────────────────────────┘
  ├─ PostgreSQL Production (postgres-prod.example.com)
  ├─ MySQL Analytics (mysql-analytics.example.com)
  ├─ Oracle Legacy (oracle-legacy.example.com)
  └─ MongoDB Events (mongodb-events.example.com)
```
**Component**: Dropdown/Select
**Options**: Fetch from `/api/v1/source-databases` endpoint
**Display**: Show `database_name` + `connection_url` or `host`
**Value**: Send `id` to backend

**Related Data** (from source_databases table):
- `database_name` - Display name
- `connection_url` - JDBC URL
- `driver_class_name` - DB type indicator
- `username` - (hidden, encrypted)
- `password` - (hidden, encrypted)

---

### 5. load_status
**Type**: `varchar(50)` (ENUM: IDLE, RUNNING, FAILED, PAUSED)
**Nullable**: YES (Default: 'IDLE')
**Example Values**: `"IDLE"`, `"RUNNING"`, `"FAILED"`, `"PAUSED"`

**UI Presentation Options**:

**List View** - Badge/Pill:
```
┌──────────────────────────────────────┐
│ Loader Code        Status            │
├──────────────────────────────────────┤
│ ALARMS_LOADER      ⚪ IDLE           │
│ SIGNALS_LOADER     🟢 RUNNING        │
│ EVENTS_LOADER      🔴 FAILED         │
│ METRICS_LOADER     ⏸️  PAUSED         │
└──────────────────────────────────────┘
```
**Component**: Badge with icon + text
**Colors**:
- `IDLE`: Gray/Neutral (`bg-gray-200 text-gray-700`)
- `RUNNING`: Blue/Green with animation (`bg-blue-500 text-white` + pulse animation)
- `FAILED`: Red (`bg-red-500 text-white`)
- `PAUSED`: Orange/Yellow (`bg-yellow-500 text-white`)

**Icons**:
- `IDLE`: ⚪ Circle or Clock icon
- `RUNNING`: 🟢 Loader/Spinner (animated) or Play icon
- `FAILED`: 🔴 X or Alert icon
- `PAUSED`: ⏸️ Pause icon

**Details View** - Prominent Badge:
```
┌────────────────────────────────────────┐
│ ALARMS_LOADER    🟢 RUNNING            │ ← Large badge
│                                        │
│ Last started: 2 minutes ago            │
│ Expected duration: ~5 minutes          │
└────────────────────────────────────────┘
```
**Additional Context**:
- `RUNNING`: Show progress indicator, start time, estimated completion
- `FAILED`: Show error message, failed_since duration, retry button
- `PAUSED`: Show "Resume" button
- `IDLE`: Show "Next run in: X minutes"

**Status Indicator** (Real-time):
```typescript
// Add auto-refresh for RUNNING status
useQuery({
  queryKey: ['loader', loaderCode],
  queryFn: () => loadersApi.getLoader(loaderCode),
  refetchInterval: (data) =>
    data?.load_status === 'RUNNING' ? 5000 : 30000, // 5s if running, 30s otherwise
})
```

**CSS Classes**:
```css
/* IDLE */
.status-idle {
  background-color: #e5e7eb;
  color: #374151;
}

/* RUNNING */
.status-running {
  background-color: #3b82f6;
  color: white;
  animation: pulse 2s cubic-bezier(0.4, 0, 0.6, 1) infinite;
}

/* FAILED */
.status-failed {
  background-color: #ef4444;
  color: white;
}

/* PAUSED */
.status-paused {
  background-color: #f59e0b;
  color: white;
}
```

---

### 6. last_load_timestamp
**Type**: `timestamptz` (timestamp with timezone, stored as UTC)
**Nullable**: YES (NULL if loader never ran)
**Example Values**: `"2025-12-27T10:05:30Z"`, `"2025-12-26T15:20:00Z"`, `null`

**UI Presentation Options**:

**List View**:
- ❌ **PROBABLY SKIP** (use last_success_timestamp instead)
- This is a data watermark, less meaningful to users than "when did it last run successfully"

**Details View**:
```
Data Watermark
┌─────────────────────────────────┐
│ 2025-12-27 10:05:30 UTC         │
└─────────────────────────────────┘

Next load will start from this timestamp
```
**Component**: Formatted timestamp
**Format Options**:
- Absolute: `"2025-12-27 10:05:30 UTC"`
- Relative: `"10 hours ago"`
- Both: `"2025-12-27 10:05:30 UTC (10 hours ago)"`

**Tooltip**: "This is the last timestamp of data successfully processed. Next execution will fetch data starting from this point."

**Special Cases**:
- `null`: Display "Never run" or "No data loaded yet"
- Very old (>7 days): Highlight in yellow/orange to indicate loader may be behind

**Technical Details Section**:
```
┌────────────────────────────────────────┐
│ 📊 Data Processing Status              │
│                                        │
│ Last Processed Timestamp:              │
│   2025-12-27 10:05:30 UTC              │
│                                        │
│ Time Behind Current:                   │
│   2 hours 15 minutes                   │ ← Calculate: NOW - last_load_timestamp
│                                        │
│ Next Load Will Fetch:                  │
│   FROM: 2025-12-27 10:05:30 UTC        │
│   TO:   2025-12-27 12:20:45 UTC        │
│   (2 hours 15 minutes of data)         │
└────────────────────────────────────────┘
```

---

### 7. last_success_timestamp
**Type**: `timestamptz` (timestamp with timezone, stored as UTC)
**Nullable**: YES (NULL if loader never succeeded)
**Example Values**: `"2025-12-27T12:15:45Z"`, `null`

**UI Presentation Options**:

**List View** - Relative Time:
```
┌──────────────────────────────────────────┐
│ Loader Code        Last Success          │
├──────────────────────────────────────────┤
│ ALARMS_LOADER      2 minutes ago         │
│ SIGNALS_LOADER     1 hour ago            │
│ EVENTS_LOADER      Never                 │ ← null value
│ METRICS_LOADER     3 days ago            │ ← Warning color
└──────────────────────────────────────────┘
```
**Component**: Relative time text
**Library**: Use `date-fns` or similar
**Format**: `formatDistanceToNow(timestamp, { addSuffix: true })`
**Examples**: `"5 seconds ago"`, `"2 minutes ago"`, `"3 hours ago"`, `"2 days ago"`

**Color Coding**:
- < 1 hour ago: Green/Success
- 1-24 hours ago: Gray/Neutral
- 1-7 days ago: Yellow/Warning
- > 7 days ago: Red/Danger
- `null`: Gray with "Never" text

**Details View** - Full Timestamp + Relative:
```
Last Successful Run
┌─────────────────────────────────┐
│ 🕐 2 minutes ago                │ ← Large, prominent
│    (2025-12-27 12:15:45 UTC)    │ ← Smaller, secondary
└─────────────────────────────────┘
```

**Tooltip**: Hover to see exact timestamp
```
Last Success: 2 minutes ago

Exact time: December 27, 2025 at 12:15:45 PM UTC
```

**Auto-Update**: Use `setInterval` to update relative time every 60 seconds without API call
```typescript
const [relativeTime, setRelativeTime] = useState('')

useEffect(() => {
  const updateTime = () => {
    if (lastSuccessTimestamp) {
      setRelativeTime(formatDistanceToNow(new Date(lastSuccessTimestamp), { addSuffix: true }))
    }
  }

  updateTime() // Initial
  const interval = setInterval(updateTime, 60000) // Every minute
  return () => clearInterval(interval)
}, [lastSuccessTimestamp])
```

---

### 8. failed_since
**Type**: `timestamptz` (timestamp with timezone, stored as UTC)
**Nullable**: YES (NULL if not currently failed)
**Example Values**: `"2025-12-27T08:30:00Z"`, `null`

**UI Presentation Options**:

**List View**:
- **SHOW ONLY** if `load_status = 'FAILED'`
- Display as part of status badge or in separate column

```
┌──────────────────────────────────────────────┐
│ Loader Code        Status                    │
├──────────────────────────────────────────────┤
│ ALARMS_LOADER      🔴 FAILED (4 hours ago)   │ ← Show duration
│ SIGNALS_LOADER     ⚪ IDLE                   │
└──────────────────────────────────────────────┘
```

**Details View** - Alert Box:
```
┌────────────────────────────────────────────┐
│ ⚠️  LOADER FAILED                           │
│                                            │
│ Failed since: 4 hours ago                  │
│ (2025-12-27 08:30:00 UTC)                  │
│                                            │
│ Auto-recovery: In 16 minutes               │ ← 20 min - duration
│                                            │
│ [View Error Details] [Reset to IDLE]      │
└────────────────────────────────────────────┘
```
**Component**: Alert/Warning box
**Color**: Red/Destructive variant
**Features**:
- Show duration: "Failed 4 hours ago"
- Show auto-recovery countdown: "Auto-recovery in 16 minutes" (if < 20 min)
- Show exact timestamp in tooltip
- Action buttons: "View Logs", "Reset to IDLE", "Retry Now"

**Auto-Recovery Countdown**:
```typescript
const getAutoRecoveryTime = (failedSince: string) => {
  const failedAt = new Date(failedSince)
  const now = new Date()
  const minutesSinceFailed = (now.getTime() - failedAt.getTime()) / 60000
  const recoveryIn = 20 - minutesSinceFailed

  if (recoveryIn > 0) {
    return `Auto-recovery in ${Math.ceil(recoveryIn)} minutes`
  } else {
    return `Auto-recovery should have occurred`
  }
}
```

**Special Case**: If `null`, don't show anything (loader not failed)

---

### 9. min_interval_seconds
**Type**: `integer` (seconds)
**Nullable**: YES
**Example Values**: `30`, `300`, `600`, `3600`

**UI Presentation Options**:

**List View** - Formatted Time:
```
┌─────────────────────────────────────┐
│ Loader Code        Min Interval     │
├─────────────────────────────────────┤
│ ALARMS_LOADER      5m               │ ← 300 seconds
│ SIGNALS_LOADER     1h               │ ← 3600 seconds
│ EVENTS_LOADER      30s              │ ← 30 seconds
└─────────────────────────────────────┘
```
**Format Function**:
```typescript
const formatInterval = (seconds: number): string => {
  const hours = Math.floor(seconds / 3600)
  const minutes = Math.floor(seconds / 60)

  if (hours > 0) return `${hours}h`
  if (minutes > 0) return `${minutes}m`
  return `${seconds}s`
}
```

**Details View** - With Explanation:
```
Minimum Interval (Cooldown)
┌─────────────────────────────────┐
│ 5 minutes (300 seconds)         │
└─────────────────────────────────┘

Wait at least 5 minutes between end of one run
and start of next run
```

**Form Input** (Create/Edit):
```
Minimum Interval *
┌────┬────┬────┐
│ 0  │ 5  │ 0  │ ← Hours, Minutes, Seconds
└────┴────┴────┘
 h     m     s

Cooldown period after each execution
Recommended: 5-10 minutes for most loaders
```
**Component**: Time picker with H/M/S inputs
**Validation**: Must be >= 0
**Helper text**: "Minimum wait time between END of one execution and START of next"

**Alternative Input** - Single field with unit selector:
```
Minimum Interval *
┌──────────┬─────────┐
│ 5        │ minutes ▼│
└──────────┴─────────┘
  └─ Options: seconds, minutes, hours
```

---

### 10. max_interval_seconds
**Type**: `integer` (seconds)
**Nullable**: YES
**Example Values**: `600`, `1800`, `3600`, `86400`

**UI Presentation Options**:

**List View** - Formatted Time:
```
┌──────────────────────────────────────────┐
│ Loader Code        Max Interval          │
├──────────────────────────────────────────┤
│ ALARMS_LOADER      1h                    │ ← 3600 seconds
│ SIGNALS_LOADER     2h                    │ ← 7200 seconds
│ EVENTS_LOADER      24h                   │ ← 86400 seconds
└──────────────────────────────────────────┘
```
**Format Function**: Same as min_interval_seconds

**Details View** - With Explanation:
```
Maximum Interval (Guaranteed Frequency)
┌─────────────────────────────────┐
│ 1 hour (3600 seconds)           │
└─────────────────────────────────┘

Loader will start at least once every 1 hour,
regardless of min_interval
```

**Form Input** (Create/Edit):
```
Maximum Interval *
┌────┬────┬────┐
│ 1  │ 0  │ 0  │ ← Hours, Minutes, Seconds
└────┴────┴────┘
 h     m     s

Guaranteed execution frequency
Recommended: 1 hour for real-time data,
            24 hours for batch data

⚠️ Must be >= Minimum Interval
```
**Component**: Time picker with H/M/S inputs
**Validation**:
- Must be >= 0
- Must be >= min_interval_seconds (cross-field validation)
**Helper text**: "Maximum time between START of consecutive executions"

---

### 11. max_query_period_seconds
**Type**: `integer` (seconds)
**Nullable**: YES
**Example Values**: `3600`, `7200`, `14400`, `86400`

**UI Presentation Options**:

**List View** - Formatted Time:
```
┌──────────────────────────────────────────┐
│ Loader Code        Query Period          │
├──────────────────────────────────────────┤
│ ALARMS_LOADER      2h                    │ ← 7200 seconds
│ SIGNALS_LOADER     1h                    │ ← 3600 seconds
│ EVENTS_LOADER      1d                    │ ← 86400 seconds
└──────────────────────────────────────────┘
```
**Format Function**:
```typescript
const formatQueryPeriod = (seconds: number): string => {
  const days = Math.floor(seconds / 86400)
  const hours = Math.floor(seconds / 3600)

  if (days > 0) return `${days}d`
  if (hours > 0) return `${hours}h`
  return `${seconds}s`
}
```

**Details View** - With Visual:
```
Maximum Query Period (Data Chunk Size)
┌─────────────────────────────────┐
│ 2 hours (7200 seconds)          │
└─────────────────────────────────┘

Each execution fetches maximum 2 hours of historical data

Example: If loader was down for 10 hours, recovery
         will happen in 5 separate runs (10h ÷ 2h)

┌──────────────────────────────────────┐
│ Run 1: [10h ago] → [8h ago]  (2h)   │
│ Run 2: [8h ago]  → [6h ago]  (2h)   │
│ Run 3: [6h ago]  → [4h ago]  (2h)   │
│ Run 4: [4h ago]  → [2h ago]  (2h)   │
│ Run 5: [2h ago]  → [now]     (2h)   │
└──────────────────────────────────────┘
```

**Form Input** (Create/Edit):
```
Maximum Query Period *
┌────┬────┬────┐
│ 0  │ 0  │ 2  │ ← Days, Hours, Minutes
└────┴────┴────┘
 d     h     m

Maximum time range of data per execution
Smaller = safer (prevents timeouts)
Larger = faster recovery from downtime

Recommended:
• Real-time data: 1-2 hours
• Hourly batch: 4-8 hours
• Daily batch: 2-7 days
```
**Component**: Time picker with D/H/M inputs
**Validation**: Must be > 0
**Helper text**: "Maximum historical data fetched in single execution"

---

### 12. max_parallel_executions
**Type**: `integer`
**Nullable**: YES
**Example Values**: `1`, `2`, `3`, `5`, `10`

**UI Presentation Options**:

**List View** - Plain Number:
```
┌──────────────────────────────────────────┐
│ Loader Code        Max Parallel          │
├──────────────────────────────────────────┤
│ ALARMS_LOADER      1                     │
│ SIGNALS_LOADER     3                     │
│ EVENTS_LOADER      5                     │
└──────────────────────────────────────────┘
```
**Component**: Plain number text

**Details View** - With Visual Indicator:
```
Maximum Parallel Executions
┌─────────────────────────────────┐
│ 3                               │
│ [▰ ▰ ▰ ▱ ▱ ▱ ▱ ▱ ▱ ▱]           │ ← Visual bar (3/10 slots)
└─────────────────────────────────┘

Up to 3 instances of this loader can run
simultaneously across all replicas

Current utilization: 2/3 (2 running)  ← If available
```
**Additional Info**:
- Show current running count if available: `"2/3 slots in use"`
- Color code: Green if < 70%, Yellow if 70-90%, Red if > 90%

**Form Input** (Create/Edit):
```
Maximum Parallel Executions *
┌─────────────────────┐
│ 1                  ▼│
└─────────────────────┘
  ├─ 1 (Sequential - safest)
  ├─ 2
  ├─ 3
  ├─ 5
  └─ 10 (High concurrency)

Controls how many instances can run simultaneously
across all application replicas

⚠️ Higher values may overload source database
✓ Use 1 for sequential processing (recommended)
```
**Component**: Select dropdown or number input
**Validation**: Must be >= 1
**Recommended Options**: 1, 2, 3, 5, 10
**Helper text**: "Number of concurrent executions allowed across all replicas"

---

### 13. purge_strategy
**Type**: `varchar(50)` (ENUM: FAIL_ON_DUPLICATE, PURGE_AND_RELOAD, SKIP_DUPLICATES)
**Nullable**: YES (Default: 'FAIL_ON_DUPLICATE')
**Example Values**: `"FAIL_ON_DUPLICATE"`, `"PURGE_AND_RELOAD"`, `"SKIP_DUPLICATES"`

**UI Presentation Options**:

**List View**:
- ❌ **PROBABLY SKIP** (show in details only, low priority)
- Alternative: Show as small badge if not default value

**Details View** - Badge + Explanation:
```
Duplicate Handling Strategy
┌─────────────────────────────────────────┐
│ 🛡️  FAIL_ON_DUPLICATE                   │ ← Badge
└─────────────────────────────────────────┘

Behavior: Abort execution if duplicate data detected
Use when: Data should never duplicate (events, logs)

Other strategies:
• PURGE_AND_RELOAD - Delete existing data in time range, then reload
• SKIP_DUPLICATES - Ignore duplicates, continue processing
```

**Form Input** (Create/Edit):
```
Duplicate Handling Strategy *
┌─────────────────────────────────────────────┐
│ ◉ FAIL_ON_DUPLICATE (Safest)               │
│   Abort if duplicate detected               │
│   ✓ Best for append-only data (logs)       │
│                                             │
│ ○ PURGE_AND_RELOAD                          │
│   Delete existing data, then reload         │
│   ✓ Best for dimension tables              │
│                                             │
│ ○ SKIP_DUPLICATES                           │
│   Ignore duplicates, continue               │
│   ✓ Best for idempotent event streams      │
└─────────────────────────────────────────────┘
```
**Component**: Radio button group with descriptions
**Visual**: Use icons/badges for each option
**Helper**: Expand each option to show when to use it

**Badge Colors**:
- `FAIL_ON_DUPLICATE`: Red/Destructive (strict)
- `PURGE_AND_RELOAD`: Blue/Primary (refresh)
- `SKIP_DUPLICATES`: Green/Success (permissive)

---

### 14. consecutive_zero_record_runs
**Type**: `integer`
**Nullable**: YES (Default: 0)
**Example Values**: `0`, `5`, `50`, `150`

**UI Presentation Options**:

**List View**:
- ❌ **PROBABLY SKIP** (show in details only, or show as alert icon if > 100)
- Alternative: Show warning icon ⚠️ if value > 100

```
┌──────────────────────────────────────────┐
│ Loader Code        Status                │
├──────────────────────────────────────────┤
│ ALARMS_LOADER      ⚪ IDLE               │
│ SIGNALS_LOADER     ⚠️  IDLE (No data)    │ ← 150 consecutive zeros
└──────────────────────────────────────────┘
```

**Details View** - Alert if High:
```
┌────────────────────────────────────────────┐
│ Data Health                                │
│                                            │
│ Consecutive Zero-Record Runs: 150          │ ← Red if > 100
│                                            │
│ ⚠️  WARNING: Source may be down            │
│ No new data received in last 150 runs      │
│ (~5 hours if running every 2 minutes)      │
│                                            │
│ [Check Source Database]                    │
└────────────────────────────────────────────┘
```
**Component**: Alert box (warning or error variant)
**Threshold**: Show warning if > 50, error if > 100
**Calculation**: Show estimated duration based on interval

**Normal State** (value < 50):
```
Data Health: ✓ Healthy
Last run returned data: 3 runs ago
```

**Chart** (Advanced):
```
Record Count (Last 50 Runs)
┌────────────────────────────────────┐
│ 100 ┤              ╭─╮             │
│  80 ┤          ╭───╯ ╰╮            │
│  60 ┤      ╭───╯      ╰─╮          │
│  40 ┤  ╭───╯            ╰─╮        │
│  20 ┤──╯                  ╰─╮      │
│   0 ┤                       ╰──────┤ ← 150 consecutive zeros!
└────────────────────────────────────┘
    Last 50 runs →
```

---

### 15. source_timezone_offset_hours
**Type**: `integer` (hours, can be negative)
**Nullable**: YES
**Example Values**: `-5` (EST), `0` (UTC), `8` (Singapore), `null`

**UI Presentation Options**:

**List View**:
- ❌ **SKIP** (show in details only, low priority)

**Details View** - Formatted Timezone:
```
Source Database Timezone
┌─────────────────────────────────┐
│ GMT-5 (EST)                     │ ← -5
│ GMT+0 (UTC)                     │ ← 0
│ GMT+8 (Singapore Time)          │ ← 8
│ Not configured                  │ ← null
└─────────────────────────────────┘

All timestamps from source database are normalized
to UTC using this offset before storage
```

**Format Function**:
```typescript
const formatTimezoneOffset = (offset: number | null): string => {
  if (offset === null) return 'Not configured (assumes UTC)'
  if (offset === 0) return 'GMT+0 (UTC)'
  const sign = offset > 0 ? '+' : ''
  return `GMT${sign}${offset}`
}
```

**Form Input** (Create/Edit):
```
Source Timezone Offset
┌─────────────────────────────────┐
│ GMT-5 (US Eastern)             ▼│
└─────────────────────────────────┘
  ├─ GMT-8 (US Pacific)
  ├─ GMT-5 (US Eastern)
  ├─ GMT+0 (UTC)
  ├─ GMT+1 (Central European)
  ├─ GMT+8 (Singapore / China)
  └─ GMT+9 (Japan / Korea)
  └─ Custom: [-12 to +14]
```
**Component**: Timezone picker dropdown
**Options**: Common timezones + custom input
**Validation**: Must be between -12 and +14

**Visual Explanation**:
```
Example: Source DB in EST (GMT-5)

Source Timestamp:      2025-12-27 10:00:00 (EST)
Stored in Database:    2025-12-27 15:00:00 (UTC)
                              ↑
                      (10:00 + 5 hours)
```

---

### 16. enabled
**Type**: `boolean`
**Nullable**: YES
**Example Values**: `true`, `false`

**UI Presentation Options**:

**List View** - Badge:
```
┌──────────────────────────────────────────┐
│ Loader Code        Status                │
├──────────────────────────────────────────┤
│ ALARMS_LOADER      🟢 ENABLED            │
│ SIGNALS_LOADER     🔴 DISABLED           │
└──────────────────────────────────────────┘
```
**Component**: Badge
**Colors**:
- `true` (ENABLED): Green/Success (`bg-green-500 text-white`)
- `false` (DISABLED): Red/Destructive (`bg-red-500 text-white`)

**Details View** - Toggle Switch:
```
┌────────────────────────────────────────┐
│ Loader Status                          │
│                                        │
│ Enabled      [  ON  |  OFF  ]         │ ← Toggle switch
│               ▰▰▰▰▰    ▱▱▱▱            │
│                                        │
│ ✓ Loader is running and will be       │
│   scheduled for execution              │
│                                        │
│ [Pause Loader]                         │
└────────────────────────────────────────┘
```
**Component**: Toggle switch or button
**Actions**:
- Click to toggle between enabled/disabled
- Show confirmation modal: "Are you sure you want to pause this loader?"
- Update via API: `PUT /api/v1/loaders/{loaderCode}` with `{ enabled: false }`

**Button Variant**:
```
┌────────────────────────────────────────┐
│ [▶️ Resume Loader]                      │ ← If disabled
│ [⏸️  Pause Loader]                      │ ← If enabled
└────────────────────────────────────────┘
```

**State Combination** (enabled + load_status):
| enabled | load_status | Display | Action Button |
|---------|-------------|---------|---------------|
| true | IDLE | 🟢 Active | Pause |
| true | RUNNING | 🔵 Running | Pause |
| true | FAILED | 🔴 Failed (Active) | Pause / Reset |
| false | any | ⏸️ Paused | Resume |

---

### 17. created_at
**Type**: `timestamptz` (timestamp with timezone, stored as UTC)
**Nullable**: YES (auto-set by JPA)
**Example Values**: `"2025-12-25T14:30:00Z"`

**UI Presentation Options**:

**List View**:
- ❌ **SKIP** (low priority, show in details only if needed)

**Details View** - Audit Info:
```
┌────────────────────────────────────────┐
│ Audit Information                      │
│                                        │
│ Created:  December 25, 2025 at 2:30 PM│
│           (2 days ago)                 │
│                                        │
│ Modified: December 27, 2025 at 9:15 AM│
│           (3 hours ago)                │
└────────────────────────────────────────┘
```
**Component**: Formatted date + relative time
**Format**: Full date with time + relative in parentheses
**Placement**: Bottom of details page, low priority

---

### 18. updated_at
**Type**: `timestamptz` (timestamp with timezone, stored as UTC)
**Nullable**: YES (auto-updated by JPA on modification)
**Example Values**: `"2025-12-27T09:15:30Z"`

**UI Presentation Options**:

**List View**:
- ❌ **SKIP** (low priority)
- Alternative: Show as tooltip on row hover

**Details View** - Relative Time:
```
┌────────────────────────────────────────┐
│ Last Modified: 3 hours ago             │ ← Prominent if recent
│ (2025-12-27 09:15:30 UTC)              │
└────────────────────────────────────────┘
```

**Header Badge** (if recently modified):
```
ALARMS_LOADER  [Recently Modified: 5m ago]
```
**Show**: Only if modified within last hour
**Purpose**: Alert users that configuration may have changed

---

## Recommended UI Layouts

### List Page (Table View)

**Essential Columns** (HIGH Priority):
1. `loader_code` - Bold, clickable
2. `enabled` - Badge (ENABLED/DISABLED)
3. `load_status` - Badge (IDLE/RUNNING/FAILED/PAUSED)
4. `last_success_timestamp` - Relative time ("2 mins ago")
5. `max_interval_seconds` - Formatted (1h, 2h, etc.)
6. Actions - Quick buttons (Pause/Resume, Details)

**Optional Columns** (MEDIUM Priority):
7. `min_interval_seconds` - Formatted
8. `max_parallel_executions` - Number
9. `max_query_period_seconds` - Formatted

**Example Table**:
```
┌────────────────┬─────────┬─────────┬───────────────┬──────────────┬─────────┐
│ Loader Code    │ Enabled │ Status  │ Last Success  │ Max Interval │ Actions │
├────────────────┼─────────┼─────────┼───────────────┼──────────────┼─────────┤
│ ALARMS_LOADER  │ 🟢 ON   │ ⚪ IDLE │ 2 mins ago    │ 1h           │ ⏸️ ℹ️   │
│ SIGNALS_LOADER │ 🟢 ON   │ 🔵 RUN  │ Running...    │ 30m          │ ⏸️ ℹ️   │
│ EVENTS_LOADER  │ 🔴 OFF  │ ⏸️ PAUSE│ 3 days ago    │ 24h          │ ▶️ ℹ️   │
│ METRICS_LOADER │ 🟢 ON   │ 🔴 FAIL │ 4 hours ago   │ 2h           │ ⏸️ ℹ️   │
└────────────────┴─────────┴─────────┴───────────────┴──────────────┴─────────┘
```

### Details Page Layout

```
┌──────────────────────────────────────────────────────────┐
│  ← Back to Loaders                    [⏸️ Pause]  [Edit] │
├──────────────────────────────────────────────────────────┤
│                                                          │
│  ALARMS_LOADER              🟢 ENABLED   ⚪ IDLE         │
│  Last run: 2 minutes ago                                 │
│                                                          │
├──────────────────────────────────────────────────────────┤
│  Configuration                                           │
│  ┌────────────────────┬────────────────────┐            │
│  │ Min Interval       │ Max Interval       │            │
│  │ 5m                 │ 1h                 │            │
│  ├────────────────────┼────────────────────┤            │
│  │ Query Period       │ Max Parallel       │            │
│  │ 2h                 │ 1                  │            │
│  └────────────────────┴────────────────────┘            │
│                                                          │
│  Source Database                                         │
│  PostgreSQL Production (postgres-prod.example.com)       │
│                                                          │
│  Timezone Offset                                         │
│  GMT-5 (US Eastern)                                      │
│                                                          │
│  Duplicate Strategy                                      │
│  🛡️ FAIL_ON_DUPLICATE                                    │
│                                                          │
├──────────────────────────────────────────────────────────┤
│  SQL Query                                 [Copy]        │
│  ┌────────────────────────────────────────────────────┐ │
│  │ SELECT                                             │ │
│  │     alarm_id,                                      │ │
│  │     alarm_timestamp,                               │ │
│  │     alarm_message                                  │ │
│  │ FROM alarms                                        │ │
│  │ WHERE alarm_timestamp >= :lastLoadTimestamp        │ │
│  │   AND alarm_timestamp <= :currentTimestamp         │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
├──────────────────────────────────────────────────────────┤
│  Runtime Status                                          │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Data Watermark: 2025-12-27 10:05:30 UTC           │ │
│  │ Last Success:   2 minutes ago                      │ │
│  │ Zero-Record Runs: 3                                │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
├──────────────────────────────────────────────────────────┤
│  Execution History                                       │
│  [Timeline chart or table - to be implemented]           │
│                                                          │
├──────────────────────────────────────────────────────────┤
│  Audit                                                   │
│  Created: December 25, 2025 (2 days ago)                 │
│  Modified: December 27, 2025 (3 hours ago)               │
└──────────────────────────────────────────────────────────┘
```

### Create/Edit Form Layout

```
┌──────────────────────────────────────────────────────────┐
│  Create New Loader                                       │
├──────────────────────────────────────────────────────────┤
│  Basic Information                                       │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Loader Code *                                      │ │
│  │ ┌────────────────────────────────────────────────┐ │ │
│  │ │ ALARMS_LOADER                                  │ │ │
│  │ └────────────────────────────────────────────────┘ │ │
│  │ Use UPPER_SNAKE_CASE (e.g., ALARMS_LOADER)        │ │
│  │                                                    │ │
│  │ Source Database *                                  │ │
│  │ ┌────────────────────────────────────────────────┐ │ │
│  │ │ PostgreSQL Production                         ▼│ │ │
│  │ └────────────────────────────────────────────────┘ │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  Scheduling Configuration                                │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Min Interval *                                     │ │
│  │ ┌────┬────┬────┐                                  │ │
│  │ │ 0  │ 5  │ 0  │  H  M  S                         │ │
│  │ └────┴────┴────┘                                  │ │
│  │                                                    │ │
│  │ Max Interval *                                     │ │
│  │ ┌────┬────┬────┐                                  │ │
│  │ │ 1  │ 0  │ 0  │  H  M  S                         │ │
│  │ └────┴────┴────┘                                  │ │
│  │                                                    │ │
│  │ Query Period *                                     │ │
│  │ ┌────┬────┬────┐                                  │ │
│  │ │ 0  │ 2  │ 0  │  D  H  M                         │ │
│  │ └────┴────┴────┘                                  │ │
│  │                                                    │ │
│  │ Max Parallel Executions *                          │ │
│  │ ┌────────────────────────────────────────────────┐ │ │
│  │ │ 1 (Sequential - recommended)                  ▼│ │ │
│  │ └────────────────────────────────────────────────┘ │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  SQL Query *                                             │
│  ┌────────────────────────────────────────────────────┐ │
│  │ SELECT                                             │ │
│  │     alarm_id,                                      │ │
│  │     alarm_timestamp,                               │ │
│  │     alarm_message                                  │ │
│  │ FROM alarms                                        │ │
│  │ WHERE alarm_timestamp >= :lastLoadTimestamp        │ │
│  │   AND alarm_timestamp <= :currentTimestamp         │ │
│  │ ORDER BY alarm_timestamp ASC                       │ │
│  └────────────────────────────────────────────────────┘ │
│  Required: :lastLoadTimestamp and :currentTimestamp     │
│                                                          │
│  Advanced Options                                        │
│  ┌────────────────────────────────────────────────────┐ │
│  │ Timezone Offset                                    │ │
│  │ ┌────────────────────────────────────────────────┐ │ │
│  │ │ GMT-5 (US Eastern)                            ▼│ │ │
│  │ └────────────────────────────────────────────────┘ │ │
│  │                                                    │ │
│  │ Duplicate Handling *                               │ │
│  │ ◉ FAIL_ON_DUPLICATE (Safest)                      │ │
│  │ ○ PURGE_AND_RELOAD                                 │ │
│  │ ○ SKIP_DUPLICATES                                  │ │
│  │                                                    │ │
│  │ Enabled                                            │ │
│  │ [  ON  |  OFF  ]                                   │ │
│  └────────────────────────────────────────────────────┘ │
│                                                          │
│  [Cancel]                                     [Create]   │
└──────────────────────────────────────────────────────────┘
```

---

## API Response Format Reference

### GET /api/v1/loaders (List All)
```json
[
  {
    "id": 1,
    "loaderCode": "ALARMS_LOADER",
    "loaderSql": "SELECT ...",  // Encrypted, decrypted in response
    "sourceDatabaseId": 1,
    "loadStatus": "IDLE",
    "lastLoadTimestamp": "2025-12-27T10:05:30Z",
    "lastSuccessTimestamp": "2025-12-27T12:15:45Z",
    "failedSince": null,
    "minIntervalSeconds": 300,
    "maxIntervalSeconds": 3600,
    "maxQueryPeriodSeconds": 7200,
    "maxParallelExecutions": 1,
    "purgeStrategy": "FAIL_ON_DUPLICATE",
    "consecutiveZeroRecordRuns": 0,
    "sourceTimezoneOffsetHours": -5,
    "enabled": true,
    "createdAt": "2025-12-25T14:30:00Z",
    "updatedAt": "2025-12-27T09:15:30Z"
  }
]
```

### GET /api/v1/loaders/{loaderCode} (Single)
```json
{
  "id": 1,
  "loaderCode": "ALARMS_LOADER",
  "loaderSql": "SELECT alarm_id, alarm_timestamp, alarm_message FROM alarms WHERE alarm_timestamp >= :lastLoadTimestamp AND alarm_timestamp <= :currentTimestamp ORDER BY alarm_timestamp ASC",
  "sourceDatabase": {  // Nested object if backend provides it
    "id": 1,
    "databaseName": "PostgreSQL Production",
    "connectionUrl": "jdbc:postgresql://postgres-prod.example.com:5432/monitoring",
    "driverClassName": "org.postgresql.Driver"
  },
  "loadStatus": "IDLE",
  "lastLoadTimestamp": "2025-12-27T10:05:30Z",
  "lastSuccessTimestamp": "2025-12-27T12:15:45Z",
  "failedSince": null,
  "minIntervalSeconds": 300,
  "maxIntervalSeconds": 3600,
  "maxQueryPeriodSeconds": 7200,
  "maxParallelExecutions": 1,
  "purgeStrategy": "FAIL_ON_DUPLICATE",
  "consecutiveZeroRecordRuns": 0,
  "sourceTimezoneOffsetHours": -5,
  "enabled": true,
  "createdAt": "2025-12-25T14:30:00Z",
  "updatedAt": "2025-12-27T09:15:30Z"
}
```

---

## Related Documentation

- **Loader Entity (Database Schema)**: `LOADER_TABLE_USER_GUIDE.md`
- **Current List Page UI**: `LOADER_LIST_PAGE_USER_GUIDE.md`
- **Loader Details Page**: `frontend/src/pages/LoaderDetailsPage.tsx`
- **Known Issues & Next Features**: `KNOWN_ISSUES.md`

---

**Document Version**: 1.0
**Last Updated**: 2025-12-27
**Purpose**: UI Design Reference for Loader Database Table
