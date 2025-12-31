/**
 * Frontend Logging Utility with Production Controls
 *
 * Features:
 * - Structured console logging
 * - Backend log shipping (errors, warnings, critical events)
 * - Production console disable option
 * - Log levels: DEBUG, INFO, WARN, ERROR
 * - Context tracking (user, session, page)
 * - Performance monitoring
 *
 * Author: Hassan Rawashdeh
 * Date: 2025-12-31
 */

import { BUILD_INFO } from '../buildInfo';

// Log levels
export enum LogLevel {
  DEBUG = 'DEBUG',
  INFO = 'INFO',
  WARN = 'WARN',
  ERROR = 'ERROR',
}

// Log entry structure
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
  private flushTimer: NodeJS.Timeout | null = null;

  constructor() {
    // Auto-detect production mode
    const isProduction = import.meta.env.PROD ||
                        import.meta.env.MODE === 'production' ||
                        window.location.hostname !== 'localhost';

    // Default configuration
    this.config = {
      enableConsole: !isProduction, // Disable console in production by default
      minConsoleLevel: LogLevel.DEBUG,
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

    // Log initialization
    this.debug('Logger initialized', {
      isProduction,
      consoleEnabled: this.config.enableConsole,
      backendEnabled: this.config.enableBackend,
      buildNumber: BUILD_INFO.buildNumber,
    });

    // Capture unhandled errors
    this.setupErrorHandlers();
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
    this.info(`Performance: ${metric}`, { ...context, duration, metric: 'performance' });
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
   * Log to console with formatting
   */
  private logToConsole(entry: LogEntry): void {
    const prefix = `[${entry.timestamp}] [${entry.level}]`;
    const style = this.getConsoleStyle(entry.level);

    switch (entry.level) {
      case LogLevel.DEBUG:
        console.debug(`%c${prefix}`, style, entry.message, entry.context || '');
        break;
      case LogLevel.INFO:
        console.info(`%c${prefix}`, style, entry.message, entry.context || '');
        break;
      case LogLevel.WARN:
        console.warn(`%c${prefix}`, style, entry.message, entry.context || '');
        break;
      case LogLevel.ERROR:
        console.error(`%c${prefix}`, style, entry.message, entry.context || '');
        if (entry.context?.stack) {
          console.error(entry.context.stack);
        }
        break;
    }
  }

  /**
   * Get console style for log level
   */
  private getConsoleStyle(level: LogLevel): string {
    const styles = {
      [LogLevel.DEBUG]: 'color: #6B7280; font-weight: normal;',
      [LogLevel.INFO]: 'color: #3B82F6; font-weight: bold;',
      [LogLevel.WARN]: 'color: #F59E0B; font-weight: bold;',
      [LogLevel.ERROR]: 'color: #EF4444; font-weight: bold;',
    };
    return styles[level];
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
    }, this.config.flushInterval || 5000);
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

export default logger;
