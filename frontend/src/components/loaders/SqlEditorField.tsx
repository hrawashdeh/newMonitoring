import { useState, useEffect } from 'react';
import CodeMirror from '@uiw/react-codemirror';
import { sql } from '@codemirror/lang-sql';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { AlertCircle, CheckCircle2, Lock, Play, Loader2 } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';
import { loadersApi } from '@/api/loaders';
import type { TestQueryResponse } from '@/types/loader';

interface SqlEditorFieldProps {
  value: string;
  onChange: (value: string) => void;
  error?: string;
  disabled?: boolean;
  required?: boolean;
  sourceDatabaseId?: number;
}

interface SqlValidation {
  isValid: boolean;
  errors: string[];
  warnings: string[];
}

export function SqlEditorField({
  value,
  onChange,
  error,
  disabled = false,
  required = false,
  sourceDatabaseId,
}: SqlEditorFieldProps) {
  const [validation, setValidation] = useState<SqlValidation | null>(null);
  const [isTesting, setIsTesting] = useState(false);
  const [testResult, setTestResult] = useState<TestQueryResponse | null>(null);

  // Validate SQL whenever it changes
  useEffect(() => {
    if (value.trim()) {
      const result = validateSqlQuery(value);
      setValidation(result);
    } else {
      setValidation(null);
    }
  }, [value]);

  const handleTestQuery = async () => {
    if (!sourceDatabaseId) {
      setTestResult({
        success: false,
        message: 'Please select a source database first',
        errors: ['Source database is required to test the query'],
      });
      return;
    }

    if (!value.trim()) {
      setTestResult({
        success: false,
        message: 'Please enter a SQL query',
        errors: ['SQL query cannot be empty'],
      });
      return;
    }

    setIsTesting(true);
    setTestResult(null);

    try {
      const response = await loadersApi.testQuery({
        sourceDatabaseId,
        sql: value,
      });

      setTestResult(response);
    } catch (err: any) {
      setTestResult({
        success: false,
        message: 'Failed to execute query',
        errors: [err.message || 'Network error occurred'],
      });
    } finally {
      setIsTesting(false);
    }
  };

  return (
    <div className="space-y-4">
      {/* SQL Editor with Syntax Highlighting */}
      <div className="space-y-2">
        <Label htmlFor="loaderSql" className="text-sm font-medium">
          SQL Query {required && <span className="text-red-500">*</span>}
        </Label>
        <div className="relative">
          {disabled ? (
            // Read-only view with disabled styling
            <div className="relative">
              <CodeMirror
                value={value}
                height="200px"
                extensions={[sql()]}
                editable={false}
                basicSetup={{
                  lineNumbers: true,
                  highlightActiveLineGutter: false,
                  highlightActiveLine: false,
                  foldGutter: false,
                }}
                theme="dark"
                className="border border-border/50 rounded-md overflow-hidden opacity-60"
              />
              <div className="absolute top-2 right-2">
                <Lock className="h-4 w-4 text-amber-600" />
              </div>
            </div>
          ) : (
            // Editable view
            <CodeMirror
              value={value}
              height="200px"
              extensions={[sql()]}
              onChange={(val) => onChange(val)}
              basicSetup={{
                lineNumbers: true,
                highlightActiveLineGutter: true,
                highlightActiveLine: true,
                foldGutter: true,
                bracketMatching: true,
                closeBrackets: true,
                autocompletion: true,
              }}
              theme="dark"
              className="border border-border/50 rounded-md overflow-hidden"
            />
          )}
        </div>
        {error && <p className="text-sm text-red-500">{error}</p>}
      </div>

      {/* Validation Results */}
      {validation && (
        <div className="space-y-2">
          {validation.isValid ? (
            <div className="flex items-center gap-2 text-sm text-green-600">
              <CheckCircle2 className="h-4 w-4" />
              <span>SQL syntax appears valid</span>
            </div>
          ) : (
            <div className="space-y-1">
              {validation.errors.map((err, idx) => (
                <div key={idx} className="flex items-start gap-2 text-sm text-red-600">
                  <AlertCircle className="h-4 w-4 mt-0.5 flex-shrink-0" />
                  <span>{err}</span>
                </div>
              ))}
            </div>
          )}
          {validation.warnings.length > 0 && (
            <div className="space-y-1">
              {validation.warnings.map((warning, idx) => (
                <div key={idx} className="flex items-start gap-2 text-sm text-amber-600">
                  <AlertCircle className="h-4 w-4 mt-0.5 flex-shrink-0" />
                  <span>{warning}</span>
                </div>
              ))}
            </div>
          )}
        </div>
      )}

      {/* Test Query Button */}
      {!disabled && (
        <div className="flex gap-2 items-center">
          <Button
            type="button"
            variant="outline"
            size="sm"
            onClick={handleTestQuery}
            disabled={isTesting || !sourceDatabaseId || !value.trim()}
            className="gap-2"
          >
            {isTesting ? (
              <>
                <Loader2 className="h-4 w-4 animate-spin" />
                Testing Query...
              </>
            ) : (
              <>
                <Play className="h-4 w-4" />
                Test Query
              </>
            )}
          </Button>
          {!sourceDatabaseId && (
            <span className="text-sm text-muted-foreground">
              Select a source database to enable query testing
            </span>
          )}
        </div>
      )}

      {/* Test Results */}
      {testResult && (
        <Alert variant={testResult.success ? 'default' : 'destructive'}>
          {testResult.success ? (
            <CheckCircle2 className="h-4 w-4" />
          ) : (
            <AlertCircle className="h-4 w-4" />
          )}
          <AlertDescription>
            <div className="space-y-3">
              <p className="font-medium">{testResult.message}</p>

              {/* Success metrics */}
              {testResult.success && (
                <div className="flex gap-4 text-sm">
                  <div>
                    <span className="font-semibold">Rows:</span> {testResult.rowCount || 0}
                  </div>
                  <div>
                    <span className="font-semibold">Execution Time:</span>{' '}
                    {testResult.executionTimeMs}ms
                  </div>
                </div>
              )}

              {/* Error messages */}
              {testResult.errors && testResult.errors.length > 0 && (
                <div className="space-y-1">
                  {testResult.errors.map((err, idx) => (
                    <div key={idx} className="text-sm bg-destructive/10 p-2 rounded">
                      {err}
                    </div>
                  ))}
                </div>
              )}

              {/* Sample data table */}
              {testResult.success &&
                testResult.sampleData &&
                testResult.sampleData.length > 0 && (
                  <div className="overflow-x-auto max-h-64 border rounded">
                    <table className="w-full text-sm">
                      <thead className="bg-muted sticky top-0">
                        <tr>
                          {Object.keys(testResult.sampleData[0]).map((col) => (
                            <th
                              key={col}
                              className="px-3 py-2 text-left font-semibold border-b"
                            >
                              {col}
                            </th>
                          ))}
                        </tr>
                      </thead>
                      <tbody>
                        {testResult.sampleData.map((row, rowIdx) => (
                          <tr key={rowIdx} className="border-b hover:bg-muted/50">
                            {Object.values(row).map((val, colIdx) => (
                              <td key={colIdx} className="px-3 py-2">
                                {val !== null ? String(val) : (
                                  <span className="text-muted-foreground italic">NULL</span>
                                )}
                              </td>
                            ))}
                          </tr>
                        ))}
                      </tbody>
                    </table>
                  </div>
                )}
            </div>
          </AlertDescription>
        </Alert>
      )}

      {/* Help Text */}
      <div className="text-xs text-muted-foreground space-y-1 bg-muted/30 p-3 rounded-md">
        <p className="font-semibold">SQL Requirements:</p>
        <p>
          <strong>Query Type:</strong> SELECT only (read-only)
        </p>
        <p>
          <strong>Required Clauses:</strong> GROUP BY (with load_time_stamp and segments), WHERE
          (with :fromTime and :toTime parameters)
        </p>
        <p>
          <strong>Aggregation Functions:</strong> COUNT(*), SUM(), AVG(), MAX(), MIN()
        </p>
        <p>
          <strong>Length:</strong> 10-10,000 characters
        </p>
      </div>
    </div>
  );
}

