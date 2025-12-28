---
id: "EPIC-005"
title: "Execution History & Activity Tracking"
status: "planned"
priority: "high"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "backend-team"
owner: "backend-team"
labels: ["backend", "database", "feature"]
estimated_points: 21
sprint: ""
target_release: "v1.2.0"
dependencies: []
---

# EPIC-005: Execution History & Activity Tracking

## Overview
Create execution history tracking system to record all loader executions with status, timing, and record counts.

## Business Value
Operations teams need visibility into loader execution history to troubleshoot failures, identify patterns, and verify successful data loads.

## Scope

### In Scope
- Create `execution_history` table in database
- Track: start time, end time, status, record count, errors
- API endpoints for fetching execution history
- Activity feed implementation (replaces placeholder)
- Execution history table in Loader Details Page
- Failed loader count calculation

### Out of Scope
- Execution log storage (separate concern)
- Performance metrics (EPIC-006)
- Alerting (future enhancement)

## Database Schema
```sql
CREATE TABLE loader.execution_history (
    id BIGSERIAL PRIMARY KEY,
    loader_id BIGINT NOT NULL,
    execution_start TIMESTAMP WITH TIME ZONE NOT NULL,
    execution_end TIMESTAMP WITH TIME ZONE,
    status VARCHAR(50) NOT NULL,  -- RUNNING, SUCCESS, FAILED
    record_count INTEGER DEFAULT 0,
    error_message TEXT,
    duration_seconds INTEGER,
    FOREIGN KEY (loader_id) REFERENCES loader.loader(id)
);
```

## User Stories
- [ ] US-016: System records execution start
- [ ] US-017: System records execution completion
- [ ] US-018: User views execution history in table
- [ ] US-019: Activity feed shows recent events
- [ ] US-020: Failed count calculated from history

## Success Criteria
- [ ] All executions tracked in database
- [ ] Execution history visible in UI
- [ ] Activity feed shows real events
- [ ] Failed loader count accurate

**Estimated Time**: 10-12 hours
**Priority**: High (unblocks many features)
**Status**: Planned for next sprint
