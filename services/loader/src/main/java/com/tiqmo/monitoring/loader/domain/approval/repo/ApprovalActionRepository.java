package com.tiqmo.monitoring.loader.domain.approval.repo;

import com.tiqmo.monitoring.loader.domain.approval.entity.ApprovalAction;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

/**
 * Repository for ApprovalAction entity
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Repository
public interface ApprovalActionRepository extends JpaRepository<ApprovalAction, Long> {

    /**
     * Find all actions for specific approval request (ordered by time)
     */
    List<ApprovalAction> findByApprovalRequestIdOrderByActionAtAsc(Long approvalRequestId);

    /**
     * Find all actions by type
     */
    List<ApprovalAction> findByActionTypeOrderByActionAtDesc(ApprovalAction.ActionType actionType);

    /**
     * Find all actions by user
     */
    List<ApprovalAction> findByActionByOrderByActionAtDesc(String actionBy);

    /**
     * Count actions for approval request
     */
    long countByApprovalRequestId(Long approvalRequestId);

    /**
     * Count actions by type
     */
    long countByActionType(ApprovalAction.ActionType actionType);
}
