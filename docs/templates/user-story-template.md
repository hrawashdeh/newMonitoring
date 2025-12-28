---
id: "US-XXX"
title: "User Story Title"
epic: "EPIC-XXX"                # Parent EPIC
status: "backlog"               # backlog | planned | in_progress | blocked | review | testing | done | cancelled
priority: "medium"              # critical | high | medium | low
created: "YYYY-MM-DD"
updated: "YYYY-MM-DD"
assignee: "unassigned"
reviewer: ""                    # Code reviewer
labels: ["frontend", "ui"]
estimated_points: 3             # Story points (1,2,3,5,8,13)
actual_hours: 0                 # Actual time spent
sprint: ""                      # Sprint ID
dependencies: []                # Blocking user stories
linear_id: ""                   # Linear issue ID (auto-filled)
jira_id: ""                     # Jira issue key (auto-filled)
github_issue: ""                # GitHub issue number (auto-filled)
---

# US-XXX: User Story Title

## User Story

**As a** [type of user],
**I want** [goal/desire],
**So that** [benefit/value].

**Example**:
> As an **operations engineer**,
> I want to **pause a running loader from the list page**,
> So that I can **quickly stop problematic loaders without navigating to details**.

---

## Acceptance Criteria

- [ ] **Criterion 1**: Given [context], when [action], then [expected result]
- [ ] **Criterion 2**: System displays confirmation message after successful action
- [ ] **Criterion 3**: Error handling: System shows clear error if action fails
- [ ] **Criterion 4**: UI is responsive and works on mobile devices
- [ ] **Criterion 5**: Accessibility: Keyboard navigation works correctly

**Example Criteria**:
- [ ] Given I am on the loaders list page, when I click the "Pause" button, then the loader status changes to "PAUSED"
- [ ] Given a loader is paused, when I click "Resume", then the loader status changes to "IDLE" and scheduling resumes
- [ ] Given the pause operation fails, when the error occurs, then I see a toast notification with the error message

---

## Functional Requirements

### Must Have (P0)
1. Button to pause loader in each table row
2. Confirmation dialog before pausing
3. Status badge updates immediately after pause
4. API endpoint to toggle enabled status

### Should Have (P1)
1. Keyboard shortcut for pause (e.g., Ctrl+P when row selected)
2. Bulk pause (select multiple loaders)
3. Undo action within 5 seconds

### Nice to Have (P2)
1. Animation when status changes
2. Reason field (why are you pausing?)

---

## Non-Functional Requirements

### Performance
- Action completes within 500ms
- UI update is instant (optimistic update)
- No page reload required

### Security
- User must have "ADMIN" or "OPERATOR" role
- Audit log entry created for pause action

### Usability
- Clear visual distinction between paused and active loaders
- Accessible via keyboard
- Works on mobile (touch-friendly)

---

## Technical Implementation

### Frontend Changes
**Files to Modify**:
- `frontend/src/pages/LoadersListPage.tsx` - Add pause button
- `frontend/src/components/LoaderRow.tsx` - Button component
- `frontend/src/api/loaders.ts` - Add `pauseLoader()` API call

**Code Sketch**:
```typescript
const pauseLoader = useMutation({
  mutationFn: (loaderCode: string) =>
    loadersApi.updateLoader(loaderCode, { enabled: false }),
  onSuccess: () => {
    queryClient.invalidateQueries(['loaders'])
    toast.success('Loader paused successfully')
  }
})
```

### Backend Changes
**Files to Modify**:
- `services/loader/src/main/java/com/tiqmo/monitoring/loader/api/loader/LoaderController.java`
  - Endpoint already exists: `PUT /api/v1/loaders/{loaderCode}`

**No backend changes needed** - existing endpoint supports this

### Database Changes
**No database schema changes needed**

---

## UI/UX Design

### Mockup
```
┌─────────────────────────────────────────────────────┐
│ Loader Code       Status    Actions                 │
├─────────────────────────────────────────────────────┤
│ ALARMS_LOADER     🟢 ENABLED  [⏸️ Pause] [ℹ️ Details]│ ← Add this button
│ SIGNALS_LOADER    🔴 PAUSED   [▶️ Resume] [ℹ️ Details]│
└─────────────────────────────────────────────────────┘
```

### Button States
- **Enabled Loader**: Show "Pause" button (destructive variant)
- **Paused Loader**: Show "Resume" button (primary variant)
- **Running Loader**: Show "Pause" button with warning icon

### Confirmation Dialog
```
┌─────────────────────────────────────────────┐
│ ⚠️  Pause Loader?                            │
│                                             │
│ Are you sure you want to pause              │
│ "ALARMS_LOADER"?                            │
│                                             │
│ This will stop all scheduled executions.    │
│                                             │
│ [Cancel]                       [Pause]      │
└─────────────────────────────────────────────┘
```

---

## Testing Checklist

### Unit Tests
- [ ] Test pause button renders for enabled loader
- [ ] Test resume button renders for paused loader
- [ ] Test mutation success updates UI
- [ ] Test mutation failure shows error

### Integration Tests
- [ ] Test full flow: Click pause → Confirm → Loader paused
- [ ] Test full flow: Click resume → Loader resumes
- [ ] Test error handling when API fails

### Manual Testing
- [ ] Test on Chrome desktop
- [ ] Test on Firefox desktop
- [ ] Test on Safari mobile
- [ ] Test with keyboard only (no mouse)
- [ ] Test with screen reader

---

## Definition of Done

- [ ] Code written and follows coding standards
- [ ] Unit tests written and passing (>80% coverage)
- [ ] Integration tests written and passing
- [ ] Code reviewed and approved
- [ ] QA tested and approved
- [ ] Documentation updated (if needed)
- [ ] Deployed to staging
- [ ] Product owner accepts feature
- [ ] Deployed to production

---

## Dependencies

### Blocked By
- [ ] US-000 - Loader list page must exist first

### Blocks
- [ ] US-999 - Bulk operations depend on single-item pause

---

## Risks

| Risk | Mitigation |
|------|------------|
| User accidentally pauses critical loader | Add confirmation dialog with loader name |
| Pause fails but UI shows paused | Implement optimistic update with rollback on error |
| User doesn't understand difference between pause and stop | Use clear labeling and tooltips |

---

## Open Questions

- [ ] Should we require a reason when pausing?
- [ ] Should we send notifications to other users when loader is paused?
- [ ] What happens to in-flight executions when paused?

---

## Notes

### 2025-12-27
- Initial user story creation
- Discussed with UX team, confirmed mockup

### YYYY-MM-DD
- Started implementation
- Blocked by dependency on US-000

---

## References

- [Parent EPIC](../epics/EPIC-XXX-description.md)
- [Figma Design](https://figma.com/...)
- [API Documentation](../../api-reference/loader-api.md)

---

**Created By**: Your Name
**Last Updated**: YYYY-MM-DD
**Status**: Backlog
