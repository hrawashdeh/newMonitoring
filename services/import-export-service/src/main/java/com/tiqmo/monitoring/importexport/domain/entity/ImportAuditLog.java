package com.tiqmo.monitoring.importexport.domain.entity;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Import Audit Log Entity
 *
 * Tracks all import operations with detailed audit trail.
 * Maps to loader.import_audit_log table.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Entity
@Table(name = "import_audit_log", schema = "loader")
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ImportAuditLog {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /**
     * Original filename uploaded by user
     */
    @Column(name = "file_name", nullable = false)
    private String fileName;

    /**
     * Full path in PVC storage where file is stored
     */
    @Column(name = "file_path", nullable = false, length = 512)
    private String filePath;

    /**
     * File size in bytes
     */
    @Column(name = "file_size_bytes", nullable = false)
    private Long fileSizeBytes;

    /**
     * User-provided label for this import batch (e.g., "2024-12-Migration")
     */
    @Column(name = "import_label")
    private String importLabel;

    /**
     * Username who performed the import
     */
    @Column(name = "imported_by", nullable = false)
    private String importedBy;

    /**
     * Timestamp when import was performed
     */
    @Column(name = "imported_at", nullable = false)
    private LocalDateTime importedAt;

    /**
     * Total number of rows in the import file
     */
    @Column(name = "total_rows", nullable = false)
    private Integer totalRows;

    /**
     * Number of successfully processed rows
     */
    @Column(name = "success_count", nullable = false)
    @Builder.Default
    private Integer successCount = 0;

    /**
     * Number of failed rows
     */
    @Column(name = "failure_count", nullable = false)
    @Builder.Default
    private Integer failureCount = 0;

    /**
     * JSON array of validation errors
     * Format: [{"row": 5, "field": "loaderCode", "error": "cannot be empty"}]
     */
    @Column(name = "validation_errors", columnDefinition = "TEXT")
    private String validationErrors;

    /**
     * True if this was a validation-only run without actual loader creation
     */
    @Column(name = "dry_run", nullable = false)
    @Builder.Default
    private Boolean dryRun = false;

    @PrePersist
    protected void onCreate() {
        if (importedAt == null) {
            importedAt = LocalDateTime.now();
        }
    }
}