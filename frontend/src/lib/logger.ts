/**
 * Frontend Logging Utility with Production Controls
 *
 * Features:
 * - Structured console logging (JSON format for Elasticsearch)
 * - Backend log shipping (errors, warnings, critical events)
 * - Production console disable option
 * - Runtime console toggle via:
 *   1. LocalStorage: localStorage.setItem('debug_console', 'true')
 *   2. URL parameter: ?debug=true
 *   3. DevTools command: window.__enableDebugConsole()
 * - Log levels: DEBUG, INFO, WARN, ERROR
 * - Context tracking (user, session, page, correlation)
 * - Performance monitoring
 * - Integration request/response marking
 *
 * Author: Hassan Rawashdeh
 * Date: 2025-12-31
 * Updated: 2026-01-05 - Added runtime console toggle
 */

import { BUILD_INFO } from '../buildInfo';

// Debug console storage key
const DEBUG_CONSOLE_KEY = 'debug_console';
const DEBUG_MIN_LEVEL_KEY = 'debug_min_level';

// Log levels
export enum LogLevel {
  DEBUG = 'DEBUG',
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR',
}

// Integration types for marking API calls
export enum IntegrationType {
  API_REQUEST = 'API_REQUEST',
  API_RESPONSE = 'API_RESPONSE',
  OUTBOUND_REQUEST = 'OUTBOUND_REQUEST',
  OUTBOUND_RESPONSE = 'OUTBOUND_RESPONSE',
}

// Log entry structure (matches backend JSON format for Elasticsearch)
export interface LogEntry {
  timestamp: string;
  level: LogLevel;
  message: string;
  context?: Record<string, any>;
  error?: Error;
  stack?: string;
  page?: string;
  userId?: string;
  sessionId?: string;
  buildNumber?: string;
  correlationId?: string;
  // Integration fields
  integration?: {
    type: IntegrationType;
    direction: 'INBOUND' | 'OUTBOUND';
    target: string;
    method: string;
    statusCode?: number;
    durationMs?: number;
    requestSize?: number;
    responseSize?: number;
  };
  // Component context
  component?: string;
  action?: string;
  phase?: 'ENTRY' | 'TRACE' | 'DEBUG' | 'RESULT' | 'EXIT' | 'ERROR';
}

// Logger configuration
interface LoggerConfig {
  // Console logging
  enableConsole: boolean;
  minConsoleLevel: LogLevel;

  // Backend shipping
  enableBackend: boolean;
  minBackendLevel: LogLevel;
  backendEndpoint?: string;
  batchSize?: number;
  flushInterval?: number; // milliseconds

  // Context
  userId?: string;
  sessionId?: string;

  // Production mode detection
  isProduction: boolean;
}

class Logger {
  private config: LoggerConfig;
  private logBuffer: LogEntry[] = [];
  private flushTimer: number | null = null;
  private correlationId: string | null = null;

  constructor() {
    // Auto-detect production mode
    const isProduction = import.meta.env.PROD ||
                        import.meta.env.MODE === 'production' ||
                        window.location.hostname !== 'localhost';

    // Check for debug override from localStorage or URL parameter
    const debugOverride = this.checkDebugOverride();

    // Default configuration
    this.config = {
      enableConsole: debugOverride.enabled ?? !isProduction, // Disable console in production unless overridden
      minConsoleLevel: debugOverride.minLevel ?? LogLevel.DEBUG,
      enableBackend: true, // Always ship to backend
      minBackendLevel: LogLevel.WARN, // Only ship warnings and errors
      backendEndpoint: '/api/logs/frontend',
      batchSize: 10,
      flushInterval: 5000, // 5 seconds
      isProduction,
    };

    // Initialize session ID
    this.config.sessionId = this.getOrCreateSessionId();

    // Start flush timer if backend logging is enabled
    if (this.config.enableBackend) {
      this.startFlushTimer();
    }

    // Setup global debug toggle functions
    this.setupGlobalDebugFunctions();

    // Log initialization (this will only show if console is enabled)
    this.debug('[ENTRY] Logger initialized', {
      isProduction,
      consoleEnabled: this.config.enableConsole,
      backendEnabled: this.config.enableBackend,
      buildNumber: BUILD_INFO.buildNumber,
      debugOverride: debugOverride.source,
    });

    // Capture unhandled errors
    this.setupErrorHandlers();
  }

