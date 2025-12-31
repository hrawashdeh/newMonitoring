# Approval Workflow - Complete Implementation Patches

## Summary of Changes Needed

The file `frontend/src/pages/LoadersListPage.tsx` needs the following modifications:

### 1. Add Approval Status Column (after line 238)

Insert this new column definition after the `enabled` column:

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

### 2. Update `createLoaderActions` Function Signature (line 84)

Replace the handlers interface:

```typescript
handlers: {
  onToggleEnabled: (loader: Loader) => void;
  onForceStart: (loader: Loader) => void;
  onEdit: (loader: Loader) => void;
  onShowDetails: (loader: Loader) => void;
  onShowSignals: (loader: Loader) => void;
  onShowExecutionLog: (loader: Loader) => void;
  onShowAlerts: (loader: Loader) => void;
  onApprove: (loader: Loader) => void;  // ADD THIS
  onReject: (loader: Loader) => void;   // ADD THIS
}
```

### 3. Add Approval Actions to `createLoaderActions` (before line 164)

Insert these actions before the closing `]` of the return array:

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

### 4. Update `getColumns` Function Signature (line 171)

Add the new handlers to the interface:

```typescript
handlers: {
  onToggleEnabled: (loader: Loader) => void;
  onForceStart: (loader: Loader) => void;
  onEdit: (loader: Loader) => void;
  onShowDetails: (loader: Loader) => void;
  onShowSignals: (loader: Loader) => void;
  onShowExecutionLog: (loader: Loader) => void;
  onShowAlerts: (loader: Loader) => void;
  onApprove: (loader: Loader) => void;  // ADD THIS
  onReject: (loader: Loader) => void;   // ADD THIS
}
```

### 5. Add Filter State (after line 381)

```typescript
const [enabledFilter, setEnabledFilter] = useState<'all' | 'enabled' | 'disabled'>('all');
const [approvalFilter, setApprovalFilter] = useState<'all' | 'APPROVED' | 'PENDING_APPROVAL' | 'REJECTED'>('all');
```

### 6. Add Filtered Loaders Logic (after line 401)

```typescript
// Filter loaders based on enabled and approval status
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

### 7. Add Approval Mutations (after line 423)

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

### 8. Add Approval Handlers (in actionHandlers object after line 463)

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
  } else if (reason !== null) {
    toast({
      title: 'Rejection Canceled',
      description: 'Rejection reason is required',
      variant: 'destructive',
    });
  }
},
```

### 9. Update Table Data Source (line 469)

Change from `loaders` to `filteredLoaders`:

```typescript
const table = useReactTable({
  data: filteredLoaders,  // CHANGED from: loaders
  columns,
  getCoreRowModel: getCoreRowModel(),
  getFilteredRowModel: getFilteredRowModel(),
  getPaginationRowModel: getPaginationRowModel(),
  getSortedRowModel: getSortedRowModel(),
});
```

### 10. Replace Filter Button in primaryActions (line 598)

Replace the existing "Filter & Sort" button with:

```typescript
{
  icon: SlidersHorizontal,
  label: `Filters${enabledFilter !== 'all' || approvalFilter !== 'all' ? ' (Active)' : ''}`,
  onClick: () => {}, // Placeholder - will be replaced with dropdown
},
```

Then add filter UI below the PageHeader (around line 641, before the table):

```typescript
{/* Filters */}
<div className="flex gap-4 mb-6">
  <div className="flex-1">
    <label className="text-sm font-medium mb-2 block">Status Filter</label>
    <select
      value={enabledFilter}
      onChange={(e) => setEnabledFilter(e.target.value as any)}
      className="w-full p-2 border rounded-md bg-background"
    >
      <option value="all">All Loaders</option>
      <option value="enabled">Enabled Only</option>
      <option value="disabled">Disabled Only</option>
    </select>
  </div>
  <div className="flex-1">
    <label className="text-sm font-medium mb-2 block">Approval Filter</label>
    <select
      value={approvalFilter}
      onChange={(e) => setApprovalFilter(e.target.value as any)}
      className="w-full p-2 border rounded-md bg-background"
    >
      <option value="all">All Status</option>
      <option value="APPROVED">Approved</option>
      <option value="PENDING_APPROVAL">Pending Approval</option>
      <option value="REJECTED">Rejected</option>
    </select>
  </div>
  {(enabledFilter !== 'all' || approvalFilter !== 'all') && (
    <div className="flex items-end">
      <Button
        variant="outline"
        onClick={() => {
          setEnabledFilter('all');
          setApprovalFilter('all');
        }}
      >
        Clear Filters
      </Button>
    </div>
  )}
</div>
```

## Testing Checklist

After applying all patches:

- [ ] Approval Status column appears in loaders table
- [ ] Badge colors correct (Green=Approved, Orange=Pending, Red=Rejected)
- [ ] Filters work correctly (enabled/disabled, approval status)
- [ ] Clear Filters button appears when filters are active
- [ ] Approve button visible only for ADMIN with PENDING_APPROVAL loaders
- [ ] Reject button visible only for ADMIN with PENDING_APPROVAL loaders
- [ ] Approve confirmation dialog works
- [ ] Reject prompt requests reason (required)
- [ ] Toast notifications appear on success/error
- [ ] Loaders list refreshes after approve/reject

## Notes

- All approval actions are ADMIN-only (enforced by backend via `_links`)
- Approval/Reject only enabled for loaders in PENDING_APPROVAL status
- Backend validates all permissions and state transitions
- Audit log automatically created for all approval actions