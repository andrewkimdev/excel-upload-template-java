package com.foo.excel.service;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.HeaderMatchMode;
import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.util.ExcelColumnUtil;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

@Slf4j
@Service
public class ExcelParserService {

    @Data
    @AllArgsConstructor
    public static class ColumnMapping {
        private Field field;
        private ExcelColumn annotation;
        private int resolvedColumnIndex;
        private String resolvedColumnLetter;
    }

    @Data
    @AllArgsConstructor
    public static class ParseResult<T> {
        private List<T> rows;
        private List<ColumnMapping> columnMappings;
    }

    public <T extends ExcelImportConfig> ParseResult<T> parse(Path xlsxFile, Class<T> dtoClass)
            throws IOException {

        T configInstance;
        try {
            configInstance = dtoClass.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            throw new IllegalArgumentException("Cannot instantiate DTO class: " + dtoClass.getName(), e);
        }

        int headerRowNum = configInstance.getHeaderRow() - 1;   // Convert to 0-based
        int dataStartRowNum = configInstance.getDataStartRow() - 1;
        int sheetIndex = configInstance.getSheetIndex();
        String footerMarker = configInstance.getFooterMarker();

        Set<Integer> skipColumnIndices = new HashSet<>();
        for (String col : configInstance.getSkipColumns()) {
            skipColumnIndices.add(ExcelColumnUtil.letterToIndex(col));
        }

        try (Workbook workbook = WorkbookFactory.create(xlsxFile.toFile())) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            Row headerRow = sheet.getRow(headerRowNum);

            if (headerRow == null) {
                throw new IllegalStateException("Header row " + configInstance.getHeaderRow() + " is empty");
            }

            List<ColumnMapping> columnMappings = resolveColumnMappings(dtoClass, headerRow, sheet);
            List<T> rows = parseDataRows(sheet, dtoClass, columnMappings, dataStartRowNum, footerMarker);

            return new ParseResult<>(rows, columnMappings);
        }
    }

    private <T> List<ColumnMapping> resolveColumnMappings(Class<T> dtoClass, Row headerRow, Sheet sheet) {
        List<ColumnMapping> mappings = new ArrayList<>();

        for (Field field : getAllFields(dtoClass)) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation == null) {
                continue;
            }

            int resolvedIndex = resolveColumnIndex(annotation, headerRow, sheet);

            if (resolvedIndex < 0) {
                log.warn("Could not resolve column for field '{}' with header '{}'",
                        field.getName(), annotation.header());
                continue;
            }

            String resolvedLetter = ExcelColumnUtil.indexToLetter(resolvedIndex);
            field.setAccessible(true);
            mappings.add(new ColumnMapping(field, annotation, resolvedIndex, resolvedLetter));
        }

        return mappings;
    }

    private int resolveColumnIndex(ExcelColumn annotation, Row headerRow, Sheet sheet) {
        // Priority 1: Explicit column letter
        if (!annotation.column().isEmpty()) {
            return ExcelColumnUtil.letterToIndex(annotation.column());
        }

        // Priority 2: Explicit index
        if (annotation.index() >= 0) {
            return annotation.index();
        }

        // Priority 3: Auto-detect from header text
        for (int i = 0; i <= headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) {
                // Check merged cells
                cell = resolveMergedCell(sheet, headerRow.getRowNum(), i);
            }
            if (cell != null) {
                String cellValue = getCellStringValue(cell);
                if (matchHeader(cellValue, annotation.header(), annotation.matchMode())) {
                    return i;
                }
            }
        }

        return -1;
    }

    private boolean matchHeader(String cellValue, String expected, HeaderMatchMode mode) {
        if (cellValue == null || cellValue.isBlank()) {
            return false;
        }

        String trimmedCell = cellValue.trim();
        String trimmedExpected = expected.trim();

        return switch (mode) {
            case EXACT -> trimmedCell.equalsIgnoreCase(trimmedExpected);
            case CONTAINS -> trimmedCell.contains(trimmedExpected);
            case STARTS_WITH -> trimmedCell.startsWith(trimmedExpected);
            case REGEX -> Pattern.matches(trimmedExpected, trimmedCell);
        };
    }

    private <T extends ExcelImportConfig> List<T> parseDataRows(Sheet sheet, Class<T> dtoClass,
            List<ColumnMapping> columnMappings, int dataStartRowNum, String footerMarker) {

        List<T> rows = new ArrayList<>();
        int lastRowNum = sheet.getLastRowNum();

        for (int i = dataStartRowNum; i <= lastRowNum; i++) {
            Row row = sheet.getRow(i);
            if (row == null) {
                continue;
            }

            // Footer detection
            if (isFooterRow(row, footerMarker)) {
                log.debug("Footer marker found at row {}, stopping", i + 1);
                break;
            }

            // Skip blank rows
            if (isBlankRow(row)) {
                continue;
            }

            T dto;
            try {
                dto = dtoClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Cannot instantiate DTO", e);
            }

            for (ColumnMapping mapping : columnMappings) {
                Cell cell = row.getCell(mapping.getResolvedColumnIndex());
                if (cell == null) {
                    cell = resolveMergedCell(sheet, i, mapping.getResolvedColumnIndex());
                }

                Object value = getCellValue(cell, mapping.getField(), mapping.getAnnotation(), sheet);
                try {
                    mapping.getField().set(dto, value);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Cannot set field: " + mapping.getField().getName(), e);
                }
            }

            rows.add(dto);
        }

        return rows;
    }

    private boolean isFooterRow(Row row, String footerMarker) {
        if (footerMarker == null || footerMarker.isEmpty()) {
            return false;
        }
        for (Cell cell : row) {
            String value = getCellStringValue(cell);
            if (value != null && value.contains(footerMarker)) {
                return true;
            }
        }
        return false;
    }

    private boolean isBlankRow(Row row) {
        for (Cell cell : row) {
            if (cell != null && cell.getCellType() != CellType.BLANK) {
                String value = getCellStringValue(cell);
                if (value != null && !value.isBlank()) {
                    return false;
                }
            }
        }
        return true;
    }

    private Object getCellValue(Cell cell, Field field, ExcelColumn annotation, Sheet sheet) {
        if (cell == null) {
            return null;
        }

        Class<?> fieldType = field.getType();

        if (fieldType == String.class) {
            return getStringValue(cell);
        } else if (fieldType == Integer.class || fieldType == int.class) {
            return getIntegerValue(cell);
        } else if (fieldType == BigDecimal.class) {
            return getBigDecimalValue(cell);
        } else if (fieldType == LocalDate.class) {
            return getLocalDateValue(cell, annotation.dateFormat());
        } else if (fieldType == LocalDateTime.class) {
            return getLocalDateTimeValue(cell, annotation.dateFormat());
        } else if (fieldType == Boolean.class || fieldType == boolean.class) {
            return getBooleanValue(cell);
        }

        return getStringValue(cell);
    }

    private String getStringValue(Cell cell) {
        DataFormatter formatter = new DataFormatter();
        String value = formatter.formatCellValue(cell);
        return value != null ? value.trim() : null;
    }

    private Integer getIntegerValue(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            return (int) cell.getNumericCellValue();
        }
        String value = getStringValue(cell);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return Integer.parseInt(value.replaceAll("[,\\s]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private BigDecimal getBigDecimalValue(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String value = getStringValue(cell);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return new BigDecimal(value.replaceAll("[,\\s]", ""));
        } catch (NumberFormatException e) {
            return null;
        }
    }

    private LocalDate getLocalDateValue(Cell cell, String dateFormat) {
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = getStringValue(cell);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDate.parse(value, DateTimeFormatter.ofPattern(dateFormat));
        } catch (Exception e) {
            return null;
        }
    }

    private LocalDateTime getLocalDateTimeValue(Cell cell, String dateFormat) {
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        String value = getStringValue(cell);
        if (value == null || value.isBlank()) {
            return null;
        }
        try {
            return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(dateFormat));
        } catch (Exception e) {
            return null;
        }
    }

    private Boolean getBooleanValue(Cell cell) {
        if (cell.getCellType() == CellType.BOOLEAN) {
            return cell.getBooleanCellValue();
        }
        String value = getStringValue(cell);
        if (value == null || value.isBlank()) {
            return false;
        }
        return "Y".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
    }

    private Cell resolveMergedCell(Sheet sheet, int rowIdx, int colIdx) {
        for (CellRangeAddress range : sheet.getMergedRegions()) {
            if (range.isInRange(rowIdx, colIdx)) {
                Row topRow = sheet.getRow(range.getFirstRow());
                if (topRow != null) {
                    return topRow.getCell(range.getFirstColumn());
                }
            }
        }
        return null;
    }

    private String getCellStringValue(Cell cell) {
        if (cell == null) {
            return null;
        }
        DataFormatter formatter = new DataFormatter();
        return formatter.formatCellValue(cell).trim();
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;
        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }
        return fields;
    }
}
