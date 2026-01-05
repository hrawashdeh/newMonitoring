import { useState, useEffect } from 'react';
import { useSearchParams } from 'react-router-dom';
import {
  CheckCircle,
  XCircle,
  Clock,
  FileText,
  AlertCircle,
  ChevronDown,
  ChevronRight,
  RotateCcw,
  RefreshCw,
  Search,
  Loader2,
  Filter,
} from 'lucide-react';
import {
  getAllPendingApprovals,
  getAllApprovedApprovals,
  approveRequest,
  rejectRequest,
  revokeApproval,
} from '@/api/approvals';
import type { ApprovalRequest, EntityType } from '@/types/approval';
import { Card } from '@/components/ui/card';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Badge } from '@/components/ui/badge';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '@/components/ui/dialog';
import {
  Select,
  SelectContent,
  SelectItem,
  SelectTrigger,
  SelectValue,
} from '@/components/ui/select';
import { Textarea } from '@/components/ui/textarea';
import { Label } from '@/components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '@/components/ui/tabs';
import { useToast } from '@/hooks/use-toast';
import logger from '@/lib/logger';

type TabType = 'pending' | 'approved' | 'rejected';

const entityTypeColors: Record<EntityType, string> = {
  LOADER: 'bg-blue-100 text-blue-800 border-blue-300',
  DASHBOARD: 'bg-purple-100 text-purple-800 border-purple-300',
  INCIDENT: 'bg-red-100 text-red-800 border-red-300',
  CHART: 'bg-green-100 text-green-800 border-green-300',
  ALERT_RULE: 'bg-yellow-100 text-yellow-800 border-yellow-300',
};

const requestTypeIcons: Record<string, typeof FileText> = {
  CREATE: FileText,
  UPDATE: AlertCircle,
  DELETE: XCircle,
};

