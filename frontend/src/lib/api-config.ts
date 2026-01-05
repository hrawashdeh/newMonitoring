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

/**
 * API Endpoints - Standardized URL Pattern: /api/v1/{service-id}/{controller-id}/{path}
 *
 * Services:
 * - auth: Authentication Service
 * - ldr: Loader Service (includes loaders, signals, segments, approvals, admin)
 * - imex: Import/Export Service
 */
export const API_ENDPOINTS = {
  // Authentication - /api/v1/auth/auth/*
  LOGIN: '/v1/auth/auth/login',
  VALIDATE_TOKEN: '/v1/auth/auth/validate',

  // Menus - /api/v1/auth/menus/*
  MENUS_USER: '/v1/auth/menus',
  MENUS_ALL: '/v1/auth/menus/all',

  // Users - /api/v1/auth/users/*
  USERS_LIST: '/v1/auth/users',
  USER_DETAILS: (id: number) => `/v1/auth/users/${id}`,
  CREATE_USER: '/v1/auth/users',
  UPDATE_USER: (id: number) => `/v1/auth/users/${id}`,
  DELETE_USER: (id: number) => `/v1/auth/users/${id}`,
  CHANGE_PASSWORD: (id: number) => `/v1/auth/users/${id}/change-password`,
  TOGGLE_USER_ENABLED: (id: number) => `/v1/auth/users/${id}/toggle-enabled`,

  // Roles - /api/v1/auth/roles/*
  ROLES_LIST: '/v1/auth/roles',
  ROLE_DETAILS: (id: number) => `/v1/auth/roles/${id}`,
  CREATE_ROLE: '/v1/auth/roles',
  UPDATE_ROLE: (id: number) => `/v1/auth/roles/${id}`,
  DELETE_ROLE: (id: number) => `/v1/auth/roles/${id}`,

  // API Permissions - /api/v1/auth/permissions/*
  PERMISSIONS_ENDPOINTS: '/v1/auth/permissions/endpoints',
  PERMISSIONS_LIST: '/v1/auth/permissions',
  PERMISSIONS_BY_ROLE: '/v1/auth/permissions/by-role',
  PERMISSIONS_BY_ENDPOINT: '/v1/auth/permissions/by-endpoint',
  PERMISSIONS_FOR_ROLE: (role: string) => `/v1/auth/permissions/role/${role}`,
  SET_PERMISSIONS_FOR_ROLE: (role: string) => `/v1/auth/permissions/role/${role}`,
  GRANT_PERMISSION: '/v1/auth/permissions/grant',
  REVOKE_PERMISSION: '/v1/auth/permissions/revoke',

  // Audit - /api/v1/auth/audit/*
  AUDIT_LOGIN_ATTEMPTS: '/v1/auth/audit/login-attempts',
  AUDIT_STATS: '/v1/auth/audit/stats',

  // Loaders - /api/v1/ldr/ldr/*
  LOADERS_LIST: '/v1/ldr/ldr/loaders',
  LOADER_DETAILS: (code: string) => `/v1/ldr/ldr/${code}`,
  CREATE_LOADER: '/v1/ldr/ldr',
  UPDATE_LOADER: (code: string) => `/v1/ldr/ldr/${code}`,
  DELETE_LOADER: (code: string) => `/v1/ldr/ldr/${code}`,
  LOADERS_STATS: '/v1/ldr/ldr/stats',
  LOADERS_ACTIVITY: '/v1/ldr/ldr/activity',
  SOURCE_DATABASES: '/v1/ldr/ldr/source-databases',
  TEST_QUERY: '/v1/ldr/ldr/test-query',

  // Loader Approval Workflow - /api/v1/ldr/ldr/*
  SUBMIT_LOADER: (code: string) => `/v1/ldr/ldr/${code}/submit`,
  APPROVE_LOADER: (code: string) => `/v1/ldr/ldr/${code}/approve`,
  REJECT_LOADER: (code: string) => `/v1/ldr/ldr/${code}/reject`,
  LOADER_VERSIONS: (code: string) => `/v1/ldr/ldr/${code}/versions`,

  // Signals - /api/v1/ldr/sig/*
  QUERY_SIGNALS: (loaderCode: string) => `/v1/ldr/sig/signal/${loaderCode}`,

  // Segments - /api/v1/ldr/seg/*
  SEGMENT_DICTIONARY: '/v1/ldr/seg/dictionary',
  SEGMENT_COMBINATIONS: '/v1/ldr/seg/combinations',

  // Source Databases Admin - /api/v1/ldr/src/*
  SOURCES_LIST: '/v1/ldr/src/db-sources',

  // Admin Operations - /api/v1/ldr/admn/*
  ADMIN_LOADER_STATUS: (code: string) => `/v1/ldr/admn/${code}/status`,
  ADMIN_PAUSE_LOADER: (code: string) => `/v1/ldr/admn/${code}/pause`,
  ADMIN_RESUME_LOADER: (code: string) => `/v1/ldr/admn/${code}/resume`,
  ADMIN_EXECUTION_HISTORY: '/v1/ldr/admn/history',

  // Backfill - /api/v1/ldr/bkfl/*
  BACKFILL_SUBMIT: '/v1/ldr/bkfl/submit',
  BACKFILL_RECENT: '/v1/ldr/bkfl/recent',

  // Approvals - /api/v1/ldr/apv/*
  APPROVALS_PENDING: '/v1/ldr/apv/pending',
  APPROVALS_HISTORY: '/v1/ldr/apv/history',
} as const;