  /**
   * Check for debug console override from localStorage or URL parameter
   */
  private checkDebugOverride(): { enabled?: boolean; minLevel?: LogLevel; source?: string } {
    // Check URL parameter first (highest priority)
    const urlParams = new URLSearchParams(window.location.search);
    const debugParam = urlParams.get('debug');
    if (debugParam !== null) {
      const enabled = debugParam === 'true' || debugParam === '1' || debugParam === '';
      return { enabled, source: 'url_parameter' };
    }

    // Check localStorage
    const storedDebug = localStorage.getItem(DEBUG_CONSOLE_KEY);
    if (storedDebug !== null) {
      const enabled = storedDebug === 'true' || storedDebug === '1';
      const storedLevel = localStorage.getItem(DEBUG_MIN_LEVEL_KEY);
      const minLevel = storedLevel ? (LogLevel[storedLevel as keyof typeof LogLevel] || LogLevel.DEBUG) : undefined;
      return { enabled, minLevel, source: 'localStorage' };
    }

    return {};
  }

  /**
   * Setup global window functions for runtime debug toggle
   */
  private setupGlobalDebugFunctions(): void {
    // Enable debug console
    (window as any).__enableDebugConsole = (minLevel?: string) => {
      localStorage.setItem(DEBUG_CONSOLE_KEY, 'true');
      if (minLevel) {
        localStorage.setItem(DEBUG_MIN_LEVEL_KEY, minLevel.toUpperCase());
      }
      this.config.enableConsole = true;
      if (minLevel) {
        this.config.minConsoleLevel = LogLevel[minLevel.toUpperCase() as keyof typeof LogLevel] || LogLevel.DEBUG;
      }
      console.log('%c🔧 Debug console ENABLED', 'color: green; font-weight: bold');
      console.log(`   Min level: ${this.config.minConsoleLevel}`);
      console.log('   To disable: window.__disableDebugConsole()');
      return true;
    };

    // Disable debug console
    (window as any).__disableDebugConsole = () => {
      localStorage.removeItem(DEBUG_CONSOLE_KEY);
      localStorage.removeItem(DEBUG_MIN_LEVEL_KEY);
      this.config.enableConsole = false;
      console.log('%c🔧 Debug console DISABLED', 'color: orange; font-weight: bold');
      console.log('   To enable: window.__enableDebugConsole()');
      return true;
    };

    // Get current debug status
    (window as any).__debugConsoleStatus = () => {
      const status = {
        enabled: this.config.enableConsole,
        minLevel: this.config.minConsoleLevel,
        isProduction: this.config.isProduction,
        backendEnabled: this.config.enableBackend,
        minBackendLevel: this.config.minBackendLevel,
        sessionId: this.config.sessionId,
        userId: this.config.userId,
        correlationId: this.correlationId,
      };
      console.table(status);
      return status;
    };

    // Set minimum log level
    (window as any).__setLogLevel = (level: string) => {
      const logLevel = LogLevel[level.toUpperCase() as keyof typeof LogLevel];
      if (logLevel) {
        localStorage.setItem(DEBUG_MIN_LEVEL_KEY, level.toUpperCase());
        this.config.minConsoleLevel = logLevel;
        console.log(`%c🔧 Log level set to: ${logLevel}`, 'color: blue; font-weight: bold');
        return true;
      } else {
        console.error('Invalid log level. Use: DEBUG, INFO, WARN, ERROR');
        return false;
      }
    };
  }

  /**
   * Configure logger
   */
  configure(config: Partial<LoggerConfig>): void {
    this.config = { ...this.config, ...config };

    // Restart flush timer if backend config changed
    if (config.enableBackend !== undefined || config.flushInterval !== undefined) {
      this.stopFlushTimer();
      if (this.config.enableBackend) {
        this.startFlushTimer();
      }
    }
  }

  /**
   * Set user context
   */
  setUserId(userId: string | undefined): void {
    this.config.userId = userId;
  }

  /**
   * Set correlation ID (from API response headers)
   */
  setCorrelationId(correlationId: string | null): void {
    this.correlationId = correlationId;
  }

  /**
   * Get current correlation ID
   */
  getCorrelationId(): string | null {
    return this.correlationId;
  }

  /**
   * Check if console logging is enabled
   */
  isConsoleEnabled(): boolean {
    return this.config.enableConsole;
  }

