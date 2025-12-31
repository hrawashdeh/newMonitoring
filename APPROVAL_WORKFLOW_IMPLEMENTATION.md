# Approval Workflow Implementation Guide

## ✅ Completed

### Backend
1. **LoadersStatsDto.java** - Added approval count fields:
   - `pendingApproval`
   - `approved`
   - `rejected`

2. **LoaderService.java** - Updated `getStats()` method to calculate approval statistics

3. **API Endpoints** (already exist):
   - `POST /api/v1/res/loaders/{loaderCode}/approve`
   - `POST /api/v1/res/loaders/{loaderCode}/reject`

### Frontend
1. **types/loader.ts** - Updated `LoadersStats` interface with approval fields
2. **lib/badge-utils.ts** - Added utility functions:
   - `getApprovalStatusVariant(status)`
   - `getApprovalStatusText(status)`

## 🔄 Remaining Tasks

### 1. Add Approval Status Column to Loaders Table

**File:** `frontend/src/pages/LoadersListPage.tsx`

**Add after line 231 (after "enabled" column):**

```typescript
{
  accessorKey: 'approvalStatus',
  header: 'Approval',
  cell: ({ row }) => {
    const status = row.getValue('approvalStatus') as string | undefined;
    const isProtected = protectedFields.includes('approvalStatus');

    if (isProtected) {
      return (
        <span className="text-xs text-muted-foreground italic flex items-center gap-1">
          <EyeOff className="h-3 w-3 text-amber-600" />
          Hidden
        </span>
      );
    }

    if (!status) {
      return <span className="text-muted-foreground">-</span>;
    }

    return (
      <Badge variant={getApprovalStatusVariant(status)}>
        {getApprovalStatusText(status)}
      </Badge>
    );
  },
},
```

**Import required:**
```typescript
import { getApprovalStatusVariant, getApprovalStatusText } from '../lib/badge-utils';
```

### 2. Add Filters for Enabled & Approval Status

**File:** `frontend/src/pages/LoadersListPage.tsx`

**Add state for filters (after line 373):**

```typescript
const [enabledFilter, setEnabledFilter] = useState<'all' | 'enabled' | 'disabled'>('all');
const [approvalFilter, setApprovalFilter] = useState<'all' | 'APPROVED' | 'PENDING_APPROVAL' | 'REJECTED'>('all');
```

**Filter loaders based on selections (replace line 380):**

```typescript
const filteredLoaders = useMemo(() => {
  let result = loaders;

  // Filter by enabled status
  if (enabledFilter === 'enabled') {
    result = result.filter(l => l.enabled === true);
  } else if (enabledFilter === 'disabled') {
    result = result.filter(l => l.enabled === false);
  }

  // Filter by approval status
  if (approvalFilter !== 'all') {
    result = result.filter(l => l.approvalStatus === approvalFilter);
  }

  return result;
}, [loaders, enabledFilter, approvalFilter]);
```

**Update table to use filtered loaders (line 460):**

```typescript
const table = useReactTable({
  data: filteredLoaders, // Changed from: loaders
  columns,
  // ... rest of config
});
```

**Add filter UI (after line 598 in the actions section):**

```typescript
{
  icon: SlidersHorizontal,
  label: 'Filters',
  children: (
    <div className="space-y-4 p-4 min-w-[300px]">
      <div>
        <label className="text-sm font-medium">Status</label>
        <select
          value={enabledFilter}
          onChange={(e) => setEnabledFilter(e.target.value as any)}
          className="w-full mt-1 p-2 border rounded-md"
        >
          <option value="all">All Loaders</option>
          <option value="enabled">Enabled Only</option>
          <option value="disabled">Disabled Only</option>
        </select>
      </div>
      <div>
        <label className="text-sm font-medium">Approval</label>
        <select
          value={approvalFilter}
          onChange={(e) => setApprovalFilter(e.target.value as any)}
          className="w-full mt-1 p-2 border rounded-md"
        >
          <option value="all">All Status</option>
          <option value="APPROVED">Approved</option>
          <option value="PENDING_APPROVAL">Pending Approval</option>
          <option value="REJECTED">Rejected</option>
        </select>
      </div>
      <button
        onClick={() => {
          setEnabledFilter('all');
          setApprovalFilter('all');
        }}
        className="w-full text-sm text-muted-foreground hover:text-foreground"
      >
        Clear Filters
      </button>
    </div>
  ),
},
```

### 3. Add Approval Actions (Approve/Reject Buttons)

**File:** `frontend/src/pages/LoadersListPage.tsx`

**Add mutations (after line 415):**

