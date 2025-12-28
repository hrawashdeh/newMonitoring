import { useQuery } from '@tanstack/react-query';
import { useNavigate } from 'react-router-dom';
import {
  useReactTable,
  getCoreRowModel,
  getFilteredRowModel,
  getPaginationRowModel,
  getSortedRowModel,
  ColumnDef,
  flexRender,
} from '@tanstack/react-table';
import { loadersApi } from '../api/loaders';
import type { Loader } from '../types/loader';
import { Button } from '../components/ui/button';
import { Badge } from '../components/ui/badge';
import { getLoaderStatusVariant, getLoaderStatusText, getZeroRecordRunsVariant } from '../lib/badge-utils';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '../components/ui/table';
import { PageHeader } from '../components/layout/PageHeader';
import {
  ArrowLeft,
  LogOut,
  Plus,
  RefreshCw,
  SlidersHorizontal,
  Download,
  Upload,
  BookOpen,
  HelpCircle,
  Pause,
  Play,
  PlayCircle,
  Edit,
  Eye,
  EyeOff,
  Activity,
  BarChart3,
  Bell,
  MoreHorizontal,
  ChevronDown,
  ChevronRight,
} from 'lucide-react';
import { useToast } from '../hooks/use-toast';
import {
  DropdownMenu,
  DropdownMenuContent,
  DropdownMenuItem,
  DropdownMenuTrigger,
} from '../components/ui/dropdown-menu';
import { useMutation, useQueryClient } from '@tanstack/react-query';
import { LoaderAction, LoaderActionButton } from '../components/loaders/LoaderActionButton';
import { LoaderDetailPanel } from '../components/loaders/LoaderDetailPanel';
import { useState } from 'react';

// Helper function to format seconds into human-readable format
function formatSeconds(seconds: number): string {
  const hours = Math.floor(seconds / 3600);
  const days = Math.floor(hours / 24);
  if (days > 0) return `${days}d`;
  if (hours > 0) return `${hours}h`;
  const minutes = Math.floor(seconds / 60);
  if (minutes > 0) return `${minutes}m`;
  return `${seconds}s`;
}

// Helper function to create actions from loader (checks _links for permissions and protected fields)
function createLoaderActions(
  loader: Loader,
  protectedFields: string[],
  handlers: {
    onToggleEnabled: (loader: Loader) => void;
    onForceStart: (loader: Loader) => void;
    onEdit: (loader: Loader) => void;
    onShowDetails: (loader: Loader) => void;
    onShowSignals: (loader: Loader) => void;
    onShowExecutionLog: (loader: Loader) => void;
    onShowAlerts: (loader: Loader) => void;
  }
): LoaderAction[] {
  const enabled = loader.enabled;
  const isEnabledFieldProtected = protectedFields.includes('enabled');

  return [
    {
      id: 'toggleEnabled',
      icon: enabled ? Pause : Play,
      label: enabled ? 'Pause Loader' : 'Resume Loader',
      onClick: () => handlers.onToggleEnabled(loader),
      enabled: !!loader._links?.toggleEnabled && !isEnabledFieldProtected,
      iconColor: enabled ? 'text-orange-600' : 'text-green-600',
      disabledReason: isEnabledFieldProtected
        ? 'Action disabled due to data protection (enabled status is hidden)'
        : !loader._links?.toggleEnabled
        ? 'Insufficient permissions'
        : undefined,
    },
    {
      id: 'forceStart',
      icon: PlayCircle,
      label: 'Force Start',
      onClick: () => handlers.onForceStart(loader),
      enabled: !!loader._links?.forceStart && !isEnabledFieldProtected,
      iconColor: 'text-blue-600',
      disabledReason: isEnabledFieldProtected
        ? 'Action disabled due to data protection (enabled status is hidden)'
        : !loader._links?.forceStart
        ? 'Insufficient permissions'
        : undefined,
    },
    {
      id: 'edit',
      icon: Edit,
      label: 'Edit Loader',
      onClick: () => handlers.onEdit(loader),
      enabled: !!loader._links?.editLoader,
      disabledReason: !loader._links?.editLoader ? 'Insufficient permissions' : undefined,
    },
    {
      id: 'viewDetails',
      icon: Eye,
      label: 'Show Details',
      onClick: () => handlers.onShowDetails(loader),
      enabled: !!loader._links?.viewDetails,
      disabledReason: !loader._links?.viewDetails ? 'Insufficient permissions' : undefined,
    },
    {
      id: 'viewSignals',
      icon: BarChart3,
      label: 'Show Signals',
      onClick: () => handlers.onShowSignals(loader),
      enabled: !!loader._links?.viewSignals,
      disabledReason: !loader._links?.viewSignals ? 'Insufficient permissions' : undefined,
    },
    {
      id: 'viewExecutionLog',
      icon: Activity,
      label: 'Show Execution Log',
      onClick: () => handlers.onShowExecutionLog(loader),
      enabled: !!loader._links?.viewExecutionLog,
      disabledReason: !loader._links?.viewExecutionLog ? 'Insufficient permissions' : undefined,
    },
    {
      id: 'viewAlerts',
      icon: Bell,
      label: 'Show Alerts',
      onClick: () => handlers.onShowAlerts(loader),
      enabled: !!loader._links?.viewAlerts,
      disabledReason: !loader._links?.viewAlerts ? 'Insufficient permissions' : undefined,
    },
  ];
}

