---
id: "BUG-XXX"
title: "Bug Title - Short Description"
status: "reported"              # reported | confirmed | in_progress | fixed | verified | closed | wont_fix
severity: "medium"              # critical | high | medium | low
priority: "medium"              # critical | high | medium | low
created: "YYYY-MM-DD"
updated: "YYYY-MM-DD"
reported_by: "user.name"
assignee: "unassigned"
verified_by: ""                 # QA tester who verified fix
labels: ["bug", "backend"]
affected_version: "v1.0.0"      # Version where bug was found
fixed_version: ""               # Version where bug was fixed
regression: false               # Is this a regression from previous version?
sprint: ""                      # Sprint ID (if planned for sprint)
linear_id: ""                   # Linear issue ID (auto-filled)
jira_id: ""                     # Jira bug key (auto-filled)
github_issue: ""                # GitHub issue number (auto-filled)
---

# BUG-XXX: Bug Title - Short Description

## Summary

**Brief Description**: One-sentence summary of the bug.

**Impact**: How does this affect users?

**Frequency**: How often does this occur?
- [ ] Always (100%)
- [ ] Frequently (>50%)
- [ ] Sometimes (10-50%)
- [ ] Rarely (<10%)

---

## Severity & Priority

### Severity
**Current Severity**: Medium

**Severity Definitions**:
- **Critical**: System down, data loss, security breach
- **High**: Major feature broken, no workaround
- **Medium**: Feature partially broken, workaround exists
- **Low**: Minor issue, cosmetic, typo

### Priority
**Current Priority**: Medium

**Priority Definitions**:
- **Critical**: Drop everything, fix now (P0)
- **High**: Fix this sprint (P1)
- **Medium**: Fix next sprint (P2)
- **Low**: Fix when time permits (P3)

---

## Environment

**Affected Environments**:
- [x] Production
- [ ] Staging
- [ ] Development
- [ ] Local

**Version**: v1.0.0

**Browser/Client** (if frontend bug):
- Browser: Chrome 120
- OS: macOS 14.1
- Screen resolution: 1920x1080

**Server/Service** (if backend bug):
- Service: loader-service
- Pod: loader-service-7d9f8c6b5d-x2k9m
- Kubernetes cluster: monitoring-app
- Database: PostgreSQL 15

---

## Steps to Reproduce

1. Navigate to `/loaders` page
2. Click on "ALARMS_LOADER" row
3. Click "Pause" button
4. Observe error message

**Expected Result**: Loader should pause successfully

**Actual Result**: Error message "Request failed with status code 405"

---

## Screenshots/Logs

### Screenshot
![Error Screenshot](./screenshots/BUG-XXX-error.png)

### Console Logs
```
POST /api/v1/loaders/ALARMS_LOADER HTTP/1.1" 405 143
Error: Request failed with status code 405
  at createError (createError.js:16)
  at settle (settle.js:17)
  at XMLHttpRequest.handleLoad (xhr.js:62)
```

### Server Logs
```
2025-12-27 10:30:45 ERROR [loader-service] Method Not Allowed
org.springframework.web.HttpRequestMethodNotSupportedException: Request method 'POST' not supported
  at org.springframework.web.servlet.mvc.method.RequestMappingInfoHandlerMapping.handleNoMatch
```

### Stack Trace
```
java.lang.NullPointerException: Cannot invoke "String.trim()" because "input" is null
  at com.tiqmo.monitoring.loader.service.LoaderService.processQuery(LoaderService.java:123)
  at com.tiqmo.monitoring.loader.api.LoaderController.updateLoader(LoaderController.java:89)
```

---

## Root Cause Analysis

### Investigation
- [ ] Reviewed server logs
- [ ] Checked database state
- [ ] Reproduced in local environment
- [ ] Identified code location

### Root Cause
The API endpoint expects PUT method but frontend is sending POST.

**Affected Code**:
- File: `frontend/src/api/loaders.ts`
- Line: 45
- Function: `pauseLoader()`

**Code Snippet**:
```typescript
// WRONG: Using POST instead of PUT
export const pauseLoader = async (loaderCode: string) => {
  return api.post(`/api/v1/loaders/${loaderCode}`, { enabled: false })
}

// CORRECT: Should use PUT
export const pauseLoader = async (loaderCode: string) => {
  return api.put(`/api/v1/loaders/${loaderCode}`, { enabled: false })
}
```

