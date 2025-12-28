package com.tiqmo.monitoring.loader.domain.loader.repo;

import com.tiqmo.monitoring.loader.domain.loader.entity.ApprovalActionType;
import com.tiqmo.monitoring.loader.domain.loader.entity.ApprovalAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.List;

/**
 * Repository for approval audit log records.
 *
 * <p>Provides query methods for:
 * <ul>
 *   <li>Finding audit trail by loader ID or code</li>
 *   <li>Finding actions by admin user</li>
 *   <li>Finding actions within time range</li>
 *   <li>Finding specific action types</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @since 1.3.0
 */
public interface ApprovalAuditLogRepository extends JpaRepository<ApprovalAuditLog, Long> {

    /**
     * Find all audit records for a specific loader.
     * Ordered by timestamp descending (most recent first).
     *
     * @param loaderId the loader ID
     * @return list of audit records
     */
    @Query("SELECT a FROM ApprovalAuditLog a WHERE a.loaderId = :loaderId ORDER BY a.actionTimestamp DESC")
    List<ApprovalAuditLog> findByLoaderIdOrderByActionTimestampDesc(@Param("loaderId") Long loaderId);

    /**
     * Find all audit records for a specific loader code.
     * Useful for querying audit trail even if loader is deleted.
     *
     * @param loaderCode the loader code
     * @return list of audit records ordered by timestamp descending
     */
    @Query("SELECT a FROM ApprovalAuditLog a WHERE a.loaderCode = :loaderCode ORDER BY a.actionTimestamp DESC")
    List<ApprovalAuditLog> findByLoaderCodeOrderByActionTimestampDesc(@Param("loaderCode") String loaderCode);

    /**
     * Find all actions performed by a specific admin.
     * Useful for auditing admin activity.
     *
     * @param adminUsername the admin username
     * @return list of audit records ordered by timestamp descending
     */
    @Query("SELECT a FROM ApprovalAuditLog a WHERE a.adminUsername = :adminUsername ORDER BY a.actionTimestamp DESC")
    List<ApprovalAuditLog> findByAdminUsernameOrderByActionTimestampDesc(@Param("adminUsername") String adminUsername);

    /**
     * Find all actions of a specific type within a time range.
     * Useful for generating compliance reports.
     *
     * @param actionType the action type to filter
     * @param fromTime start of time range (inclusive)
     * @param toTime end of time range (inclusive)
     * @return list of audit records ordered by timestamp descending
     */
    @Query("SELECT a FROM ApprovalAuditLog a WHERE a.actionType = :actionType " +
           "AND a.actionTimestamp >= :fromTime AND a.actionTimestamp <= :toTime " +
           "ORDER BY a.actionTimestamp DESC")
    List<ApprovalAuditLog> findByActionTypeAndTimestampBetween(
            @Param("actionType") ApprovalActionType actionType,
            @Param("fromTime") Instant fromTime,
            @Param("toTime") Instant toTime
    );

    /**
     * Find recent audit records (last N days).
     * Useful for dashboard and recent activity views.
     *
     * @param fromTime start time (e.g., now minus 7 days)
     * @return list of audit records ordered by timestamp descending
     */
    @Query("SELECT a FROM ApprovalAuditLog a WHERE a.actionTimestamp >= :fromTime ORDER BY a.actionTimestamp DESC")
    List<ApprovalAuditLog> findRecentActions(@Param("fromTime") Instant fromTime);

    /**
     * Count total approvals by a specific admin.
     * Useful for admin activity metrics.
     *
     * @param adminUsername the admin username
     * @return count of approvals
     */
    @Query("SELECT COUNT(a) FROM ApprovalAuditLog a WHERE a.adminUsername = :adminUsername AND a.actionType = 'APPROVED'")
    long countApprovalsByAdmin(@Param("adminUsername") String adminUsername);

    /**
     * Count total rejections by a specific admin.
     * Useful for admin activity metrics.
     *
     * @param adminUsername the admin username
     * @return count of rejections
     */
    @Query("SELECT COUNT(a) FROM ApprovalAuditLog a WHERE a.adminUsername = :adminUsername AND a.actionType = 'REJECTED'")
    long countRejectionsByAdmin(@Param("adminUsername") String adminUsername);
}
