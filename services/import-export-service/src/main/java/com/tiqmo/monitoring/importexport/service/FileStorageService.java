package com.tiqmo.monitoring.importexport.service;

import com.tiqmo.monitoring.importexport.config.FileStorageProperties;
import com.tiqmo.monitoring.importexport.dto.ImportErrorDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
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
        // Validate file
        if (file.isEmpty()) {
            throw new IllegalArgumentException("Cannot store empty file");
        }

        if (file.getSize() > fileStorageProperties.getMaxFileSize()) {
            throw new IllegalArgumentException(
                    String.format("File size exceeds maximum allowed size (%d bytes)",
                            fileStorageProperties.getMaxFileSize())
            );
        }

        // Create base directory if not exists
        Path baseDir = Paths.get(fileStorageProperties.getBasePath());
        Files.createDirectories(baseDir);

        // Generate unique filename
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String originalFilename = file.getOriginalFilename();
        String safeFilename = sanitizeFilename(originalFilename);
        String storedFilename = String.format("%s_%s_%s", timestamp, username, safeFilename);

        // Store file
        Path targetPath = baseDir.resolve(storedFilename);
        Files.copy(file.getInputStream(), targetPath, StandardCopyOption.REPLACE_EXISTING);

        log.info("Stored file: {} (size: {} bytes)", targetPath, file.getSize());
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

        log.info("Generating error file for {} errors", errors.size());

        // Create error files directory
        Path errorDir = Paths.get(fileStorageProperties.getErrorFilesPath());
        Files.createDirectories(errorDir);

        // Generate error filename
        String originalFilename = Paths.get(originalFilePath).getFileName().toString();
        String errorFilename = originalFilename.replace(".xlsx", "_errors.xlsx");
        Path errorFilePath = errorDir.resolve(errorFilename);

        // Create error workbook
        try (Workbook workbook = new XSSFWorkbook()) {
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

            // Create error rows
            int rowNum = 1;
            for (ImportErrorDto error : errors) {
                Row row = sheet.createRow(rowNum++);
                row.createCell(0).setCellValue(error.getRow() != null ? error.getRow() : 0);
                row.createCell(1).setCellValue(error.getLoaderCode() != null ? error.getLoaderCode() : "");
                row.createCell(2).setCellValue(error.getField() != null ? error.getField() : "");
                row.createCell(3).setCellValue(error.getError() != null ? error.getError() : "");
                row.createCell(4).setCellValue(error.getErrorType() != null ? error.getErrorType() : "");
            }

            // Auto-size columns
            for (int i = 0; i < headers.length; i++) {
                sheet.autoSizeColumn(i);
            }

            // Write to file
            try (FileOutputStream fos = new FileOutputStream(errorFilePath.toFile())) {
                workbook.write(fos);
            }
        }

        log.info("Generated error file: {}", errorFilePath);
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