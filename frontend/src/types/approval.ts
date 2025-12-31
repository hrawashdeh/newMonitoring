/**
 * Type definitions for Approval Workflow
 * Based on loader-service approval API contracts
 */

export type EntityType = 'LOADER' | 'DASHBOARD' | 'INCIDENT' | 'CHART' | 'ALERT_RULE';

export type RequestType = 'CREATE' | 'UPDATE' | 'DELETE';

export type ApprovalStatus = 'PENDING_APPROVAL' | 'APPROVED' | 'REJECTED';

export type ActionType = 'SUBMIT' | 'APPROVE' | 'REJECT' | 'RESUBMIT' | 'REVOKE' | 'UPDATE_REQUEST';

export type SourceType = 'WEB_UI' | 'IMPORT' | 'API' | 'MANUAL';

/**
 * Approval Action (audit trail entry)
 */
export interface ApprovalAction {
  id: number;
  approvalRequestId: number;
  actionType: ActionType;
  actionBy: string;
  actionAt: string; // ISO 8601 timestamp
  justification?: string;
  previousStatus?: ApprovalStatus;
  newStatus?: ApprovalStatus;
}

/**
 * Approval Request
 */
export interface ApprovalRequest {
  id: number;
  entityType: EntityType;
  entityId: string; // loader code, dashboard id, etc.
  requestType: RequestType;
  approvalStatus: ApprovalStatus;
  requestedBy: string;
  requestedAt: string; // ISO 8601 timestamp
  requestData: any; // JSON object - proposed new state
  currentData?: any; // JSON object - current state (for UPDATE)
  changeSummary?: string;
  source?: SourceType;
  importLabel?: string; // For import batches
  approvedBy?: string;
  approvedAt?: string; // ISO 8601 timestamp
  rejectedBy?: string;
  rejectedAt?: string; // ISO 8601 timestamp
  rejectionReason?: string;
  actions?: ApprovalAction[]; // Optional audit trail
}

/**
 * Submit Approval Request
 */
export interface SubmitApprovalRequest {
  entityType: string;
  entityId: string;
  requestType: string;
  requestData: any;
  currentData?: any;
  changeSummary?: string;
  requestedBy: string;
  source?: string;
  importLabel?: string;
}

/**
 * Approve Request
 */
export interface ApproveRequest {
  requestId: number;
  justification?: string;
}

/**
 * Reject Request
 */
export interface RejectRequest {
  requestId: number;
  rejectionReason: string; // Required
}

/**
 * Resubmit Request
 */
export interface ResubmitRequest {
  requestId: number;
  updatedRequestData?: any;
  changeSummary?: string;
}

/**
 * Revoke Approval
 */
export interface RevokeApprovalRequest {
  requestId: number;
  revocationReason: string; // Required
}

/**
 * Approval Counts by Entity Type
 */
export interface ApprovalCounts {
  total: number;
  byEntityType: {
    [key in EntityType]?: number;
  };
  byStatus: {
    [key in ApprovalStatus]?: number;
  };
}