  /**
   * Enable console logging at runtime
   */
  enableConsole(minLevel?: LogLevel): void {
    this.config.enableConsole = true;
    if (minLevel) {
      this.config.minConsoleLevel = minLevel;
    }
    localStorage.setItem(DEBUG_CONSOLE_KEY, 'true');
    if (minLevel) {
      localStorage.setItem(DEBUG_MIN_LEVEL_KEY, minLevel);
    }
  }

  /**
   * Disable console logging at runtime
   */
  disableConsole(): void {
    this.config.enableConsole = false;
    localStorage.removeItem(DEBUG_CONSOLE_KEY);
    localStorage.removeItem(DEBUG_MIN_LEVEL_KEY);
  }

  /**
   * DEBUG level logging
   */
  debug(message: string, context?: Record<string, any>): void {
    this.log(LogLevel.DEBUG, message, context);
  }

  /**
   * INFO level logging
   */
  info(message: string, context?: Record<string, any>): void {
    this.log(LogLevel.INFO, message, context);
  }

  /**
   * WARN level logging
   */
  warn(message: string, context?: Record<string, any>): void {
    this.log(LogLevel.WARN, message, context);
  }

  /**
   * ERROR level logging
   */
  error(message: string, error?: Error, context?: Record<string, any>): void {
    this.log(LogLevel.ERROR, message, { ...context, error: error?.message, stack: error?.stack });
  }

  /**
   * Log performance metric
   */
  performance(metric: string, duration: number, context?: Record<string, any>): void {
    this.info(`[RESULT] Performance: ${metric}`, { ...context, duration, metric: 'performance' });
  }

  /**
   * Log API request (outbound call to backend)
   */
  apiRequest(method: string, url: string, context?: Record<string, any>): void {
    this.info(`[INTEGRATION_REQUEST] ${method} ${url}`, {
      ...context,
      integration: {
        type: IntegrationType.API_REQUEST,
        direction: 'OUTBOUND',
        target: url,
        method,
      },
    });
  }

  /**
   * Log API response (from backend)
   */
  apiResponse(method: string, url: string, statusCode: number, durationMs: number, context?: Record<string, any>): void {
    const level = statusCode >= 400 ? LogLevel.ERROR : LogLevel.INFO;
    this.log(level, `[INTEGRATION_RESPONSE] ${method} ${url} | status=${statusCode} | duration=${durationMs}ms`, {
      ...context,
      integration: {
        type: IntegrationType.API_RESPONSE,
        direction: 'OUTBOUND',
        target: url,
        method,
        statusCode,
        durationMs,
      },
    });
  }

  /**
   * Log component entry (method entry pattern)
   */
  entry(component: string, action: string, context?: Record<string, any>): void {
    this.debug(`[ENTRY] ${component}.${action}`, {
      ...context,
      component,
      action,
      phase: 'ENTRY',
    });
  }

  /**
   * Log component exit (method exit pattern)
   */
  exit(component: string, action: string, success: boolean, durationMs?: number, context?: Record<string, any>): void {
    this.debug(`[EXIT] ${component}.${action} | success=${success}${durationMs ? ` | duration=${durationMs}ms` : ''}`, {
      ...context,
      component,
      action,
      phase: 'EXIT',
      success,
      durationMs,
    });
  }

  /**
   * Log component result (after action completion)
   */
  result(component: string, action: string, result: string, context?: Record<string, any>): void {
    this.info(`[RESULT] ${component}.${action} | ${result}`, {
      ...context,
      component,
      action,
      phase: 'RESULT',
    });
  }

  /**
   * Log trace step (intermediate operation)
   */
  trace(component: string, step: string, context?: Record<string, any>): void {
    this.debug(`[TRACE] ${component} | ${step}`, {
      ...context,
      component,
      phase: 'TRACE',
    });
  }

  /**
   * Core logging function
   */
  private log(level: LogLevel, message: string, context?: Record<string, any>): void {
    const entry: LogEntry = {
      timestamp: new Date().toISOString(),
      level,
      message,
      context,
      page: window.location.pathname,
      userId: this.config.userId,
      sessionId: this.config.sessionId,
      buildNumber: BUILD_INFO.buildNumber,
      correlationId: this.correlationId || undefined,
      integration: context?.integration,
      component: context?.component,
      action: context?.action,
      phase: context?.phase,
    };

    // Console logging
    if (this.config.enableConsole && this.shouldLogToConsole(level)) {
      this.logToConsole(entry);
    }

    // Backend shipping
    if (this.config.enableBackend && this.shouldLogToBackend(level)) {
      this.addToBuffer(entry);
    }
  }

