package com.tiqmo.monitoring.loader.domain.loader.repo;

import com.tiqmo.monitoring.loader.domain.loader.entity.ApprovalStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import com.tiqmo.monitoring.workflow.domain.VersionStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface LoaderRepository extends JpaRepository<Loader, Long> {
    Optional<Loader> findByLoaderCode(String loaderCode);
    boolean existsByLoaderCode(String loaderCode);

    /**
     * Find all enabled loaders for scheduling with source database eagerly fetched.
     * Used by LoaderSchedulerService to find loaders ready for execution.
     *
     * @deprecated Use {@link #findAllByEnabledTrueAndApprovalStatus(ApprovalStatus)} instead.
     *             This method does not check approval status and may return non-approved loaders.
     * @return list of enabled loaders with source databases loaded
     */
    @Deprecated
    @Query("SELECT l FROM Loader l JOIN FETCH l.sourceDatabase WHERE l.enabled = true")
    List<Loader> findAllByEnabledTrue();

    /**
     * Find all enabled AND APPROVED loaders for scheduling with source database eagerly fetched.
     *
     * <p><b>SECURITY:</b> Only APPROVED loaders should be executed by the scheduler.
     * PENDING_APPROVAL and REJECTED loaders must NOT execute.
     *
     * <p>Used by LoaderSchedulerService to find loaders ready for execution.
     *
     * @param approvalStatus the approval status to filter by (typically APPROVED)
     * @return list of enabled and approved loaders with source databases loaded
     * @deprecated Use {@link #findAllByEnabledTrueAndVersionStatus(VersionStatus)} instead.
     *             Approval workflow is now unified to use version_status field.
     */
    @Deprecated
    @Query("SELECT l FROM Loader l JOIN FETCH l.sourceDatabase WHERE l.enabled = true AND l.approvalStatus = :approvalStatus")
    List<Loader> findAllByEnabledTrueAndApprovalStatus(ApprovalStatus approvalStatus);

    /**
     * Find all enabled AND ACTIVE loaders for scheduling with source database eagerly fetched.
     *
     * <p><b>SECURITY:</b> Only ACTIVE loaders should be executed by the scheduler.
     * DRAFT and PENDING_APPROVAL loaders must NOT execute.
     *
     * <p><b>UNIFIED WORKFLOW:</b> This method uses version_status instead of approval_status
     * for the unified approval workflow system.
     *
     * <p>Used by LoaderSchedulerService to find loaders ready for execution.
     *
     * @param versionStatus the version status to filter by (typically ACTIVE)
     * @return list of enabled and active loaders with source databases loaded
     */
    @Query("SELECT l FROM Loader l JOIN FETCH l.sourceDatabase WHERE l.enabled = true AND l.versionStatus = :versionStatus")
    List<Loader> findAllByEnabledTrueAndVersionStatus(@Param("versionStatus") VersionStatus versionStatus);

    // ==================== VERSIONING QUERIES ====================

    /**
     * Find ACTIVE version by loader code.
     * Returns the production version currently in use.
     *
     * <p><b>Use Case:</b> Draft service needs to find parent version
     *
     * @param loaderCode Loader code
     * @return ACTIVE loader or empty if no active version exists
     */
    @Query("SELECT l FROM Loader l WHERE l.loaderCode = :loaderCode AND l.versionStatus = 'ACTIVE'")
    Optional<Loader> findActiveByLoaderCode(@Param("loaderCode") String loaderCode);

    /**
     * Find DRAFT or PENDING_APPROVAL version by loader code.
     * Returns the working draft (only one draft per loader_code).
     *
     * <p><b>Use Case:</b> Check if draft exists before creating new one
     *
     * @param loaderCode Loader code
     * @return DRAFT/PENDING loader or empty if no draft exists
     */
    @Query("SELECT l FROM Loader l WHERE l.loaderCode = :loaderCode " +
           "AND l.versionStatus IN ('DRAFT', 'PENDING_APPROVAL')")
    Optional<Loader> findDraftByLoaderCode(@Param("loaderCode") String loaderCode);

    /**
     * Find all ACTIVE loaders.
     *
     * <p><b>Use Case:</b> Display production loaders
     *
     * @return List of all ACTIVE loaders
     */
    @Query("SELECT l FROM Loader l WHERE l.versionStatus = 'ACTIVE'")
    List<Loader> findAllActive();

    /**
     * Find all PENDING_APPROVAL loaders.
     *
     * <p><b>Use Case:</b> Admin approval queue
     *
     * @return List of all PENDING_APPROVAL loaders
     */
    @Query("SELECT l FROM Loader l WHERE l.versionStatus = 'PENDING_APPROVAL'")
    List<Loader> findAllPendingApproval();

    /**
     * Find all DRAFT loaders.
     *
     * <p><b>Use Case:</b> Display user's drafts
     *
     * @return List of all DRAFT loaders
     */
    @Query("SELECT l FROM Loader l WHERE l.versionStatus = 'DRAFT'")
    List<Loader> findAllDrafts();

    /**
     * Check if ACTIVE version exists for loader code.
     *
     * <p><b>Use Case:</b> Determine if new draft is for new loader or update
     *
     * @param loaderCode Loader code
     * @return true if ACTIVE version exists
     */
    @Query("SELECT COUNT(l) > 0 FROM Loader l WHERE l.loaderCode = :loaderCode AND l.versionStatus = 'ACTIVE'")
    boolean existsActiveByLoaderCode(@Param("loaderCode") String loaderCode);

    /**
     * Find all loaders by version status.
     *
     * <p><b>Use Case:</b> Generic query for specific status
     *
     * @param versionStatus Version status to filter by
     * @return List of loaders with specified status
     */
    List<Loader> findByVersionStatus(VersionStatus versionStatus);

    /**
     * Find loaders created by specific user.
     *
     * <p><b>Use Case:</b> User's draft history
     *
     * @param createdBy Username
     * @return List of loaders created by user
     */
    List<Loader> findByCreatedBy(String createdBy);

    /**
     * Count drafts and pending approvals by loader code.
     *
     * <p><b>Use Case:</b> Validate one draft per loader_code constraint
     *
     * @param loaderCode Loader code
     * @return Number of drafts/pending (should be 0 or 1)
     */
    @Query("SELECT COUNT(l) FROM Loader l WHERE l.loaderCode = :loaderCode " +
           "AND l.versionStatus IN ('DRAFT', 'PENDING_APPROVAL')")
    long countDraftsByLoaderCode(@Param("loaderCode") String loaderCode);
}

