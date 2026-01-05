import axios, { AxiosError, InternalAxiosRequestConfig } from 'axios';
import { API_BASE_URL } from './api-config';
import logger from './logger';

// Extend axios config to include timing metadata
interface TimedAxiosRequestConfig extends InternalAxiosRequestConfig {
  metadata?: {
    startTime: number;
  };
}

const apiClient = axios.create({
  baseURL: API_BASE_URL,
  headers: {
    'Content-Type': 'application/json',
  },
});

// Request interceptor: Add JWT token and logging
apiClient.interceptors.request.use(
  (config: TimedAxiosRequestConfig) => {
    // Add auth token
    const token = localStorage.getItem('auth_token');
    if (token) {
      config.headers.Authorization = `Bearer ${token}`;
    }

    // Track request timing
    config.metadata = { startTime: Date.now() };

    // Log outbound request
    const method = config.method?.toUpperCase() || 'GET';
    const url = config.url || '';
    logger.apiRequest(method, url, {
      requestSize: config.data ? JSON.stringify(config.data).length : 0,
    });

    return config;
  },
  (error) => {
    logger.error('[INTEGRATION_REQUEST] Request setup failed', error);
    return Promise.reject(error);
  }
);

// Response interceptor: Handle 401 errors and logging
apiClient.interceptors.response.use(
  (response) => {
    // Calculate duration
    const config = response.config as TimedAxiosRequestConfig;
    const duration = config.metadata?.startTime
      ? Date.now() - config.metadata.startTime
      : 0;

    // Extract correlation ID from response headers if present
    const correlationId = response.headers['x-correlation-id'];
    if (correlationId) {
      logger.setCorrelationId(correlationId);
    }

    // Log successful response
    const method = config.method?.toUpperCase() || 'GET';
    const url = config.url || '';
    logger.apiResponse(method, url, response.status, duration, {
      responseSize: response.data ? JSON.stringify(response.data).length : 0,
    });

    return response;
  },
  (error: AxiosError) => {
    // Calculate duration
    const config = error.config as TimedAxiosRequestConfig | undefined;
    const duration = config?.metadata?.startTime
      ? Date.now() - config.metadata.startTime
      : 0;

    const method = config?.method?.toUpperCase() || 'GET';
    const url = config?.url || '';
    const status = error.response?.status || 0;

    // Log error response
    logger.apiResponse(method, url, status, duration, {
      errorMessage: error.message,
      errorCode: error.code,
    });

    if (status === 401) {
      // Token expired or invalid - clear auth and redirect to login
      localStorage.removeItem('auth_token');
      localStorage.removeItem('auth_user');
      logger.warn('Authentication expired, redirecting to login');
      window.location.href = '/login';
    }

    return Promise.reject(error);
  }
);

export default apiClient;
