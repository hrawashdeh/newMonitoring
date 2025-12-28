# Field Protection Implementation Guide

## Overview
This system implements **role-based field-level data protection** with both backend enforcement and frontend visual indicators.

## 🎯 Design Philosophy: Manual Frontend Implementation (Intentional)

**This is NOT a limitation - it's a deliberate architectural choice.**

### Why Manual Frontend?

✅ **Backend Security: 100% Database-Driven**
- No code deployments needed to change protection rules
- Zero hardcoding - all rules in `field_protection` table
- Protection enforced at API layer (cannot be bypassed)

✅ **Frontend UX Quality: Custom Formatting & Layouts**
- Field-specific formatting (seconds → "5m", timestamps → "Dec 27, 2025")
- Custom components (badges with colors, icons)
- Logical grouping (Basic Info, Execution Config, Metadata)
- User-friendly field names ("Status" instead of "enabled")
- Contextual tooltips and help text

✅ **Production-Ready Approach**
- Backend enforces security (automatic)
- Frontend provides excellent UX (manual but flexible)
- Clear separation of concerns
- Testable and maintainable

### Alternative: Fully Automatic Frontend (NOT Recommended)

We COULD make it fully automatic with generic field iteration:

```typescript
// Hypothetical automatic approach (DON'T DO THIS)
{Object.keys(loader).map(fieldName => (
  isFieldProtected(fieldName) ? <div>***HIDDEN***</div> : <div>{loader[fieldName]}</div>
))}
```

**But you'd LOSE:**
- ❌ Custom field names ("enabled" becomes "enabled" instead of "Status")
- ❌ Custom formatting (seconds as raw numbers instead of "5m")
- ❌ Custom components (no colored badges, no icons)
- ❌ Logical grouping (all fields in one flat list)
- ❌ Field-specific tooltips (no contextual help)
- ❌ Professional UX (looks like a generic CRUD form)

### ✅ Current Approach is Recommended

**This architectural trade-off gives you:**
1. **Security**: Backend-enforced, database-driven, zero-bypass protection
2. **Quality**: Professional UX with custom formatting and layouts
3. **Flexibility**: Add new protected fields via SQL only (no backend deploy)
4. **Clarity**: Explicit protection checks make code self-documenting

**Bottom Line:** Manual frontend implementation is a FEATURE, not a bug. It ensures production-quality UX while maintaining automatic backend security.

---

## Architecture

### Backend (Fully Automatic ✅)
- **Database-Driven**: All rules stored in `resource_management.field_protection` table
- **Zero Hardcoding**: Uses `ObjectMapper.convertValue(dto, Map.class)` to iterate ALL fields dynamically
- **Default Policy**: Fields without protection rules → VISIBLE
- **Redaction Strategies**: REMOVE, MASK, TRUNCATE, HASH

**No code changes needed** to protect a new field - just insert a database row.

### Frontend (Manual Implementation Required ⚠️)
- **Protection State**: Received via `protectedFields` array from API
- **Visual Indicators**: EyeOff icon for hidden fields
- **Custom Formatting**: Maintains field-specific layouts, badges, and formatters

**IMPORTANT**: All fields displayed in the UI **MUST** manually implement protection checks.

---

## Frontend Implementation Requirements

### ✅ **Rule 1: All Fields Must Check Protection First**

Every field rendered in the UI must follow this pattern:

```typescript
{isFieldProtected('fieldName') ? (
  // Protected: Show placeholder with icon
  <FieldDisplay
    label="Field Label"
    value="***HIDDEN***"
    isProtected={true}  // Shows EyeOff icon
  />
) : (
  // Not Protected: Show actual value
  <FieldDisplay
    label="Field Label"
    value={loader.fieldName}
  />
)}
```

**Why?** Backend removes protected fields from API response, so frontend gets `undefined`. Without the check, UI shows default/fallback values instead of protection indicator.

### ✅ **Rule 2: Protection Check Comes BEFORE Value Check**

