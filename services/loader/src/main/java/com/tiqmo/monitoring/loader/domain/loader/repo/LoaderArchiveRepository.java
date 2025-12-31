package com.tiqmo.monitoring.loader.domain.loader.repo;

import com.tiqmo.monitoring.loader.domain.loader.entity.LoaderArchive;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

/**
 * Repository for archived loader versions.
 *
 * <p><b>Purpose:</b>
 * <ul>
 *   <li>Query version history for loaders</li>
 *   <li>Support rollback operations</li>
 *   <li>Audit trail for rejected drafts</li>
 *   <li>Version comparison</li>
 * </ul>
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-30
 */
@Repository
public interface LoaderArchiveRepository extends JpaRepository<LoaderArchive, Long> {

    /**
     * Find all archived versions for a loader.
     * Returns versions ordered by version_number DESC (newest first).
     *
     * <p><b>Use Case:</b> Version history display
     *
     * @param loaderCode Loader code
     * @return List of archived versions (newest first)
     */
    List<LoaderArchive> findByLoaderCodeOrderByVersionNumberDesc(String loaderCode);

    /**
     * Find specific archived version by loader code and version number.
     *
     * <p><b>Use Case:</b> Version comparison, rollback
     *
     * @param loaderCode Loader code
     * @param versionNumber Version number
     * @return Archived version if found
     */
    Optional<LoaderArchive> findByLoaderCodeAndVersionNumber(String loaderCode, Integer versionNumber);

    /**
     * Count total archived versions for a loader.
     *
     * <p><b>Use Case:</b> Display version count in UI
     *
     * @param loaderCode Loader code
     * @return Number of archived versions
     */
    long countByLoaderCode(String loaderCode);

    /**
     * Check if specific version exists in archive.
     *
     * <p><b>Use Case:</b> Prevent duplicate archiving
     *
     * @param loaderCode Loader code
     * @param versionNumber Version number
     * @return true if version is archived
     */
    boolean existsByLoaderCodeAndVersionNumber(String loaderCode, Integer versionNumber);

    /**
     * Find all rejected drafts for a loader.
     * Returns only versions where rejected_by is not null.
     *
     * <p><b>Use Case:</b> Rejection history display
     *
     * @param loaderCode Loader code
     * @return List of rejected versions (newest first)
     */
    @Query("SELECT la FROM LoaderArchive la " +
           "WHERE la.loaderCode = :loaderCode " +
           "AND la.rejectedBy IS NOT NULL " +
           "ORDER BY la.rejectedAt DESC")
    List<LoaderArchive> findRejectedByLoaderCode(@Param("loaderCode") String loaderCode);

    /**
     * Count rejected drafts for a loader.
     *
     * <p><b>Use Case:</b> Display rejection count
     *
     * @param loaderCode Loader code
     * @return Number of rejected drafts
     */
    long countByLoaderCodeAndRejectedByIsNotNull(String loaderCode);

    /**
     * Find latest archived version for a loader.
     *
     * <p><b>Use Case:</b> Quick rollback to previous version
     *
     * @param loaderCode Loader code
     * @return Latest archived version if exists
     */
    @Query("SELECT la FROM LoaderArchive la " +
           "WHERE la.loaderCode = :loaderCode " +
           "ORDER BY la.versionNumber DESC " +
           "LIMIT 1")
    Optional<LoaderArchive> findLatestByLoaderCode(@Param("loaderCode") String loaderCode);

    /**
     * Find all archived versions created by specific user.
     *
     * <p><b>Use Case:</b> Audit trail by user
     *
     * @param createdBy Username
     * @return List of archived versions created by user
     */
    List<LoaderArchive> findByCreatedBy(String createdBy);

    /**
     * Find all archived versions rejected by specific admin.
     *
     * <p><b>Use Case:</b> Admin audit trail
     *
     * @param rejectedBy Admin username
     * @return List of rejected versions
     */
    List<LoaderArchive> findByRejectedByOrderByRejectedAtDesc(String rejectedBy);

    /**
     * Find archived versions by import label.
     *
     * <p><b>Use Case:</b> Track versions from specific import batch
     *
     * @param importLabel Import batch identifier
     * @return List of archived versions from import
     */
    List<LoaderArchive> findByImportLabel(String importLabel);

    /**
     * Delete all archived versions for a loader.
     *
     * <p><b>WARNING:</b> This is a destructive operation.
     * Only use for manual cleanup or when completely removing a loader.
     *
     * @param loaderCode Loader code
     */
    void deleteByLoaderCode(String loaderCode);
}
