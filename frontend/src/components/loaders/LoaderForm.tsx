import { useForm } from 'react-hook-form';
import { zodResolver } from '@hookform/resolvers/zod';
import * as z from 'zod';
import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { Loader, PurgeStrategy } from '@/types/loader';
import { loadersApi } from '@/api/loaders';
import { Button } from '@/components/ui/button';
import { Input } from '@/components/ui/input';
import { Label } from '@/components/ui/label';
import { Card, CardContent, CardDescription, CardHeader, CardTitle } from '@/components/ui/card';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { AlertCircle, CheckCircle2, Loader2 } from 'lucide-react';
import { SqlEditorField } from './SqlEditorField';

// Validation schema matching backend constraints
const loaderSchema = z.object({
  loaderCode: z.string()
    .min(1, 'Loader code is required')
    .max(64, 'Loader code must be 64 characters or less')
    .regex(/^[A-Z0-9_]+$/, 'Loader code must contain only uppercase letters, numbers, and underscores'),

  loaderSql: z.string()
    .min(10, 'SQL must be at least 10 characters')
    .max(10000, 'SQL must not exceed 10000 characters'),

  minIntervalSeconds: z.number()
    .int('Must be a whole number')
    .min(1, 'Minimum interval must be at least 1 second')
    .max(86400, 'Minimum interval must not exceed 86400 seconds (24 hours)'),

  maxIntervalSeconds: z.number()
    .int('Must be a whole number')
    .min(1, 'Maximum interval must be at least 1 second')
    .max(86400, 'Maximum interval must not exceed 86400 seconds (24 hours)'),

  maxQueryPeriodSeconds: z.number()
    .int('Must be a whole number')
    .min(1, 'Maximum query period must be at least 1 second')
    .max(604800, 'Maximum query period must not exceed 604800 seconds (7 days)'),

  maxParallelExecutions: z.number()
    .int('Must be a whole number')
    .min(1, 'Maximum parallel executions must be at least 1')
    .max(100, 'Maximum parallel executions must not exceed 100'),

  enabled: z.boolean().default(true),

  sourceDatabaseId: z.number()
    .int('Must be a valid database ID')
    .positive('Source database is required'),

  purgeStrategy: z.enum(['FAIL_ON_DUPLICATE', 'PURGE_AND_RELOAD', 'SKIP_DUPLICATES'] as const)
    .optional()
    .default('FAIL_ON_DUPLICATE'),

  sourceTimezoneOffsetHours: z.number()
    .int('Must be a whole number')
    .min(-12, 'Timezone offset must be between -12 and +14')
    .max(14, 'Timezone offset must be between -12 and +14')
    .default(0),

  aggregationPeriodSeconds: z.number()
    .int('Must be a whole number')
    .min(1, 'Aggregation period must be at least 1 second')
    .max(86400, 'Aggregation period must not exceed 86400 seconds')
    .optional()
    .nullable(),
});

export type LoaderFormData = z.infer<typeof loaderSchema>;

interface LoaderFormProps {
  loader?: Loader;
  onSubmit: (data: LoaderFormData) => Promise<void>;
  onCancel: () => void;
  isEdit?: boolean;
  protectedFields?: string[];
}

