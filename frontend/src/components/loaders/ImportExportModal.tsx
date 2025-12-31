import { useState } from 'react';
import { Dialog, DialogContent, DialogHeader, DialogTitle } from '../ui/dialog';
import { Button } from '../ui/button';
import { Download, Upload, FileSpreadsheet, X, AlertCircle, CheckCircle, Loader2 } from 'lucide-react';
import { Loader } from '../../types/loader';
import * as XLSX from 'xlsx';
import { useToast } from '../../hooks/use-toast';
import { importApi, ImportResultDto } from '../../api/import';

interface ImportExportModalProps {
  open: boolean;
  onClose: () => void;
  loaders: Loader[];
  protectedFields: string[];
  userRole?: string;
}

// Define all available loader fields for export
interface LoaderField {
  key: keyof Loader | 'sourceDatabase';
  label: string;
  isProtected: (fields: string[]) => boolean;
  getValue: (loader: Loader, protectedFields: string[]) => any;
}

// Importable fields for Excel template (subset of all fields)
const IMPORTABLE_FIELDS: string[] = [
  'Loader Code',
  'SQL Query',
  'Min Interval (seconds)',
  'Max Interval (seconds)',
  'Query Period (seconds)',
  'Max Parallel Executions',
  'Source Database Code',
  'Purge Strategy',
  'Timezone Offset (hours)',
  'Aggregation Period (seconds)',
];