  /**
   * Check if should log to console
   */
  private shouldLogToConsole(level: LogLevel): boolean {
    const levels = [LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR];
    const minIndex = levels.indexOf(this.config.minConsoleLevel);
    const currentIndex = levels.indexOf(level);
    return currentIndex >= minIndex;
  }

  /**
   * Check if should log to backend
   */
  private shouldLogToBackend(level: LogLevel): boolean {
    const levels = [LogLevel.DEBUG, LogLevel.INFO, LogLevel.WARN, LogLevel.ERROR];
    const minIndex = levels.indexOf(this.config.minBackendLevel);
    const currentIndex = levels.indexOf(level);
    return currentIndex >= minIndex;
  }

  /**
   * Get OpenTelemetry trace context
   */
  private getTraceContext(): { traceId?: string; spanId?: string } {
    try {
      // Access OpenTelemetry context from window if available
      const otel = (window as any).opentelemetry;
      if (otel) {
        const span = otel.trace?.getSpan(otel.context?.active());
        if (span) {
          const spanContext = span.spanContext();
          return {
            traceId: spanContext.traceId,
            spanId: spanContext.spanId,
          };
        }
      }
    } catch (e) {
      // Silently fail if OTel not available
    }
    return {};
  }

  /**
   * Log to console with JSON formatting (Elasticsearch-compatible)
   * Matches the unified logging strategy with nested fields for indexing
   */
  private logToConsole(entry: LogEntry): void {
    // Get OpenTelemetry trace context
    const traceContext = this.getTraceContext();

    // Build structured log entry matching backend/LOGGING_STRATEGY.md format
    const structuredLog: Record<string, any> = {
      '@timestamp': entry.timestamp,
      'log.level': entry.level,
      'service.name': 'frontend',
      'service.version': BUILD_INFO.buildNumber || '1.2.0',
      message: entry.message,
      logger: entry.component || 'frontend',

      // Trace fields (nested for Elasticsearch indexing)
      trace: {
        id: traceContext.traceId,
        span_id: traceContext.spanId,
        correlation_id: entry.correlationId,
        session_id: entry.sessionId,
      },

      // Context fields (nested)
      context: {
        component: entry.component,
        action: entry.action,
        phase: entry.phase,
        page: entry.page,
      },

      // User fields (nested)
      user: {
        id: entry.userId,
      },

      // Environment
      environment: this.config.isProduction ? 'production' : 'development',
    };

    // Add integration fields if present (nested)
    if (entry.integration) {
      structuredLog.integration = {
        type: entry.integration.type,
        direction: entry.integration.direction,
        target: entry.integration.target,
        method: entry.integration.method,
        status_code: entry.integration.statusCode,
        duration_ms: entry.integration.durationMs,
        request_size: entry.integration.requestSize,
        response_size: entry.integration.responseSize,
      };
    }

    // Add extra context if provided (excluding already mapped fields)
    if (entry.context) {
      const extraContext = { ...entry.context };
      delete extraContext.integration;
      delete extraContext.component;
      delete extraContext.action;
      delete extraContext.phase;
      if (Object.keys(extraContext).length > 0) {
        structuredLog.extra = extraContext;
      }
    }

    // Recursively filter out undefined/null values from nested objects
    const cleanObject = (obj: Record<string, any>): Record<string, any> => {
      const cleaned: Record<string, any> = {};
      for (const key of Object.keys(obj)) {
        const value = obj[key];
        if (value !== undefined && value !== null) {
          if (typeof value === 'object' && !Array.isArray(value)) {
            const cleanedNested = cleanObject(value);
            if (Object.keys(cleanedNested).length > 0) {
              cleaned[key] = cleanedNested;
            }
          } else {
            cleaned[key] = value;
          }
        }
      }
      return cleaned;
    };

    const cleanedLog = cleanObject(structuredLog);

    // Output JSON to console
    const jsonOutput = JSON.stringify(cleanedLog, null, 2);

    switch (entry.level) {
      case LogLevel.DEBUG:
        console.debug(jsonOutput);
        break;
      case LogLevel.INFO:
        console.info(jsonOutput);
        break;
      case LogLevel.WARN:
        console.warn(jsonOutput);
        break;
      case LogLevel.ERROR:
        console.error(jsonOutput);
        if (entry.context?.stack) {
          console.error('Stack trace:', entry.context.stack);
        }
        break;
    }
  }

