# How to Manually Adjust the Loader View

This guide explains how to customize the loaders list page in the frontend.

---

## Architecture Overview

```
┌─────────────────────────────────────────────────────────────┐
│                    Data Flow Architecture                   │
└─────────────────────────────────────────────────────────────┘

Backend API (/api/v1/res/loaders/loaders)
    ↓
API Client (src/api/loaders.ts)
    ↓
TypeScript Types (src/types/loader.ts)
    ↓
React Query Hook (useQuery in LoadersListPage.tsx)
    ↓
TanStack Table (columns definition)
    ↓
UI Components (shadcn/ui: Table, Badge, Button)
    ↓
Rendered View (LoadersListPage.tsx)
```

---

## Files Involved

| File | Purpose | What to Edit |
|------|---------|--------------|
| `src/types/loader.ts` | TypeScript types/interfaces | Add/remove fields from Loader interface |
| `src/api/loaders.ts` | API functions | Modify API calls, add new endpoints |
| `src/lib/api-config.ts` | API endpoint URLs | Change API paths |
| `src/pages/LoadersListPage.tsx` | Main view component | **This is where you customize the UI** |
| `src/components/ui/*` | shadcn/ui components | Pre-built UI components (rarely modified) |

---

## Common Customizations

### 1. Add/Remove Columns in the Table

**File:** `src/pages/LoadersListPage.tsx`

**Current columns:** (lines 24-81)
- Loader Code
- Status (ENABLED/DISABLED)
- Min Interval
- Max Interval
- Max Parallel Executions
- Query Period

**To ADD a new column:**

```typescript
// In LoadersListPage.tsx, add to the columns array:
const columns: ColumnDef<Loader>[] = [
  // ... existing columns ...
  
  // New column example: Show Loader ID
  {
    accessorKey: 'id',
    header: 'ID',
    cell: ({ row }) => (
      <span className="text-muted-foreground">#{row.getValue('id')}</span>
    ),
  },
  
  // New column example: Show SQL Query (truncated)
  {
    accessorKey: 'loaderSql',
    header: 'SQL Query',
    cell: ({ row }) => {
      const sql = row.getValue('loaderSql') as string;
      return (
        <span className="font-mono text-xs">
          {sql.substring(0, 50)}...
        </span>
      );
    },
  },
];
```

**To REMOVE a column:**

Just delete the entire column object from the `columns` array.

---

### 2. Change Column Formatting

**Example: Format intervals differently**

```typescript
// Current format (lines 44-51):
{
  accessorKey: 'minIntervalSeconds',
  header: 'Min Interval',
  cell: ({ row }) => {
    const seconds = row.getValue('minIntervalSeconds') as number;
    const minutes = Math.floor(seconds / 60);
    const hours = Math.floor(minutes / 60);
    if (hours > 0) return `${hours}h`;
    if (minutes > 0) return `${minutes}m`;
    return `${seconds}s`;
  },
}

// Custom format (show exact time):
{
  accessorKey: 'minIntervalSeconds',
  header: 'Min Interval',
  cell: ({ row }) => {
    const seconds = row.getValue('minIntervalSeconds') as number;
    const hours = Math.floor(seconds / 3600);
    const minutes = Math.floor((seconds % 3600) / 60);
    const secs = seconds % 60;
    return `${hours}h ${minutes}m ${secs}s`;
  },
}
```

---

### 3. Add Search/Filter Functionality

**Currently missing in LoadersListPage.tsx. Here's how to add it:**

```typescript
// Add state for search (after line 83)
const [searchTerm, setSearchTerm] = useState('');
const [statusFilter, setStatusFilter] = useState<'ALL' | 'ENABLED' | 'DISABLED'>('ALL');

// Filter data
const filteredLoaders = useMemo(() => {
  return loaders.filter((loader) => {
    // Search by loader code
    const matchesSearch = loader.loaderCode
      .toLowerCase()
      .includes(searchTerm.toLowerCase());
    
    // Filter by status
    const matchesStatus = 
      statusFilter === 'ALL' || 
      (statusFilter === 'ENABLED' && loader.enabled) ||
      (statusFilter === 'DISABLED' && !loader.enabled);
    
    return matchesSearch && matchesStatus;
  });
}, [loaders, searchTerm, statusFilter]);

// Update table data source (line 90)
const table = useReactTable({
  data: filteredLoaders, // Changed from 'loaders'
  columns,
  // ... rest of config
});

// Add search/filter UI (before the table, around line 122)
<div className="flex gap-4 mb-6">
  <Input
    placeholder="Search by loader code..."
    value={searchTerm}
    onChange={(e) => setSearchTerm(e.target.value)}
    className="max-w-sm"
  />
  
  <Select value={statusFilter} onValueChange={(val) => setStatusFilter(val as any)}>
    <SelectTrigger className="w-[180px]">
      <SelectValue placeholder="Filter by status" />
    </SelectTrigger>
    <SelectContent>
      <SelectItem value="ALL">All Loaders</SelectItem>
      <SelectItem value="ENABLED">Enabled Only</SelectItem>
      <SelectItem value="DISABLED">Disabled Only</SelectItem>
    </SelectContent>
  </Select>
</div>
```