```typescript
// Mutation for approving loader
const approveMutation = useMutation({
  mutationFn: async (loader: Loader) => {
    return loadersApi.approveLoader(loader.loaderCode);
  },
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['loaders'] });
    toast({
      title: 'Loader Approved',
      description: 'Loader has been approved successfully',
    });
  },
  onError: (error) => {
    toast({
      title: 'Error',
      description: `Failed to approve loader: ${error instanceof Error ? error.message : 'Unknown error'}`,
      variant: 'destructive',
    });
  },
});

// Mutation for rejecting loader
const rejectMutation = useMutation({
  mutationFn: async ({ loader, reason }: { loader: Loader; reason: string }) => {
    return loadersApi.rejectLoader(loader.loaderCode, reason);
  },
  onSuccess: () => {
    queryClient.invalidateQueries({ queryKey: ['loaders'] });
    toast({
      title: 'Loader Rejected',
      description: 'Loader has been rejected',
    });
  },
  onError: (error) => {
    toast({
      title: 'Error',
      description: `Failed to reject loader: ${error instanceof Error ? error.message : 'Unknown error'}`,
      variant: 'destructive',
    });
  },
});
```

**Add action handlers (in actionHandlers object after line 428):**

```typescript
onApprove: (loader: Loader) => {
  if (confirm(`Approve loader "${loader.loaderCode}"?`)) {
    approveMutation.mutate(loader);
  }
},
onReject: (loader: Loader) => {
  const reason = prompt(`Enter rejection reason for "${loader.loaderCode}":`);
  if (reason && reason.trim()) {
    rejectMutation.mutate({ loader, reason: reason.trim() });
  }
},
```

**Update createLoaderActions function (add to actions array around line 89):**

```typescript
{
  id: 'approve',
  icon: CheckCircle,
  label: 'Approve Loader',
  onClick: () => handlers.onApprove(loader),
  enabled: !!loader._links?.approveLoader && loader.approvalStatus === 'PENDING_APPROVAL',
  iconColor: 'text-green-600',
  disabledReason: !loader._links?.approveLoader
    ? 'Insufficient permissions (ADMIN only)'
    : loader.approvalStatus !== 'PENDING_APPROVAL'
    ? 'Loader not in PENDING_APPROVAL status'
    : undefined,
},
{
  id: 'reject',
  icon: XCircle,
  label: 'Reject Loader',
  onClick: () => handlers.onReject(loader),
  enabled: !!loader._links?.rejectLoader && loader.approvalStatus === 'PENDING_APPROVAL',
  iconColor: 'text-red-600',
  disabledReason: !loader._links?.rejectLoader
    ? 'Insufficient permissions (ADMIN only)'
    : loader.approvalStatus !== 'PENDING_APPROVAL'
    ? 'Loader not in PENDING_APPROVAL status'
    : undefined,
},
```

**Import icons:**
```typescript
import { CheckCircle, XCircle } from 'lucide-react';
```

### 4. Add Dashboard Stats Card for Pending Approvals

**File:** `frontend/src/pages/LoadersOverviewPage.tsx` (or HomePage.tsx)

**Add pending approvals card to stats display:**

```typescript
<StatsCard
  title="Pending Approvals"
  value={stats?.pendingApproval ?? 0}
  description="Loaders awaiting approval"
  icon={AlertCircle}
  trend={undefined}
  variant="warning"
/>
```

**Make it clickable to filter loaders:**

```typescript
<StatsCard
  title="Pending Approvals"
  value={stats?.pendingApproval ?? 0}
  description="Loaders awaiting approval"
  icon={AlertCircle}
  trend={undefined}
  variant="warning"
  onClick={() => navigate('/loaders?filter=pending-approval')}
  className="cursor-pointer hover:shadow-lg transition-shadow"
/>
```

**Import:**
```typescript
import { AlertCircle } from 'lucide-react';
```

## 📝 Notes

### Token Validity Enhancement (Deferred for Later Discussion)

**Requirements:**
- User-based token validity periods
- Token versioning for selective invalidation
- Auto-renewal mechanism
- Special consideration for monitoring displays across departments

**Recommended Approach:**
- Access Token: 15-60 minutes (short-lived, stateless JWT)
- Refresh Token: User-configurable (7-90 days based on role)
- Store refresh tokens in database with user_id + token_version
- Auto-refresh in frontend 5 minutes before expiry
- Increment token_version to invalidate all user sessions

**Migration File Needed:**
```sql
-- V6__add_token_management.sql
ALTER TABLE auth.users ADD COLUMN token_version INTEGER DEFAULT 1;
ALTER TABLE auth.users ADD COLUMN token_validity_hours INTEGER DEFAULT 24;

CREATE TABLE auth.refresh_tokens (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL REFERENCES auth.users(id),
    token_hash VARCHAR(255) NOT NULL UNIQUE,
    expires_at TIMESTAMP NOT NULL,
    created_at TIMESTAMP DEFAULT NOW(),
    last_used_at TIMESTAMP,
    revoked BOOLEAN DEFAULT FALSE
);
```

## 🚀 Deployment Checklist

Before redeploying:

1. ✅ Backend changes compiled and built
2. ✅ Frontend changes applied to LoadersListPage.tsx
3. ✅ Approval actions tested with ADMIN role
4. ✅ Filters working correctly
5. ✅ Dashboard stats card displaying pending approvals
6. ⏳ Token management (deferred - needs discussion)

## 🔐 Security Notes

- Only ADMIN role can approve/reject loaders (enforced by backend)
- Approval status cannot be changed via regular update endpoint
- All approval actions logged in approval_audit_log table
- Token versioning allows instant invalidation on security incidents