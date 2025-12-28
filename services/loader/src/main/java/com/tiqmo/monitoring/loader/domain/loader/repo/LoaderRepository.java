package com.tiqmo.monitoring.loader.domain.loader.repo;

import com.tiqmo.monitoring.loader.domain.loader.entity.ApprovalStatus;
import com.tiqmo.monitoring.loader.domain.loader.entity.Loader;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
     */
    @Query("SELECT l FROM Loader l JOIN FETCH l.sourceDatabase WHERE l.enabled = true AND l.approvalStatus = :approvalStatus")
    List<Loader> findAllByEnabledTrueAndApprovalStatus(ApprovalStatus approvalStatus);
}

