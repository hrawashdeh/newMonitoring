---
id: "US-054"
title: "Protect SQL Query Field with Permission-Based Access"
epic: "EPIC-010"
status: "ready"
priority: "high"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "backend-team"
reviewer: ""
labels: ["backend", "security", "permissions", "api"]
estimated_points: 5
actual_hours: 0
sprint: "sprint-03"
dependencies: ["US-051", "US-053"]
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-054: Protect SQL Query Field with Permission-Based Access

## User Story

**As a** security-conscious administrator,
**I want** SQL queries to be hidden from unauthorized users,
**So that** sensitive business logic and database schemas are not exposed to viewers or operators.

---

## Business Context

### Problem
Currently, loader SQL queries are **visible to all users** regardless of role:
- SQL queries contain **sensitive business logic**
- Queries expose **database schema** information
- May contain **sensitive column names** or **security-sensitive patterns**
- VIEWER role should not see implementation details

### Security Risk
```sql
-- Example sensitive SQL that should be protected:
SELECT
    customer_id,
    ssn_last_four,  -- ← Sensitive field name exposed
    credit_score,   -- ← Sensitive field name exposed
    account_balance
FROM customers.sensitive_data  -- ← Schema structure exposed
WHERE status = 'active'
  AND created_at > :lastExecutionTime
```

### Solution
Implement **permission-based field filtering** at API level:
- **ADMIN**: Can view SQL queries (full access)
- **OPERATOR**: Can view SQL queries (need to troubleshoot)
- **VIEWER**: Cannot view SQL queries (redacted/hidden)

---

## Acceptance Criteria

### API-Level Protection
- [ ] When VIEWER requests loader details, SQL field is **redacted** or **null**
- [ ] When ADMIN requests loader details, SQL field is **returned in full**
- [ ] When OPERATOR requests loader details, SQL field is **returned in full**
- [ ] Protection applies to **ALL endpoints** that return loader data:
  - `GET /api/v1/res/loaders` (list)
  - `GET /api/v1/res/loaders/{loaderCode}` (detail)
  - `GET /api/v1/res/loaders/{loaderCode}/history` (execution history)

### Frontend Handling
- [ ] Detail panel shows SQL field only if present in API response
- [ ] If SQL is redacted, show placeholder: "SQL query hidden (requires ADMIN/OPERATOR role)"
- [ ] No error messages - graceful handling of missing field

### Audit Logging
- [ ] Log when VIEWER attempts to access loader details (for security audit)
- [ ] Include user ID, role, and timestamp in audit log

---

## Technical Implementation

### Backend: Add VIEW_SQL Action

**Update V7 Seed Data** (or create V10 migration):
```sql
-- Add new action for viewing SQL queries
INSERT INTO auth.actions (resource_type, action_code, action_name, http_method, url_template, description) VALUES
('LOADER', 'VIEW_SQL', 'View SQL Query', 'GET', '/api/v1/res/loaders/{loaderCode}/sql', 'View loader SQL query text')
ON CONFLICT (resource_type, action_code) DO NOTHING;

-- Grant to ADMIN
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'ADMIN', id, 'LOADER' FROM auth.actions WHERE action_code = 'VIEW_SQL' AND resource_type = 'LOADER'
ON CONFLICT DO NOTHING;

-- Grant to OPERATOR
INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'OPERATOR', id, 'LOADER' FROM auth.actions WHERE action_code = 'VIEW_SQL' AND resource_type = 'LOADER'
ON CONFLICT DO NOTHING;

-- VIEWER does NOT get this permission
```

---

### Backend: Conditional Field Filtering

**LoaderController.java**:
```java
@RestController
@RequestMapping("/api/v1/res/loaders")
public class LoaderController {

    @Autowired
    private HateoasLinkBuilder hateoasLinkBuilder;

    @Autowired
    private LoaderService loaderService;

    @GetMapping("/{loaderCode}")
    public ResponseEntity<LoaderResponse> getLoader(
        @PathVariable String loaderCode,
        @AuthenticationPrincipal JwtUser currentUser
    ) {
        Loader loader = loaderService.findByCode(loaderCode);

        // Build HATEOAS links based on user role and loader state
        LoaderActionLinks links = hateoasLinkBuilder.buildLoaderLinks(
            currentUser.getRole(),
            loader.getState()
        );

        // Build response DTO
        LoaderResponse response = LoaderResponse.from(loader);

        // CONDITIONAL: Remove SQL if user doesn't have VIEW_SQL permission
        if (!links.hasAction("VIEW_SQL")) {
            response.setLoaderSql(null);  // Redact SQL
        }

        response.set_links(links);

        return ResponseEntity.ok(response);
    }
}
```

**Alternative: Use DTO Projection**:
```java
@JsonInclude(JsonInclude.Include.NON_NULL)
public class LoaderResponse {
    private String loaderCode;

    @JsonProperty(access = JsonProperty.Access.WRITE_ONLY)  // Never serialize by default
    private String loaderSql;

    // Conditional setter based on permissions
    public void setLoaderSqlIfAuthorized(String sql, boolean hasPermission) {
        this.loaderSql = hasPermission ? sql : null;
    }
}
```

---

### Backend: Helper Method in HateoasLinkBuilder

```java
public class HateoasLinkBuilder {

    public boolean hasPermission(String userRole, String resourceType, String resourceState, String actionCode) {
        String sql = "SELECT EXISTS(" +
                     "  SELECT 1 FROM resource_management.get_allowed_actions(?, ?, ?) " +
                     "  WHERE action_code = ?" +
                     ")";

        return jdbcTemplate.queryForObject(sql,
            Boolean.class,
            userRole, resourceType, resourceState, actionCode);
    }
}

// Usage in controller
boolean canViewSql = hateoasLinkBuilder.hasPermission(
    currentUser.getRole(),
    "LOADER",
    loader.getState(),
    "VIEW_SQL"
);

if (canViewSql) {
    response.setLoaderSql(loader.getLoaderSql());
} else {
    response.setLoaderSql(null);
}
```

