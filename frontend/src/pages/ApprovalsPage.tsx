import { useState, useEffect } from 'react';
import {
  CheckCircle,
  XCircle,
  Clock,
  FileText,
  AlertCircle,
  ChevronDown,
  ChevronRight,
  RotateCcw,
} from 'lucide-react';
import {
  getAllPendingApprovals,
  getAllApprovedApprovals,
  approveRequest,
  rejectRequest,
  revokeApproval,
} from '../api/approvals';
import type { ApprovalRequest, EntityType } from '../types/approval';
import { Card } from '../components/ui/card';
import { Button } from '../components/ui/button';
import {
  Dialog,
  DialogContent,
  DialogDescription,
  DialogFooter,
  DialogHeader,
  DialogTitle,
} from '../components/ui/dialog';
import { Textarea } from '../components/ui/textarea';
import { Label } from '../components/ui/label';
import { Tabs, TabsContent, TabsList, TabsTrigger } from '../components/ui/tabs';
import { useToast } from '../hooks/use-toast';

type TabType = 'pending' | 'approved';

export default function ApprovalsPage() {
  const [activeTab, setActiveTab] = useState<TabType>('pending');
  const [pendingApprovals, setPendingApprovals] = useState<ApprovalRequest[]>([]);
  const [approvedApprovals, setApprovedApprovals] = useState<ApprovalRequest[]>([]);
  const [loading, setLoading] = useState(true);
  const [expandedIds, setExpandedIds] = useState<Set<number>>(new Set());

  // Approve dialog state
  const [approveDialog, setApproveDialog] = useState<{
    open: boolean;
    request?: ApprovalRequest;
    justification: string;
    submitting: boolean;
  }>({
    open: false,
    justification: '',
    submitting: false,
  });

  // Reject dialog state
  const [rejectDialog, setRejectDialog] = useState<{
    open: boolean;
    request?: ApprovalRequest;
    reason: string;
    submitting: boolean;
  }>({
    open: false,
    reason: '',
    submitting: false,
  });

  // Revoke dialog state
  const [revokeDialog, setRevokeDialog] = useState<{
    open: boolean;
    request?: ApprovalRequest;
    reason: string;
    submitting: boolean;
  }>({
    open: false,
    reason: '',
    submitting: false,
  });

  const { toast } = useToast();

  useEffect(() => {
    loadApprovals();
  }, []);

  const loadApprovals = async () => {
    try {
      setLoading(true);
      const [pending, approved] = await Promise.all([
        getAllPendingApprovals(),
        getAllApprovedApprovals(),
      ]);
      setPendingApprovals(pending);
      setApprovedApprovals(approved);
    } catch (error: any) {
      toast({
        title: 'Error',
        description: error.message || 'Failed to load approvals',
        variant: 'destructive',
      });
    } finally {
      setLoading(false);
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
      loadApprovals(); // Reload list
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
      loadApprovals(); // Reload list
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
      loadApprovals(); // Reload list
    } catch (error: any) {
      toast({
        title: 'Error',
        description: error.message || 'Failed to revoke approval',
        variant: 'destructive',
      });
      setRevokeDialog({ ...revokeDialog, submitting: false });
    }
  };

  const getEntityTypeColor = (entityType: EntityType): string => {
    const colors: Record<EntityType, string> = {
      LOADER: 'bg-blue-100 text-blue-800',
      DASHBOARD: 'bg-purple-100 text-purple-800',
      INCIDENT: 'bg-red-100 text-red-800',
      CHART: 'bg-green-100 text-green-800',
      ALERT_RULE: 'bg-yellow-100 text-yellow-800',
    };
    return colors[entityType] || 'bg-gray-100 text-gray-800';
  };

  const getRequestTypeIcon = (requestType: string) => {
    switch (requestType) {
      case 'CREATE':
        return <FileText className="w-4 h-4" />;
      case 'UPDATE':
        return <AlertCircle className="w-4 h-4" />;
      case 'DELETE':
        return <XCircle className="w-4 h-4" />;
      default:
        return <FileText className="w-4 h-4" />;
    }
  };

  const formatDate = (dateString: string): string => {
    return new Date(dateString).toLocaleString();
  };

  const renderApprovalCard = (approval: ApprovalRequest, isPending: boolean) => {
    const isExpanded = expandedIds.has(approval.id);

    return (
      <Card key={approval.id} className="p-6">
        <div className="flex items-start justify-between mb-4">
          <div className="flex items-start gap-4 flex-1">
            <Button
              variant="ghost"
              size="sm"
              onClick={() => toggleExpanded(approval.id)}
              className="p-0 h-6 w-6"
            >
              {isExpanded ? (
                <ChevronDown className="w-5 h-5" />
              ) : (
                <ChevronRight className="w-5 h-5" />
              )}
            </Button>

            <div className="flex-1">
              <div className="flex items-center gap-2 mb-2">
                <span
                  className={`px-2 py-1 rounded text-xs font-semibold ${getEntityTypeColor(
                    approval.entityType
                  )}`}
                >
                  {approval.entityType}
                </span>
                <span className="flex items-center gap-1 text-sm text-gray-600">
                  {getRequestTypeIcon(approval.requestType)}
                  {approval.requestType}
                </span>
                {approval.source && (
                  <span className="text-xs px-2 py-1 bg-gray-100 text-gray-600 rounded">
                    {approval.source}
                  </span>
                )}
                {approval.importLabel && (
                  <span className="text-xs px-2 py-1 bg-purple-100 text-purple-800 rounded">
                    {approval.importLabel}
                  </span>
                )}
              </div>

              <h3 className="text-lg font-semibold mb-1">
                {approval.entityId}
              </h3>

              {approval.changeSummary && (
                <p className="text-gray-600 text-sm mb-2">
                  {approval.changeSummary}
                </p>
              )}

              <div className="flex items-center gap-4 text-sm text-gray-500">
                <span>
                  Requested by <strong>{approval.requestedBy}</strong>
                </span>
                <span>{formatDate(approval.requestedAt)}</span>
              </div>

              {!isPending && approval.approvedBy && (
                <div className="flex items-center gap-4 text-sm text-green-600 mt-2">
                  <span>
                    Approved by <strong>{approval.approvedBy}</strong>
                  </span>
                  {approval.approvedAt && (
                    <span>{formatDate(approval.approvedAt)}</span>
                  )}
                </div>
              )}
            </div>
          </div>

          <div className="flex items-center gap-2">
            {isPending ? (
              <>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setApproveDialog({
                      open: true,
                      request: approval,
                      justification: '',
                      submitting: false,
                    })
                  }
                  className="text-green-600 hover:text-green-700"
                >
                  <CheckCircle className="w-4 h-4 mr-1" />
                  Approve
                </Button>
                <Button
                  variant="outline"
                  size="sm"
                  onClick={() =>
                    setRejectDialog({
                      open: true,
                      request: approval,
                      reason: '',
                      submitting: false,
                    })
                  }
                  className="text-red-600 hover:text-red-700"
                >
                  <XCircle className="w-4 h-4 mr-1" />
                  Reject
                </Button>
              </>
            ) : (
              <Button
                variant="outline"
                size="sm"
                onClick={() =>
                  setRevokeDialog({
                    open: true,
                    request: approval,
                    reason: '',
                    submitting: false,
                  })
                }
                className="text-orange-600 hover:text-orange-700"
              >
                <RotateCcw className="w-4 h-4 mr-1" />
                Revoke
              </Button>
            )}
          </div>
        </div>

        {/* Expanded details */}
        {isExpanded && (
          <div className="mt-4 pt-4 border-t">
            <h4 className="font-semibold mb-2">Proposed Changes:</h4>
            <pre className="bg-gray-50 p-4 rounded text-sm overflow-auto max-h-96">
              {JSON.stringify(approval.requestData, null, 2)}
            </pre>

            {approval.currentData && (
              <>
                <h4 className="font-semibold mt-4 mb-2">Current State:</h4>
                <pre className="bg-gray-50 p-4 rounded text-sm overflow-auto max-h-96">
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
      <div className="p-8">
        <div className="flex items-center justify-center h-64">
          <div className="text-center">
            <div className="inline-block animate-spin rounded-full h-8 w-8 border-b-2 border-gray-900 mb-4"></div>
            <p className="text-gray-600">Loading approvals...</p>
          </div>
        </div>
      </div>
    );
  }

  return (
    <div className="p-8">
      <div className="mb-8">
        <h1 className="text-3xl font-bold mb-2">Approval Management</h1>
        <p className="text-gray-600">
          Review and manage approval requests for loaders, dashboards, and other entities
        </p>
      </div>

      {/* Stats cards */}
      <div className="grid grid-cols-1 md:grid-cols-4 gap-4 mb-8">
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600">Pending</p>
              <p className="text-2xl font-bold">{pendingApprovals.length}</p>
            </div>
            <Clock className="w-8 h-8 text-orange-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600">Approved</p>
              <p className="text-2xl font-bold">{approvedApprovals.length}</p>
            </div>
            <CheckCircle className="w-8 h-8 text-green-500" />
          </div>
        </Card>
        <Card className="p-4">
          <div className="flex items-center justify-between">
            <div>
              <p className="text-sm text-gray-600">Loaders</p>
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
              <p className="text-sm text-gray-600">From Imports</p>
              <p className="text-2xl font-bold">
                {[...pendingApprovals, ...approvedApprovals].filter((a) => a.source === 'IMPORT').length}
              </p>
            </div>
            <AlertCircle className="w-8 h-8 text-purple-500" />
          </div>
        </Card>
      </div>

      {/* Tabs */}
      <Tabs value={activeTab} onValueChange={(value) => setActiveTab(value as TabType)}>
        <TabsList className="mb-6">
          <TabsTrigger value="pending">
            Pending ({pendingApprovals.length})
          </TabsTrigger>
          <TabsTrigger value="approved">
            Approved ({approvedApprovals.length})
          </TabsTrigger>
        </TabsList>

        <TabsContent value="pending">
          {pendingApprovals.length === 0 ? (
            <Card className="p-8 text-center">
              <CheckCircle className="w-16 h-16 text-green-500 mx-auto mb-4" />
              <h3 className="text-xl font-semibold mb-2">No Pending Approvals</h3>
              <p className="text-gray-600">All approval requests have been processed</p>
            </Card>
          ) : (
            <div className="space-y-4">
              {pendingApprovals.map((approval) => renderApprovalCard(approval, true))}
            </div>
          )}
        </TabsContent>

        <TabsContent value="approved">
          {approvedApprovals.length === 0 ? (
            <Card className="p-8 text-center">
              <Clock className="w-16 h-16 text-gray-400 mx-auto mb-4" />
              <h3 className="text-xl font-semibold mb-2">No Approved Approvals</h3>
              <p className="text-gray-600">No approvals have been approved yet</p>
            </Card>
          ) : (
            <div className="space-y-4">
              {approvedApprovals.map((approval) => renderApprovalCard(approval, false))}
            </div>
          )}
        </TabsContent>
      </Tabs>

      {/* Approve Dialog */}
      <Dialog open={approveDialog.open} onOpenChange={(open) => !approveDialog.submitting && setApproveDialog({ ...approveDialog, open })}>
        <DialogContent>
          <DialogHeader>
            <DialogTitle>Approve Request</DialogTitle>
            <DialogDescription>
              Approve {approveDialog.request?.entityType} {approveDialog.request?.entityId}?
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Label htmlFor="justification">Justification (Optional)</Label>
            <Textarea
              id="justification"
              value={approveDialog.justification}
              onChange={(e) =>
                setApproveDialog({ ...approveDialog, justification: e.target.value })
              }
              placeholder="Why are you approving this request?"
              className="mt-2"
              rows={4}
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setApproveDialog({ ...approveDialog, open: false })}
              disabled={approveDialog.submitting}
            >
              Cancel
            </Button>
            <Button
              onClick={handleApprove}
              disabled={approveDialog.submitting}
              className="bg-green-600 hover:bg-green-700"
            >
              {approveDialog.submitting ? 'Approving...' : 'Approve'}
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
              Reject {rejectDialog.request?.entityType} {rejectDialog.request?.entityId}?
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Label htmlFor="reason" className="text-red-600">
              Rejection Reason (Required) *
            </Label>
            <Textarea
              id="reason"
              value={rejectDialog.reason}
              onChange={(e) =>
                setRejectDialog({ ...rejectDialog, reason: e.target.value })
              }
              placeholder="Explain why this request is being rejected..."
              className="mt-2"
              rows={4}
              required
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setRejectDialog({ ...rejectDialog, open: false })}
              disabled={rejectDialog.submitting}
            >
              Cancel
            </Button>
            <Button
              onClick={handleReject}
              disabled={rejectDialog.submitting || !rejectDialog.reason.trim()}
              className="bg-red-600 hover:bg-red-700"
            >
              {rejectDialog.submitting ? 'Rejecting...' : 'Reject'}
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
              Revoke approval for {revokeDialog.request?.entityType} {revokeDialog.request?.entityId}?
            </DialogDescription>
          </DialogHeader>
          <div className="py-4">
            <Label htmlFor="revoke-reason" className="text-orange-600">
              Revocation Reason (Required) *
            </Label>
            <Textarea
              id="revoke-reason"
              value={revokeDialog.reason}
              onChange={(e) =>
                setRevokeDialog({ ...revokeDialog, reason: e.target.value })
              }
              placeholder="Explain why this approval is being revoked..."
              className="mt-2"
              rows={4}
              required
            />
          </div>
          <DialogFooter>
            <Button
              variant="outline"
              onClick={() => setRevokeDialog({ ...revokeDialog, open: false })}
              disabled={revokeDialog.submitting}
            >
              Cancel
            </Button>
            <Button
              onClick={handleRevoke}
              disabled={revokeDialog.submitting || !revokeDialog.reason.trim()}
              className="bg-orange-600 hover:bg-orange-700"
            >
              {revokeDialog.submitting ? 'Revoking...' : 'Revoke'}
            </Button>
          </DialogFooter>
        </DialogContent>
      </Dialog>
    </div>
  );
}