**WRONG ❌:**
```typescript
// Checks value first - shows UTC+00:00 instead of protected indicator
{loader.sourceTimezoneOffsetHours && (
  <FieldDisplay value={formatTz(loader.sourceTimezoneOffsetHours)} />
)}
```

**CORRECT ✅:**
```typescript
// Checks protection first
{isFieldProtected('sourceTimezoneOffsetHours') ? (
  <FieldDisplay value="***HIDDEN***" isProtected={true} />
) : (
  <FieldDisplay value={formatTz(loader.sourceTimezoneOffsetHours)} />
)}
```

### ✅ **Rule 3: Special Handling for SQL/Large Fields**

For fields like SQL queries, use bordered placeholder:

```typescript
{isFieldProtected('loaderSql') ? (
  <div>
    <h4 className="flex items-center gap-2">
      SQL Query
      <span title="Field hidden due to permission restrictions">
        <EyeOff className="h-4 w-4 text-amber-600" />
      </span>
    </h4>
    <div className="bg-muted/50 p-4 rounded-md border border-dashed">
      <p className="text-sm text-muted-foreground italic flex items-center gap-2">
        <EyeOff className="h-4 w-4" />
        SQL query hidden due to permission restrictions
      </p>
    </div>
  </div>
) : loader.loaderSql ? (
  <div>
    <h4>SQL Query</h4>
    <pre className="bg-muted/50 p-4 rounded-md text-xs">
      {loader.loaderSql}
    </pre>
  </div>
) : null}
```

---

## Action Button Protection Logic

### Understanding Field Dependencies

Actions fall into two categories:

#### 1. **Field-Dependent Actions** (require seeing field state)

These actions **depend on field values** to make sense:

| Action | Depends On Field | Why? |
|--------|-----------------|------|
| Toggle Enabled/Disabled | `enabled` | Can't toggle what you can't see |
| Force Start | `enabled` | Only makes sense if enabled=false |
| Pause/Resume | `enabled` | Button label depends on current state |

**Protection Logic:**
```typescript
const isEnabledFieldProtected = protectedFields.includes('enabled');

{
  id: 'toggleEnabled',
  enabled: !!loader._links?.toggleEnabled && !isEnabledFieldProtected,
  disabledReason: isEnabledFieldProtected
    ? 'Action disabled due to data protection (enabled status is hidden)'
    : !loader._links?.toggleEnabled
    ? 'Insufficient permissions'
    : undefined,
}
```

**Result:**
- If `enabled` is protected → Button **DISABLED** with tooltip explaining why
- If user lacks permission → Button **DISABLED** with "Insufficient permissions"
- If both conditions pass → Button **ENABLED**

#### 2. **Field-Independent Actions** (don't require field state)

These actions **do NOT** depend on protected fields:

| Action | Depends On Field? | Why Not? |
|--------|------------------|----------|
| View Details | ❌ No | Just opens detail view |
| View Signals | ❌ No | Shows historical data |
| View Execution Log | ❌ No | Shows audit trail |
| View Alerts | ❌ No | Shows alert history |
| Edit Loader | ❌ No | Opens edit form (form handles protection) |

**Protection Logic:**
```typescript
{
  id: 'viewDetails',
  enabled: !!loader._links?.viewDetails,  // No field dependency
  disabledReason: !loader._links?.viewDetails
    ? 'Insufficient permissions'
    : undefined,
}
```

**Result:**
- Only checks HATEOAS `_links` permission
- Does NOT check protected fields
- User can still view details page (which shows protected indicators there)

---

## Decision Matrix: When to Disable Actions

