package com.foo.excel.service.pipeline.parse;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.HeaderMatchMode;
import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.util.ExcelColumnUtil;
import com.foo.excel.util.SecureExcelUtils;
import com.foo.excel.validation.CellError;
import com.foo.excel.validation.RowError;
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

    private static final ThreadLocal<DataFormatter> DATA_FORMATTER =
            ThreadLocal.withInitial(DataFormatter::new);

    public record ColumnMapping(
            Field field,
            ExcelColumn annotation,
            int resolvedColumnIndex,
            String resolvedColumnLetter) {}

    public record ParseResult<T>(
            List<T> rows,
            List<Integer> sourceRowNumbers,
            List<ColumnMapping> columnMappings,
            List<RowError> parseErrors) {}

    public <T> ParseResult<T> parse(Path xlsxFile, Class<T> dtoClass, ExcelImportConfig config)
            throws IOException {
        return parse(xlsxFile, dtoClass, config, Integer.MAX_VALUE);
    }

    public <T> ParseResult<T> parse(Path xlsxFile, Class<T> dtoClass, ExcelImportConfig config,
            int maxRows) throws IOException {

        int headerRowNum = config.getHeaderRow() - 1;   // Convert to 0-based
        int dataStartRowNum = config.getDataStartRow() - 1;
        int sheetIndex = config.getSheetIndex();
        String footerMarker = config.getFooterMarker();

        // SECURITY: Use SecureExcelUtils to protect against XXE and Zip Bomb attacks.
        // See SecureExcelUtils for configured limits and protections.
        try (Workbook workbook = SecureExcelUtils.createWorkbook(xlsxFile)) {
            Sheet sheet = workbook.getSheetAt(sheetIndex);
            Row headerRow = sheet.getRow(headerRowNum);

            if (headerRow == null) {
                throw new IllegalStateException("Header row " + config.getHeaderRow() + " is empty");
            }

            List<ColumnMapping> columnMappings = resolveColumnMappings(dtoClass, headerRow, sheet);
            List<RowError> parseErrors = new ArrayList<>();
            List<Integer> sourceRowNumbers = new ArrayList<>();
            List<T> rows = parseDataRows(sheet, dtoClass, columnMappings, dataStartRowNum,
                    footerMarker, parseErrors, sourceRowNumbers, maxRows);

            return new ParseResult<>(rows, sourceRowNumbers, columnMappings, parseErrors);
        }
    }

    private <T> List<ColumnMapping> resolveColumnMappings(Class<T> dtoClass, Row headerRow, Sheet sheet) {
        List<ColumnMapping> mappings = new ArrayList<>();
        List<ColumnResolutionException> errors = new ArrayList<>();
        Map<Integer, String> headerMap = buildHeaderMap(headerRow, sheet);

        for (Field field : getAllFields(dtoClass)) {
            ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
            if (annotation == null) {
                continue;
            }

            try {
                int resolvedIndex = resolveColumnIndex(annotation, field.getName(), headerMap);

                if (resolvedIndex < 0) {
                    // optional field, not found
                    continue;
                }

                String resolvedLetter = ExcelColumnUtil.indexToLetter(resolvedIndex);
                field.setAccessible(true);
                mappings.add(new ColumnMapping(field, annotation, resolvedIndex, resolvedLetter));
            } catch (ColumnResolutionException e) {
                errors.add(e);
            }
        }

        if (!errors.isEmpty()) {
            throw new ColumnResolutionBatchException(errors);
        }

        return mappings;
    }

    private Map<Integer, String> buildHeaderMap(Row headerRow, Sheet sheet) {
        var map = new LinkedHashMap<Integer, String>();
        for (int i = 0; i <= headerRow.getLastCellNum(); i++) {
            Cell cell = headerRow.getCell(i);
            if (cell == null) {
                cell = resolveMergedCell(sheet, headerRow.getRowNum(), i);
            }
            if (cell != null) {
                String value = getCellStringValue(cell);
                if (value != null && !value.isBlank()) {
                    map.put(i, value);
                }
            }
        }
        return map;
    }

    private int resolveColumnIndex(ExcelColumn annotation, String fieldName,
                                   Map<Integer, String> headerMap) {
        // Mode 1: Fixed column with header verification
        if (!annotation.column().isEmpty()) {
            int index = ExcelColumnUtil.letterToIndex(annotation.column());
            String actual = headerMap.get(index);

            if (matchHeader(actual, annotation.header(), annotation.matchMode())) {
                return index;
            }

            // Header mismatch
            if (annotation.required()) {
                throw new ColumnResolutionException(fieldName, annotation.header(),
                        actual, annotation.column(), annotation.matchMode());
            }
            log.warn("Header mismatch for optional field '{}' at column {}: expected '{}', actual '{}'",
                    fieldName, annotation.column(), annotation.header(), actual);
            return -1;
        }

        // Mode 2: Auto-detect from header text
        for (var entry : headerMap.entrySet()) {
            if (matchHeader(entry.getValue(), annotation.header(), annotation.matchMode())) {
                return entry.getKey();
            }
        }

        // Not found
        if (annotation.required()) {
            throw new ColumnResolutionException(fieldName, annotation.header(),
                    null, null, annotation.matchMode());
        }
        log.warn("Could not resolve column for optional field '{}' with header '{}'",
                fieldName, annotation.header());
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

    private <T> List<T> parseDataRows(Sheet sheet, Class<T> dtoClass,
            List<ColumnMapping> columnMappings, int dataStartRowNum, String footerMarker,
            List<RowError> parseErrors, List<Integer> sourceRowNumbers, int maxRows) {

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

            int excelRowNumber = i + 1; // 1-based
            List<CellError> cellErrors = new ArrayList<>();

            for (ColumnMapping mapping : columnMappings) {
                Cell cell = row.getCell(mapping.resolvedColumnIndex());
                if (cell == null) {
                    cell = resolveMergedCell(sheet, i, mapping.resolvedColumnIndex());
                }

                Object value = getCellValue(cell, mapping, sheet, cellErrors);
                try {
                    mapping.field().set(dto, value);
                } catch (IllegalAccessException e) {
                    throw new RuntimeException("Cannot set field: " + mapping.field().getName(), e);
                }
            }

            if (!cellErrors.isEmpty()) {
                parseErrors.add(RowError.builder()
                        .rowNumber(excelRowNumber)
                        .cellErrors(cellErrors)
                        .build());
            }

            sourceRowNumbers.add(excelRowNumber);
            rows.add(dto);

            if (rows.size() > maxRows) {
                log.info("Row limit exceeded during parsing at row {}, stopping early", excelRowNumber);
                break;
            }
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

    private Object getCellValue(Cell cell, ColumnMapping mapping, Sheet sheet,
                                List<CellError> parseErrors) {
        if (cell == null) {
            return null;
        }

        Class<?> fieldType = mapping.field().getType();
        String dateFormat = mapping.annotation().dateFormat();

        try {
            if (fieldType == String.class) {
                return getStringValue(cell);
            } else if (fieldType == Integer.class || fieldType == int.class) {
                return getIntegerValue(cell);
            } else if (fieldType == BigDecimal.class) {
                return getBigDecimalValue(cell);
            } else if (fieldType == LocalDate.class) {
                return getLocalDateValue(cell, dateFormat);
            } else if (fieldType == LocalDateTime.class) {
                return getLocalDateTimeValue(cell, dateFormat);
            } else if (fieldType == Boolean.class || fieldType == boolean.class) {
                return getBooleanValue(cell);
            }
            return getStringValue(cell);
        } catch (Exception e) {
            String rawValue = getCellStringValue(cell);
            parseErrors.add(CellError.builder()
                    .columnIndex(mapping.resolvedColumnIndex())
                    .columnLetter(mapping.resolvedColumnLetter())
                    .fieldName(mapping.field().getName())
                    .headerName(mapping.annotation().header())
                    .rejectedValue(rawValue)
                    .message("'" + rawValue + "' 값을 " + fieldType.getSimpleName()
                            + " 타입으로 변환할 수 없습니다")
                    .build());
            return null;
        }
    }

    private String getStringValue(Cell cell) {
        String value = DATA_FORMATTER.get().formatCellValue(cell);
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
        return Integer.parseInt(value.replaceAll("[,\\s]", ""));
    }

    private BigDecimal getBigDecimalValue(Cell cell) {
        if (cell.getCellType() == CellType.NUMERIC) {
            return BigDecimal.valueOf(cell.getNumericCellValue());
        }
        String value = getStringValue(cell);
        if (value == null || value.isBlank()) {
            return null;
        }
        return new BigDecimal(value.replaceAll("[,\\s]", ""));
    }

    private LocalDate getLocalDateValue(Cell cell, String dateFormat) {
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue().toLocalDate();
        }
        String value = getStringValue(cell);
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDate.parse(value, DateTimeFormatter.ofPattern(dateFormat));
    }

    private LocalDateTime getLocalDateTimeValue(Cell cell, String dateFormat) {
        if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
            return cell.getLocalDateTimeCellValue();
        }
        String value = getStringValue(cell);
        if (value == null || value.isBlank()) {
            return null;
        }
        return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(dateFormat));
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
        return DATA_FORMATTER.get().formatCellValue(cell).trim();
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
