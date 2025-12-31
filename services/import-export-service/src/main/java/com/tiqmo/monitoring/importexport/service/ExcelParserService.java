package com.tiqmo.monitoring.importexport.service;

import com.tiqmo.monitoring.importexport.config.ImportProperties;
import com.tiqmo.monitoring.importexport.dto.ImportErrorDto;
import com.tiqmo.monitoring.importexport.dto.LoaderImportDto;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Excel Parser Service
 *
 * Parses Excel files (.xlsx) and converts rows to LoaderImportDto objects.
 *
 * @author Hassan Rawashdeh
 * @version 1.0.0
 * @since 2025-12-29
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class ExcelParserService {

    private final ImportProperties importProperties;

    /**
     * Parse Excel file and return list of loader DTOs
     *
     * @param file Uploaded Excel file
     * @return Parse result containing loaders and errors
     * @throws IOException If file cannot be read
     */
    public ParseResult parseExcelFile(MultipartFile file) throws IOException {
        log.info("Parsing Excel file: {}", file.getOriginalFilename());

        List<LoaderImportDto> loaders = new ArrayList<>();
        List<ImportErrorDto> errors = new ArrayList<>();

        try (InputStream is = file.getInputStream();
             Workbook workbook = new XSSFWorkbook(is)) {

            Sheet sheet = workbook.getSheetAt(0); // First sheet
            if (sheet == null) {
                errors.add(ImportErrorDto.builder()
                        .row(0)
                        .error("No sheets found in Excel file")
                        .errorType("VALIDATION")
                        .build());
                return new ParseResult(loaders, errors);
            }

            // Parse header row
            Row headerRow = sheet.getRow(0);
            if (headerRow == null) {
                errors.add(ImportErrorDto.builder()
                        .row(1)
                        .error("Header row is missing")
                        .errorType("VALIDATION")
                        .build());
                return new ParseResult(loaders, errors);
            }

            Map<String, Integer> columnMap = parseHeaderRow(headerRow);

            // Validate required columns
            List<String> missingColumns = validateRequiredColumns(columnMap);
            if (!missingColumns.isEmpty()) {
                errors.add(ImportErrorDto.builder()
                        .row(1)
                        .error("Missing required columns: " + String.join(", ", missingColumns))
                        .errorType("VALIDATION")
                        .build());
                return new ParseResult(loaders, errors);
            }

            // Parse data rows
            int totalRows = sheet.getPhysicalNumberOfRows();
            if (totalRows > importProperties.getMaxRowsPerFile() + 1) { // +1 for header
                errors.add(ImportErrorDto.builder()
                        .row(0)
                        .error(String.format("File exceeds maximum allowed rows (%d)",
                                importProperties.getMaxRowsPerFile()))
                        .errorType("VALIDATION")
                        .build());
                return new ParseResult(loaders, errors);
            }

            for (int i = 1; i <= sheet.getLastRowNum(); i++) {
                Row row = sheet.getRow(i);
                if (row == null || isRowEmpty(row)) {
                    continue; // Skip empty rows
                }

                try {
                    LoaderImportDto loader = parseRow(row, columnMap, i + 1);
                    loaders.add(loader);
                } catch (Exception e) {
                    log.error("Error parsing row {}: {}", i + 1, e.getMessage());
                    errors.add(ImportErrorDto.builder()
                            .row(i + 1)
                            .error(e.getMessage())
                            .errorType("VALIDATION")
                            .build());
                }
            }

            log.info("Parsed {} loaders with {} errors", loaders.size(), errors.size());
            return new ParseResult(loaders, errors);

        } catch (Exception e) {
            log.error("Fatal error parsing Excel file", e);
            errors.add(ImportErrorDto.builder()
                    .row(0)
                    .error("Failed to parse Excel file: " + e.getMessage())
                    .errorType("SYSTEM")
                    .build());
            return new ParseResult(loaders, errors);
        }
    }

    /**
     * Parse header row and create column name -> column index map
     */
    private Map<String, Integer> parseHeaderRow(Row headerRow) {
        Map<String, Integer> columnMap = new HashMap<>();
        for (Cell cell : headerRow) {
            String columnName = getCellValueAsString(cell).trim();
            if (!columnName.isEmpty()) {
                columnMap.put(columnName, cell.getColumnIndex());
            }
        }
        return columnMap;
    }

    /**
     * Validate that all required columns are present
     */
    private List<String> validateRequiredColumns(Map<String, Integer> columnMap) {
        List<String> missingColumns = new ArrayList<>();
        for (String requiredColumn : importProperties.getValidation().getRequiredColumns()) {
            if (!columnMap.containsKey(requiredColumn)) {
                missingColumns.add(requiredColumn);
            }
        }
        return missingColumns;
    }

    /**
     * Parse a single data row
     */
    private LoaderImportDto parseRow(Row row, Map<String, Integer> columnMap, int rowNumber) {
        LoaderImportDto.LoaderImportDtoBuilder builder = LoaderImportDto.builder();
        builder.rowNumber(rowNumber);

        // Parse each field using user-friendly column names
        builder.importAction(getCellValue(row, columnMap, "Import Action", String.class, "UPDATE"));
        builder.loaderCode(getCellValue(row, columnMap, "Loader Code", String.class, null));
        builder.loaderSql(getCellValue(row, columnMap, "SQL Query", String.class, null));
        builder.minIntervalSeconds(getCellValue(row, columnMap, "Min Interval (seconds)", Integer.class, null));
        builder.maxIntervalSeconds(getCellValue(row, columnMap, "Max Interval (seconds)", Integer.class, null));
        builder.maxQueryPeriodSeconds(getCellValue(row, columnMap, "Query Period (seconds)", Integer.class, null));
        builder.maxParallelExecutions(getCellValue(row, columnMap, "Max Parallel Executions", Integer.class, null));
        builder.purgeStrategy(getCellValue(row, columnMap, "Purge Strategy", String.class, null));
        builder.sourceTimezoneOffsetHours(getCellValue(row, columnMap, "Timezone Offset (hours)", Integer.class, null));
        builder.aggregationPeriodSeconds(getCellValue(row, columnMap, "Aggregation Period (seconds)", Integer.class, null));
        builder.sourceDatabaseCode(getCellValue(row, columnMap, "Source Database Code", String.class, null));

        return builder.build();
    }

    /**
     * Get cell value with type conversion
     */
    @SuppressWarnings("unchecked")
    private <T> T getCellValue(Row row, Map<String, Integer> columnMap, String columnName,
                               Class<T> targetType, T defaultValue) {
        Integer columnIndex = columnMap.get(columnName);
        if (columnIndex == null) {
            return defaultValue;
        }

        Cell cell = row.getCell(columnIndex);
        if (cell == null) {
            return defaultValue;
        }

        String cellValue = getCellValueAsString(cell).trim();
        if (cellValue.isEmpty() || "***protected***".equals(cellValue)) {
            return defaultValue;
        }

        try {
            if (targetType == String.class) {
                return (T) cellValue;
            } else if (targetType == Integer.class) {
                return (T) Integer.valueOf((int) Double.parseDouble(cellValue));
            } else if (targetType == Long.class) {
                return (T) Long.valueOf((long) Double.parseDouble(cellValue));
            } else if (targetType == Double.class) {
                return (T) Double.valueOf(cellValue);
            }
        } catch (NumberFormatException e) {
            log.warn("Failed to convert '{}' to {}: {}", cellValue, targetType.getSimpleName(), e.getMessage());
        }

        return defaultValue;
    }

    /**
     * Get cell value as string
     */
    private String getCellValueAsString(Cell cell) {
        if (cell == null) {
            return "";
        }

        return switch (cell.getCellType()) {
            case STRING -> cell.getStringCellValue();
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(cell)) {
                    yield cell.getDateCellValue().toString();
                } else {
                    // Format numeric cell without scientific notation
                    double value = cell.getNumericCellValue();
                    if (value == (long) value) {
                        yield String.valueOf((long) value);
                    } else {
                        yield String.valueOf(value);
                    }
                }
            }
            case BOOLEAN -> String.valueOf(cell.getBooleanCellValue());
            case FORMULA -> cell.getCellFormula();
            case BLANK -> "";
            default -> "";
        };
    }

    /**
     * Check if row is empty
     */
    private boolean isRowEmpty(Row row) {
        for (int i = row.getFirstCellNum(); i < row.getLastCellNum(); i++) {
            Cell cell = row.getCell(i);
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellValueAsString(cell).trim();
                if (!value.isEmpty()) {
                    return false;
                }
            }
        }
        return true;
    }

    /**
     * Parse result containing loaders and errors
     */
    public record ParseResult(List<LoaderImportDto> loaders, List<ImportErrorDto> errors) {
        public boolean hasErrors() {
            return !errors.isEmpty();
        }
    }
}