const getColumns = (
  expandedRows: Set<string>,
  toggleRow: (loaderCode: string) => void,
  protectedFields: string[],
  handlers: {
    onToggleEnabled: (loader: Loader) => void;
    onForceStart: (loader: Loader) => void;
    onEdit: (loader: Loader) => void;
    onShowDetails: (loader: Loader) => void;
    onShowSignals: (loader: Loader) => void;
    onShowExecutionLog: (loader: Loader) => void;
    onShowAlerts: (loader: Loader) => void;
  }
): ColumnDef<Loader>[] => [
  {
    id: 'expand',
    header: '',
    cell: ({ row }) => {
      const isExpanded = expandedRows.has(row.original.loaderCode);
      return (
        <Button
          variant="ghost"
          size="icon"
          className="h-6 w-6"
          onClick={(e) => {
            e.stopPropagation();
            toggleRow(row.original.loaderCode);
          }}
        >
          {isExpanded ? (
            <ChevronDown className="h-4 w-4" />
          ) : (
            <ChevronRight className="h-4 w-4" />
          )}
        </Button>
      );
    },
  },
  {
    accessorKey: 'loaderCode',
    header: 'Loader Code',
    cell: ({ row }) => (
      <span className="font-medium">{row.getValue('loaderCode')}</span>
    ),
  },
  {
    accessorKey: 'enabled',
    header: 'Status',
    cell: ({ row }) => {
      const loader = row.original;
      const enabled = row.getValue('enabled') as boolean;
      const isProtected = protectedFields.includes('enabled');

      // Debug: Check if field is actually missing from loader object
      const fieldExists = 'enabled' in loader;

      // If field is protected OR doesn't exist in the loader object, show protection indicator
      if (isProtected || !fieldExists) {
        return (
          <span className="text-xs text-muted-foreground italic flex items-center gap-1">
            <EyeOff className="h-3 w-3 text-amber-600" />
            Hidden
          </span>
        );
      }

      return (
        <Badge variant={getLoaderStatusVariant(enabled)}>
          {getLoaderStatusText(enabled)}
        </Badge>
      );
    },
  },
  {
    accessorKey: 'sourceTimezoneOffsetHours',
    header: 'Time Zone',
    cell: ({ row }) => {
      const hours = row.getValue('sourceTimezoneOffsetHours') as number | undefined;
      const isProtected = protectedFields.includes('sourceTimezoneOffsetHours');

      if (isProtected) {
        return (
          <span className="text-xs text-muted-foreground italic flex items-center gap-1">
            <EyeOff className="h-3 w-3 text-amber-600" />
            Hidden
          </span>
        );
      }

      const formatTz = (h?: number) => {
        if (h === undefined || h === null) return 'UTC+00:00';
        const sign = h >= 0 ? '+' : '-';
        return `UTC${sign}${Math.abs(h).toString().padStart(2, '0')}:00`;
      };
      return <span className="text-muted-foreground">{formatTz(hours)}</span>;
    },
  },
  {
    accessorKey: 'consecutiveZeroRecordRuns',
    header: 'Zero Record Runs',
    cell: ({ row }) => {
      const count = row.getValue('consecutiveZeroRecordRuns') as number | undefined;
      const isProtected = protectedFields.includes('consecutiveZeroRecordRuns');

      if (isProtected) {
        return (
          <span className="text-xs text-muted-foreground italic flex items-center gap-1">
            <EyeOff className="h-3 w-3 text-amber-600" />
            Hidden
          </span>
        );
      }

      if (!count || count === 0) {
        return <span className="text-muted-foreground">-</span>;
      }
      return <Badge variant={getZeroRecordRunsVariant(count)}>{count}</Badge>;
    },
  },
  {
    accessorKey: 'aggregationPeriodSeconds',
    header: 'Aggregation Period',
    cell: ({ row }) => {
      const seconds = row.getValue('aggregationPeriodSeconds') as number | undefined;
      const isProtected = protectedFields.includes('aggregationPeriodSeconds');

      if (isProtected) {
        return (
          <span className="text-xs text-muted-foreground italic flex items-center gap-1">
            <EyeOff className="h-3 w-3 text-amber-600" />
            Hidden
          </span>
        );
      }

      if (!seconds) {
        return <span className="text-muted-foreground">-</span>;
      }
      const formatted = formatSeconds(seconds);
      return (
        <span
          className="font-medium"
          title={`Data aggregated in ${formatted} intervals based on load_time_stamp`}
        >
          {formatted}
        </span>
      );
    },
  },
  {
    id: 'actions',
    header: 'Actions',
    cell: ({ row }) => {
      const loader = row.original;
      const actions = createLoaderActions(loader, protectedFields, handlers);

      // First 3 actions shown as icons
      const primaryActions = actions.slice(0, 3);
      // Remaining actions in dropdown
      const secondaryActions = actions.slice(3);

      return (
        <div className="flex items-center gap-1">
          {/* Primary actions: Icon only */}
          {primaryActions.map((action) => (
            <LoaderActionButton
              key={action.id}
              action={action}
              showLabel={false}
            />
          ))}

          {/* Secondary actions: Dropdown menu */}
          <DropdownMenu>
            <DropdownMenuTrigger asChild>
              <Button
                variant="ghost"
                size="icon"
                className="h-8 w-8"
                onClick={(e) => e.stopPropagation()}
              >
                <MoreHorizontal className="h-4 w-4" />
              </Button>
            </DropdownMenuTrigger>
            <DropdownMenuContent align="end" className="w-48">
              {secondaryActions.map((action) => {
                const Icon = action.icon;
                return (
                  <DropdownMenuItem
                    key={action.id}
                    onClick={(e) => {
                      e.stopPropagation();
                      action.onClick();
                    }}
                    disabled={!action.enabled}
                  >
                    <Icon className={`mr-2 h-4 w-4 ${action.iconColor || ''}`} />
                    <span>{action.label}</span>
                  </DropdownMenuItem>
                );
              })}
            </DropdownMenuContent>
          </DropdownMenu>
        </div>
      );
    },
  },
];