export default function AdminPendingApprovalsPage() {
  const [searchParams] = useSearchParams();
  const initialTab = searchParams.get('tab') === 'history' ? 'approved' : 'pending';

  const [activeTab, setActiveTab] = useState<TabType>(initialTab as TabType);
  const [pendingApprovals, setPendingApprovals] = useState<ApprovalRequest[]>([]);
  const [approvedApprovals, setApprovedApprovals] = useState<ApprovalRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [refreshing, setRefreshing] = useState(false);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());
  const [searchTerm, setSearchTerm] = useState('');
  const [entityTypeFilter, setEntityTypeFilter] = useState<string>('ALL');

  // Dialog states
  const [approveDialog, setApproveDialog] = useState<{
    open: boolean;
    request?: ApprovalRequest;
    justification: string;
    submitting: boolean;
  }>({ open: false, justification: '', submitting: false });

  const [rejectDialog, setRejectDialog] = useState<{
    open: boolean;
    request?: ApprovalRequest;
    reason: string;
    submitting: boolean;
  }>({ open: false, reason: '', submitting: false });

  const [revokeDialog, setRevokeDialog] = useState<{
    open: boolean;
    request?: ApprovalRequest;
    reason: string;
    submitting: boolean;
  }>({ open: false, reason: '', submitting: false });

  const { toast } = useToast();

  useEffect(() => {
    loadApprovals();
  }, []);

  const loadApprovals = async (showRefreshing = false) => {
    logger.entry('AdminPendingApprovalsPage', 'loadApprovals');
    try {
      if (showRefreshing) setRefreshing(true);
      else setLoading(true);

      const [pending, approved] = await Promise.all([
        getAllPendingApprovals(),
        getAllApprovedApprovals(),
      ]);

      setPendingApprovals(pending);
      setApprovedApprovals(approved);
      logger.result('AdminPendingApprovalsPage', 'loadApprovals', `Loaded ${pending.length} pending, ${approved.length} approved`);
    } catch (error: any) {
      logger.error('AdminPendingApprovalsPage', 'loadApprovals', error);
      toast({
        title: 'Error',
        description: error.message || 'Failed to load approvals',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
      setRefreshing(false);
    }
  };

  const toggleExpanded = (id: number) => {
    const newExpanded = new Set(expandedIds);
    if (newExpanded.has(id)) {
      newExpanded.delete(id);
    } else {
      newExpanded.add(id);
    }
    setExpandedIds(newExpanded);
  };

  const handleApprove = async () => {
    if (!approveDialog.request) return;

    try {
      setApproveDialog({ ...approveDialog, submitting: true });
      await approveRequest({
        requestId: approveDialog.request.id,
        justification: approveDialog.justification || undefined,
      });

      toast({
        title: 'Approved',
        description: `${approveDialog.request.entityType} ${approveDialog.request.entityId} has been approved`,
      });

      setApproveDialog({ open: false, justification: '', submitting: false });
      loadApprovals(true);
    } catch (error: any) {
      toast({
        title: 'Error',
        description: error.message || 'Failed to approve request',
        variant: 'destructive',
      });
      setApproveDialog({ ...approveDialog, submitting: false });
    }
  };

  const handleReject = async () => {
    if (!rejectDialog.request || !rejectDialog.reason.trim()) {
      toast({
        title: 'Validation Error',
        description: 'Rejection reason is required',
        variant: 'destructive',
      });
      return;
    }

    try {
      setRejectDialog({ ...rejectDialog, submitting: true });
      await rejectRequest({
        requestId: rejectDialog.request.id,
        rejectionReason: rejectDialog.reason,
      });

      toast({
        title: 'Rejected',
        description: `${rejectDialog.request.entityType} ${rejectDialog.request.entityId} has been rejected`,
      });

      setRejectDialog({ open: false, reason: '', submitting: false });
      loadApprovals(true);
    } catch (error: any) {
      toast({
        title: 'Error',
        description: error.message || 'Failed to reject request',
        variant: 'destructive',
      });
      setRejectDialog({ ...rejectDialog, submitting: false });
    }
  };

  const handleRevoke = async () => {
    if (!revokeDialog.request || !revokeDialog.reason.trim()) {
      toast({
        title: 'Validation Error',
        description: 'Revocation reason is required',
        variant: 'destructive',
      });
      return;
    }

    try {
      setRevokeDialog({ ...revokeDialog, submitting: true });
      await revokeApproval({
        requestId: revokeDialog.request.id,
        revocationReason: revokeDialog.reason,
      });

      toast({
        title: 'Revoked',
        description: `${revokeDialog.request.entityType} ${revokeDialog.request.entityId} approval has been revoked`,
      });

      setRevokeDialog({ open: false, reason: '', submitting: false });
      loadApprovals(true);
    } catch (error: any) {
      toast({
        title: 'Error',
        description: error.message || 'Failed to revoke approval',
        variant: 'destructive',
      });
      setRevokeDialog({ ...revokeDialog, submitting: false });
    }
  };

  const filterApprovals = (approvals: ApprovalRequest[]) => {
    return approvals.filter((a) => {
      if (searchTerm && !a.entityId.toLowerCase().includes(searchTerm.toLowerCase())) {
        return false;
      }
      if (entityTypeFilter !== 'ALL' && a.entityType !== entityTypeFilter) {
        return false;
      }
      return true;
    });
  };

  const formatDate = (dateString: string): string => {
    return new Date(dateString).toLocaleString();
  };

  const renderApprovalCard = (approval: ApprovalRequest, isPending: boolean) => {
    const isExpanded = expandedIds.has(approval.id);
    const RequestTypeIcon = requestTypeIcons[approval.requestType] || FileText;

    return (
      <Card key={approval.id} className="p-4">
        <div className="flex items-start gap-4">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => toggleExpanded(approval.id)}
            className="p-0 h-6 w-6 shrink-0"
          >
            {isExpanded ? <ChevronDown className="w-4 h-4" /> : <ChevronRight className="w-4 h-4" />}
          </Button>

          <div className="flex-1 min-w-0">
            <div className="flex items-center gap-2 flex-wrap mb-2">
              <Badge className={entityTypeColors[approval.entityType] || 'bg-gray-100'}>
                {approval.entityType}
              </Badge>
              <Badge variant="outline" className="flex items-center gap-1">
                <RequestTypeIcon className="w-3 h-3" />
                {approval.requestType}
              </Badge>
              {approval.source && (
                <Badge variant="secondary">{approval.source}</Badge>
              )}
              {approval.importLabel && (
                <Badge variant="outline" className="bg-purple-50">{approval.importLabel}</Badge>
              )}
            </div>

            <h3 className="text-lg font-semibold truncate">{approval.entityId}</h3>

            {approval.changeSummary && (
              <p className="text-sm text-muted-foreground mt-1 line-clamp-2">{approval.changeSummary}</p>
            )}

            <div className="flex items-center gap-4 text-xs text-muted-foreground mt-2">
              <span>By <strong>{approval.requestedBy}</strong></span>
              <span>{formatDate(approval.requestedAt)}</span>
            </div>

            {!isPending && approval.approvedBy && (
              <div className="flex items-center gap-2 text-xs text-green-600 mt-1">
                <CheckCircle className="w-3 h-3" />
                <span>Approved by <strong>{approval.approvedBy}</strong></span>
                {approval.approvedAt && <span>on {formatDate(approval.approvedAt)}</span>}
              </div>
            )}
          </div>

          <div className="flex items-center gap-2 shrink-0">
            {isPending ? (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setApproveDialog({ open: true, request: approval, justification: '', submitting: false })}
                  className="text-green-600 hover:text-green-700 hover:bg-green-50"
                >
                  <CheckCircle className="w-4 h-4 mr-1" />
                  Approve
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() => setRejectDialog({ open: true, request: approval, reason: '', submitting: false })}
                  className="text-red-600 hover:text-red-700 hover:bg-red-50"
                >
                  <XCircle className="w-4 h-4 mr-1" />
                  Reject
                </Button>
              </>
            ) : (
              <Button
                variant="outline"
                size="sm"
                onClick={() => setRevokeDialog({ open: true, request: approval, reason: '', submitting: false })}
                className="text-orange-600 hover:text-orange-700 hover:bg-orange-50"
              >
                <RotateCcw className="w-4 h-4 mr-1" />
                Revoke
              </Button>
            )}
          </div>
        </div>

        {isExpanded && (
          <div className="mt-4 pt-4 border-t">
            <h4 className="text-sm font-semibold mb-2">Proposed Changes:</h4>
            <pre className="bg-muted p-3 rounded text-xs overflow-auto max-h-64">
              {JSON.stringify(approval.requestData, null, 2)}
            </pre>

            {approval.currentData && (
              <>
                <h4 className="text-sm font-semibold mt-4 mb-2">Current State:</h4>
                <pre className="bg-muted p-3 rounded text-xs overflow-auto max-h-64">
                  {JSON.stringify(approval.currentData, null, 2)}
                </pre>
              </>
            )}
          </div>
        )}
      </Card>
    );
  };

  if (loading) {
    return (
      <div className="flex items-center justify-center h-64">
        <Loader2 className="h-8 w-8 animate-spin text-muted-foreground" />
      </div>
    );
  }

  const filteredPending = filterApprovals(pendingApprovals);
  const filteredApproved = filterApprovals(approvedApprovals);

  return (
    <div className="space-y-6">
      {/* Header */}
      <div className="flex items-center justify-between">
        <div>
          <h1 className="text-2xl font-bold">Approval Management</h1>
          <p className="text-muted-foreground">Review and manage approval requests</p>
        </div>
        <Button variant="outline" onClick={() => loadApprovals(true)} disabled={refreshing}>
          <RefreshCw className={`h-4 w-4 mr-2 ${refreshing ? 'animate-spin' : ''}`} />
          Refresh
        </Button>
      </div>

      {/* Stats */}
      <div className="grid grid-cols-4 gap-4">
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Pending</p>
              <p className="text-2xl font-bold">{pendingApprovals.length}</p>
            </div>
            <Clock className="w-8 h-8 text-orange-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Approved</p>
              <p className="text-2xl font-bold">{approvedApprovals.length}</p>
            </div>
            <CheckCircle className="w-8 h-8 text-green-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">Loaders</p>
              <p className="text-2xl font-bold">
                {[...pendingApprovals, ...approvedApprovals].filter((a) => a.entityType === 'LOADER').length}
              </p>
            </div>
            <FileText className="w-8 h-8 text-blue-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-muted-foreground">From Imports</p>
              <p className="text-2xl font-bold">
                {[...pendingApprovals, ...approvedApprovals].filter((a) => a.source === 'IMPORT').length}
              </p>
            </div>
            <AlertCircle className="w-8 h-8 text-purple-500" />
          </div>
        </Card>
      </div>

      {/* Filters */}
      <div className="flex items-center gap-4">
        <div className="relative flex-1 max-w-sm">
          <Search className="absolute left-3 top-1/2 -translate-y-1/2 h-4 w-4 text-muted-foreground" />
          <Input
            placeholder="Search by entity ID..."
            value={searchTerm}
            onChange={(e) => setSearchTerm(e.target.value)}
            className="pl-9"
          />
        </div>
        <Select value={entityTypeFilter} onValueChange={setEntityTypeFilter}>
          <SelectTrigger className="w-[150px]">
            <Filter className="h-4 w-4 mr-2" />
            <SelectValue placeholder="Entity Type" />
          </SelectTrigger>
          <SelectContent>
            <SelectItem value="ALL">All Types</SelectItem>
            <SelectItem value="LOADER">Loaders</SelectItem>
            <SelectItem value="DASHBOARD">Dashboards</SelectItem>
            <SelectItem value="CHART">Charts</SelectItem>
            <SelectItem value="ALERT_RULE">Alert Rules</SelectItem>
          </SelectContent>
        </Select>
        {(searchTerm || entityTypeFilter !== 'ALL') && (
          <Button variant="ghost" size="sm" onClick={() => { setSearchTerm(''); setEntityTypeFilter('ALL'); }}>
            Clear
          </Button>
        )}
      </div>

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={(v) => setActiveTab(v as TabType)}>
        <TabsList>
          <TabsTrigger value="pending">
            <Clock className="w-4 h-4 mr-2" />
            Pending ({filteredPending.length})
          </TabsTrigger>
          <TabsTrigger value="approved">
            <CheckCircle className="w-4 h-4 mr-2" />
            Approved ({filteredApproved.length})
          </TabsTrigger>
        </TabsList>

        <TabsContent value="pending" className="space-y-4 mt-4">
          {filteredPending.length === 0 ? (
            <Card className="p-8 text-center">
              <CheckCircle className="w-12 h-12 text-green-500 mx-auto mb-4" />
              <h3 className="text-lg font-semibold">No Pending Approvals</h3>
              <p className="text-muted-foreground">All approval requests have been processed</p>
            </Card>
          ) : (
            filteredPending.map((approval) => renderApprovalCard(approval, true))
          )}
        </TabsContent>

        <TabsContent value="approved" className="space-y-4 mt-4">
          {filteredApproved.length === 0 ? (
            <Card className="p-8 text-center">
              <Clock className="w-12 h-12 text-gray-400 mx-auto mb-4" />
              <h3 className="text-lg font-semibold">No Approved Items</h3>
              <p className="text-muted-foreground">No approvals have been approved yet</p>
            </Card>
          ) : (
            filteredApproved.map((approval) => renderApprovalCard(approval, false))
          )}
        </TabsContent>
      </Tabs>

      {/* Approve Dialog */}
      <Dialog open={approveDialog.open} onOpenChange={(open) => !approveDialog.submitting && setApproveDialog({ ...approveDialog, open })}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Approve Request</DialogTitle>
            <DialogDescription>
              Approve {approveDialog.request?.entityType} <strong>{approveDialog.request?.entityId}</strong>?
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Label htmlFor="justification">Justification (Optional)</Label>
            <Textarea
              id="justification"
              value={approveDialog.justification}
              onChange={(e) => setApproveDialog({ ...approveDialog, justification: e.target.value })}
              placeholder="Why are you approving this request?"
              className="mt-2"
              rows={3}
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setApproveDialog({ ...approveDialog, open: false })} disabled={approveDialog.submitting}>
              Cancel
            </Button>
            <Button onClick={handleApprove} disabled={approveDialog.submitting} className="bg-green-600 hover:bg-green-700">
              {approveDialog.submitting ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <CheckCircle className="h-4 w-4 mr-2" />}
              Approve
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Reject Dialog */}
      <Dialog open={rejectDialog.open} onOpenChange={(open) => !rejectDialog.submitting && setRejectDialog({ ...rejectDialog, open })}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Reject Request</DialogTitle>
            <DialogDescription>
              Reject {rejectDialog.request?.entityType} <strong>{rejectDialog.request?.entityId}</strong>?
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Label htmlFor="reason" className="text-red-600">Rejection Reason (Required) *</Label>
            <Textarea
              id="reason"
              value={rejectDialog.reason}
              onChange={(e) => setRejectDialog({ ...rejectDialog, reason: e.target.value })}
              placeholder="Explain why this request is being rejected..."
              className="mt-2"
              rows={3}
              required
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRejectDialog({ ...rejectDialog, open: false })} disabled={rejectDialog.submitting}>
              Cancel
            </Button>
            <Button onClick={handleReject} disabled={rejectDialog.submitting || !rejectDialog.reason.trim()} className="bg-red-600 hover:bg-red-700">
              {rejectDialog.submitting ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <XCircle className="h-4 w-4 mr-2" />}
              Reject
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>

      {/* Revoke Dialog */}
      <Dialog open={revokeDialog.open} onOpenChange={(open) => !revokeDialog.submitting && setRevokeDialog({ ...revokeDialog, open })}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Revoke Approval</DialogTitle>
            <DialogDescription>
              Revoke approval for {revokeDialog.request?.entityType} <strong>{revokeDialog.request?.entityId}</strong>?
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Label htmlFor="revoke-reason" className="text-orange-600">Revocation Reason (Required) *</Label>
            <Textarea
              id="revoke-reason"
              value={revokeDialog.reason}
              onChange={(e) => setRevokeDialog({ ...revokeDialog, reason: e.target.value })}
              placeholder="Explain why this approval is being revoked..."
              className="mt-2"
              rows={3}
              required
            />
          </div>
          <DialogFooter>
            <Button variant="outline" onClick={() => setRevokeDialog({ ...revokeDialog, open: false })} disabled={revokeDialog.submitting}>
              Cancel
            </Button>
            <Button onClick={handleRevoke} disabled={revokeDialog.submitting || !revokeDialog.reason.trim()} className="bg-orange-600 hover:bg-orange-700">
              {revokeDialog.submitting ? <Loader2 className="h-4 w-4 animate-spin mr-2" /> : <RotateCcw className="h-4 w-4 mr-2" />}
              Revoke
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
