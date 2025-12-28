---
id: "EPIC-007"
title: "Full CRUD Operations for Loaders"
status: "planned"
priority: "critical"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "fullstack-team"
owner: "product-team"
labels: ["frontend", "backend", "feature"]
estimated_points: 21
sprint: ""
target_release: "v1.2.0"
dependencies: ["EPIC-004"]
---

# EPIC-007: Full CRUD Operations for Loaders

## Overview
Implement complete Create, Read, Update, Delete operations for loaders through the UI.

## Business Value
Administrators need ability to manage loaders without direct database access or API calls.

## Scope

### In Scope
- Create new loader (form modal)
- Edit existing loader (form modal)
- Delete loader (confirmation dialog)
- Form validation (intervals, SQL syntax)
- Loader form component with all fields
- Source database dropdown

### Out of Scope
- Bulk operations (future enhancement)
- Loader templates (future enhancement)
- Import/export (future enhancement)

## User Stories
- [ ] US-026: Create new loader via form
- [ ] US-027: Edit loader configuration
- [ ] US-028: Delete loader with confirmation
- [ ] US-029: Validate form inputs
- [ ] US-030: Select source database from dropdown

## Form Fields
- Loader Code (required, unique, alphanumeric)
- Loader SQL (required, textarea, syntax validation)
- Source Database (dropdown, required)
- Min Interval Seconds (number, > 0)
- Max Interval Seconds (number, >= min)
- Max Query Period Seconds (number, > 0)
- Max Parallel Executions (number, >= 1)
- Enabled (checkbox)

## Success Criteria
- [ ] Create loader successfully
- [ ] Edit loader updates correctly
- [ ] Delete removes loader permanently
- [ ] Validation prevents invalid inputs
- [ ] SQL syntax highlighting works

**Estimated Time**: 6-8 hours
**Priority**: Critical
**Status**: Planned
