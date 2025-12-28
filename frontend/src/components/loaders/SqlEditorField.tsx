import { useState, useEffect } from 'react';
import { Textarea } from '@/components/ui/textarea';
import { Button } from '@/components/ui/button';
import { Label } from '@/components/ui/label';
import { AlertCircle, CheckCircle2, Play, Loader2, Lock } from 'lucide-react';
import { Alert, AlertDescription } from '@/components/ui/alert';

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

interface TestQueryResult {
  success: boolean;
  message: string;
  rowCount?: number;
  executionTime?: number;
  sampleData?: any[];
  errors?: string[];
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
  const [testResult, setTestResult] = useState<TestQueryResult | null>(null);

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
    // TODO: Implement SQL test query functionality
    // API Endpoint: POST /api/v1/res/loaders/test-query
    // Request body: { sourceDatabaseId: number, sql: string }
    // Response: { success: boolean, message: string, rowCount?: number, executionTimeMs?: number, sampleData?: any[], errors?: string[] }

    setTestResult({
      success: false,
      message: '🚧 Coming Soon: SQL Test Query feature is under development',
      errors: ['This feature will allow you to test your SQL query against the source database before saving the loader.'],
    });
  };

  return (
    <div className="space-y-4">
      {/* SQL Textarea with dark theme styling to match detail view */}
      <div className="space-y-2">
        <Label htmlFor="loaderSql" className="text-sm font-medium">
          SQL Query {required && <span className="text-red-500">*</span>}
        </Label>
        <div className="relative">
          <Textarea
            id="loaderSql"
            value={value}
            onChange={(e) => onChange(e.target.value)}
            placeholder="SELECT ..."
            disabled={disabled}
            className="font-mono text-xs min-h-[200px] resize-y bg-[rgb(30,30,30)] text-gray-100 border-border/50"
            style={{
              tabSize: 2,
              lineHeight: '1.5',
            }}
          />
          {disabled && (
            <div className="absolute top-2 right-2">
              <Lock className="h-4 w-4 text-amber-600" />
            </div>
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

      {/* Test Query Button - DISABLED */}
      <div className="flex gap-2">
        <Button
          type="button"
          variant="outline"
          size="sm"
          onClick={handleTestQuery}
          disabled={true}
          className="gap-2"
          title="Coming Soon - Feature under development"
        >
          <Play className="h-4 w-4" />
          Test Query (Coming Soon)
        </Button>
        {isTesting && (
          <span className="text-sm text-muted-foreground flex items-center">
            Executing query against source database...
          </span>
        )}
      </div>

      {/* Test Results */}
      {testResult && (
        <Alert variant={testResult.success ? 'default' : 'destructive'}>
          {testResult.success ? (
            <CheckCircle2 className="h-4 w-4" />
          ) : (
            <AlertCircle className="h-4 w-4" />
          )}
          <AlertDescription>
            <div className="space-y-2">
              <p className="font-medium">{testResult.message}</p>

              {testResult.errors && testResult.errors.length > 0 && (
                <ul className="list-disc list-inside space-y-1 text-sm">
                  {testResult.errors.map((err, idx) => (
                    <li key={idx}>{err}</li>
                  ))}
                </ul>
              )}

              {testResult.success && testResult.rowCount !== undefined && (
                <p className="text-sm">
                  ✓ Returned {testResult.rowCount} row(s) in {testResult.executionTime}ms
                </p>
              )}
            </div>
          </AlertDescription>
        </Alert>
      )}

      {/* Help Text */}
      <div className="text-xs text-muted-foreground space-y-1 bg-muted/30 p-3 rounded-md">
        <p className="font-semibold">SQL Requirements:</p>
        <p><strong>Query Type:</strong> SELECT only (read-only)</p>
        <p><strong>Required Clauses:</strong> GROUP BY (with load_time_stamp and segments), WHERE (with :fromTime and :toTime parameters)</p>
        <p><strong>Aggregation Functions:</strong> COUNT(*), SUM(), AVG(), MAX(), MIN()</p>
        <p><strong>Length:</strong> 10-10,000 characters</p>
        <p><strong>Test:</strong> "Test Query" feature coming soon to validate execution against source database</p>
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
  const foundDangerous = dangerousKeywords.filter(keyword => upperSql.includes(keyword));
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
