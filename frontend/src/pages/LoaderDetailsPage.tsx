import { useState } from 'react';
import { useParams, useNavigate, Link } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import {
  ArrowLeft,
  Play,
  Pause,
  RefreshCw,
  CheckCircle2,
  XCircle,
  Edit,
} from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Badge } from '@/components/ui/badge';
import { Separator } from '@/components/ui/separator';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { loadersApi } from '@/api/loaders';
import { Loader } from '@/types/loader';
import { LoaderDetailPanel } from '@/components/loaders/LoaderDetailPanel';
import { LoaderAction } from '@/components/loaders/LoaderActionButton';
import { ApproveLoaderDialog } from '@/components/loaders/ApproveLoaderDialog';
import { RejectLoaderDialog } from '@/components/loaders/RejectLoaderDialog';
import { getLoaderStatusVariant } from '@/lib/badge-utils';
import { useToast } from '../hooks/use-toast';

export default function LoaderDetailsPage() {
  const { loaderCode } = useParams<{ loaderCode: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();
  const { toast } = useToast();

  // Dialog state
  const [isApproveDialogOpen, setIsApproveDialogOpen] = useState(false);
  const [isRejectDialogOpen, setIsRejectDialogOpen] = useState(false);

  // Fetch loader details
  const { data: loader, isLoading, error } = useQuery({
    queryKey: ['loader', loaderCode],
    queryFn: () => loadersApi.getLoader(loaderCode!),
    enabled: !!loaderCode,
  });

  // Fetch loaders list to get protectedFields
  const { data: loadersData } = useQuery({
    queryKey: ['loaders'],
    queryFn: loadersApi.getLoaders,
  });

  const protectedFields = loadersData?.protectedFields || [];

  // Toggle loader enabled/disabled
  const toggleMutation = useMutation({
    mutationFn: async (loader: Loader) => {
      const updated = { ...loader, enabled: !loader.enabled };
      return loadersApi.updateLoader(loaderCode!, updated);
    },
    onSuccess: () => {
      queryClient.invalidateQueries({ queryKey: ['loader', loaderCode] });
      queryClient.invalidateQueries({ queryKey: ['loaders'] });
      queryClient.invalidateQueries({ queryKey: ['loaders', 'stats'] });
    },
  });

  // Approve loader mutation
  const approveMutation = useMutation({
    mutationFn: ({ comments }: { comments?: string }) =>
      loadersApi.approveLoader(loaderCode!, comments),
    onSuccess: () => {
      toast({
        title: 'Loader approved',
        description: `Loader ${loaderCode} has been approved successfully`,
      });
      queryClient.invalidateQueries({ queryKey: ['loader', loaderCode] });
      queryClient.invalidateQueries({ queryKey: ['loaders'] });
      queryClient.invalidateQueries({ queryKey: ['loaders', 'stats'] });
    },
    onError: (error) => {
      toast({
        title: 'Approval failed',
        description: `Failed to approve loader: ${error instanceof Error ? error.message : 'Unknown error'}`,
        variant: 'destructive',
      });
    },
  });

  // Reject loader mutation
  const rejectMutation = useMutation({
    mutationFn: ({ rejectionReason, comments }: { rejectionReason: string; comments?: string }) =>
      loadersApi.rejectLoader(loaderCode!, rejectionReason, comments),
    onSuccess: () => {
      toast({
        title: 'Loader rejected',
        description: `Loader ${loaderCode} has been rejected successfully`,
      });
      queryClient.invalidateQueries({ queryKey: ['loader', loaderCode] });
      queryClient.invalidateQueries({ queryKey: ['loaders'] });
      queryClient.invalidateQueries({ queryKey: ['loaders', 'stats'] });
    },
    onError: (error) => {
      toast({
        title: 'Rejection failed',
        description: `Failed to reject loader: ${error instanceof Error ? error.message : 'Unknown error'}`,
        variant: 'destructive',
      });
    },
  });

  const handleToggleEnabled = () => {
    if (loader) {
      toggleMutation.mutate(loader);
    }
  };

  const handleApprove = async (comments?: string) => {
    await approveMutation.mutateAsync({ comments });
  };

  const handleReject = async (rejectionReason: string, comments?: string) => {
    await rejectMutation.mutateAsync({ rejectionReason, comments });
  };

  if (isLoading) {
    return (
      <div className="container mx-auto p-6">
        <div className="animate-pulse space-y-4">
          <div className="h-8 bg-gray-200 rounded w-1/4"></div>
          <div className="h-64 bg-gray-200 rounded"></div>
        </div>
      </div>
    );
  }

  if (error || !loader) {
    return (
      <div className="container mx-auto p-6">
        <Alert variant="destructive">
          <XCircle className="h-4 w-4" />
          <AlertDescription>
            {error instanceof Error ? error.message : 'Loader not found'}
          </AlertDescription>
        </Alert>
        <Button onClick={() => navigate('/loaders')} className="mt-4">
          <ArrowLeft className="mr-2 h-4 w-4" />
          Back to Loaders
        </Button>
      </div>
    );
  }

  // Create actions based on _links (HATEOAS) and protected fields
  const isEnabledFieldProtected = protectedFields.includes('enabled');
  const actions: LoaderAction[] = [];

  if (loader._links?.toggleEnabled) {
    actions.push({
      id: 'toggleEnabled',
      icon: loader.enabled ? Pause : Play,
      label: loader.enabled ? 'Pause Loader' : 'Resume Loader',
      onClick: handleToggleEnabled,
      enabled: !isEnabledFieldProtected,
      iconColor: loader.enabled ? 'text-orange-600' : 'text-green-600',
      disabledReason: isEnabledFieldProtected
        ? 'Action disabled due to data protection (enabled status is hidden)'
        : undefined,
    });
  }

  return (
    <div className="container mx-auto p-6 space-y-6">
      {/* Breadcrumbs & Header */}
      <div className="flex items-center justify-between">
        <div className="space-y-1">
          <div className="flex items-center text-sm text-muted-foreground">
            <Link to="/loaders" className="hover:text-foreground">
              Loaders
            </Link>
            <span className="mx-2">/</span>
            <span className="text-foreground">{loader.loaderCode}</span>
          </div>
          <div className="flex items-center gap-3">
            <h1 className="text-3xl font-bold">{loader.loaderCode}</h1>
            <Badge variant={getLoaderStatusVariant(loader.enabled)}>
              {loader.enabled ? (
                <>
                  <CheckCircle2 className="mr-1 h-3 w-3" />
                  ENABLED
                </>
              ) : (
                <>
                  <XCircle className="mr-1 h-3 w-3" />
                  DISABLED
                </>
              )}
            </Badge>
          </div>
        </div>

        <div className="flex gap-2">
          <Button
            variant="outline"
            onClick={() => navigate('/loaders')}
          >
            <ArrowLeft className="mr-2 h-4 w-4" />
            Back
          </Button>

          {/* Approve/Reject Buttons (ADMIN only, PENDING_APPROVAL only) */}
          {loader._links?.approveLoader && loader.approvalStatus === 'PENDING_APPROVAL' && (
            <Button
              variant="default"
              onClick={() => setIsApproveDialogOpen(true)}
              className="bg-green-500 hover:bg-green-600"
            >
              <CheckCircle2 className="mr-2 h-4 w-4" />
              Approve
            </Button>
          )}
          {loader._links?.rejectLoader && loader.approvalStatus === 'PENDING_APPROVAL' && (
            <Button
              variant="destructive"
              onClick={() => setIsRejectDialogOpen(true)}
            >
              <XCircle className="mr-2 h-4 w-4" />
              Reject
            </Button>
          )}

          {loader._links?.editLoader && (
            <Button
              variant="outline"
              onClick={() => navigate(`/loaders/${loaderCode}/edit`)}
            >
              <Edit className="mr-2 h-4 w-4" />
              Edit
            </Button>
          )}
          {loader._links?.toggleEnabled && (
            <Button
              variant={loader.enabled ? 'outline' : 'default'}
              onClick={handleToggleEnabled}
              disabled={toggleMutation.isPending || isEnabledFieldProtected}
            >
              {toggleMutation.isPending ? (
                <RefreshCw className="mr-2 h-4 w-4 animate-spin" />
              ) : loader.enabled ? (
                <Pause className="mr-2 h-4 w-4" />
              ) : (
                <Play className="mr-2 h-4 w-4" />
              )}
              {loader.enabled ? 'Pause' : 'Resume'}
            </Button>
          )}
        </div>
      </div>

      <Separator />

      {/* Loader Details using shared component */}
      <LoaderDetailPanel
        loader={loader}
        actions={actions}
        protectedFields={protectedFields}
      />

      {/* Approval Dialogs */}
      {loader && (
        <>
          <ApproveLoaderDialog
            loader={loader}
            open={isApproveDialogOpen}
            onOpenChange={setIsApproveDialogOpen}
            onConfirm={handleApprove}
          />
          <RejectLoaderDialog
            loader={loader}
            open={isRejectDialogOpen}
            onOpenChange={setIsRejectDialogOpen}
            onConfirm={handleReject}
          />
        </>
      )}
    </div>
  );
}
