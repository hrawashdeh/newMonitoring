import { useState, useEffect } from 'react';
import { Prism as SyntaxHighlighter } from 'react-syntax-highlighter';
import { vscDarkPlus } from 'react-syntax-highlighter/dist/esm/styles/prism';
import { AlertCircle, CheckCircle2 } from 'lucide-react';

interface SqlCodeBlockProps {
  sql: string;
  validateSql?: boolean;
  className?: string;
}

interface SqlValidationResult {
  isValid: boolean;
  errors: string[];
  warnings: string[];
}

/**
 * SQL Code Block Component
 * - Displays SQL with syntax highlighting
 * - Optionally validates SQL and shows errors
 * - Read-only view mode (edit mode will be added in future)
 */
export function SqlCodeBlock({ sql, validateSql = false, className = '' }: SqlCodeBlockProps) {
  const [validation, setValidation] = useState<SqlValidationResult | null>(null);

  useEffect(() => {
    if (validateSql) {
      const result = validateSqlQuery(sql);
      setValidation(result);
    }
  }, [sql, validateSql]);

  return (
    <div className={`space-y-2 ${className}`}>
      {/* Validation Results */}
      {validateSql && validation && (
        <div className="space-y-1">
          {validation.isValid ? (
            <div className="flex items-center gap-2 text-sm text-green-600">
              <CheckCircle2 className="h-4 w-4" />
              <span>SQL syntax appears valid</span>
            </div>
          ) : (
            <div className="space-y-1">
              {validation.errors.map((error, idx) => (
                <div key={idx} className="flex items-start gap-2 text-sm text-red-600">
                  <AlertCircle className="h-4 w-4 mt-0.5 flex-shrink-0" />
                  <span>{error}</span>
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

      {/* SQL Code Display */}
      <div className="relative rounded-md overflow-hidden border border-border/50">
        <SyntaxHighlighter
          language="sql"
          style={vscDarkPlus}
          customStyle={{
            margin: 0,
            padding: '1rem',
            fontSize: '0.75rem',
            lineHeight: '1.5',
            background: 'rgb(30, 30, 30)',
          }}
          showLineNumbers={true}
          wrapLines={true}
          lineNumberStyle={{
            minWidth: '2.5em',
            paddingRight: '1em',
            color: 'rgb(133, 133, 133)',
            userSelect: 'none',
          }}
        >
          {sql}
        </SyntaxHighlighter>
      </div>
    </div>
  );
}

/**
 * Basic SQL Validation
 * - Checks for common SQL errors and warnings
 * - NOT a full SQL parser, just basic sanity checks
 */
function validateSqlQuery(sql: string): SqlValidationResult {
  const errors: string[] = [];
  const warnings: string[] = [];

  // Trim whitespace
  const trimmed = sql.trim();

  // Empty SQL
  if (!trimmed) {
    errors.push('SQL query is empty');
    return { isValid: false, errors, warnings };
  }

  // Convert to uppercase for keyword checking
  const upperSql = trimmed.toUpperCase();

  // Check if it's a SELECT query (required for loaders)
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
  const doubleQuotes = (sql.match(/"/g) || []).length;
  if (singleQuotes % 2 !== 0) {
    errors.push(`Unclosed single quote (') detected`);
  }
  if (doubleQuotes % 2 !== 0) {
    warnings.push(`Unclosed double quote (") detected - verify if intentional`);
  }

  // Check for semicolon at end (not required but common)
  if (!trimmed.endsWith(';')) {
    warnings.push('SQL query does not end with semicolon (;) - recommended but not required');
  }

  // Check for common keywords that shouldn't be in loader queries
  const dangerousKeywords = ['DROP', 'DELETE', 'UPDATE', 'INSERT', 'TRUNCATE', 'ALTER', 'CREATE'];
  const foundDangerous = dangerousKeywords.filter(keyword => upperSql.includes(keyword));
  if (foundDangerous.length > 0) {
    errors.push(`Loader queries should be read-only (SELECT). Found: ${foundDangerous.join(', ')}`);
  }

  // Check for FROM clause (required for SELECT)
  if (upperSql.startsWith('SELECT') && !upperSql.includes('FROM')) {
    warnings.push('SELECT query without FROM clause - verify if intentional');
  }

  // Check for suspicious patterns
  if (upperSql.includes('--')) {
    warnings.push('SQL contains comments (--) - verify they are intentional');
  }

  if (upperSql.includes('/*') && !upperSql.includes('*/')) {
    errors.push('Unclosed block comment (/* without */)');
  }

  // Determine validity
  const isValid = errors.length === 0;

  return { isValid, errors, warnings };
}
