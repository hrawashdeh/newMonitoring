/**
 * Approval Workflow API Client
 */

import apiClient from '../lib/axios';
import type {
  ApprovalRequest,
  ApproveRequest,
  RejectRequest,
  ResubmitRequest,
  RevokeApprovalRequest,
  EntityType,
} from '../types/approval';

const APPROVALS_BASE_URL = '/approvals';

/**
 * Get all pending approval requests
 */
export async function getAllPendingApprovals(): Promise<ApprovalRequest[]> {
  const response = await apiClient.get<ApprovalRequest[]>(`${APPROVALS_BASE_URL}/pending`);
  return response.data;
}

/**
 * Get all approved approval requests
 */
export async function getAllApprovedApprovals(): Promise<ApprovalRequest[]> {
  const response = await apiClient.get<ApprovalRequest[]>(`${APPROVALS_BASE_URL}/approved`);
  return response.data;
}

/**
 * Get pending approvals by entity type
 */
export async function getPendingApprovalsByType(
  entityType: EntityType
): Promise<ApprovalRequest[]> {
  const response = await apiClient.get<ApprovalRequest[]>(
    `${APPROVALS_BASE_URL}/pending/${entityType}`
  );
  return response.data;
}

/**
 * Get approval request by ID
 */
export async function getApprovalRequest(
  requestId: number,
  includeActions: boolean = false
): Promise<ApprovalRequest> {
  const params = includeActions ? '?includeActions=true' : '';
  const response = await apiClient.get<ApprovalRequest>(
    `${APPROVALS_BASE_URL}/${requestId}${params}`
  );
  return response.data;
}

/**
 * Get approval history for specific entity
 */
export async function getApprovalHistory(
  entityType: EntityType,
  entityId: string
): Promise<ApprovalRequest[]> {
  const response = await apiClient.get<ApprovalRequest[]>(
    `${APPROVALS_BASE_URL}/history/${entityType}/${entityId}`
  );
  return response.data;
}

/**
 * Count pending approvals
 */
export async function countPendingApprovals(
  entityType?: EntityType
): Promise<number> {
  const params = entityType ? `?entityType=${entityType}` : '';
  const response = await apiClient.get<number>(
    `${APPROVALS_BASE_URL}/pending/count${params}`
  );
  return response.data;
}

/**
 * Approve an approval request
 */
export async function approveRequest(
  request: ApproveRequest
): Promise<ApprovalRequest> {
  const response = await apiClient.post<ApprovalRequest>(
    `${APPROVALS_BASE_URL}/approve`,
    request
  );
  return response.data;
}

/**
 * Reject an approval request
 */
export async function rejectRequest(
  request: RejectRequest
): Promise<ApprovalRequest> {
  const response = await apiClient.post<ApprovalRequest>(
    `${APPROVALS_BASE_URL}/reject`,
    request
  );
  return response.data;
}

/**
 * Resubmit a rejected approval request
 */
export async function resubmitRequest(
  request: ResubmitRequest
): Promise<ApprovalRequest> {
  const response = await apiClient.post<ApprovalRequest>(
    `${APPROVALS_BASE_URL}/resubmit`,
    request
  );
  return response.data;
}

/**
 * Revoke an approved request
 */
export async function revokeApproval(
  request: RevokeApprovalRequest
): Promise<ApprovalRequest> {
  const response = await apiClient.post<ApprovalRequest>(
    `${APPROVALS_BASE_URL}/revoke`,
    request
  );
  return response.data;
}
