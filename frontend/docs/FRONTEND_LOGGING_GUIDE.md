# Frontend Logging Guide

## Overview

The frontend logging system provides structured logging with production controls, console output, and backend shipping for critical events.

## Features

- **Structured Logging**: Consistent log format with context
- **Production Controls**: Auto-disable console logs in production
- **Backend Shipping**: Send warnings/errors to backend for monitoring
- **Log Levels**: DEBUG, INFO, WARN, ERROR
- **Context Tracking**: User ID, session ID, page, build number
- **Error Capture**: Automatic capture of uncaught errors and unhandled promises
- **Performance Monitoring**: Track operation durations
- **Batching**: Efficient log shipping with configurable batch size and flush interval

## Quick Start

```typescript
import logger from '@/lib/logger';

// Basic logging
logger.debug('Debug message');
logger.info('User logged in', { userId: '123' });
logger.warn('API slow response', { endpoint: '/api/loaders', duration: 5000 });
logger.error('Failed to fetch loaders', error, { endpoint: '/api/loaders' });

// Performance logging
const start = performance.now();
// ... operation ...
const duration = performance.now() - start;
logger.performance('fetchLoaders', duration, { count: 10 });
```

## Configuration

### Environment Variables

Create `.env.development` and `.env.production` files:

**Development (.env.development):**
```env
VITE_ENABLE_CONSOLE_LOGS=true
VITE_ENABLE_BACKEND_LOGS=false
VITE_MIN_CONSOLE_LEVEL=DEBUG
VITE_MIN_BACKEND_LEVEL=WARN
VITE_LOG_BATCH_SIZE=10
VITE_LOG_FLUSH_INTERVAL=5000
```

**Production (.env.production):**
```env
VITE_ENABLE_CONSOLE_LOGS=false
VITE_ENABLE_BACKEND_LOGS=true
VITE_MIN_CONSOLE_LEVEL=ERROR
VITE_MIN_BACKEND_LEVEL=WARN
VITE_LOG_BATCH_SIZE=20
VITE_LOG_FLUSH_INTERVAL=10000
```

### Runtime Configuration

```typescript
import logger from '@/lib/logger';

// Configure logger at runtime
logger.configure({
  enableConsole: false,         // Disable console logging
  enableBackend: true,          // Enable backend shipping
  minConsoleLevel: LogLevel.ERROR,
  minBackendLevel: LogLevel.WARN,
  backendEndpoint: '/api/logs/frontend',
  batchSize: 20,
  flushInterval: 10000,
});

// Set user context
logger.setUserId('user-123');
```

## Usage Examples

### Basic Logging

```typescript
import logger from '@/lib/logger';

// Debug - development only
logger.debug('Component rendered', { component: 'LoadersListPage' });

// Info - general information
logger.info('Loaders fetched successfully', { count: 15 });

// Warn - potential issues
logger.warn('Slow API response', {
  endpoint: '/api/loaders',
  duration: 3000
});

// Error - with error object
try {
  await api.fetchLoaders();
} catch (error) {
  logger.error('Failed to fetch loaders', error as Error, {
    endpoint: '/api/loaders',
    retry: 0,
  });
}
```

### React Component Integration

```typescript
import { useEffect } from 'react';
import logger from '@/lib/logger';

export function LoadersListPage() {
  useEffect(() => {
    logger.info('LoadersListPage mounted');

    return () => {
      logger.debug('LoadersListPage unmounted');
    };
  }, []);

  const handleFetchLoaders = async () => {
    const start = performance.now();

    try {
      logger.debug('Fetching loaders...');
      const loaders = await api.fetchLoaders();

      const duration = performance.now() - start;
      logger.performance('fetchLoaders', duration, {
        count: loaders.length
      });

      logger.info('Loaders fetched', { count: loaders.length });
    } catch (error) {
      logger.error('Failed to fetch loaders', error as Error);
      throw error;
    }
  };

  return <div>...</div>;
}
```

### API Integration

```typescript
import logger from '@/lib/logger';
import axios from 'axios';

// Add request interceptor
axios.interceptors.request.use(
  (config) => {
    logger.debug('API Request', {
      method: config.method,
      url: config.url,
      params: config.params,
    });
    return config;
  },
  (error) => {
    logger.error('API Request Error', error);
    return Promise.reject(error);
  }
);

// Add response interceptor
axios.interceptors.response.use(
  (response) => {
    logger.debug('API Response', {
      status: response.status,
      url: response.config.url,
    });
    return response;
  },
  (error) => {
    logger.error('API Response Error', error, {
      status: error.response?.status,
      url: error.config?.url,
      data: error.response?.data,
    });
    return Promise.reject(error);
  }
);
```

### Performance Monitoring

```typescript
import logger from '@/lib/logger';

// Measure component render time
function ComponentWithPerformanceTracking() {
  useEffect(() => {
    const start = performance.now();

    // Component logic...

    const duration = performance.now() - start;
    logger.performance('ComponentRender', duration, {
      component: 'ComponentWithPerformanceTracking',
    });
  }, []);

  return <div>...</div>;
}

// Measure API call time
async function fetchWithPerformanceTracking() {
  const start = performance.now();

  try {
    const result = await api.fetchLoaders();
    const duration = performance.now() - start;

    logger.performance('fetchLoaders', duration, {
      count: result.length,
      cached: false,
    });

    return result;
  } catch (error) {
    const duration = performance.now() - start;
    logger.performance('fetchLoaders', duration, {
      success: false,
      error: true,
    });
    throw error;
  }
}
```