```
┌─────────────────────────────────────────────────────────────────┐
│ Decision Tree: Should Action Be Disabled Due to Field Protection?│
└─────────────────────────────────────────────────────────────────┘

Q1: Does the action CHANGE a field value?
    ├─ YES → Q2
    └─ NO  → Skip field protection check (use _links only)

Q2: Is that field protected (hidden)?
    ├─ YES → DISABLE action + show tooltip
    └─ NO  → Allow (check _links permission)

Examples:
- Toggle Enabled: Changes `enabled` field → Check if `enabled` protected
- Force Start: Changes `enabled` field → Check if `enabled` protected
- View Details: Doesn't change anything → Don't check fields
- Edit: Opens form (form handles protection) → Don't check fields
```

---

## Implementation Checklist

### Adding a New Field to UI

- [ ] **Backend**: Field already auto-protected via database rules ✅
- [ ] **Frontend**: Add field to TypeScript interface
- [ ] **Frontend**: Add `isFieldProtected('fieldName')` check in JSX
- [ ] **Frontend**: Show `***HIDDEN***` with `isProtected={true}` when protected
- [ ] **Frontend**: Format actual value when not protected

### Adding a New Action Button

- [ ] **Determine**: Does action depend on a field value?
- [ ] **If YES**: Add field protection check + `disabledReason`
- [ ] **If NO**: Only check HATEOAS `_links` permission
- [ ] **Document**: Which field(s) the action depends on

### Protecting an Existing Field

- [ ] **Backend**: Insert row into `resource_management.field_protection`
- [ ] **Test**: Verify field shows `***HIDDEN***` in UI
- [ ] **Test**: Verify related actions disabled if field-dependent
- [ ] **No Code Deploy**: Pure database configuration change ✅

---

## Examples

### Example 1: Protect New Field `lastExecutionTime`

**Step 1: Database** (No code changes)
```sql
INSERT INTO resource_management.field_protection
(resource_type, field_name, role_code, is_visible, redaction_type, description)
VALUES
('LOADER', 'lastExecutionTime', 'VIEWER', false, 'REMOVE', 'Last execution timestamp');
```

**Step 2: Add to TypeScript Interface**
```typescript
export interface Loader {
  // ... existing fields
  lastExecutionTime?: string; // ISO 8601 timestamp
}
```

**Step 3: Add to LoaderDetailPanel**
```typescript
{isFieldProtected('lastExecutionTime') ? (
  <FieldDisplay
    label="Last Execution"
    value="***HIDDEN***"
    isProtected={true}
  />
) : (
  <FieldDisplay
    label="Last Execution"
    value={loader.lastExecutionTime ? formatDateTime(loader.lastExecutionTime) : '-'}
  />
)}
```

**Step 4: Check Action Dependencies**
```typescript
// If we add a "View Last Execution Details" action that uses this field:
const isLastExecProtected = protectedFields.includes('lastExecutionTime');

{
  id: 'viewLastExecution',
  enabled: !!loader._links?.viewLastExecution && !isLastExecProtected,
  disabledReason: isLastExecProtected
    ? 'Action disabled due to data protection (last execution time is hidden)'
    : undefined,
}
```

---

### Example 2: New Action "Download Logs" (Field-Independent)

This action downloads logs - doesn't depend on any field state.

```typescript
{
  id: 'downloadLogs',
  icon: Download,
  label: 'Download Logs',
  onClick: () => handlers.onDownloadLogs(loader),
  enabled: !!loader._links?.downloadLogs,  // Only check permission
  disabledReason: !loader._links?.downloadLogs
    ? 'Insufficient permissions'
    : undefined,
  // NO field protection check - action doesn't depend on field values
}
```

---

### Example 3: New Action "Reset Loader" (Field-Dependent)

This action resets the loader state - depends on seeing current `enabled` status.

```typescript
const isEnabledFieldProtected = protectedFields.includes('enabled');

{
  id: 'resetLoader',
  icon: RotateCcw,
  label: 'Reset Loader',
  onClick: () => handlers.onReset(loader),
  enabled: !!loader._links?.resetLoader && !isEnabledFieldProtected,
  disabledReason: isEnabledFieldProtected
    ? 'Action disabled due to data protection (enabled status is hidden)'
    : !loader._links?.resetLoader
    ? 'Insufficient permissions'
    : undefined,
  // Field protection check - action requires knowing enabled state
}
```

