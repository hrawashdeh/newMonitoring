import { Loader } from '@/types/loader';
import { LoaderAction, LoaderActionButton } from './LoaderActionButton';
import { SqlCodeBlock } from './SqlCodeBlock';
import { ApprovalStatusBadge } from './ApprovalStatusBadge';
import { Card, CardContent, CardDescription, CardFooter, CardHeader, CardTitle } from '@/components/ui/card';
import { Badge } from '@/components/ui/badge';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { getLoaderStatusVariant, getLoaderStatusText, getZeroRecordRunsVariant } from '@/lib/badge-utils';
import { EyeOff, AlertCircle } from 'lucide-react';

interface LoaderDetailPanelProps {
  loader: Loader;
  actions: LoaderAction[];
  protectedFields?: string[];
}

export function LoaderDetailPanel({ loader, actions, protectedFields = [] }: LoaderDetailPanelProps) {
  // Helper to check if a field is protected
  const isFieldProtected = (fieldName: string) => protectedFields.includes(fieldName);

  return (
    <Card className="border-0 shadow-none bg-muted/30">
      <CardHeader className="pb-3">
        <CardTitle className="text-lg">Loader Details: {loader.loaderCode}</CardTitle>
        <CardDescription>
          Complete loader configuration and metadata
        </CardDescription>
      </CardHeader>

      <CardContent className="space-y-6">
        {/* Basic Information */}
        <div>
          <h4 className="text-sm font-semibold mb-3 text-muted-foreground">Basic Information</h4>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            <FieldDisplay label="Loader Code" value={loader.loaderCode} />

            {isFieldProtected('enabled') ? (
              <FieldDisplay
                label="Status"
                value="***HIDDEN***"
                isProtected={true}
              />
            ) : (
              <FieldDisplay
                label="Status"
                value={
                  <Badge variant={getLoaderStatusVariant(loader.enabled)}>
                    {getLoaderStatusText(loader.enabled)}
                  </Badge>
                }
              />
            )}

            {isFieldProtected('sourceTimezoneOffsetHours') ? (
              <FieldDisplay
                label="Time Zone"
                value="***HIDDEN***"
                isProtected={true}
              />
            ) : (
              <FieldDisplay
                label="Time Zone"
                value={formatTimezoneOffset(loader.sourceTimezoneOffsetHours)}
              />
            )}

            {isFieldProtected('consecutiveZeroRecordRuns') ? (
              <FieldDisplay
                label="Consecutive Zero Runs"
                value="***HIDDEN***"
                isProtected={true}
              />
            ) : (
              <FieldDisplay
                label="Consecutive Zero Runs"
                value={
                  <Badge variant={getZeroRecordRunsVariant(loader.consecutiveZeroRecordRuns || 0)}>
                    {loader.consecutiveZeroRecordRuns || 0}
                  </Badge>
                }
              />
            )}
          </div>
        </div>

        {/* Approval Workflow */}
        {loader.approvalStatus && (
          <div>
            <h4 className="text-sm font-semibold mb-3 text-muted-foreground">Approval Workflow</h4>
            <div className="space-y-3">
              {/* Approval Status Badge */}
              <div className="flex items-center gap-2">
                <span className="text-xs text-muted-foreground min-w-[100px]">Status:</span>
                {isFieldProtected('approvalStatus') ? (
                  <span className="text-sm">***HIDDEN***</span>
                ) : (
                  <ApprovalStatusBadge status={loader.approvalStatus} />
                )}
              </div>

              {/* Rejection Alert */}
              {loader.approvalStatus === 'REJECTED' && loader.rejectionReason && !isFieldProtected('rejectionReason') && (
                <Alert variant="destructive" className="mt-3">
                  <AlertCircle className="h-4 w-4" />
                  <AlertDescription>
                    <div className="space-y-1">
                      <p className="font-semibold">Rejection Reason:</p>
                      <p className="text-sm">{loader.rejectionReason}</p>
                    </div>
                  </AlertDescription>
                </Alert>
              )}

              {/* Approval Details */}
              <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
                {/* Approved By */}
                {loader.approvalStatus === 'APPROVED' && (
                  <>
                    {isFieldProtected('approvedBy') ? (
                      <FieldDisplay label="Approved By" value="***HIDDEN***" isProtected={true} />
                    ) : (
                      <FieldDisplay label="Approved By" value={loader.approvedBy || '-'} />
                    )}
                    {isFieldProtected('approvedAt') ? (
                      <FieldDisplay label="Approved At" value="***HIDDEN***" isProtected={true} />
                    ) : (
                      <FieldDisplay
                        label="Approved At"
                        value={loader.approvedAt ? formatDateTime(loader.approvedAt) : '-'}
                      />
                    )}
                  </>
                )}

                {/* Rejected By */}
                {loader.approvalStatus === 'REJECTED' && (
                  <>
                    {isFieldProtected('rejectedBy') ? (
                      <FieldDisplay label="Rejected By" value="***HIDDEN***" isProtected={true} />
                    ) : (
                      <FieldDisplay label="Rejected By" value={loader.rejectedBy || '-'} />
                    )}
                    {isFieldProtected('rejectedAt') ? (
                      <FieldDisplay label="Rejected At" value="***HIDDEN***" isProtected={true} />
                    ) : (
                      <FieldDisplay
                        label="Rejected At"
                        value={loader.rejectedAt ? formatDateTime(loader.rejectedAt) : '-'}
                      />
                    )}
                  </>
                )}
              </div>
            </div>
          </div>
        )}

        {/* Source Database */}
        {loader.sourceDatabase && (
          <div>
            <h4 className="text-sm font-semibold mb-3 text-muted-foreground">Source Database Connection</h4>
            <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
              <FieldDisplay
                label="Database Code"
                value={loader.sourceDatabase.dbCode || '-'}
              />
              <FieldDisplay
                label="Database Type"
                value={
                  <Badge variant="outline">
                    {loader.sourceDatabase.dbType || '-'}
                  </Badge>
                }
              />
              <FieldDisplay
                label="Host"
                value={loader.sourceDatabase.ip || '-'}
                tooltip={`Connection host: ${loader.sourceDatabase.ip}`}
              />
              <FieldDisplay
                label="Port"
                value={loader.sourceDatabase.port || '-'}
              />
              <FieldDisplay
                label="Database Name"
                value={loader.sourceDatabase.dbName || '-'}
              />
              <FieldDisplay
                label="Username"
                value={loader.sourceDatabase.userName || '-'}
              />
            </div>
          </div>
        )}

        {/* Execution Configuration */}
        <div>
          <h4 className="text-sm font-semibold mb-3 text-muted-foreground">Execution Configuration</h4>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            {isFieldProtected('minIntervalSeconds') ? (
              <FieldDisplay label="Min Interval" value="***HIDDEN***" isProtected={true} />
            ) : (
              <FieldDisplay label="Min Interval" value={formatSeconds(loader.minIntervalSeconds)} />
            )}

            {isFieldProtected('maxIntervalSeconds') ? (
              <FieldDisplay label="Max Interval" value="***HIDDEN***" isProtected={true} />
            ) : (
              <FieldDisplay label="Max Interval" value={formatSeconds(loader.maxIntervalSeconds)} />
            )}

            {isFieldProtected('maxQueryPeriodSeconds') ? (
              <FieldDisplay label="Max Query Period" value="***HIDDEN***" isProtected={true} />
            ) : (
              <FieldDisplay label="Max Query Period" value={formatSeconds(loader.maxQueryPeriodSeconds)} />
            )}

            {isFieldProtected('maxParallelExecutions') ? (
              <FieldDisplay label="Max Parallel Executions" value="***HIDDEN***" isProtected={true} />
            ) : (
              <FieldDisplay label="Max Parallel Executions" value={loader.maxParallelExecutions} />
            )}
          </div>
        </div>

        {/* Metadata */}
        <div>
          <h4 className="text-sm font-semibold mb-3 text-muted-foreground">Metadata</h4>
          <div className="grid grid-cols-2 md:grid-cols-4 gap-4 text-sm">
            {isFieldProtected('aggregationPeriodSeconds') ? (
              <FieldDisplay label="Aggregation Period" value="***HIDDEN***" isProtected={true} />
            ) : (
              <FieldDisplay
                label="Aggregation Period"
                value={loader.aggregationPeriodSeconds
                  ? formatSeconds(loader.aggregationPeriodSeconds)
                  : '-'
                }
                tooltip={loader.aggregationPeriodSeconds
                  ? `Data aggregated in ${formatSeconds(loader.aggregationPeriodSeconds)} intervals`
                  : 'No aggregation configured'
                }
              />
            )}

            {isFieldProtected('createdAt') ? (
              <FieldDisplay label="Created At" value="***HIDDEN***" isProtected={true} />
            ) : (
              <FieldDisplay
                label="Created At"
                value={loader.createdAt ? formatDateTime(loader.createdAt) : '-'}
              />
            )}

            {isFieldProtected('updatedAt') ? (
              <FieldDisplay label="Updated At" value="***HIDDEN***" isProtected={true} />
            ) : (
              <FieldDisplay
                label="Updated At"
                value={loader.updatedAt ? formatDateTime(loader.updatedAt) : '-'}
              />
            )}

            {isFieldProtected('createdBy') ? (
              <FieldDisplay label="Created By" value="***HIDDEN***" isProtected={true} />
            ) : (
              <FieldDisplay
                label="Created By"
                value={loader.createdBy || '-'}
              />
            )}

            {isFieldProtected('updatedBy') ? (
              <FieldDisplay label="Updated By" value="***HIDDEN***" isProtected={true} />
            ) : (
              <FieldDisplay
                label="Updated By"
                value={loader.updatedBy || '-'}
              />
            )}
          </div>
        </div>

        {/* SQL Query */}
        {isFieldProtected('loaderSql') ? (
          <div>
            <h4 className="text-sm font-semibold mb-3 text-muted-foreground flex items-center gap-2">
              SQL Query
              <span title="Field hidden due to permission restrictions">
                <EyeOff className="h-4 w-4 text-amber-600" />
              </span>
            </h4>
            <div className="bg-muted/50 p-4 rounded-md border border-dashed">
              <p className="text-sm text-muted-foreground italic flex items-center gap-2">
                <EyeOff className="h-4 w-4" />
                SQL query hidden due to permission restrictions
              </p>
            </div>
          </div>
        ) : loader.loaderSql ? (
          <div>
            <h4 className="text-sm font-semibold mb-3 text-muted-foreground">SQL Query</h4>
            <SqlCodeBlock
              sql={loader.loaderSql}
              validateSql={true}
            />
          </div>
        ) : null}
      </CardContent>

      {/* Actions Footer - 8 buttons per row with wrapping */}
      <CardFooter className="flex-col items-start gap-3 border-t pt-4 bg-muted/10">
        <h4 className="text-sm font-semibold text-muted-foreground">Available Actions</h4>
        <div className="w-full flex flex-wrap gap-2">
          {actions.map((action) => (
            <LoaderActionButton
              key={action.id}
              action={action}
              showLabel={true}
              className="min-w-[100px] max-w-[150px]" // Constrain width for label truncation
            />
          ))}
        </div>
      </CardFooter>
    </Card>
  );
}

