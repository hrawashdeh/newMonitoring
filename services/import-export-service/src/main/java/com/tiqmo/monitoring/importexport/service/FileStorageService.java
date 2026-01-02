package com.tiqmo.monitoring.importexport.service;

import com.tiqmo.monitoring.importexport.config.FileStorageProperties;
import com.tiqmo.monitoring.importexport.dto.ImportErrorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.slf4j.MDC;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;

/**
 * File Storage Service
 *
 * Handles file operations with PVC storage.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FileStorageService {

    private final FileStorageProperties fileStorageProperties;
    private static final DateTimeFormatter TIMESTAMP_FORMAT =
            DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss");

    /**
     * Store uploaded file in PVC
     *
     * @param file Uploaded file
     * @param username Username who uploaded the file
     * @return Stored file path
     * @throws IOException If file cannot be stored
     */
    public String storeFile(MultipartFile file, String username) throws IOException {
        String correlationId = MDC.get("correlationId");
        String originalFilename = file.getOriginalFilename();
        long fileSize = file.getSize();
        long maxFileSize = fileStorageProperties.getMaxFileSize();

        log.trace("Entering storeFile() | fileName={} | fileSize={} | username={} | correlationId={}",
                originalFilename, fileSize, username, correlationId);

        log.debug("Storing uploaded file | fileName={} | fileSize={} | username={} | correlationId={}",
                originalFilename, fileSize, username, correlationId);

        // Validate file
        log.trace("Validating file before storage | fileName={} | correlationId={}", originalFilename, correlationId);

        if (file.isEmpty()) {
            log.error("FILE_STORAGE_FAILED: Cannot store empty file | fileName={} | username={} | correlationId={} | " +
                            "reason=File has zero size | " +
                            "suggestion=Ensure file contains data before uploading",
                    originalFilename, username, correlationId);
            throw new IllegalArgumentException("Cannot store empty file");
        }

        if (fileSize > maxFileSize) {
            log.error("FILE_STORAGE_FAILED: File size exceeds limit | fileName={} | fileSize={} | maxFileSize={} | " +
                            "username={} | correlationId={} | " +
                            "reason=Uploaded file exceeds maximum allowed size | " +
                            "suggestion=Reduce file size or split into smaller files (max {} bytes)",
                    originalFilename, fileSize, maxFileSize, username, correlationId, maxFileSize);
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed size (%d bytes)", maxFileSize)
            );
        }

        log.debug("File validation passed | fileName={} | fileSize={} | correlationId={}",
                originalFilename, fileSize, correlationId);

        // Create base directory if not exists
        Path baseDir = Paths.get(fileStorageProperties.getBasePath());
        log.trace("Creating base storage directory if not exists | basePath={} | correlationId={}",
                baseDir, correlationId);
        Files.createDirectories(baseDir);
        log.debug("Storage directory ready | basePath={} | correlationId={}", baseDir, correlationId);

        // Generate unique filename
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String safeFilename = sanitizeFilename(originalFilename);
        String storedFilename = String.format("%s_%s_%s", timestamp, username, safeFilename);

        log.debug("Generated storage filename | originalFilename={} | storedFilename={} | correlationId={}",
                originalFilename, storedFilename, correlationId);

        // Store file
        Path targetPath = baseDir.resolve(storedFilename);
        log.trace("Copying file to storage | targetPath={} | correlationId={}", targetPath, correlationId);

        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("File stored successfully | fileName={} | storedPath={} | fileSize={} | username={} | correlationId={}",
                originalFilename, targetPath, fileSize, username, correlationId);

        log.trace("Exiting storeFile() | success=true | storedPath={}", targetPath);

        return targetPath.toString();
    }

    /**
     * Generate error file with validation errors
     *
     * @param originalFilePath Path to original import file
     * @param errors List of validation errors
     * @return Path to error file
     * @throws IOException If error file cannot be created
     */
    public String generateErrorFile(String originalFilePath, List<ImportErrorDto> errors)
            throws IOException {

        String correlationId = MDC.get("correlationId");
        int errorCount = errors.size();

        log.trace("Entering generateErrorFile() | originalFilePath={} | errorCount={} | correlationId={}",
                originalFilePath, errorCount, correlationId);

        log.info("Starting error file generation | originalFilePath={} | errorCount={} | correlationId={}",
                originalFilePath, errorCount, correlationId);

        // Create error files directory
        Path errorDir = Paths.get(fileStorageProperties.getErrorFilesPath());
        log.trace("Creating error files directory | errorDir={} | correlationId={}", errorDir, correlationId);
        Files.createDirectories(errorDir);
        log.debug("Error files directory ready | errorDir={} | correlationId={}", errorDir, correlationId);

        // Generate error filename
        String originalFilename = Paths.get(originalFilePath).getFileName().toString();
        String errorFilename = originalFilename.replace(".xlsx", "_errors.xlsx");
        Path errorFilePath = errorDir.resolve(errorFilename);

        log.debug("Generating error report | originalFilename={} | errorFilename={} | correlationId={}",
                originalFilename, errorFilename, correlationId);

        // Create error workbook
        try (Workbook workbook = new XSSFWorkbook()) {
            log.trace("Creating error workbook | correlationId={}", correlationId);
            Sheet sheet = workbook.createSheet("Import Errors");

            // Create header row
            Row headerRow = sheet.createRow(0);
            String[] headers = {"Row", "Loader Code", "Field", "Error", "Error Type"};
            CellStyle headerStyle = createHeaderStyle(workbook);

            for (int i = 0; i < headers.length; i++) {
                Cell cell = headerRow.createCell(i);
                cell.setCellValue(headers[i]);
                cell.setCellStyle(headerStyle);
            }

            log.trace("Header row created | headerCount={} | correlationId={}", headers.length, correlationId);

            // Create error rows
            log.trace("Writing error rows | errorCount={} | correlationId={}", errorCount, correlationId);
            int rowNum = 1;
            for (ImportErrorDto error : errors) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(error.getRow() != null ? error.getRow() : 0);
                row.createCell(1).setCellValue(error.getLoaderCode() != null ? error.getLoaderCode() : "");
                row.createCell(2).setCellValue(error.getField() != null ? error.getField() : "");
                row.createCell(3).setCellValue(error.getError() != null ? error.getError() : "");
                row.createCell(4).setCellValue(error.getErrorType() != null ? error.getErrorType() : "");
            }

            log.debug("Error rows written | totalRows={} | correlationId={}", errorCount, correlationId);

            // Auto-size columns
            log.trace("Auto-sizing columns | correlationId={}", correlationId);
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to file
            log.trace("Writing workbook to file | errorFilePath={} | correlationId={}", errorFilePath, correlationId);
            try (FileOutputStream fos = new FileOutputStream(errorFilePath.toFile())) {
                workbook.write(fos);
            }

            log.info("Error file generated successfully | errorFilePath={} | errorCount={} | correlationId={}",
                    errorFilePath, errorCount, correlationId);
        }

        log.trace("Exiting generateErrorFile() | success=true | errorFilePath={}", errorFilePath);

        return errorFilePath.toString();
    }

    /**
     * Create header cell style
     */
    private CellStyle createHeaderStyle(Workbook workbook) {
        CellStyle style = workbook.createCellStyle();
        Font font = workbook.createFont();
        font.setBold(true);
        font.setFontHeightInPoints((short) 11);
        style.setFont(font);
        style.setFillForegroundColor(IndexedColors.GREY_25_PERCENT.getIndex());
        style.setFillPattern(FillPatternType.SOLID_FOREGROUND);
        style.setBorderBottom(BorderStyle.THIN);
        style.setBorderTop(BorderStyle.THIN);
        style.setBorderLeft(BorderStyle.THIN);
        style.setBorderRight(BorderStyle.THIN);
        return style;
    }

    /**
     * Sanitize filename to prevent path traversal attacks
     */
    private String sanitizeFilename(String filename) {
        if (filename == null) {
            return "upload.xlsx";
        }
        // Remove path separators and keep only filename
        String safe = filename.replaceAll("[^a-zA-Z0-9._-]", "_");
        // Ensure .xlsx extension
        if (!safe.toLowerCase().endsWith(".xlsx")) {
            safe += ".xlsx";
        }
        return safe;
    }

    /**
     * Delete file from PVC
     *
     * @param filePath Path to file
     */
    public void deleteFile(String filePath) {
        try {
            Path path = Paths.get(filePath);
            Files.deleteIfExists(path);
            log.info("Deleted file: {}", filePath);
        } catch (IOException e) {
            log.error("Failed to delete file: {}", filePath, e);
        }
    }

    /**
     * Check if file exists
     *
     * @param filePath Path to file
     * @return True if file exists
     */
    public boolean fileExists(String filePath) {
        return Files.exists(Paths.get(filePath));
    }
}