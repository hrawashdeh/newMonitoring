package com.tiqmo.monitoring.importexport.domain.repo;

import com.tiqmo.monitoring.importexport.domain.entity.ImportAuditLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

/**
 * Import Audit Log Repository
 *
 * Provides data access operations for import audit logs.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Repository
public interface ImportAuditLogRepository extends JpaRepository<ImportAuditLog, Long> {

    /**
     * Find all imports by a specific user
     *
     * @param importedBy Username
     * @return List of import audit logs
     */
    List<ImportAuditLog> findByImportedByOrderByImportedAtDesc(String importedBy);

    /**
     * Find all imports with a specific label
     *
     * @param importLabel Import label
     * @return List of import audit logs
     */
    List<ImportAuditLog> findByImportLabelOrderByImportedAtDesc(String importLabel);

    /**
     * Find all imports in a date range
     *
     * @param startDate Start date
     * @param endDate End date
     * @return List of import audit logs
     */
    List<ImportAuditLog> findByImportedAtBetweenOrderByImportedAtDesc(
            LocalDateTime startDate,
            LocalDateTime endDate
    );

    /**
     * Find all dry run imports
     *
     * @param dryRun Dry run flag
     * @return List of import audit logs
     */
    List<ImportAuditLog> findByDryRunOrderByImportedAtDesc(Boolean dryRun);

    /**
     * Find imports with failures
     *
     * @return List of import audit logs with failure_count > 0
     */
    @Query("SELECT i FROM ImportAuditLog i WHERE i.failureCount > 0 ORDER BY i.importedAt DESC")
    List<ImportAuditLog> findImportsWithFailures();

    /**
     * Find most recent import by label
     *
     * @param importLabel Import label
     * @return Optional import audit log
     */
    Optional<ImportAuditLog> findFirstByImportLabelOrderByImportedAtDesc(String importLabel);

    /**
     * Get import statistics for a user
     *
     * @param importedBy Username
     * @return Statistics object with total imports, success count, failure count
     */
    @Query("""
            SELECT new map(
                COUNT(i) as totalImports,
                SUM(i.successCount) as totalSuccesses,
                SUM(i.failureCount) as totalFailures,
                SUM(i.totalRows) as totalRows
            )
            FROM ImportAuditLog i
            WHERE i.importedBy = :importedBy
            """)
    Object getImportStatisticsByUser(@Param("importedBy") String importedBy);
}