const LOADER_FIELDS: LoaderField[] = [
  {
    key: 'loaderCode',
    label: 'Loader Code',
    isProtected: (fields) => fields.includes('loaderCode'),
    getValue: (loader, fields) => fields.includes('loaderCode') ? '***protected***' : loader.loaderCode,
  },
  {
    key: 'loaderSql',
    label: 'SQL Query',
    isProtected: (fields) => fields.includes('loaderSql'),
    getValue: (loader, fields) => fields.includes('loaderSql') ? '***protected***' : loader.loaderSql,
  },
  {
    key: 'enabled',
    label: 'Enabled',
    isProtected: (fields) => fields.includes('enabled'),
    getValue: (loader, fields) => fields.includes('enabled') ? '***protected***' : (loader.enabled ? 'TRUE' : 'FALSE'),
  },
  {
    key: 'approvalStatus',
    label: 'Approval Status',
    isProtected: (fields) => fields.includes('approvalStatus'),
    getValue: (loader, fields) => fields.includes('approvalStatus') ? '***protected***' : (loader.approvalStatus || '-'),
  },
  {
    key: 'minIntervalSeconds',
    label: 'Min Interval (seconds)',
    isProtected: (fields) => fields.includes('minIntervalSeconds'),
    getValue: (loader, fields) => fields.includes('minIntervalSeconds') ? '***protected***' : loader.minIntervalSeconds,
  },
  {
    key: 'maxIntervalSeconds',
    label: 'Max Interval (seconds)',
    isProtected: (fields) => fields.includes('maxIntervalSeconds'),
    getValue: (loader, fields) => fields.includes('maxIntervalSeconds') ? '***protected***' : loader.maxIntervalSeconds,
  },
  {
    key: 'maxQueryPeriodSeconds',
    label: 'Query Period (seconds)',
    isProtected: (fields) => fields.includes('maxQueryPeriodSeconds'),
    getValue: (loader, fields) => fields.includes('maxQueryPeriodSeconds') ? '***protected***' : loader.maxQueryPeriodSeconds,
  },
  {
    key: 'maxParallelExecutions',
    label: 'Max Parallel Executions',
    isProtected: (fields) => fields.includes('maxParallelExecutions'),
    getValue: (loader, fields) => fields.includes('maxParallelExecutions') ? '***protected***' : loader.maxParallelExecutions,
  },
  {
    key: 'purgeStrategy',
    label: 'Purge Strategy',
    isProtected: (fields) => fields.includes('purgeStrategy'),
    getValue: (loader, fields) => fields.includes('purgeStrategy') ? '***protected***' : (loader.purgeStrategy || '-'),
  },
  {
    key: 'sourceTimezoneOffsetHours',
    label: 'Timezone Offset (hours)',
    isProtected: (fields) => fields.includes('sourceTimezoneOffsetHours'),
    getValue: (loader, fields) => fields.includes('sourceTimezoneOffsetHours') ? '***protected***' : (loader.sourceTimezoneOffsetHours ?? 0),
  },
  {
    key: 'aggregationPeriodSeconds',
    label: 'Aggregation Period (seconds)',
    isProtected: (fields) => fields.includes('aggregationPeriodSeconds'),
    getValue: (loader, fields) => fields.includes('aggregationPeriodSeconds') ? '***protected***' : (loader.aggregationPeriodSeconds || '-'),
  },
  {
    key: 'consecutiveZeroRecordRuns',
    label: 'Zero Record Runs',
    isProtected: (fields) => fields.includes('consecutiveZeroRecordRuns'),
    getValue: (loader, fields) => fields.includes('consecutiveZeroRecordRuns') ? '***protected***' : (loader.consecutiveZeroRecordRuns || 0),
  },
  {
    key: 'sourceDatabase',
    label: 'Source Database Code',
    isProtected: (fields) => fields.includes('sourceDatabase'),
    getValue: (loader, fields) => {
      if (fields.includes('sourceDatabase')) return '***protected***';
      return loader.sourceDatabase?.dbCode || '-';
    },
  },
  {
    key: 'createdAt',
    label: 'Created At',
    isProtected: (fields) => fields.includes('createdAt'),
    getValue: (loader, fields) => fields.includes('createdAt') ? '***protected***' : (loader.createdAt || '-'),
  },
  {
    key: 'updatedAt',
    label: 'Updated At',
    isProtected: (fields) => fields.includes('updatedAt'),
    getValue: (loader, fields) => fields.includes('updatedAt') ? '***protected***' : (loader.updatedAt || '-'),
  },
  {
    key: 'createdBy',
    label: 'Created By',
    isProtected: (fields) => fields.includes('createdBy'),
    getValue: (loader, fields) => fields.includes('createdBy') ? '***protected***' : (loader.createdBy || '-'),
  },
  {
    key: 'updatedBy',
    label: 'Updated By',
    isProtected: (fields) => fields.includes('updatedBy'),
    getValue: (loader, fields) => fields.includes('updatedBy') ? '***protected***' : (loader.updatedBy || '-'),
  },
  {
    key: 'approvedBy',
    label: 'Approved By',
    isProtected: (fields) => fields.includes('approvedBy'),
    getValue: (loader, fields) => fields.includes('approvedBy') ? '***protected***' : (loader.approvedBy || '-'),
  },
  {
    key: 'approvedAt',
    label: 'Approved At',
    isProtected: (fields) => fields.includes('approvedAt'),
    getValue: (loader, fields) => fields.includes('approvedAt') ? '***protected***' : (loader.approvedAt || '-'),
  },
  {
    key: 'rejectedBy',
    label: 'Rejected By',
    isProtected: (fields) => fields.includes('rejectedBy'),
    getValue: (loader, fields) => fields.includes('rejectedBy') ? '***protected***' : (loader.rejectedBy || '-'),
  },
  {
    key: 'rejectedAt',
    label: 'Rejected At',
    isProtected: (fields) => fields.includes('rejectedAt'),
    getValue: (loader, fields) => fields.includes('rejectedAt') ? '***protected***' : (loader.rejectedAt || '-'),
  },
  {
    key: 'rejectionReason',
    label: 'Rejection Reason',
    isProtected: (fields) => fields.includes('rejectionReason'),
    getValue: (loader, fields) => fields.includes('rejectionReason') ? '***protected***' : (loader.rejectionReason || '-'),
  },
];

// Default columns (currently visible in table)
const DEFAULT_SELECTED_FIELDS: (keyof Loader | 'sourceDatabase')[] = [
  'loaderCode',
  'enabled',
  'approvalStatus',
  'sourceTimezoneOffsetHours',
  'consecutiveZeroRecordRuns',
  'aggregationPeriodSeconds',
];

