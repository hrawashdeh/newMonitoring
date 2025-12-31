package com.tiqmo.monitoring.loader.domain.approval.repo;

import com.tiqmo.monitoring.loader.domain.approval.entity.ApprovalRequest;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for ApprovalRequest entity
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Repository
public interface ApprovalRequestRepository extends JpaRepository<ApprovalRequest, Long> {

    /**
     * Find all pending approvals
     */
    List<ApprovalRequest> findByApprovalStatusOrderByRequestedAtDesc(
            ApprovalRequest.ApprovalStatus status);

    /**
     * Find all pending approvals (convenience method)
     */
    default List<ApprovalRequest> findAllPending() {
        return findByApprovalStatusOrderByRequestedAtDesc(
                ApprovalRequest.ApprovalStatus.PENDING_APPROVAL);
    }

    /**
     * Find pending approval for specific entity
     */
    Optional<ApprovalRequest> findByEntityTypeAndEntityIdAndApprovalStatus(
            ApprovalRequest.EntityType entityType,
            String entityId,
            ApprovalRequest.ApprovalStatus status);

    /**
     * Find pending approval for specific entity (convenience method)
     */
    default Optional<ApprovalRequest> findPendingForEntity(
            ApprovalRequest.EntityType entityType,
            String entityId) {
        return findByEntityTypeAndEntityIdAndApprovalStatus(
                entityType, entityId, ApprovalRequest.ApprovalStatus.PENDING_APPROVAL);
    }

    /**
     * Find all approvals for specific entity (all statuses)
     */
    List<ApprovalRequest> findByEntityTypeAndEntityIdOrderByRequestedAtDesc(
            ApprovalRequest.EntityType entityType,
            String entityId);

    /**
     * Find all approvals by entity type
     */
    List<ApprovalRequest> findByEntityTypeOrderByRequestedAtDesc(
            ApprovalRequest.EntityType entityType);

    /**
     * Find all pending approvals by entity type
     */
    List<ApprovalRequest> findByEntityTypeAndApprovalStatusOrderByRequestedAtDesc(
            ApprovalRequest.EntityType entityType,
            ApprovalRequest.ApprovalStatus status);

    /**
     * Find all approvals requested by user
     */
    List<ApprovalRequest> findByRequestedByOrderByRequestedAtDesc(String requestedBy);

    /**
     * Find all approvals from import batch
     */
    List<ApprovalRequest> findByImportLabelOrderByRequestedAtDesc(String importLabel);

    /**
     * Check if entity has pending approval
     */
    @Query("SELECT CASE WHEN COUNT(ar) > 0 THEN true ELSE false END " +
           "FROM ApprovalRequest ar " +
           "WHERE ar.entityType = :entityType " +
           "AND ar.entityId = :entityId " +
           "AND ar.approvalStatus = 'PENDING_APPROVAL'")
    boolean hasPendingApproval(
            @Param("entityType") ApprovalRequest.EntityType entityType,
            @Param("entityId") String entityId);

    /**
     * Count pending approvals by entity type
     */
    long countByEntityTypeAndApprovalStatus(
            ApprovalRequest.EntityType entityType,
            ApprovalRequest.ApprovalStatus status);

    /**
     * Count all pending approvals
     */
    default long countAllPending() {
        return countByApprovalStatus(ApprovalRequest.ApprovalStatus.PENDING_APPROVAL);
    }

    /**
     * Count by status
     */
    long countByApprovalStatus(ApprovalRequest.ApprovalStatus status);
}
