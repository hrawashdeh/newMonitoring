---
id: "EPIC-XXX"
title: "Epic Title Here"
status: "backlog"              # backlog | planned | in_progress | blocked | review | testing | done | cancelled
priority: "medium"             # critical | high | medium | low
created: "YYYY-MM-DD"
updated: "YYYY-MM-DD"
assignee: "unassigned"         # GitHub username or name
owner: "product-team"          # Team responsible
labels: ["feature", "backend"] # Tags for categorization
estimated_points: 21           # Story points (Fibonacci: 1,2,3,5,8,13,21,34)
sprint: ""                     # Sprint ID (e.g., "sprint-01")
target_release: "v1.2.0"       # Target version
dependencies: []               # List of blocking EPICs/issues
linear_id: ""                  # Linear project ID (auto-filled by sync)
jira_id: ""                    # Jira epic key (auto-filled by sync)
github_project_id: ""          # GitHub project item ID (auto-filled by sync)
---

# EPIC-XXX: Epic Title Here

## Overview

**Brief Description**: One-sentence summary of what this epic delivers.

**Business Value**: Why are we building this? What problem does it solve?

**Success Criteria**: How do we know this epic is complete and successful?

---

## Background

### Problem Statement
Describe the problem or opportunity this epic addresses.

**Current State**: What's the situation today?

**Desired State**: What do we want to achieve?

**Impact if Not Addressed**: What happens if we don't do this?

### User Personas
Who benefits from this epic?
- **Persona 1**: Operations team managing loaders
- **Persona 2**: Developers configuring ETL pipelines
- **Persona 3**: Executives monitoring system health

---

## Scope

### In Scope
What this epic WILL include:
- Feature A
- Feature B
- Feature C

### Out of Scope
What this epic will NOT include (but might be future work):
- Feature X (deferred to EPIC-YYY)
- Feature Y (technical debt, tracked separately)

---

## User Stories

List of user stories that make up this epic:

- [ ] [US-001](../user-stories/US-001-description.md) - User can view loader list
- [ ] [US-002](../user-stories/US-002-description.md) - User can pause/resume loader
- [ ] [US-003](../user-stories/US-003-description.md) - User can edit loader configuration
- [ ] [US-004](../user-stories/US-004-description.md) - User can delete loader with confirmation

**Total User Stories**: 4
**Completed**: 0
**In Progress**: 0

---

## Technical Design

### Architecture Changes
High-level technical approach:
- Backend: Add new endpoints to LoaderController
- Frontend: Create new React components
- Database: Add new columns to loader table (if needed)

### API Changes
New or modified endpoints:
- `PUT /api/v1/loaders/{id}` - Update loader configuration
- `DELETE /api/v1/loaders/{id}` - Delete loader

### Database Changes
```sql
-- Example migration (if needed)
ALTER TABLE loader.loader
ADD COLUMN display_order INTEGER DEFAULT 0;
```

### UI/UX Changes
- New page: Loader Configuration Editor
- New component: Confirmation modal for delete
- Updated component: Loader list table with inline actions

---

## Dependencies

### Blocked By
- [ ] EPIC-000 - Foundation work must be complete first

### Blocks
- [ ] EPIC-999 - This epic must be done before advanced features

### Related EPICs
- EPIC-ABC - Similar work in different area
- EPIC-XYZ - Complementary feature

---

## Risks & Mitigation

| Risk | Impact | Probability | Mitigation |
|------|--------|-------------|------------|
| Database migration fails in production | High | Low | Test migration on staging, create rollback script |
| UI performance degrades with large datasets | Medium | Medium | Implement pagination, add performance testing |
| Users find new UI confusing | Medium | High | Conduct user testing, provide inline help |

---

## Testing Strategy

### Unit Tests
- Test all new API endpoints
- Test new React components in isolation
- Test database migration scripts

### Integration Tests
- Test end-to-end flow: Create → Edit → Delete loader
- Test error handling and validation

### User Acceptance Testing
- [ ] Scenario 1: User creates new loader successfully
- [ ] Scenario 2: User edits existing loader
- [ ] Scenario 3: User deletes loader with confirmation

---

## Rollout Plan

### Phase 1: Alpha (Internal Testing)
- Deploy to dev environment
- Internal team testing
- Gather feedback

### Phase 2: Beta (Limited Release)
- Deploy to staging
- Select pilot users
- Monitor for issues

### Phase 3: General Availability
- Deploy to production
- Announce to all users
- Monitor metrics

---

## Success Metrics

### Key Performance Indicators (KPIs)
- User adoption: 80% of users use new feature within 1 month
- Error rate: < 0.1% of operations fail
- Performance: Page load < 2 seconds

### Monitoring
- Track API endpoint usage
- Monitor error logs
- User feedback survey

---

## Timeline

| Milestone | Date | Status |
|-----------|------|--------|
| Design Complete | YYYY-MM-DD | ⏳ Pending |
| Development Start | YYYY-MM-DD | ⏳ Pending |
| Code Review | YYYY-MM-DD | ⏳ Pending |
| QA Testing | YYYY-MM-DD | ⏳ Pending |
| Production Deploy | YYYY-MM-DD | ⏳ Pending |

**Total Estimated Time**: X weeks

---

## Open Questions

- [ ] Question 1: Should we support bulk operations?
- [ ] Question 2: What permissions are required for delete?
- [ ] Question 3: How do we handle loaders currently running when deleted?

---

## Notes

### 2025-12-27
- Initial epic creation
- Reviewed with team, consensus to proceed

### YYYY-MM-DD
- Design review completed
- Approved to start development

---

## References

- [Technical Design Doc](../../architecture/feature-design.md)
- [User Research](../../user-guides/user-research-findings.md)
- [API Specification](../../api-reference/loader-api.md)
- [Figma Designs](https://figma.com/...)

---

**Created By**: Your Name
**Last Updated**: YYYY-MM-DD
**Status**: Backlog