**Don't forget to import Select components:**

```typescript
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '../components/ui/select';
import { useMemo, useState } from 'react'; // Add useMemo, useState
```

---

### 4. Add Actions Column (Edit/Delete Buttons)

**Add to columns array:**

```typescript
{
  id: 'actions',
  header: 'Actions',
  cell: ({ row }) => {
    const loader = row.original;
    
    return (
      <div className="flex gap-2">
        <Button
          variant="ghost"
          size="icon"
          onClick={() => handleEdit(loader.loaderCode)}
          title="Edit loader"
        >
          <Pencil className="h-4 w-4" />
        </Button>
        
        <Button
          variant="ghost"
          size="icon"
          onClick={() => handleDelete(loader.loaderCode)}
          title="Delete loader"
        >
          <Trash2 className="h-4 w-4" />
        </Button>
      </div>
    );
  },
}
```

**Add handler functions:**

```typescript
import { Pencil, Trash2 } from 'lucide-react';
import { useNavigate } from 'react-router-dom';

export default function LoadersListPage() {
  const navigate = useNavigate();
  
  const handleEdit = (loaderCode: string) => {
    navigate(`/loaders/${loaderCode}/edit`);
  };
  
  const handleDelete = async (loaderCode: string) => {
    if (!confirm(`Delete loader "${loaderCode}"?`)) return;
    
    try {
      await loadersApi.deleteLoader(loaderCode);
      // Refresh data
      queryClient.invalidateQueries(['loaders']);
    } catch (error) {
      console.error('Delete failed:', error);
      alert('Failed to delete loader');
    }
  };
  
  // ... rest of component
}
```

---

### 5. Make Columns Sortable

**TanStack Table already supports sorting! Just add `enableSorting: true`:**

```typescript
const table = useReactTable({
  data: loaders,
  columns,
  getCoreRowModel: getCoreRowModel(),
  getFilteredRowModel: getFilteredRowModel(),
  getPaginationRowModel: getPaginationRowModel(),
  getSortedRowModel: getSortedRowModel(), // Already added
  enableSorting: true, // Add this
});
```

**Make headers clickable:**

```typescript
<TableHead key={header.id}>
  {header.isPlaceholder ? null : (
    <div
      className={
        header.column.getCanSort()
          ? 'cursor-pointer select-none flex items-center gap-1'
          : ''
      }
      onClick={header.column.getToggleSortingHandler()}
    >
      {flexRender(header.column.columnDef.header, header.getContext())}
      {{
        asc: ' 🔼',
        desc: ' 🔽',
      }[header.column.getIsSorted() as string] ?? null}
    </div>
  )}
</TableHead>
```

---

### 6. Customize Badge Colors

**Current badge (lines 34-39):**

```typescript
cell: ({ row }) => {
  const enabled = row.getValue('enabled') as boolean;
  const variant = enabled ? 'success' : 'destructive';
  return <Badge variant={variant}>{enabled ? 'ENABLED' : 'DISABLED'}</Badge>;
}
```

**Custom colors:**

```typescript
cell: ({ row }) => {
  const enabled = row.getValue('enabled') as boolean;
  
  // Option 1: Use different variants
  return (
    <Badge variant={enabled ? 'default' : 'outline'}>
      {enabled ? 'ACTIVE' : 'INACTIVE'}
    </Badge>
  );
  
  // Option 2: Custom styling
  return (
    <Badge 
      className={enabled 
        ? 'bg-green-100 text-green-800 border-green-300' 
        : 'bg-gray-100 text-gray-600 border-gray-300'
      }
    >
      {enabled ? '✓ ENABLED' : '✗ DISABLED'}
    </Badge>
  );
}
```

---

### 7. Change Pagination Options

**Current pagination (lines 172-189):**

```typescript
<div className="flex items-center justify-end space-x-2 py-4">
  <Button
    variant="outline"
    size="sm"
    onClick={() => table.previousPage()}
    disabled={!table.getCanPreviousPage()}
  >
    Previous
  </Button>
  <Button
    variant="outline"
    size="sm"
    onClick={() => table.nextPage()}
    disabled={!table.getCanNextPage()}
  >
    Next
  </Button>
</div>
```

**Enhanced pagination with page size selector:**