export function ImportExportModal({ open, onClose, loaders, protectedFields, userRole }: ImportExportModalProps) {
  const { toast } = useToast();
  const [selectedFields, setSelectedFields] = useState<Set<string>>(
    new Set(DEFAULT_SELECTED_FIELDS)
  );
  const [selectedFile, setSelectedFile] = useState<File | null>(null);
  const [isImporting, setIsImporting] = useState(false);
  const [importResult, setImportResult] = useState<ImportResultDto | null>(null);

  const isAdmin = userRole === 'ROLE_ADMIN';  // Fixed: Check for 'ROLE_ADMIN' not 'ADMIN'

  // Debug logging
  console.log('[issue_blocked_imp] ImportExportModal initialized:', {
    open,
    userRole,
    isAdmin,
    loadersCount: loaders.length,
    protectedFieldsCount: protectedFields.length,
    timestamp: new Date().toISOString()
  });

  const toggleField = (fieldKey: string) => {
    const newSelected = new Set(selectedFields);
    if (newSelected.has(fieldKey)) {
      newSelected.delete(fieldKey);
    } else {
      newSelected.add(fieldKey);
    }
    setSelectedFields(newSelected);
  };

  const toggleAll = () => {
    if (selectedFields.size === LOADER_FIELDS.length) {
      setSelectedFields(new Set());
    } else {
      setSelectedFields(new Set(LOADER_FIELDS.map(f => f.key as string)));
    }
  };

  const handleExport = () => {
    if (selectedFields.size === 0) {
      toast({
        title: 'No columns selected',
        description: 'Please select at least one column to export',
        variant: 'destructive',
      });
      return;
    }

    if (loaders.length === 0) {
      toast({
        title: 'No loaders to export',
        description: 'There are no loaders in the current view',
        variant: 'destructive',
      });
      return;
    }

    try {
      // Filter selected fields
      const selectedFieldDefs = LOADER_FIELDS.filter(f => selectedFields.has(f.key as string));

      // Create headers
      const headers = selectedFieldDefs.map(f => f.label);

      // Create data rows
      const rows = loaders.map(loader =>
        selectedFieldDefs.map(field => field.getValue(loader, protectedFields))
      );

      // Create worksheet
      const ws = XLSX.utils.aoa_to_sheet([headers, ...rows]);

      // Create workbook
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, 'Loaders');

      // Generate filename with timestamp
      const timestamp = new Date().toISOString().replace(/[:.]/g, '-').split('T');
      const filename = `loaders-export-${timestamp[0]}-${timestamp[1].split('-')[0]}.xlsx`;

      // Download file
      XLSX.writeFile(wb, filename);

      toast({
        title: 'Export successful',
        description: `Exported ${loaders.length} loader(s) to Excel`,
      });

      onClose();
    } catch (error) {
      toast({
        title: 'Export failed',
        description: error instanceof Error ? error.message : 'Failed to export loaders',
        variant: 'destructive',
      });
    }
  };

  const handleDownloadTemplate = () => {
    try {
      // Create template with importable field headers + Import Action column
      const headers = [...IMPORTABLE_FIELDS, 'Import Action'];

      // Add instruction row showing field placeholders
      const instructionRow = [...IMPORTABLE_FIELDS.map(f => `[${f}]`), 'CREATE|UPDATE|SKIP'];

      // Sample loaders with actual data
      const sampleLoaders = [
        [
          'SALES_DATA',
          `SELECT
  FLOOR(UNIX_TIMESTAMP(timestamp) / 60) * 60 AS load_time_stamp,
  product AS segment_1,
  NULL AS segment_2,
  NULL AS segment_3,
  NULL AS segment_4,
  NULL AS segment_5,
  NULL AS segment_6,
  NULL AS segment_7,
  NULL AS segment_8,
  NULL AS segment_9,
  NULL AS segment_10,
  COUNT(*) AS rec_count,
  SUM(amount) AS sum_val,
  AVG(amount) AS avg_val,
  MAX(amount) AS max_val,
  MIN(amount) AS min_val
FROM sales_data
WHERE timestamp >= STR_TO_DATE(':fromTime', '%Y-%m-%d %H:%i')
  AND timestamp <  STR_TO_DATE(':toTime',   '%Y-%m-%d %H:%i')
GROUP BY load_time_stamp, segment_1, segment_2, segment_3,
  segment_4, segment_5, segment_6, segment_7, segment_8, segment_9, segment_10
ORDER BY load_time_stamp`,
          60,
          120,
          86400,
          2,
          'TEST_MYSQL',
          'FAIL_ON_DUPLICATE',
          0,
          60,
          'CREATE'
        ],
        [
          'USER_ACTIVITY',
          `SELECT
  FLOOR(UNIX_TIMESTAMP(timestamp) / 60) * 60 AS load_time_stamp,
  action AS segment_1,
  NULL AS segment_2,
  NULL AS segment_3,
  NULL AS segment_4,
  NULL AS segment_5,
  NULL AS segment_6,
  NULL AS segment_7,
  NULL AS segment_8,
  NULL AS segment_9,
  NULL AS segment_10,
  COUNT(*) AS rec_count,
  SUM(session_duration) AS sum_val,
  AVG(session_duration) AS avg_val,
  MAX(session_duration) AS max_val,
  MIN(session_duration) AS min_val
FROM user_activity
WHERE timestamp >= STR_TO_DATE(':fromTime', '%Y-%m-%d %H:%i')
  AND timestamp <  STR_TO_DATE(':toTime',   '%Y-%m-%d %H:%i')
GROUP BY load_time_stamp, segment_1, segment_2, segment_3,
  segment_4, segment_5, segment_6, segment_7, segment_8, segment_9, segment_10
ORDER BY load_time_stamp`,
          60,
          120,
          86400,
          2,
          'TEST_MYSQL',
          'FAIL_ON_DUPLICATE',
          0,
          60,
          'CREATE'
        ],
        [
          'SENSOR_READINGS',
          `SELECT
  FLOOR(UNIX_TIMESTAMP(timestamp) / 60) * 60 AS load_time_stamp,
  location AS segment_1,
  NULL AS segment_2,
  NULL AS segment_3,
  NULL AS segment_4,
  NULL AS segment_5,
  NULL AS segment_6,
  NULL AS segment_7,
  NULL AS segment_8,
  NULL AS segment_9,
  NULL AS segment_10,
  COUNT(*) AS rec_count,
  SUM(temperature) AS sum_val,
  AVG(temperature) AS avg_val,
  MAX(temperature) AS max_val,
  MIN(temperature) AS min_val
FROM sensor_readings
WHERE timestamp >= STR_TO_DATE(':fromTime', '%Y-%m-%d %H:%i')
  AND timestamp <  STR_TO_DATE(':toTime',   '%Y-%m-%d %H:%i')
GROUP BY load_time_stamp, segment_1, segment_2, segment_3,
  segment_4, segment_5, segment_6, segment_7, segment_8, segment_9, segment_10
ORDER BY load_time_stamp`,
          60,
          120,
          86400,
          2,
          'TEST_MYSQL',
          'FAIL_ON_DUPLICATE',
          0,
          60,
          'CREATE'
        ]
      ];

      // Create worksheet with headers, instructions, and sample data
      const ws = XLSX.utils.aoa_to_sheet([headers, instructionRow, ...sampleLoaders]);

      // Set column widths
      ws['!cols'] = [
        { wch: 20 },  // Loader Code
        { wch: 80 },  // SQL Query (wide for readability)
        { wch: 20 },  // Min Interval
        { wch: 20 },  // Max Interval
        { wch: 20 },  // Query Period
        { wch: 20 },  // Max Parallel
        { wch: 20 },  // Source DB Code
        { wch: 20 },  // Purge Strategy
        { wch: 20 },  // Timezone Offset
        { wch: 20 },  // Aggregation Period
        { wch: 15 },  // Import Action
      ];

      // Create workbook
      const wb = XLSX.utils.book_new();
      XLSX.utils.book_append_sheet(wb, ws, 'Loaders');

      // Download template with timestamp
      const timestamp = new Date().toISOString().split('T')[0];
      const filename = `loader-import-template-${timestamp}.xlsx`;
      XLSX.writeFile(wb, filename);

      toast({
        title: 'Template downloaded',
        description: 'Excel template with sample loaders downloaded successfully',
      });
    } catch (error) {
      toast({
        title: 'Template download failed',
        description: error instanceof Error ? error.message : 'Failed to generate template',
        variant: 'destructive',
      });
    }
  };

  const handleFileSelect = (event: React.ChangeEvent<HTMLInputElement>) => {
    const file = event.target.files?.[0];
    if (file) {
      if (!file.name.endsWith('.xlsx')) {
        toast({
          title: 'Invalid file type',
          description: 'Please select an Excel file (.xlsx)',
          variant: 'destructive',
        });
        return;
      }
      setSelectedFile(file);
    }
  };

  const handleImport = async () => {
    if (!selectedFile) {
      toast({
        title: 'No file selected',
        description: 'Please select an Excel file to import',
        variant: 'destructive',
      });
      return;
    }

    setIsImporting(true);
    setImportResult(null);

    try {
      console.log('[issue_blocked_imp] Starting import:', {
        fileName: selectedFile.name,
        fileSize: selectedFile.size,
        timestamp: new Date().toISOString()
      });

      const result = await importApi.uploadImportFile(selectedFile);

      console.log('[issue_blocked_imp] Import completed:', {
        result,
        timestamp: new Date().toISOString()
      });

      setImportResult(result);

      // Show success toast
      if (result.errorCount === 0) {
        toast({
          title: 'Import successful',
          description: `Successfully processed ${result.totalRows} row(s): ${result.created} created, ${result.updated} updated`,
        });

        // Close modal after successful import
        setTimeout(() => {
          onClose();
          // Reload page to show new loaders
          window.location.reload();
        }, 2000);
      } else {
        toast({
          title: 'Import completed with errors',
          description: `Processed ${result.successCount} row(s), ${result.errorCount} error(s). Check details below.`,
          variant: 'destructive',
        });
      }
    } catch (error: any) {
      console.error('[issue_blocked_imp] Import failed:', error);

      toast({
        title: 'Import failed',
        description: error.response?.data?.message || error.message || 'Failed to import loaders',
        variant: 'destructive',
      });
    } finally {
      setIsImporting(false);
    }
  };

  return (
    <Dialog open={open} onOpenChange={onClose}>
      <DialogContent className="max-w-4xl max-h-[85vh] overflow-hidden flex flex-col">
        <DialogHeader>
          <div className="flex items-center justify-between">
            <DialogTitle className="text-xl font-bold">Import / Export Loaders</DialogTitle>
            <Button variant="ghost" size="icon" onClick={onClose}>
              <X className="h-4 w-4" />
            </Button>
          </div>
        </DialogHeader>

        <div className="flex-1 overflow-y-auto space-y-6 py-4">
          {/* Import Section */}
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <Upload className="h-5 w-5" />
              <h3 className="text-lg font-semibold">Import from Excel</h3>
              {!isAdmin && (
                <span className="text-xs bg-amber-100 text-amber-800 px-2 py-1 rounded">
                  ADMIN only
                </span>
              )}
            </div>

            {!isAdmin ? (
              <div className="border rounded-lg p-6 bg-muted/30">
                <p className="text-sm text-muted-foreground">
                  Only users with ADMIN role can import loader configurations
                </p>
              </div>
            ) : (
              <div className="border rounded-lg p-4 space-y-4">
                <div className="flex items-center gap-3">
                  <Button
                    variant="outline"
                    size="sm"
                    onClick={handleDownloadTemplate}
                    className="gap-2"
                  >
                    <FileSpreadsheet className="h-4 w-4" />
                    Download Template
                  </Button>
                  <p className="text-sm text-muted-foreground">
                    Download the Excel template to fill with loader data
                  </p>
                </div>

                <div className="space-y-2">
                  <label className="block">
                    <input
                      type="file"
                      accept=".xlsx"
                      onChange={handleFileSelect}
                      className="hidden"
                      id="file-upload"
                    />
                    <Button
                      variant="outline"
                      size="sm"
                      onClick={() => document.getElementById('file-upload')?.click()}
                      className="gap-2"
                    >
                      <Upload className="h-4 w-4" />
                      Select Excel File
                    </Button>
                  </label>
                  {selectedFile && (
                    <p className="text-sm text-muted-foreground">
                      Selected: <span className="font-medium">{selectedFile.name}</span>
                    </p>
                  )}
                </div>

                <Button
                  onClick={handleImport}
                  disabled={!selectedFile || isImporting}
                  className="gap-2"
                  size="sm"
                >
                  {isImporting ? (
                    <>
                      <Loader2 className="h-4 w-4 animate-spin" />
                      Processing...
                    </>
                  ) : (
                    <>
                      <Upload className="h-4 w-4" />
                      Import Loaders
                    </>
                  )}
                </Button>

                {/* Import Result */}
                {importResult && (
                  <div className={`border rounded-lg p-4 space-y-3 ${
                    importResult.errorCount === 0 ? 'bg-green-50 border-green-200' : 'bg-amber-50 border-amber-200'
                  }`}>
                    <div className="flex items-center gap-2">
                      {importResult.errorCount === 0 ? (
                        <>
                          <CheckCircle className="h-5 w-5 text-green-600" />
                          <h4 className="font-semibold text-green-900">Import Successful</h4>
                        </>
                      ) : (
                        <>
                          <AlertCircle className="h-5 w-5 text-amber-600" />
                          <h4 className="font-semibold text-amber-900">Import Completed with Errors</h4>
                        </>
                      )}
                    </div>
                    <div className="grid grid-cols-2 gap-2 text-sm">
                      <div>Total Rows: <span className="font-medium">{importResult.totalRows}</span></div>
                      <div>Success: <span className="font-medium text-green-600">{importResult.successCount}</span></div>
                      <div>Created: <span className="font-medium">{importResult.created}</span></div>
                      <div>Updated: <span className="font-medium">{importResult.updated}</span></div>
                      <div>Skipped: <span className="font-medium">{importResult.skipped}</span></div>
                      <div>Errors: <span className="font-medium text-red-600">{importResult.errorCount}</span></div>
                    </div>
                    {importResult.errors && importResult.errors.length > 0 && (
                      <div className="mt-3 space-y-1 max-h-32 overflow-y-auto">
                        <p className="text-sm font-medium text-amber-900">Error Details:</p>
                        {importResult.errors.map((error, idx) => (
                          <div key={idx} className="text-xs bg-white/50 p-2 rounded">
                            Row {error.row}, Field: {error.field} - {error.errorMessage}
                          </div>
                        ))}
                      </div>
                    )}
                  </div>
                )}
              </div>
            )}
          </div>

          {/* Separator */}
          <div className="border-t" />

          {/* Export Section */}
          <div className="space-y-4">
            <div className="flex items-center gap-2">
              <Download className="h-5 w-5" />
              <h3 className="text-lg font-semibold">Export to Excel</h3>
            </div>

            <div className="flex items-center justify-between">
              <p className="text-sm text-muted-foreground">
                Select columns to include in Excel export ({selectedFields.size} selected)
              </p>
              <Button variant="outline" size="sm" onClick={toggleAll}>
                {selectedFields.size === LOADER_FIELDS.length ? 'Deselect All' : 'Select All'}
              </Button>
            </div>

            {/* Column Selection - Scrollable */}
            <div className="border rounded-lg p-4 max-h-64 overflow-y-auto">
              <div className="grid grid-cols-2 gap-3">
                {LOADER_FIELDS.map((field) => {
                  const isProtected = field.isProtected(protectedFields);
                  const isSelected = selectedFields.has(field.key as string);

                  return (
                    <label
                      key={field.key as string}
                      className={`flex items-center gap-2 p-2 rounded cursor-pointer hover:bg-muted/50 transition-colors ${
                        isProtected ? 'opacity-60' : ''
                      }`}
                    >
                      <input
                        type="checkbox"
                        checked={isSelected}
                        onChange={() => toggleField(field.key as string)}
                        className="h-4 w-4"
                      />
                      <span className="text-sm">
                        {field.label}
                        {isProtected && (
                          <span className="ml-2 text-xs text-amber-600 font-medium">
                            (Protected)
                          </span>
                        )}
                      </span>
                    </label>
                  );
                })}
              </div>
            </div>

            <Button onClick={handleExport} className="gap-2" size="sm">
              <Download className="h-4 w-4" />
              Export to Excel ({loaders.length} loaders)
            </Button>
          </div>

          {/* Footer Buttons */}
          <div className="flex justify-end gap-2 pt-4 border-t">
            <Button variant="outline" onClick={onClose}>
              Close
            </Button>
          </div>
        </div>
      </DialogContent>
    </Dialog>
  );
}