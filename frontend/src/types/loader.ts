/**
 * Type definitions for Loader Management
 * Based on loader-service API contracts
 */

export type LoaderStatus = 'ACTIVE' | 'PAUSED' | 'FAILED';

export type DatabaseType = 'POSTGRESQL' | 'MYSQL';

export interface SourceDatabase {
  id?: number;
  dbCode: string;
  ip: string;
  port: number;
  dbName: string;
  dbType: string; // "MYSQL" | "POSTGRESQL"
  userName: string;
  // Password is encrypted and never returned in API responses
  passWord?: string;
}

export interface Segment {
  segmentCode: string;
  segmentDescription?: string;
}

/**
 * Action links for role-based permissions
 * Following HATEOAS pattern - backend controls which actions are available
 */
export interface LoaderActionLinks {
  toggleEnabled?: { href: string; method: string }; // Can pause/resume
  forceStart?: { href: string; method: string };    // Can force execution
  editLoader?: { href: string; method: string };    // Can edit configuration (EDIT_LOADER)
  deleteLoader?: { href: string; method: string };  // Can delete loader (DELETE_LOADER)
  viewDetails?: { href: string; method: string };    // Can view details
  viewSignals?: { href: string; method: string };    // Can view signal data
  viewExecutionLog?: { href: string; method: string }; // Can view execution history
  viewAlerts?: { href: string; method: string };     // Can view associated alerts
  approveLoader?: { href: string; method: string };  // Can approve loader (ADMIN only)
  rejectLoader?: { href: string; method: string };   // Can reject loader (ADMIN only)
}

export interface Loader {
  id: number;
  loaderCode: string;
  loaderSql: string; // Encrypted in backend
  minIntervalSeconds: number;
  maxIntervalSeconds: number;
  maxQueryPeriodSeconds: number;
  maxParallelExecutions: number;
  enabled: boolean;
  purgeStrategy?: PurgeStrategy; // Strategy for handling duplicate data
  sourceTimezoneOffsetHours?: number; // Timezone offset in hours (e.g., 0, -5, +3)
  consecutiveZeroRecordRuns?: number; // Count of consecutive runs with 0 records
  aggregationPeriodSeconds?: number; // Time window for data aggregation (60 = 1 minute)
  createdAt?: string; // ISO 8601 timestamp
  updatedAt?: string; // ISO 8601 timestamp
  createdBy?: string; // User who created this loader
  updatedBy?: string; // User who last updated this loader
  // Approval workflow fields (read-only - cannot be modified via update endpoint)
  approvalStatus?: ApprovalStatus; // Current approval status
  approvedBy?: string; // Username of admin who approved
  approvedAt?: string; // ISO 8601 timestamp when approved
  rejectedBy?: string; // Username of admin who rejected
  rejectedAt?: string; // ISO 8601 timestamp when rejected
  rejectionReason?: string; // Reason for rejection (helps creator fix issues)
  sourceDatabase?: SourceDatabase; // Source database connection info (password excluded)
  _links?: LoaderActionLinks; // Role-based action permissions (HATEOAS)
}

/**
 * Purge Strategy for handling duplicate data
 */
export type PurgeStrategy = 'FAIL_ON_DUPLICATE' | 'PURGE_AND_RELOAD' | 'SKIP_DUPLICATES';

/**
 * Approval workflow status
 */
export type ApprovalStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';

/**
 * Loader Create/Update Request (matches backend EtlLoaderDto)
 */
export interface CreateLoaderRequest {
  loaderCode: string;
  loaderSql: string;
  minIntervalSeconds: number;
  maxIntervalSeconds: number;
  maxQueryPeriodSeconds: number;
  maxParallelExecutions: number;
  sourceDatabaseId: number;
  purgeStrategy?: PurgeStrategy;
  enabled?: boolean;
  sourceTimezoneOffsetHours?: number;
  aggregationPeriodSeconds?: number;
}

export interface UpdateLoaderRequest {
  loaderCode: string;
  loaderSql?: string;
  minIntervalSeconds?: number;
  maxIntervalSeconds?: number;
  maxQueryPeriodSeconds?: number;
  maxParallelExecutions?: number;
  sourceDatabaseId?: number;
  purgeStrategy?: PurgeStrategy;
  enabled?: boolean;
  sourceTimezoneOffsetHours?: number;
  aggregationPeriodSeconds?: number;
}

export interface SignalData {
  timestamp: number; // epoch milliseconds
  recordCount: number;
  minValue: number;
  maxValue: number;
  avgValue: number;
  segmentCode?: string;
}

export interface SignalsQueryParams {
  fromEpoch: number;
  toEpoch: number;
  segmentCode?: string;
}

/**
 * Loaders Overview Statistics
 * Operational metrics for loader management
 */
export interface LoadersStats {
  total: number;
  active: number;
  paused: number;
  failed: number;
  pendingApproval: number;
  approved: number;
  rejected: number;
  trend?: {
    activeChange: string; // e.g., "+8%"
    period: string; // e.g., "24h"
  };
}

/**
 * Activity Event Types
 */
export type ActivityEventType =
  | 'EXECUTION_SUCCESS'
  | 'EXECUTION_FAILED'
  | 'LOADER_CREATED'
  | 'LOADER_UPDATED'
  | 'LOADER_PAUSED'
  | 'LOADER_RESUMED'
  | 'LOADER_DELETED'
  | 'BACKFILL_COMPLETED'
  | 'BACKFILL_FAILED';

export type ActivityEventStatus = 'success' | 'error' | 'warning' | 'info';

/**
 * Recent Activity Event
 */
export interface ActivityEvent {
  timestamp: string; // ISO 8601 format
  type: ActivityEventType;
  loaderCode?: string;
  message: string;
  status: ActivityEventStatus;
  details?: Record<string, unknown>;
}

/**
 * Test Query Request
 */
export interface TestQueryRequest {
  sourceDatabaseId: number;
  sql: string;
}

/**
 * Test Query Response
 */
export interface TestQueryResponse {
  success: boolean;
  message: string;
  rowCount?: number;
  totalRowCount?: number;
  executionTimeMs?: number;
  sampleData?: Array<Record<string, any>>;
  errors?: string[];
  warnings?: string[];
}
