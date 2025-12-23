/**
 * API Configuration for Loader Management UI
 *
 * In Kubernetes cluster, the frontend will call the backend service using internal DNS:
 * - Service Name: signal-loader
 * - Namespace: monitoring-app
 * - Port: 8080
 *
 * The API_BASE_URL can be overridden at build time using environment variables.
 */

// For cluster deployment (production): Use relative path, NGINX proxy will handle routing
// For local development: Use full URL to backend service
export const API_BASE_URL = import.meta.env.VITE_API_BASE_URL || '/api';

export const API_ENDPOINTS = {
  // Authentication
  LOGIN: '/v1/auth/login',

  // Loaders
  LOADERS_LIST: '/v1/res/loaders/loaders',
  LOADER_DETAILS: (code: string) => `/v1/res/loaders/${code}`,
  CREATE_LOADER: '/v1/res/loaders',
  UPDATE_LOADER: (code: string) => `/v1/res/loaders/${code}`,
  DELETE_LOADER: (code: string) => `/v1/res/loaders/${code}`,

  // Source Databases
  SOURCES_LIST: '/v1/admin/res/db-sources',

  // Signals
  QUERY_SIGNALS: (loaderCode: string) => `/v1/res/signals/signal/${loaderCode}`,
} as const;