export function LoaderForm({ loader, onSubmit, onCancel, isEdit = false, protectedFields = [] }: LoaderFormProps) {
  const [isSubmitting, setIsSubmitting] = useState(false);
  const [submitError, setSubmitError] = useState<string | null>(null);

  // Fetch source databases for selection
  const { data: sourceDatabases = [], isLoading: isLoadingDatabases } = useQuery({
    queryKey: ['sourceDatabases'],
    queryFn: loadersApi.getSourceDatabases,
  });

  const {
    register,
    handleSubmit,
    formState: { errors },
    setValue,
    watch,
  } = useForm<LoaderFormData>({
    resolver: zodResolver(loaderSchema),
    defaultValues: loader ? {
      loaderCode: loader.loaderCode,
      loaderSql: loader.loaderSql || '',
      minIntervalSeconds: loader.minIntervalSeconds,
      maxIntervalSeconds: loader.maxIntervalSeconds,
      maxQueryPeriodSeconds: loader.maxQueryPeriodSeconds,
      maxParallelExecutions: loader.maxParallelExecutions,
      enabled: loader.enabled ?? true,
      sourceDatabaseId: loader.sourceDatabase?.id,
      purgeStrategy: (loader.purgeStrategy as PurgeStrategy) || 'FAIL_ON_DUPLICATE',
      sourceTimezoneOffsetHours: loader.sourceTimezoneOffsetHours ?? 0,
      aggregationPeriodSeconds: loader.aggregationPeriodSeconds ?? null,
    } : {
      enabled: true,
      sourceTimezoneOffsetHours: 0,
      minIntervalSeconds: 60,
      maxIntervalSeconds: 300,
      maxQueryPeriodSeconds: 3600,
      maxParallelExecutions: 1,
    },
  });

  const loaderSql = watch('loaderSql');
  const sourceDatabaseId = watch('sourceDatabaseId');

  const handleFormSubmit = async (data: LoaderFormData) => {
    setIsSubmitting(true);
    setSubmitError(null);
    try {
      await onSubmit(data);
    } catch (error) {
      setSubmitError(error instanceof Error ? error.message : 'Failed to save loader');
    } finally {
      setIsSubmitting(false);
    }
  };

  const isFieldProtected = (fieldName: string) => protectedFields.includes(fieldName);
  const isFieldDisabled = (fieldName: string) => isEdit && isFieldProtected(fieldName);

  return (
    <form onSubmit={handleSubmit(handleFormSubmit)} className="space-y-6">
      {submitError && (
        <Alert variant="destructive">
          <AlertCircle className="h-4 w-4" />
          <AlertDescription>{submitError}</AlertDescription>
        </Alert>
      )}

      {/* Basic Information */}
      <Card>
        <CardHeader>
          <CardTitle>Basic Information</CardTitle>
          <CardDescription>
            Configure the loader identification and status
          </CardDescription>
        </CardHeader>
        <CardContent className="space-y-4">
          {/* Loader Code */}
          <div className="space-y-2">
            <Label htmlFor="loaderCode">
              Loader Code <span className="text-red-500">*</span>
            </Label>
            <Input
              id="loaderCode"
              {...register('loaderCode')}
              placeholder="EXAMPLE_LOADER_01"
              disabled={isEdit} // Loader code cannot be changed after creation
              className={errors.loaderCode ? 'border-red-500' : ''}
            />
            {errors.loaderCode && (
              <p className="text-sm text-red-500">{errors.loaderCode.message}</p>
            )}
            <p className="text-xs text-muted-foreground">
              Unique identifier using uppercase letters, numbers, and underscores only
            </p>
          </div>

          {/* Source Database */}
          <div className="space-y-2">
            <Label htmlFor="sourceDatabaseId">
              Source Database <span className="text-red-500">*</span>
            </Label>
            {isLoadingDatabases ? (
              <div className="flex items-center gap-2 text-sm text-muted-foreground">
                <Loader2 className="h-4 w-4 animate-spin" />
                Loading databases...
              </div>
            ) : (
              <>
                <select
                  id="sourceDatabaseId"
                  {...register('sourceDatabaseId', { valueAsNumber: true })}
                  className={`flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${
                    errors.sourceDatabaseId ? 'border-red-500' : ''
                  }`}
                  disabled={isFieldDisabled('sourceDatabaseId')}
                >
                  <option value="">Select a database...</option>
                  {sourceDatabases.map((db) => (
                    <option key={db.id} value={db.id}>
                      {db.dbCode} ({db.dbType} - {db.ip}:{db.port}/{db.dbName})
                    </option>
                  ))}
                </select>
                {errors.sourceDatabaseId && (
                  <p className="text-sm text-red-500">{errors.sourceDatabaseId.message}</p>
                )}
              </>
            )}
            <p className="text-xs text-muted-foreground">
              The source database this loader will query data from
            </p>
          </div>

          {/* Purge Strategy */}
          <div className="space-y-2">
            <Label htmlFor="purgeStrategy">
              Purge Strategy
            </Label>
            <select
              id="purgeStrategy"
              {...register('purgeStrategy')}
              className={`flex h-10 w-full rounded-md border border-input bg-background px-3 py-2 text-sm ring-offset-background focus-visible:outline-none focus-visible:ring-2 focus-visible:ring-ring focus-visible:ring-offset-2 disabled:cursor-not-allowed disabled:opacity-50 ${
                errors.purgeStrategy ? 'border-red-500' : ''
              }`}
              disabled={isFieldDisabled('purgeStrategy')}
            >
              <option value="FAIL_ON_DUPLICATE">Fail on Duplicate (Safest)</option>
              <option value="PURGE_AND_RELOAD">Purge and Reload (Delete then reload)</option>
              <option value="SKIP_DUPLICATES">Skip Duplicates (Keep existing)</option>
            </select>
            {errors.purgeStrategy && (
              <p className="text-sm text-red-500">{errors.purgeStrategy.message}</p>
            )}
            <p className="text-xs text-muted-foreground">
              How to handle duplicate data when reprocessing historical data
            </p>
          </div>

          {/* Enabled Status */}
          <div className="space-y-2">
            <div className="flex items-center space-x-2">
              <input
                id="enabled"
                type="checkbox"
                {...register('enabled')}
                disabled={isFieldDisabled('enabled')}
                className="h-4 w-4 rounded border-gray-300"
              />
              <Label htmlFor="enabled">Enabled</Label>
            </div>
            <p className="text-xs text-muted-foreground">
              Enable or disable this loader. Disabled loaders will not execute.
            </p>
          </div>
        </CardContent>
      </Card>

      {/* SQL Query */}
      <Card>
        <CardHeader>
          <CardTitle>SQL Query <span className="text-red-500">*</span></CardTitle>
          <CardDescription>
            Define the SQL SELECT query for data extraction. Validation is mandatory.
          </CardDescription>
        </CardHeader>
        <CardContent>
          <SqlEditorField
            value={loaderSql || ''}
            onChange={(value) => setValue('loaderSql', value, { shouldValidate: true })}
            error={errors.loaderSql?.message}
            disabled={isFieldDisabled('loaderSql')}
            required={true}
            sourceDatabaseId={sourceDatabaseId}
          />
        </CardContent>
      </Card>

      {/* Execution Configuration */}
      <Card>
        <CardHeader>
          <CardTitle>Execution Configuration</CardTitle>
          <CardDescription>
            Configure timing and parallelization parameters
          </CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Min Interval */}
          <div className="space-y-2">
            <Label htmlFor="minIntervalSeconds">
              Minimum Interval (seconds) <span className="text-red-500">*</span>
            </Label>
            <Input
              id="minIntervalSeconds"
              type="number"
              {...register('minIntervalSeconds', { valueAsNumber: true })}
              disabled={isFieldDisabled('minIntervalSeconds')}
              className={errors.minIntervalSeconds ? 'border-red-500' : ''}
            />
            {errors.minIntervalSeconds && (
              <p className="text-sm text-red-500">{errors.minIntervalSeconds.message}</p>
            )}
            <p className="text-xs text-muted-foreground">Range: 1-86400 seconds (1 sec - 24 hours)</p>
          </div>

          {/* Max Interval */}
          <div className="space-y-2">
            <Label htmlFor="maxIntervalSeconds">
              Maximum Interval (seconds) <span className="text-red-500">*</span>
            </Label>
            <Input
              id="maxIntervalSeconds"
              type="number"
              {...register('maxIntervalSeconds', { valueAsNumber: true })}
              disabled={isFieldDisabled('maxIntervalSeconds')}
              className={errors.maxIntervalSeconds ? 'border-red-500' : ''}
            />
            {errors.maxIntervalSeconds && (
              <p className="text-sm text-red-500">{errors.maxIntervalSeconds.message}</p>
            )}
            <p className="text-xs text-muted-foreground">Range: 1-86400 seconds (1 sec - 24 hours)</p>
          </div>

          {/* Max Query Period */}
          <div className="space-y-2">
            <Label htmlFor="maxQueryPeriodSeconds">
              Maximum Query Period (seconds) <span className="text-red-500">*</span>
            </Label>
            <Input
              id="maxQueryPeriodSeconds"
              type="number"
              {...register('maxQueryPeriodSeconds', { valueAsNumber: true })}
              disabled={isFieldDisabled('maxQueryPeriodSeconds')}
              className={errors.maxQueryPeriodSeconds ? 'border-red-500' : ''}
            />
            {errors.maxQueryPeriodSeconds && (
              <p className="text-sm text-red-500">{errors.maxQueryPeriodSeconds.message}</p>
            )}
            <p className="text-xs text-muted-foreground">Range: 1-604800 seconds (1 sec - 7 days)</p>
          </div>

          {/* Max Parallel Executions */}
          <div className="space-y-2">
            <Label htmlFor="maxParallelExecutions">
              Maximum Parallel Executions <span className="text-red-500">*</span>
            </Label>
            <Input
              id="maxParallelExecutions"
              type="number"
              {...register('maxParallelExecutions', { valueAsNumber: true })}
              disabled={isFieldDisabled('maxParallelExecutions')}
              className={errors.maxParallelExecutions ? 'border-red-500' : ''}
            />
            {errors.maxParallelExecutions && (
              <p className="text-sm text-red-500">{errors.maxParallelExecutions.message}</p>
            )}
            <p className="text-xs text-muted-foreground">Range: 1-100 concurrent executions</p>
          </div>
        </CardContent>
      </Card>

      {/* Advanced Settings */}
      <Card>
        <CardHeader>
          <CardTitle>Advanced Settings</CardTitle>
          <CardDescription>
            Optional configuration for timezone and aggregation
          </CardDescription>
        </CardHeader>
        <CardContent className="grid grid-cols-1 md:grid-cols-2 gap-4">
          {/* Source Timezone Offset */}
          <div className="space-y-2">
            <Label htmlFor="sourceTimezoneOffsetHours">
              Source Timezone Offset (hours)
            </Label>
            <Input
              id="sourceTimezoneOffsetHours"
              type="number"
              {...register('sourceTimezoneOffsetHours', { valueAsNumber: true })}
              disabled={isFieldDisabled('sourceTimezoneOffsetHours')}
              className={errors.sourceTimezoneOffsetHours ? 'border-red-500' : ''}
            />
            {errors.sourceTimezoneOffsetHours && (
              <p className="text-sm text-red-500">{errors.sourceTimezoneOffsetHours.message}</p>
            )}
            <p className="text-xs text-muted-foreground">Range: -12 to +14 (UTC offset)</p>
          </div>

          {/* Aggregation Period */}
          <div className="space-y-2">
            <Label htmlFor="aggregationPeriodSeconds">
              Aggregation Period (seconds)
            </Label>
            <Input
              id="aggregationPeriodSeconds"
              type="number"
              {...register('aggregationPeriodSeconds', { valueAsNumber: true })}
              disabled={isFieldDisabled('aggregationPeriodSeconds')}
              className={errors.aggregationPeriodSeconds ? 'border-red-500' : ''}
            />
            {errors.aggregationPeriodSeconds && (
              <p className="text-sm text-red-500">{errors.aggregationPeriodSeconds.message}</p>
            )}
            <p className="text-xs text-muted-foreground">Optional: Data aggregation interval (1-86400 seconds)</p>
          </div>
        </CardContent>
      </Card>

      {/* Form Actions */}
      <div className="flex justify-end gap-3 pt-4 border-t">
        <Button
          type="button"
          variant="outline"
          onClick={onCancel}
          disabled={isSubmitting}
        >
          Cancel
        </Button>
        <Button
          type="submit"
          disabled={isSubmitting}
        >
          {isSubmitting ? (
            <>
              <Loader2 className="mr-2 h-4 w-4 animate-spin" />
              {isEdit ? 'Updating...' : 'Creating...'}
            </>
          ) : (
            <>
              <CheckCircle2 className="mr-2 h-4 w-4" />
              {isEdit ? 'Update Loader' : 'Create Loader'}
            </>
          )}
        </Button>
      </div>
    </form>
  );
}