// Helper component for displaying field label-value pairs
interface FieldDisplayProps {
  label: string;
  value: React.ReactNode;
  tooltip?: string;
  isProtected?: boolean;
}

function FieldDisplay({ label, value, tooltip, isProtected = false }: FieldDisplayProps) {
  return (
    <div title={tooltip}>
      <p className="text-muted-foreground text-xs mb-1 flex items-center gap-1">
        {label}
        {isProtected && (
          <span title="Field hidden due to permission restrictions">
            <EyeOff className="h-3 w-3 text-amber-600" />
          </span>
        )}
      </p>
      <p className="font-medium">
        {typeof value === 'string' || typeof value === 'number' ? value : value}
      </p>
    </div>
  );
}

function formatSeconds(seconds: number): string {
  const hours = Math.floor(seconds / 3600);
  const days = Math.floor(hours / 24);
  if (days > 0) return `${days}d`;
  if (hours > 0) return `${hours}h`;
  const minutes = Math.floor(seconds / 60);
  if (minutes > 0) return `${minutes}m`;
  return `${seconds}s`;
}

function formatTimezoneOffset(hours?: number): string {
  if (hours === undefined || hours === null) return 'UTC+00:00';
  const sign = hours >= 0 ? '+' : '-';
  const absHours = Math.abs(hours);
  return `UTC${sign}${absHours.toString().padStart(2, '0')}:00`;
}

function formatDateTime(isoString: string): string {
  try {
    const date = new Date(isoString);
    return date.toLocaleString('en-US', {
      year: 'numeric',
      month: 'short',
      day: '2-digit',
      hour: '2-digit',
      minute: '2-digit',
      hour12: false
    });
  } catch {
    return isoString;
  }
}