---

## Fix Implementation

### Code Changes
**File**: `frontend/src/api/loaders.ts`
**Line**: 45
**Change**: Replace `post` with `put`

```diff
export const pauseLoader = async (loaderCode: string) => {
-  return api.post(`/api/v1/loaders/${loaderCode}`, { enabled: false })
+  return api.put(`/api/v1/loaders/${loaderCode}`, { enabled: false })
}
```

### Testing
- [ ] Unit test added for pauseLoader function
- [ ] Integration test covers pause flow
- [ ] Manual testing on all browsers

---

## Workaround

**Temporary Workaround** (until fix is deployed):
1. Use Loader Details page instead of list page
2. Pause button on details page works correctly
3. Or use direct API call: `curl -X PUT /api/v1/loaders/{code} -d '{"enabled": false}'`

---

## Related Issues

### Duplicate Bugs
- BUG-000 - Similar issue with different endpoint

### Related User Stories
- US-123 - Pause loader from list page (where bug was introduced)

### Caused By
- PR #456 - Added pause functionality but used wrong HTTP method

---

## Testing Verification

### Test Cases
- [ ] Test pause loader with valid loader code
- [ ] Test pause loader with invalid loader code
- [ ] Test pause already paused loader
- [ ] Test pause while loader is running
- [ ] Test resume paused loader

### Regression Testing
- [ ] All existing loader operations still work
- [ ] Other API endpoints not affected
- [ ] No performance degradation

---

## Timeline

| Event | Date | Person |
|-------|------|--------|
| Bug Reported | 2025-12-27 10:30 | john.doe |
| Confirmed | 2025-12-27 11:00 | jane.smith |
| Investigation Started | 2025-12-27 11:15 | dev.team |
| Root Cause Identified | 2025-12-27 11:45 | dev.team |
| Fix Implemented | 2025-12-27 12:00 | developer.a |
| Code Review | 2025-12-27 13:00 | developer.b |
| QA Verified | 2025-12-27 14:00 | qa.tester |
| Deployed to Staging | 2025-12-27 14:30 | devops |
| Deployed to Production | 2025-12-27 16:00 | devops |
| Closed | 2025-12-27 16:30 | qa.tester |

---

## Prevention

### Why Did This Happen?
- Insufficient code review
- No integration tests for new feature
- API contract not documented

### How to Prevent in Future?
- [ ] Add ESLint rule to catch incorrect HTTP methods
- [ ] Require integration tests for all API changes
- [ ] Document API contracts in OpenAPI spec
- [ ] Add pre-commit hook to validate API calls match contracts

---

## Impact Assessment

### Users Affected
- **Count**: 5 users reported
- **Percentage**: ~20% of active users
- **Time Affected**: 6 hours (from deploy to fix)

### Business Impact
- **Revenue Impact**: None
- **SLA Impact**: No SLA breach
- **Reputation Impact**: Low (internal tool)

### Data Impact
- **Data Loss**: None
- **Data Corruption**: None
- **Data Integrity**: Not affected

---

## Post-Incident Review

### What Went Well
- Bug was detected quickly (within 1 hour of deploy)
- Root cause identified rapidly
- Fix deployed same day

### What Could Be Improved
- Should have caught this in code review
- Integration tests should have caught this before production
- Need better API contract validation

### Action Items
- [ ] Add integration test for pause functionality
- [ ] Update code review checklist to verify HTTP methods
- [ ] Set up OpenAPI schema validation in CI/CD

---

## Notes

### 2025-12-27 10:30
- Bug reported by user john.doe
- Error: 405 Method Not Allowed when pausing loader

### 2025-12-27 11:45
- Root cause identified: Using POST instead of PUT
- Simple fix, will deploy today

### 2025-12-27 16:30
- Fix verified in production
- Closing bug

---

## References

- [Incident Report](../../runbooks/incidents/2025-12-27-loader-pause-405.md)
- [Fix PR #457](https://github.com/org/repo/pull/457)
- [API Documentation](../../api-reference/loader-api.md)
- [Post-Incident Review Meeting Notes](./meeting-notes-2025-12-27.md)

---

**Reported By**: john.doe
**Assigned To**: developer.a
**Last Updated**: YYYY-MM-DD
**Status**: Closed