export default function LoadersListPage() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const username = localStorage.getItem('auth_username') || 'User';
  const [expandedRows, setExpandedRows] = useState<Set<string>>(new Set());

  const { data, isLoading, error, refetch, isRefetching } = useQuery({
    queryKey: ['loaders'],
    queryFn: loadersApi.getLoaders,
  });

  const loaders = data?.loaders || [];
  const protectedFields = data?.protectedFields || [];

  const toggleRow = (loaderCode: string) => {
    setExpandedRows((prev) => {
      const next = new Set(prev);
      if (next.has(loaderCode)) {
        next.delete(loaderCode);
      } else {
        next.add(loaderCode);
      }
      return next;
    });
  };

  // Mutation for toggling loader enabled status
  const toggleEnabledMutation = useMutation({
    mutationFn: async (loader: Loader) => {
      const updated = { ...loader, enabled: !loader.enabled };
      return loadersApi.updateLoader(loader.loaderCode, updated);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loaders'] });
      toast({
        title: 'Loader updated',
        description: 'Loader status changed successfully',
      });
    },
    onError: (error) => {
      toast({
        title: 'Error',
        description: `Failed to update loader: ${error instanceof Error ? error.message : 'Unknown error'}`,
        variant: 'destructive',
      });
    },
  });

  // Action handlers
  const actionHandlers = {
    onToggleEnabled: (loader: Loader) => {
      toggleEnabledMutation.mutate(loader);
    },
    onForceStart: (loader: Loader) => {
      toast({
        title: 'Force Start',
        description: `Forcing execution of ${loader.loaderCode}...`,
      });
      // TODO: Implement force start API call
    },
    onEdit: (loader: Loader) => {
      navigate(`/loaders/${loader.loaderCode}/edit`);
    },
    onShowDetails: (loader: Loader) => {
      navigate(`/loaders/${loader.loaderCode}`);
    },
    onShowSignals: (loader: Loader) => {
      toast({
        title: 'Coming soon',
        description: `Signals view for ${loader.loaderCode} will be available soon`,
      });
      // TODO: Navigate to signals page
    },
    onShowExecutionLog: (loader: Loader) => {
      toast({
        title: 'Coming soon',
        description: `Execution log for ${loader.loaderCode} will be available soon`,
      });
      // TODO: Navigate to execution log page
    },
    onShowAlerts: (loader: Loader) => {
      toast({
        title: 'Coming soon',
        description: `Alerts for ${loader.loaderCode} will be available soon`,
      });
      // TODO: Navigate to alerts page
    },
  };

  const columns = getColumns(expandedRows, toggleRow, protectedFields, actionHandlers);

  const table = useReactTable({
    data: loaders,
    columns,
    getCoreRowModel: getCoreRowModel(),
    getFilteredRowModel: getFilteredRowModel(),
    getPaginationRowModel: getPaginationRowModel(),
    getSortedRowModel: getSortedRowModel(),
  });

  const handleLogout = () => {
    localStorage.removeItem('auth_token');
    localStorage.removeItem('auth_username');
    localStorage.removeItem('auth_roles');
    window.location.href = '/login';
  };

  const handleExport = () => {
    // Generate CSV
    const headers = [
      'Loader Code',
      'Status',
      'Min Interval (s)',
      'Max Interval (s)',
      'Max Parallel',
      'Query Period (s)',
    ];

    const rows = loaders.map((loader) => [
      loader.loaderCode,
      loader.enabled ? 'ENABLED' : 'DISABLED',
      loader.minIntervalSeconds.toString(),
      loader.maxIntervalSeconds.toString(),
      loader.maxParallelExecutions.toString(),
      loader.maxQueryPeriodSeconds.toString(),
    ]);

    const csvContent = [headers, ...rows]
      .map((row) => row.join(','))
      .join('\n');

    // Download file
    const blob = new Blob([csvContent], { type: 'text/csv' });
    const url = URL.createObjectURL(blob);
    const link = document.createElement('a');
    link.href = url;
    link.download = `loaders-${new Date().toISOString().split('T')[0]}.csv`;
    link.click();
    URL.revokeObjectURL(url);

    toast({
      title: 'Export successful',
      description: `Exported ${loaders.length} loader(s) to CSV`,
    });
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-lg text-muted-foreground">Loading loaders...</div>
      </div>
    );
  }

  if (error) {
    return (
      <div className="flex items-center justify-center h-screen">
        <div className="text-lg text-destructive">
          Error loading loaders: {error instanceof Error ? error.message : 'Unknown error'}
        </div>
      </div>
    );
  }

  return (
    <div className="min-h-screen bg-background">
      {/* Header */}
      <header className="border-b bg-card">
        <div className="container mx-auto px-6 py-4">
          <div className="flex items-center justify-between">
            <div className="flex items-center gap-4">
              <Button
                variant="ghost"
                size="sm"
                onClick={() => navigate('/')}
                className="gap-2"
              >
                <ArrowLeft className="h-4 w-4" />
                Back to Home
              </Button>
              <div className="h-6 w-px bg-border" />
              <div>
                <h1 className="text-xl font-bold text-foreground">
                  Loaders Management
                </h1>
              </div>
            </div>
            <div className="flex items-center gap-4">
              <p className="text-sm font-medium text-foreground">{username}</p>
              <Button
                variant="outline"
                size="sm"
                onClick={handleLogout}
                className="gap-2"
              >
                <LogOut className="h-4 w-4" />
                Logout
              </Button>
            </div>
          </div>
        </div>
      </header>

      {/* Main Content */}
      <div className="container mx-auto px-6 py-10">
        <PageHeader
          title="Active Loaders"
          subtitle="Manage and monitor data loader configurations"
          primaryActions={[
            {
              icon: Plus,
              label: 'Create New Loader',
              onClick: () => navigate('/loaders/new'),
              variant: 'default',
            },
            {
              icon: RefreshCw,
              label: 'Refresh List',
              onClick: () => refetch(),
              loading: isRefetching,
            },
            {
              icon: SlidersHorizontal,
              label: 'Filter & Sort',
              onClick: () =>
                toast({
                  title: 'Coming soon',
                  description: 'Advanced filtering will be available in a future release',
                }),
            },
          ]}
          secondaryActions={[
            {
              icon: Download,
              label: 'Export to CSV',
              onClick: handleExport,
            },
            {
              icon: Upload,
              label: 'Import from File',
              onClick: () =>
                toast({
                  title: 'Coming soon',
                  description: 'Import functionality will be available in a future release',
                }),
              disabled: true,
            },
            { divider: true },
            {
              icon: BookOpen,
              label: 'Documentation',
              onClick: () => window.open('/docs/loaders', '_blank'),
            },
            {
              icon: HelpCircle,
              label: 'Help',
              onClick: () =>
                toast({
                  title: 'Coming soon',
                  description: 'Interactive help tour will be available soon',
                }),
              disabled: true,
            },
          ]}
        />

      <div className="rounded-md border">
        <Table>
          <TableHeader>
            {table.getHeaderGroups().map((headerGroup) => (
              <TableRow key={headerGroup.id}>
                {headerGroup.headers.map((header) => (
                  <TableHead key={header.id}>
                    {header.isPlaceholder
                      ? null
                      : flexRender(
                          header.column.columnDef.header,
                          header.getContext()
                        )}
                  </TableHead>
                ))}
              </TableRow>
            ))}
          </TableHeader>
          <TableBody>
            {table.getRowModel().rows?.length ? (
              table.getRowModel().rows.map((row) => {
                const isExpanded = expandedRows.has(row.original.loaderCode);
                return (
                  <>
                    {/* Main row */}
                    <TableRow
                      key={row.id}
                      data-state={row.getIsSelected() && 'selected'}
                      className="hover:bg-muted/50 cursor-pointer"
                      onClick={() => toggleRow(row.original.loaderCode)}
                    >
                      {row.getVisibleCells().map((cell) => (
                        <TableCell key={cell.id}>
                          {flexRender(
                            cell.column.columnDef.cell,
                            cell.getContext()
                          )}
                        </TableCell>
                      ))}
                    </TableRow>

                    {/* Expanded detail panel */}
                    {isExpanded && (
                      <TableRow key={`${row.id}-detail`}>
                        <TableCell colSpan={columns.length} className="p-0">
                          <div className="p-4 bg-muted/20">
                            <LoaderDetailPanel
                              loader={row.original}
                              actions={createLoaderActions(row.original, protectedFields, actionHandlers)}
                              protectedFields={protectedFields}
                            />
                          </div>
                        </TableCell>
                      </TableRow>
                    )}
                  </>
                );
              })
            ) : (
              <TableRow>
                <TableCell
                  colSpan={columns.length}
                  className="h-24 text-center"
                >
                  No loaders found.
                </TableCell>
              </TableRow>
            )}
          </TableBody>
        </Table>
      </div>

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
      </div>
    </div>
  );
}