```typescript
<div className="flex items-center justify-between py-4">
  {/* Page info */}
  <div className="text-sm text-muted-foreground">
    Showing {table.getState().pagination.pageIndex * table.getState().pagination.pageSize + 1} to{' '}
    {Math.min(
      (table.getState().pagination.pageIndex + 1) * table.getState().pagination.pageSize,
      loaders.length
    )}{' '}
    of {loaders.length} loaders
  </div>

  {/* Page size selector */}
  <div className="flex items-center gap-2">
    <span className="text-sm">Rows per page:</span>
    <Select
      value={table.getState().pagination.pageSize.toString()}
      onValueChange={(value) => table.setPageSize(Number(value))}
    >
      <SelectTrigger className="w-[80px]">
        <SelectValue />
      </SelectTrigger>
      <SelectContent>
        <SelectItem value="10">10</SelectItem>
        <SelectItem value="25">25</SelectItem>
        <SelectItem value="50">50</SelectItem>
        <SelectItem value="100">100</SelectItem>
      </SelectContent>
    </Select>
  </div>

  {/* Navigation buttons */}
  <div className="flex gap-2">
    <Button
      variant="outline"
      size="sm"
      onClick={() => table.setPageIndex(0)}
      disabled={!table.getCanPreviousPage()}
    >
      First
    </Button>
    <Button
      variant="outline"
      size="sm"
      onClick={() => table.previousPage()}
      disabled={!table.getCanPreviousPage()}
    >
      Previous
    </Button>
    <Button
      variant="outline"
      size="sm"
      onClick={() => table.nextPage()}
      disabled={!table.getCanNextPage()}
    >
      Next
    </Button>
    <Button
      variant="outline"
      size="sm"
      onClick={() => table.setPageIndex(table.getPageCount() - 1)}
      disabled={!table.getCanNextPage()}
    >
      Last
    </Button>
  </div>
</div>
```

---

### 8. Add Row Click Navigation

**Make entire row clickable to view details:**

```typescript
<TableRow
  key={row.id}
  data-state={row.getIsSelected() && 'selected'}
  className="cursor-pointer hover:bg-muted/50"
  onClick={() => navigate(`/loaders/${row.original.loaderCode}`)}
>
  {row.getVisibleCells().map((cell) => (
    <TableCell key={cell.id}>
      {flexRender(cell.column.columnDef.cell, cell.getContext())}
    </TableCell>
  ))}
</TableRow>
```

---

### 9. Add Loading Skeleton

**Replace loading state (lines 98-104):**

```typescript
if (isLoading) {
  return (
    <div className="container mx-auto py-10">
      <div className="rounded-md border">
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Loading...</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {[1, 2, 3, 4, 5].map((i) => (
              <TableRow key={i}>
                <TableCell colSpan={6}>
                  <div className="h-8 bg-muted animate-pulse rounded" />
                </TableCell>
              </TableRow>
            ))}
          </TableBody>
        </Table>
      </div>
    </div>
  );
}
```

---

### 10. Modify API Backend URL

**File:** `src/lib/api-config.ts`

```typescript
// Current (uses environment variable or default)
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

// For local development (direct to backend)
export const API_BASE_URL = 'http://localhost:8080/api';

// For production (proxy via NGINX)
export const API_BASE_URL = '/api';
```

**Set environment variable:**

Create `.env.local` file:
```
VITE_API_BASE_URL=http://localhost:8080/api
```

---

## Quick Reference: File Locations

```
frontend/
├── src/
│   ├── api/
│   │   └── loaders.ts          ← API functions (getLoaders, deleteLoader, etc.)
│   ├── types/
│   │   └── loader.ts           ← TypeScript interfaces
│   ├── lib/
│   │   ├── api-config.ts       ← API base URL and endpoints
│   │   └── axios.ts            ← HTTP client with interceptors
│   ├── pages/
│   │   └── LoadersListPage.tsx ← **MAIN FILE TO CUSTOMIZE**
│   └── components/
│       └── ui/                 ← shadcn/ui components (Table, Button, Badge, etc.)
```

---

## Testing Your Changes

1. **Start dev server:**
   ```bash
   cd /Volumes/Files/Projects/newLoader/frontend
   npm run dev
   ```

2. **Open browser:**
   ```
   http://localhost:5173/loaders
   ```

3. **Check console for errors:**
   - Press F12 → Console tab
   - Look for API errors, TypeScript errors, etc.

4. **Test with backend:**
   - Ensure backend is running at `http://localhost:8080`
   - Or use port-forward: `kubectl port-forward -n monitoring-app svc/signal-loader 8080:8080`

---

## Pro Tips

1. **Use React DevTools** to inspect component state
2. **Enable TypeScript strict mode** to catch errors early
3. **Use TanStack Query DevTools** to debug API calls:
   ```typescript
   import { ReactQueryDevtools } from '@tanstack/react-query-devtools';
   
   // In App.tsx
   <ReactQueryDevtools initialIsOpen={false} />
   ```

4. **Hot reload works automatically** - just save the file!

---

## Need Help?

- **TanStack Table docs:** https://tanstack.com/table/latest
- **shadcn/ui components:** https://ui.shadcn.com/docs/components
- **React Query:** https://tanstack.com/query/latest