/**
 * Basic SQL Validation
 */
function validateSqlQuery(sql: string): SqlValidation {
  const errors: string[] = [];
  const warnings: string[] = [];

  const trimmed = sql.trim();

  if (!trimmed) {
    errors.push('SQL query is empty');
    return { isValid: false, errors, warnings };
  }

  const upperSql = trimmed.toUpperCase();

  // Check if SELECT query
  if (!upperSql.startsWith('SELECT')) {
    errors.push('Loader SQL must be a SELECT query');
  }

  // Check for balanced parentheses
  const openCount = (sql.match(/\(/g) || []).length;
  const closeCount = (sql.match(/\)/g) || []).length;
  if (openCount !== closeCount) {
    errors.push(`Unbalanced parentheses: ${openCount} opening, ${closeCount} closing`);
  }

  // Check for unclosed quotes
  const singleQuotes = (sql.match(/'/g) || []).length;
  if (singleQuotes % 2 !== 0) {
    errors.push(`Unclosed single quote (') detected`);
  }

  // Check for dangerous keywords
  const dangerousKeywords = ['DROP', 'DELETE', 'UPDATE', 'INSERT', 'TRUNCATE', 'ALTER', 'CREATE'];
  const foundDangerous = dangerousKeywords.filter((keyword) => upperSql.includes(keyword));
  if (foundDangerous.length > 0) {
    errors.push(`Loader queries must be read-only. Found: ${foundDangerous.join(', ')}`);
  }

  // Check for FROM clause
  if (upperSql.startsWith('SELECT') && !upperSql.includes('FROM')) {
    warnings.push('SELECT query without FROM clause - verify if intentional');
  }

  // Check for comments
  if (upperSql.includes('--')) {
    warnings.push('SQL contains comments (--) - verify they are intentional');
  }

  if (upperSql.includes('/*') && !upperSql.includes('*/')) {
    errors.push('Unclosed block comment (/* without */)');
  }

  const isValid = errors.length === 0;

  return { isValid, errors, warnings };
}