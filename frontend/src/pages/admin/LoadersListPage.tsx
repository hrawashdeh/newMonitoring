import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { useNavigate, useSearchParams } from 'react-router-dom';
import { useState, useMemo } from 'react';
import { loadersApi } from '@/api/loaders';
import type { Loader } from '@/types/loader';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Card } from '@/components/ui/card';
import { Input } from '@/components/ui/input';
import {
  Table,
  TableBody,
  TableCell,
  TableHead,
  TableHeader,
  TableRow,
} from '@/components/ui/table';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import {
  Plus,
  RefreshCw,
  Search,
  Eye,
  Edit,
  CheckCircle,
  XCircle,
  Clock,
  PlayCircle,
  PauseCircle,
  Loader2,
} from 'lucide-react';
import { useToast } from '@/hooks/use-toast';
import logger from '@/lib/logger';

type VersionStatus = 'ALL' | 'ACTIVE' | 'DRAFT' | 'PENDING_APPROVAL' | 'REJECTED';

const statusColors: Record<string, string> = {
  ACTIVE: 'bg-green-100 text-green-800 border-green-300',
  DRAFT: 'bg-gray-100 text-gray-800 border-gray-300',
  PENDING_APPROVAL: 'bg-yellow-100 text-yellow-800 border-yellow-300',
  REJECTED: 'bg-red-100 text-red-800 border-red-300',
};

