---
id: "US-001"
title: "User Login with Credentials"
epic: "EPIC-001"
status: "done"
priority: "critical"
created: "2025-12-24"
updated: "2025-12-27"
assignee: "backend-team"
reviewer: "security-team"
labels: ["backend", "security", "authentication"]
estimated_points: 5
actual_hours: 4
sprint: "sprint-01"
dependencies: []
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-001: User Login with Credentials

## User Story

**As a** system administrator,
**I want** to log in with my username and password,
**So that** I can securely access the ETL monitoring system.

---

## Acceptance Criteria

- [x] Given I am on the login page, when I enter valid credentials, then I receive a JWT token
- [x] Given I submit valid credentials, when login succeeds, then I am redirected to the loaders overview page
- [x] Given I submit invalid credentials, when login fails, then I see an error message
- [x] Given I submit invalid credentials, when login fails 3 times, then I see a rate limit warning
- [x] Given I have a valid token, when I access protected endpoints, then my request succeeds

---

## Implementation

**Endpoint**: `POST /api/v1/auth/login`

**Request**:
```json
{
  "username": "admin",
  "password": "admin123"
}
```

**Response** (Success):
```json
{
  "token": "eyJhbGciOiJIUzI1NiIsInR...",
  "username": "admin",
  "roles": ["ADMIN"]
}
```

**Response** (Failure):
```json
{
  "error": "Invalid credentials"
}
```

---

## Files Modified

**Backend**:
- ✅ `AuthController.java` - Added /login endpoint
- ✅ `JwtTokenProvider.java` - Token generation logic
- ✅ `SecurityConfig.java` - Authentication configuration

**Frontend**:
- ✅ `LoginPage.tsx` - Login form UI
- ✅ `loaders.ts` - API call for login

---

## Testing

- [x] Unit test: Valid credentials return token
- [x] Unit test: Invalid credentials return error
- [x] Integration test: Full login flow
- [x] Manual test: Login from UI works

---

## Definition of Done

- [x] Code written and follows standards
- [x] Unit tests passing
- [x] Integration tests passing
- [x] Code reviewed and approved
- [x] Deployed to staging
- [x] Deployed to production
- [x] Product owner accepted

---

**Status**: ✅ COMPLETED (2025-12-25)