---

### Frontend: Conditional Rendering

**LoaderDetailPanel.tsx**:
```typescript
export function LoaderDetailPanel({ loader }: LoaderDetailPanelProps) {
  return (
    <Card>
      <CardHeader>
        <CardTitle>Loader Details: {loader.loaderCode}</CardTitle>
      </CardHeader>
      <CardContent>
        {/* Show SQL only if present */}
        {loader.loaderSql ? (
          <div>
            <h4 className="text-sm font-semibold mb-2">SQL Query</h4>
            <pre className="bg-muted p-3 rounded text-xs overflow-x-auto">
              {loader.loaderSql}
            </pre>
          </div>
        ) : (
          <div className="text-sm text-muted-foreground italic">
            SQL query hidden (requires ADMIN/OPERATOR role)
          </div>
        )}

        {/* Other fields always visible */}
        <div>
          <h4>Status</h4>
          <Badge variant={loader.enabled ? "success" : "secondary"}>
            {loader.enabled ? "ENABLED" : "DISABLED"}
          </Badge>
        </div>
        {/* ... other fields ... */}
      </CardContent>
    </Card>
  );
}
```

---

## Permission Matrix (Updated)

| Action | ADMIN | OPERATOR | VIEWER | Description |
|--------|-------|----------|--------|-------------|
| VIEW_DETAILS | ✅ | ✅ | ✅ | View loader metadata (code, status, intervals) |
| **VIEW_SQL** | ✅ | ✅ | ❌ | View SQL query text (NEW) |
| EDIT_LOADER | ✅ | ✅ | ❌ | Edit loader configuration |
| DELETE_LOADER | ✅ | ❌ | ❌ | Delete loader |

---

## Security Considerations

### Defense in Depth
1. **Database Level**: SQL stored encrypted (separate user story)
2. **API Level**: Conditional field filtering based on permissions (THIS story)
3. **Frontend Level**: Graceful handling of missing field

### Audit Requirements
- Log all attempts to view loader details
- Include role and permission check result
- Enable security team to detect unauthorized access attempts

---

## Testing Plan

### Backend Tests
```java
@Test
public void testViewerCannotSeeSql() {
    JwtUser viewer = new JwtUser("user123", "VIEWER");

    ResponseEntity<LoaderResponse> response = loaderController.getLoader(
        "SIGNAL_LOADER_001",
        viewer
    );

    assertNotNull(response.getBody());
    assertNull(response.getBody().getLoaderSql(), "SQL should be redacted for VIEWER");
}

@Test
public void testAdminCanSeeSql() {
    JwtUser admin = new JwtUser("admin123", "ADMIN");

    ResponseEntity<LoaderResponse> response = loaderController.getLoader(
        "SIGNAL_LOADER_001",
        admin
    );

    assertNotNull(response.getBody());
    assertNotNull(response.getBody().getLoaderSql(), "SQL should be visible for ADMIN");
}

@Test
public void testOperatorCanSeeSql() {
    JwtUser operator = new JwtUser("operator123", "OPERATOR");

    ResponseEntity<LoaderResponse> response = loaderController.getLoader(
        "SIGNAL_LOADER_001",
        operator
    );

    assertNotNull(response.getBody());
    assertNotNull(response.getBody().getLoaderSql(), "SQL should be visible for OPERATOR");
}
```

### Frontend Tests
- [ ] VIEWER sees placeholder text instead of SQL
- [ ] ADMIN sees SQL query in detail panel
- [ ] No console errors when SQL field is null

---

## Migration Path

### Option 1: Add to US-051 (HATEOAS Implementation)
Since US-051 is already implementing `HateoasLinkBuilder`, add `VIEW_SQL` action there.

### Option 2: Create V10 Migration
Create separate migration to add `VIEW_SQL` action:
```sql
-- V10__add_view_sql_permission.sql
INSERT INTO auth.actions (resource_type, action_code, action_name, ...) VALUES
('LOADER', 'VIEW_SQL', 'View SQL Query', 'GET', '/api/v1/res/loaders/{loaderCode}/sql', '...');

INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'ADMIN', id, 'LOADER' FROM auth.actions WHERE action_code = 'VIEW_SQL';

INSERT INTO auth.role_permissions (role_code, action_id, resource_type)
SELECT 'OPERATOR', id, 'LOADER' FROM auth.actions WHERE action_code = 'VIEW_SQL';
```

---

## Definition of Done

- [ ] `VIEW_SQL` action added to database
- [ ] ADMIN and OPERATOR roles granted VIEW_SQL permission
- [ ] VIEWER role does NOT have VIEW_SQL permission
- [ ] `LoaderController` conditionally filters SQL field based on permission
- [ ] Frontend gracefully handles missing SQL field
- [ ] Unit tests pass (backend)
- [ ] Integration tests pass (API)
- [ ] E2E tests pass (frontend + backend)
- [ ] Security audit logging implemented
- [ ] Documentation updated

---

## Related User Stories

- **US-051**: Backend HATEOAS Implementation (dependency)
- **US-053**: Resource Type Segregation (dependency)
- **US-055** (Future): Encrypt SQL in database at rest

---

**Status**: ✅ READY FOR DEVELOPMENT
**Priority**: HIGH (Security requirement)
**Estimated Effort**: 1-2 days
