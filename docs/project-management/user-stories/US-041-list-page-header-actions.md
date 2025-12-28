---
id: "US-041"
title: "List Page Header with Quick Actions"
epic: "EPIC-010"
status: "in_progress"
priority: "medium"
created: "2025-12-27"
updated: "2025-12-27"
assignee: "frontend-team"
reviewer: ""
labels: ["frontend", "ui", "ux"]
estimated_points: 3
actual_hours: 0
sprint: "sprint-02"
dependencies: []
linear_id: ""
jira_id: ""
github_issue: ""
---

# US-041: List Page Header with Quick Actions

## User Story

**As an** operations engineer,
**I want** quick access buttons in the list page header,
**So that** I can perform common actions (create, refresh, filter, export) without navigating away or searching for buttons.

---

## Acceptance Criteria

- [ ] Given I am on the Loaders List page, when I look at the header, then I see 3 iconic action buttons (Create, Refresh, Filter)
- [ ] Given I hover over an iconic button, when I pause, then I see a tooltip describing the action
- [ ] Given I click the Create button, when successful, then the create loader modal opens
- [ ] Given I click the Refresh button, when successful, then the loader list refetches from API
- [ ] Given I click the Filter button, when successful, then the filter panel opens
- [ ] Given I click the "More" dropdown, when it opens, then I see 7 secondary actions
- [ ] Given I select "Export to CSV" from dropdown, when clicked, then loader data downloads as CSV file
- [ ] Given I am on mobile (< 768px width), when I view the header, then actions are responsive and usable

---

## Functional Requirements

### Must Have (P0)
1. **PageHeader Component**: Reusable component for consistent headers
2. **3 Iconic Primary Actions**:
   - Create (+) - Opens create modal
   - Refresh (↻) - Refetches list data
   - Filter (⚙) - Opens filter panel
3. **Dropdown Menu (⋮ More)** with 7 actions:
   - Export to CSV
   - Import from File (placeholder for now)
   - Advanced Filters (placeholder for now)
   - Bulk Operations (placeholder for now)
   - List Settings (placeholder for now)
   - Documentation (link to docs)
   - Help (placeholder for now)
4. **Tooltips**: All buttons have descriptive tooltips
5. **Mobile Responsive**: Works on screens < 768px

### Should Have (P1)
1. Export includes all visible loader fields
2. Filter panel with status and code filtering
3. Keyboard shortcuts (Ctrl+R for refresh, etc.)

### Nice to Have (P2)
1. Animated refresh icon when fetching
2. Badge on filter icon when filters active
3. Recent actions history in dropdown

---

## Non-Functional Requirements

### Performance
- Header renders instantly (<50ms)
- Dropdown opens without delay
- Refresh action completes within 1-2 seconds

### Usability
- Icons are recognizable and standard
- Dropdown menu items clearly labeled
- Tooltips appear after 500ms hover
- Mobile: Buttons minimum 44x44px (touch-friendly)

### Accessibility
- All buttons have ARIA labels
- Keyboard navigation (Tab, Enter, Esc)
- Screen reader announcements
- Focus indicators visible

---

## Technical Implementation

### Component Structure

**New Components**:
```
frontend/src/components/layout/
└── PageHeader.tsx          # Reusable header component
```

**Modified Components**:
```
frontend/src/pages/
└── LoadersListPage.tsx     # Use PageHeader
```

### PageHeader Component Interface

```typescript
interface PageHeaderProps {
  title: string;
  subtitle?: string;
  primaryActions?: Array<{
    icon: LucideIcon;
    label: string;
    onClick: () => void;
    variant?: 'primary' | 'secondary' | 'ghost';
    loading?: boolean;
  }>;
  secondaryActions?: Array<{
    icon?: LucideIcon;
    label: string;
    onClick: () => void;
    divider?: boolean;
    disabled?: boolean;
  }>;
}
```

### LoadersListPage Usage

```typescript
import { PageHeader } from '@/components/layout/PageHeader';
import { Plus, RefreshCw, SlidersHorizontal, Download, Upload, BookOpen, HelpCircle } from 'lucide-react';

export default function LoadersListPage() {
  const { refetch, isRefetching } = useQuery({
    queryKey: ['loaders'],
    queryFn: loadersApi.getLoaders,
  });

  const handleExport = () => {
    const csv = generateCSV(loaders);
    downloadFile(csv, 'loaders.csv');
  };

  return (
    <div className="container mx-auto p-6">
      <PageHeader
        title="Active Loaders"
        subtitle="Manage and monitor data loader configurations"
        primaryActions={[
          {
            icon: Plus,
            label: "Create New Loader",
            onClick: () => setCreateModalOpen(true),
            variant: 'primary'
          },
          {
            icon: RefreshCw,
            label: "Refresh List",
            onClick: () => refetch(),
            loading: isRefetching
          },
          {
            icon: SlidersHorizontal,
            label: "Filter & Sort",
            onClick: () => setFilterPanelOpen(true)
          }
        ]}
        secondaryActions={[
          {
            icon: Download,
            label: "Export to CSV",
            onClick: handleExport
          },
          {
            icon: Upload,
            label: "Import from File",
            onClick: () => toast.info("Coming soon"),
            disabled: true
          },
          { divider: true },
          {
            icon: BookOpen,
            label: "Documentation",
            onClick: () => window.open('/docs/loaders', '_blank')
          },
          {
            icon: HelpCircle,
            label: "Help",
            onClick: () => toast.info("Coming soon"),
            disabled: true
          }
        ]}
      />

      {/* Rest of page... */}
    </div>
  );
}
```