  /**
   * Add log to buffer
   */
  private addToBuffer(entry: LogEntry): void {
    this.logBuffer.push(entry);

    // Flush if buffer is full
    if (this.logBuffer.length >= (this.config.batchSize || 10)) {
      this.flush();
    }
  }

  /**
   * Flush logs to backend
   */
  private async flush(): Promise<void> {
    if (this.logBuffer.length === 0) return;

    const logs = [...this.logBuffer];
    this.logBuffer = [];

    try {
      const response = await fetch(this.config.backendEndpoint || '/api/logs/frontend', {
        method: 'POST',
        headers: {
          'Content-Type': 'application/json',
          'Authorization': `Bearer ${localStorage.getItem('token') || ''}`,
        },
        body: JSON.stringify({ logs }),
      });

      if (!response.ok) {
        // If backend logging fails, just log to console (if enabled)
        if (this.config.enableConsole) {
          console.warn('Failed to ship logs to backend', response.status);
        }
      }
    } catch (error) {
      // Silently fail - don't want logging to break the app
      if (this.config.enableConsole) {
        console.warn('Error shipping logs to backend', error);
      }
    }
  }

  /**
   * Start flush timer
   */
  private startFlushTimer(): void {
    this.flushTimer = setInterval(() => {
      this.flush();
    }, this.config.flushInterval || 5000) as unknown as number;
  }

  /**
   * Stop flush timer
   */
  private stopFlushTimer(): void {
    if (this.flushTimer) {
      clearInterval(this.flushTimer);
      this.flushTimer = null;
    }
  }

  /**
   * Get or create session ID
   */
  private getOrCreateSessionId(): string {
    let sessionId = sessionStorage.getItem('logger_session_id');
    if (!sessionId) {
      sessionId = `${Date.now()}-${Math.random().toString(36).substr(2, 9)}`;
      sessionStorage.setItem('logger_session_id', sessionId);
    }
    return sessionId;
  }

  /**
   * Setup global error handlers
   */
  private setupErrorHandlers(): void {
    // Uncaught errors
    window.addEventListener('error', (event) => {
      this.error('Uncaught error', event.error, {
        message: event.message,
        filename: event.filename,
        lineno: event.lineno,
        colno: event.colno,
      });
    });

    // Unhandled promise rejections
    window.addEventListener('unhandledrejection', (event) => {
      this.error('Unhandled promise rejection', event.reason, {
        promise: event.promise,
      });
    });
  }

  /**
   * Force flush on page unload
   */
  destroy(): void {
    this.stopFlushTimer();
    this.flush();
  }
}

// Singleton instance
const logger = new Logger();

// Force flush on page unload
window.addEventListener('beforeunload', () => {
  logger.destroy();
});

// Export additional types for external use
export { LogLevel, IntegrationType };
export type { LogEntry, LoggerConfig };

export default logger;

/**
 * Usage Examples:
 *
 * // Basic logging
 * logger.debug('Processing data');
 * logger.info('User logged in');
 * logger.warn('Slow query detected');
 * logger.error('Failed to connect', error);
 *
 * // Integration logging (API calls)
 * logger.apiRequest('POST', '/api/v1/loaders');
 * logger.apiResponse('POST', '/api/v1/loaders', 201, 150);
 *
 * // Component lifecycle logging
 * logger.entry('LoaderForm', 'submit', { loaderCode: 'ABC' });
 * logger.trace('LoaderForm', 'Validating SQL syntax');
 * logger.result('LoaderForm', 'submit', 'Loader created successfully');
 * logger.exit('LoaderForm', 'submit', true, 250);
 *
 * // Runtime console toggle (in browser DevTools):
 * window.__enableDebugConsole()       // Enable console logging
 * window.__enableDebugConsole('INFO') // Enable with minimum level
 * window.__disableDebugConsole()      // Disable console logging
 * window.__debugConsoleStatus()       // Show current status
 * window.__setLogLevel('WARN')        // Change minimum level
 *
 * // Or via localStorage:
 * localStorage.setItem('debug_console', 'true')
 * localStorage.setItem('debug_min_level', 'DEBUG')
 *
 * // Or via URL parameter:
 * ?debug=true
 */
