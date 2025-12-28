import { useParams, useNavigate } from 'react-router-dom';
import { useQuery, useMutation, useQueryClient } from '@tanstack/react-query';
import { ArrowLeft, XCircle } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { loadersApi } from '@/api/loaders';
import { LoaderForm, LoaderFormData } from '@/components/loaders/LoaderForm';

export default function EditLoaderPage() {
  const { loaderCode } = useParams<{ loaderCode: string }>();
  const navigate = useNavigate();
  const queryClient = useQueryClient();

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

  // Update mutation
  const updateMutation = useMutation({
    mutationFn: async (data: LoaderFormData) => {
      // Convert null to undefined for optional fields
      const cleanedData = {
        ...data,
        sourceTimezoneOffsetHours: data.sourceTimezoneOffsetHours ?? undefined,
        aggregationPeriodSeconds: data.aggregationPeriodSeconds ?? undefined,
      };
      return loadersApi.updateLoader(loaderCode!, cleanedData);
    },
    onSuccess: () => {
      // Invalidate queries to refresh data
      queryClient.invalidateQueries({ queryKey: ['loader', loaderCode] });
      queryClient.invalidateQueries({ queryKey: ['loaders'] });
      queryClient.invalidateQueries({ queryKey: ['loaders', 'stats'] });

      // Navigate back to details page
      navigate(`/loaders/${loaderCode}`);
    },
  });

  const handleSubmit = async (data: LoaderFormData) => {
    await updateMutation.mutateAsync(data);
  };

  const handleCancel = () => {
    navigate(`/loaders/${loaderCode}`);
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

  return (
    <div className="container mx-auto p-6 max-w-5xl">
      {/* Header */}
      <div className="mb-6">
        <div className="flex items-center gap-2 text-sm text-muted-foreground mb-2">
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate('/loaders')}
            className="p-0 h-auto hover:bg-transparent"
          >
            Loaders
          </Button>
          <span>/</span>
          <Button
            variant="ghost"
            size="sm"
            onClick={() => navigate(`/loaders/${loaderCode}`)}
            className="p-0 h-auto hover:bg-transparent"
          >
            {loader.loaderCode}
          </Button>
          <span>/</span>
          <span className="text-foreground">Edit</span>
        </div>
        <h1 className="text-3xl font-bold">Edit Loader: {loader.loaderCode}</h1>
        <p className="text-muted-foreground mt-1">
          Modify the configuration for this ETL loader
        </p>
      </div>

      {/* Form */}
      <LoaderForm
        loader={loader}
        onSubmit={handleSubmit}
        onCancel={handleCancel}
        isEdit={true}
        protectedFields={protectedFields}
      />
    </div>
  );
}