export default function AdminLoadersListPage() {
  const navigate = useNavigate();
  const { toast } = useToast();
  const queryClient = useQueryClient();
  const [searchParams, setSearchParams] = useSearchParams();

  const [searchTerm, setSearchTerm] = useState('');
  const [statusFilter, setStatusFilter] = useState<VersionStatus>(
    (searchParams.get('status') as VersionStatus) || 'ALL'
  );
  const [enabledFilter, setEnabledFilter] = useState<'ALL' | 'ENABLED' | 'DISABLED'>('ALL');

  const { data, isLoading, error, refetch, isRefetching } = useQuery({
    queryKey: ['loaders'],
    queryFn: loadersApi.getLoaders,
  });

  const loaders = data?.loaders || [];

  // Filter loaders
  const filteredLoaders = useMemo(() => {
    logger.entry('AdminLoadersListPage', 'filterLoaders');
    return loaders.filter((loader) => {
      // Search filter
      if (searchTerm && !loader.loaderCode.toLowerCase().includes(searchTerm.toLowerCase())) {
        return false;
      }

      // Version status filter (using unified version_status)
      if (statusFilter !== 'ALL') {
        const versionStatus = loader.versionStatus || loader.approvalStatus;
        if (versionStatus !== statusFilter) {
          return false;
        }
      }

      // Enabled filter
      if (enabledFilter === 'ENABLED' && !loader.enabled) return false;
      if (enabledFilter === 'DISABLED' && loader.enabled) return false;

      return true;
    });
  }, [loaders, searchTerm, statusFilter, enabledFilter]);

  // Mutations for approve/reject
  const approveMutation = useMutation({
    mutationFn: (loaderCode: string) => loadersApi.approveLoader(loaderCode),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loaders'] });
      toast({ title: 'Success', description: 'Loader approved successfully' });
    },
    onError: (error) => {
      toast({
        title: 'Error',
        description: `Failed to approve: ${error instanceof Error ? error.message : 'Unknown error'}`,
        variant: 'destructive',
      });
    },
  });

  const rejectMutation = useMutation({
    mutationFn: ({ loaderCode, reason }: { loaderCode: string; reason: string }) =>
      loadersApi.rejectLoader(loaderCode, reason),
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loaders'] });
      toast({ title: 'Success', description: 'Loader rejected' });
    },
    onError: (error) => {
      toast({
        title: 'Error',
        description: `Failed to reject: ${error instanceof Error ? error.message : 'Unknown error'}`,
        variant: 'destructive',
      });
    },
  });

  const handleApprove = (loader: Loader) => {
    if (confirm(`Approve loader "${loader.loaderCode}"?`)) {
      approveMutation.mutate(loader.loaderCode);
    }
  };

  const handleReject = (loader: Loader) => {
    const reason = prompt(`Enter rejection reason for "${loader.loaderCode}":`);
    if (reason && reason.trim()) {
      rejectMutation.mutate({ loaderCode: loader.loaderCode, reason: reason.trim() });
    }
  };

  const getStatusBadge = (loader: Loader) => {
    const status = loader.versionStatus || loader.approvalStatus || 'DRAFT';
    return (
      <Badge className={statusColors[status] || statusColors.DRAFT}>
        {status.replace('_', ' ')}
      </Badge>
    );
  };

  if (isLoading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  if (error) {
    return (
      <div className="text-center py-8">
        <p className="text-destructive">Error loading loaders: {error instanceof Error ? error.message : 'Unknown'}</p>
        <Button onClick={() => refetch()} className="mt-4">Retry</Button>
      </div>
    );
  }

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Loaders</h1>
          <p className="text-muted-foreground">Manage ETL loader configurations</p>
        </div>
        <div className="flex items-center gap-2">
          <Button variant="outline" size="sm" onClick={() => refetch()} disabled={isRefetching}>
            <RefreshCw className={`h-4 w-4 mr-2 ${isRefetching ? 'animate-spin' : ''}`} />
            Refresh
          </Button>
          <Button onClick={() => navigate('/admin/loaders/new')}>
            <Plus className="h-4 w-4 mr-2" />
            Create Loader
          </Button>
        </div>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-4 gap-4">
        <Card className="p-4">
          <div className="text-sm text-muted-foreground">Total</div>
          <div className="text-2xl font-bold">{loaders.length}</div>
        </Card>
        <Card className="p-4">
          <div className="text-sm text-muted-foreground">Active</div>
          <div className="text-2xl font-bold text-green-600">
            {loaders.filter(l => (l.versionStatus || l.approvalStatus) === 'ACTIVE' || l.approvalStatus === 'APPROVED').length}
          </div>
        </Card>
        <Card className="p-4">
          <div className="text-sm text-muted-foreground">Pending</div>
          <div className="text-2xl font-bold text-yellow-600">
            {loaders.filter(l => (l.versionStatus || l.approvalStatus) === 'PENDING_APPROVAL').length}
          </div>
        </Card>
        <Card className="p-4">
          <div className="text-sm text-muted-foreground">Enabled</div>
          <div className="text-2xl font-bold text-blue-600">
            {loaders.filter(l => l.enabled).length}
          </div>
        </Card>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by loader code..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-9"
          />
        </div>
        <Select value={statusFilter} onValueChange={(v) => setStatusFilter(v as VersionStatus)}>
          <SelectTrigger className="w-[180px]">
            <SelectValue placeholder="Status" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Status</SelectItem>
            <SelectItem value="ACTIVE">Active</SelectItem>
            <SelectItem value="PENDING_APPROVAL">Pending Approval</SelectItem>
            <SelectItem value="DRAFT">Draft</SelectItem>
            <SelectItem value="REJECTED">Rejected</SelectItem>
          </SelectContent>
        </Select>
        <Select value={enabledFilter} onValueChange={(v) => setEnabledFilter(v as any)}>
          <SelectTrigger className="w-[150px]">
            <SelectValue placeholder="Enabled" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All</SelectItem>
            <SelectItem value="ENABLED">Enabled</SelectItem>
            <SelectItem value="DISABLED">Disabled</SelectItem>
          </SelectContent>
        </Select>
        {(searchTerm || statusFilter !== 'ALL' || enabledFilter !== 'ALL') && (
          <Button
            variant="ghost"
            size="sm"
            onClick={() => {
              setSearchTerm('');
              setStatusFilter('ALL');
              setEnabledFilter('ALL');
            }}
          >
            Clear Filters
          </Button>
        )}
      </div>

      {/* Table */}
      <Card>
        <Table>
          <TableHeader>
            <TableRow>
              <TableHead>Loader Code</TableHead>
              <TableHead>Status</TableHead>
              <TableHead>Enabled</TableHead>
              <TableHead>Created By</TableHead>
              <TableHead>Aggregation</TableHead>
              <TableHead className="text-right">Actions</TableHead>
            </TableRow>
          </TableHeader>
          <TableBody>
            {filteredLoaders.length === 0 ? (
              <TableRow>
                <TableCell colSpan={6} className="text-center py-8 text-muted-foreground">
                  No loaders found
                </TableCell>
              </TableRow>
            ) : (
              filteredLoaders.map((loader) => (
                <TableRow key={loader.loaderCode}>
                  <TableCell className="font-medium">{loader.loaderCode}</TableCell>
                  <TableCell>{getStatusBadge(loader)}</TableCell>
                  <TableCell>
                    {loader.enabled ? (
                      <PlayCircle className="h-5 w-5 text-green-600" />
                    ) : (
                      <PauseCircle className="h-5 w-5 text-gray-400" />
                    )}
                  </TableCell>
                  <TableCell className="text-muted-foreground">{loader.createdBy || '-'}</TableCell>
                  <TableCell className="text-muted-foreground">
                    {loader.aggregationPeriodSeconds ? `${loader.aggregationPeriodSeconds}s` : '-'}
                  </TableCell>
                  <TableCell className="text-right">
                    <div className="flex items-center justify-end gap-1">
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => navigate(`/loaders/${loader.loaderCode}`)}
                        title="View Details"
                      >
                        <Eye className="h-4 w-4" />
                      </Button>
                      <Button
                        variant="ghost"
                        size="icon"
                        onClick={() => navigate(`/loaders/${loader.loaderCode}/edit`)}
                        title="Edit"
                      >
                        <Edit className="h-4 w-4" />
                      </Button>
                      {(loader.versionStatus === 'PENDING_APPROVAL' || loader.approvalStatus === 'PENDING_APPROVAL') && (
                        <>
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => handleApprove(loader)}
                            title="Approve"
                            className="text-green-600 hover:text-green-700"
                          >
                            <CheckCircle className="h-4 w-4" />
                          </Button>
                          <Button
                            variant="ghost"
                            size="icon"
                            onClick={() => handleReject(loader)}
                            title="Reject"
                            className="text-red-600 hover:text-red-700"
                          >
                            <XCircle className="h-4 w-4" />
                          </Button>
                        </>
                      )}
                    </div>
                  </TableCell>
                </TableRow>
              ))
            )}
          </TableBody>
        </Table>
      </Card>

      <div className="text-sm text-muted-foreground text-center">
        Showing {filteredLoaders.length} of {loaders.length} loaders
      </div>
    </div>
  );
}