---

## Testing Protection

### Manual Testing Steps

1. **Protect a field via SQL**:
   ```sql
   UPDATE resource_management.field_protection
   SET is_visible = false
   WHERE resource_type = 'LOADER'
     AND field_name = 'sourceTimezoneOffsetHours'
     AND role_code = 'ADMIN';
   ```

2. **Refresh UI** (no code deployment needed)

3. **Verify**:
   - [ ] Field shows `***HIDDEN***` with EyeOff icon
   - [ ] Field-dependent actions disabled with tooltip
   - [ ] Field-independent actions still work
   - [ ] API response does not contain field
   - [ ] Backend logs show field filtered

### Security Testing

- [ ] Direct API call doesn't return protected field
- [ ] Browser DevTools doesn't show protected value
- [ ] Protected field not in network response payload
- [ ] Changing `protectedFields` client-side doesn't reveal data (backend enforces)

---

## FAQ

**Q: Why not make frontend fully automatic?**
A: We'd lose custom field names, formatting, badges, tooltips, and logical grouping. Current approach balances security automation (backend) with UX quality (frontend).

**Q: What if I forget to add protection check in UI?**
A: Field will show as `undefined` or default value instead of protection indicator. Backend still protects data (field not in API response), but UX is degraded.

**Q: Can I bypass protection by modifying frontend code?**
A: No. Backend enforces protection. Frontend just provides visual indicators. Even if you hack the UI, API won't return the data.

**Q: How do I know which actions depend on which fields?**
A: Ask: "Does this action change or depend on a field value?" If yes, add protection check. If no (view/read actions), skip it.

**Q: What if an action depends on MULTIPLE fields?**
A: Check all dependent fields:
```typescript
const isAnyDependentFieldProtected =
  protectedFields.includes('enabled') ||
  protectedFields.includes('lastRunTime');

enabled: !!loader._links?.action && !isAnyDependentFieldProtected
```

---

## Best Practices

1. ✅ **Always check protection BEFORE value checks**
2. ✅ **Use `isProtected={true}` prop for visual consistency**
3. ✅ **Provide clear `disabledReason` messages**
4. ✅ **Document which fields each action depends on**
5. ✅ **Test protection with different roles**
6. ✅ **Use database to configure, not code**
7. ✅ **Keep protection logic in createActions() functions**

---

## Related Files

### Backend
- `/services/loader/src/main/java/com/tiqmo/monitoring/loader/service/security/FieldProtectionService.java` - Core filtering logic
- `/services/loader/src/main/java/com/tiqmo/monitoring/loader/api/loader/LoaderController.java` - API response filtering
- `/services/etl_initializer/src/main/resources/db/migration/V10__create_field_protection_configuration.sql` - Schema

### Frontend
- `/frontend/src/components/loaders/LoaderDetailPanel.tsx` - Field display with protection
- `/frontend/src/components/loaders/LoaderActionButton.tsx` - Action button component
- `/frontend/src/pages/LoadersListPage.tsx` - List page with actions
- `/frontend/src/pages/LoaderDetailsPage.tsx` - Detail page
- `/frontend/src/types/loader.ts` - TypeScript interfaces

---

## Summary

| Component | Dynamic? | Global? | Manual Work Required |
|-----------|----------|---------|----------------------|
| Backend Filtering | ✅ 100% | ✅ Yes | ❌ No - SQL only |
| Frontend Fields | ✅ Via API | ⚠️ Per component | ✅ Yes - Add JSX per field |
| Action Buttons | ✅ Via API | ✅ Yes | ⚠️ If field-dependent |
| Security Enforcement | ✅ 100% | ✅ Yes | ❌ No - Database-driven |

**Key Takeaway**: Protection is **database-driven and secure**, but **UI requires manual implementation** for each field to maintain UX quality and custom formatting.
