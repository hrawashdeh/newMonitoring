import { useNavigate } from 'react-router-dom';
import { useMutation, useQueryClient, useQuery } from '@tanstack/react-query';
import { Plus } from 'lucide-react';
import { Button } from '@/components/ui/button';
import { loadersApi } from '@/api/loaders';
import { LoaderForm, LoaderFormData } from '@/components/loaders/LoaderForm';

export default function NewLoaderPage() {
  const navigate = useNavigate();
  const queryClient = useQueryClient();

  // Fetch loaders list to get protectedFields (even for create, some fields might be restricted)
  const { data: loadersData } = useQuery({
    queryKey: ['loaders'],
    queryFn: loadersApi.getLoaders,
  });

  const protectedFields = loadersData?.protectedFields || [];

  // Create mutation
  const createMutation = useMutation({
    mutationFn: async (data: LoaderFormData) => {
      // Convert null to undefined for optional fields
      const cleanedData = {
        ...data,
        sourceTimezoneOffsetHours: data.sourceTimezoneOffsetHours ?? undefined,
        aggregationPeriodSeconds: data.aggregationPeriodSeconds ?? undefined,
      };
      return loadersApi.createLoader(cleanedData);
    },
    onSuccess: (createdLoader) => {
      // Invalidate queries to refresh data
      queryClient.invalidateQueries({ queryKey: ['loaders'] });
      queryClient.invalidateQueries({ queryKey: ['loaders', 'stats'] });

      // Navigate to the newly created loader's details page
      navigate(`/loaders/${createdLoader.loaderCode}`);
    },
  });

  const handleSubmit = async (data: LoaderFormData) => {
    await createMutation.mutateAsync(data);
  };

  const handleCancel = () => {
    navigate('/loaders');
  };

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
          <span className="text-foreground">New</span>
        </div>
        <div className="flex items-center gap-3">
          <h1 className="text-3xl font-bold">Create New Loader</h1>
          <Plus className="h-8 w-8 text-primary" />
        </div>
        <p className="text-muted-foreground mt-1">
          Configure a new ETL loader for signal data extraction
        </p>
      </div>

      {/* Help Text */}
      <div className="mb-6 p-4 bg-blue-50 border border-blue-200 rounded-md">
        <h3 className="text-sm font-semibold text-blue-900 mb-2">Before You Begin</h3>
        <ul className="text-sm text-blue-800 space-y-1 list-disc list-inside">
          <li><strong>Loader Code:</strong> Must be unique, uppercase, with numbers and underscores only</li>
          <li><strong>SQL Query:</strong> Must be a SELECT query (read-only). Validation is mandatory.</li>
          <li><strong>Intervals:</strong> Define min/max execution intervals and query periods</li>
          <li><strong>Parallelization:</strong> Set maximum concurrent executions to control load</li>
        </ul>
      </div>

      {/* Form */}
      <LoaderForm
        onSubmit={handleSubmit}
        onCancel={handleCancel}
        isEdit={false}
        protectedFields={protectedFields}
      />
    </div>
  );
}