## Log Entry Structure

All logs follow this structure:

```typescript
{
  timestamp: "2025-12-31T14:30:15.123Z",
  level: "ERROR",
  message: "Failed to fetch loaders",
  context: {
    endpoint: "/api/loaders",
    error: "Network timeout",
    stack: "Error: Network timeout\n  at fetchLoaders..."
  },
  page: "/loaders",
  userId: "user-123",
  sessionId: "1735653015-abc123xyz",
  buildNumber: "1735653015"
}
```

## Production Behavior

### Console Logs

In production (`VITE_ENABLE_CONSOLE_LOGS=false`):
- **Console is silent** - no logs printed to browser console
- Users cannot see debug information
- Reduces console noise
- Improves performance (no string formatting)

### Backend Shipping

In production (`VITE_ENABLE_BACKEND_LOGS=true`):
- Only **WARN and ERROR** levels are shipped to backend
- Logs are **batched** (default: 20 logs or 10 seconds)
- **Automatic retry** on network failure (silent failure)
- Includes full context (user, session, page, build)

### Error Capturing

Automatic capture (always enabled):
- **Uncaught errors**: `window.onerror`
- **Unhandled promise rejections**: `window.onunhandledrejection`
- Shipped to backend for monitoring

## Backend Integration

### Backend Endpoint

The logger expects a backend endpoint at `/api/logs/frontend`:

```http
POST /api/logs/frontend
Authorization: Bearer <token>
Content-Type: application/json

{
  "logs": [
    {
      "timestamp": "2025-12-31T14:30:15.123Z",
      "level": "ERROR",
      "message": "Failed to fetch loaders",
      "context": { ... },
      "page": "/loaders",
      "userId": "user-123",
      "sessionId": "1735653015-abc123xyz",
      "buildNumber": "1735653015"
    }
  ]
}
```

### Backend Controller Example (Java/Spring)

```java
@RestController
@RequestMapping("/api/logs")
public class FrontendLogsController {

  @PostMapping("/frontend")
  public ResponseEntity<Void> receiveFrontendLogs(
      @RequestBody FrontendLogsRequest request,
      @AuthenticationPrincipal UserDetails userDetails
  ) {
    for (FrontendLogEntry log : request.getLogs()) {
      // Store in database or forward to logging system
      log.info("Frontend log: {} - {}", log.getLevel(), log.getMessage());

      // If ERROR level, create alert or notification
      if ("ERROR".equals(log.getLevel())) {
        alertService.createAlert(log);
      }
    }

    return ResponseEntity.ok().build();
  }
}
```

## Best Practices

### 1. Log Levels

- **DEBUG**: Development information (function calls, state changes)
- **INFO**: Business logic events (user actions, successful operations)
- **WARN**: Potential issues (slow responses, deprecation warnings)
- **ERROR**: Failures (API errors, validation errors, exceptions)

### 2. Context

Always include relevant context:

```typescript
// Good
logger.error('Failed to save loader', error, {
  loaderCode: 'LOADER_001',
  endpoint: '/api/loaders',
  retry: 2,
  userId: currentUser.id,
});

// Bad
logger.error('Error occurred', error);
```

### 3. Sensitive Data

**Never log sensitive data:**
- Passwords
- Tokens
- API keys
- Personal information (SSN, credit cards)

```typescript
// Bad
logger.debug('Login attempt', { password: 'secret123' });

// Good
logger.debug('Login attempt', { username: 'user@example.com' });
```

### 4. Performance

Use DEBUG level for frequent logs:

```typescript
// This runs on every keystroke - use DEBUG
const handleInputChange = (value: string) => {
  logger.debug('Input changed', { value, length: value.length });
};
```

### 5. Error Handling

Always include error object:

```typescript
try {
  await api.fetchLoaders();
} catch (error) {
  // Good - includes error object
  logger.error('Failed to fetch loaders', error as Error, {
    endpoint: '/api/loaders'
  });

  // Bad - missing error object
  logger.error('Failed to fetch loaders', undefined, {
    endpoint: '/api/loaders'
  });
}
```

## Testing

### Enable Console in Production (Debugging)

```typescript
import logger from '@/lib/logger';

// Temporarily enable console for debugging
logger.configure({
  enableConsole: true,
  minConsoleLevel: LogLevel.DEBUG,
});
```

### View Logs

```javascript
// In browser console
localStorage.setItem('debug_logging', 'true');
// Reload page

// Disable
localStorage.removeItem('debug_logging');
// Reload page
```

## Troubleshooting

### Logs Not Appearing in Console

1. Check production mode: `import.meta.env.PROD`
2. Check configuration: `logger.configure({ enableConsole: true })`
3. Check log level: `logger.configure({ minConsoleLevel: LogLevel.DEBUG })`

### Logs Not Shipping to Backend

1. Check network tab for POST requests to `/api/logs/frontend`
2. Check backend endpoint is implemented
3. Check authentication token is valid
4. Check CORS configuration

### Too Many Logs

1. Increase minimum level: `logger.configure({ minConsoleLevel: LogLevel.WARN })`
2. Disable backend shipping: `logger.configure({ enableBackend: false })`
3. Increase flush interval: `logger.configure({ flushInterval: 30000 })`

---

## Additional Resources

- [Vite Environment Variables](https://vitejs.dev/guide/env-and-mode.html)
- [React Error Boundaries](https://reactjs.org/docs/error-boundaries.html)
- [Performance API](https://developer.mozilla.org/en-US/docs/Web/API/Performance)