### Export to CSV Implementation

```typescript
function generateCSV(loaders: Loader[]): string {
  const headers = [
    'Loader Code',
    'Status',
    'Min Interval',
    'Max Interval',
    'Max Parallel',
    'Query Period'
  ];

  const rows = loaders.map(loader => [
    loader.loaderCode,
    loader.enabled ? 'ENABLED' : 'DISABLED',
    `${loader.minIntervalSeconds}s`,
    `${loader.maxIntervalSeconds}s`,
    loader.maxParallelExecutions,
    `${loader.maxQueryPeriodSeconds}s`
  ]);

  return [headers, ...rows]
    .map(row => row.join(','))
    .join('\n');
}

function downloadFile(content: string, filename: string) {
  const blob = new Blob([content], { type: 'text/csv' });
  const url = URL.createObjectURL(blob);
  const link = document.createElement('a');
  link.href = url;
  link.download = filename;
  link.click();
  URL.revokeObjectURL(url);
}
```

---

## UI/UX Design

### Desktop Layout (>=1024px)
```
┌──────────────────────────────────────────────────────────────────┐
│  Active Loaders                                                   │
│  Manage and monitor data loader configurations                    │
│                                                                    │
│  [➕ Create] [🔄 Refresh] [⚙️ Filter] [⋮ More ▼]                │
└──────────────────────────────────────────────────────────────────┘
```

### Tablet Layout (768px - 1023px)
```
┌────────────────────────────────────────────────┐
│  Active Loaders                                │
│  Manage and monitor loaders                    │
│                                                │
│  [➕] [🔄] [⚙️] [⋮]                          │
└────────────────────────────────────────────────┘
```

### Mobile Layout (<768px)
```
┌──────────────────────────────┐
│  Active Loaders              │
│                              │
│  [➕] [🔄] [⚙️] [☰]        │
└──────────────────────────────┘
```

---

## Testing Checklist

### Unit Tests
- [ ] PageHeader renders with title and subtitle
- [ ] Primary actions render as buttons with icons
- [ ] Secondary actions render in dropdown menu
- [ ] onClick handlers called when buttons clicked
- [ ] Tooltips display on hover
- [ ] Loading state shows spinner on refresh button

### Integration Tests
- [ ] Clicking Create opens create modal
- [ ] Clicking Refresh refetches loader data
- [ ] Clicking Export downloads CSV file
- [ ] Dropdown menu opens/closes correctly

### Manual Testing
- [ ] Test on Chrome desktop
- [ ] Test on Firefox desktop
- [ ] Test on Safari mobile
- [ ] Test with keyboard only (Tab, Enter, Esc)
- [ ] Test with screen reader
- [ ] Test on 375px width (iPhone SE)
- [ ] Test on 1920px width (desktop)

---

## Definition of Done

- [ ] PageHeader component created in `components/layout/`
- [ ] PageHeader has TypeScript interfaces
- [ ] LoadersListPage updated to use PageHeader
- [ ] All 3 primary actions functional
- [ ] Dropdown menu with 7 secondary actions (some placeholders OK)
- [ ] Export to CSV works
- [ ] Mobile responsive (tested at 375px, 768px, 1024px)
- [ ] Tooltips on all buttons
- [ ] Unit tests written and passing
- [ ] Manual testing complete
- [ ] Code reviewed and approved
- [ ] Deployed to dev/staging
- [ ] Product owner approved

---

## Dependencies

### Blocked By
- None

### Blocks
- Future: Standardization of other list pages

---

## Risks

| Risk | Mitigation |
|------|------------|
| Dropdown component not in shadcn/ui | Install shadcn dropdown-menu component |
| CSV export library needed | Use native JavaScript (no external lib needed) |
| Mobile layout cramped | Stack actions vertically or use hamburger menu |

---

## Open Questions

- [x] Which 3 actions should be primary? → Create, Refresh, Filter
- [ ] Should filter be inline panel or modal?
- [ ] Should export support JSON format too?
- [ ] Should we add "Clear All Filters" in dropdown?

---

## Notes

### 2025-12-27
- User story created based on ENH-001 enhancement
- Linked to new EPIC-010 (Enhanced Loader Management UI)
- Assigned to frontend-team
- Estimated 3 story points (3-4 hours)
- Status: In Progress

---

## References

- [Parent Enhancement: ENH-001](../enhancements/ENH-001-list-page-header-actions.md)
- [Parent EPIC: EPIC-010](../epics/EPIC-010-enhanced-loader-management-ui.md)
- [shadcn/ui Dropdown](https://ui.shadcn.com/docs/components/dropdown-menu)
- [Lucide Icons](https://lucide.dev/icons/)

---

**Created By**: frontend-team
**Last Updated**: 2025-12-27
**Status**: 🚧 In Progress
