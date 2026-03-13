package com.foo.excel.service.pipeline.parse;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.HeaderMatchMode;
import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.util.ExcelColumnUtil;
import com.foo.excel.util.SecureExcelUtils;
import com.foo.excel.validation.CellError;
import com.foo.excel.validation.ExcelColumnRef;
import com.foo.excel.validation.RowError;
import java.io.IOException;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.nio.file.Path;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Pattern;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellType;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.springframework.stereotype.Service;

@Slf4j
@Service
public class ExcelParserService {

  private static final ThreadLocal<DataFormatter> DATA_FORMATTER =
      ThreadLocal.withInitial(DataFormatter::new);
  private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");

  public record ColumnMapping(
      Field field,
      ExcelColumn annotation,
      int resolvedColumnIndex,
      ExcelColumnRef resolvedColumnRef) {}

  public record ParseResult<T>(
      List<T> rows,
      List<Integer> sourceRowNumbers,
      List<ColumnMapping> columnMappings,
      List<RowError> parseErrors) {}

  private record HeaderRowRange(int startRowIndex, int endRowIndex) {}

  public <T> ParseResult<T> parse(Path xlsxFile, Class<T> dtoClass, ExcelImportConfig config)
      throws IOException {
    return parse(xlsxFile, dtoClass, config, Integer.MAX_VALUE);
  }

  public <T> ParseResult<T> parse(
      Path xlsxFile, Class<T> dtoClass, ExcelImportConfig config, int maxRows) throws IOException {

    int headerRowNum = config.getHeaderRow() - 1; // 0-based로 변환
    int dataStartRowNum = config.getDataStartRow() - 1;
    int sheetIndex = config.getSheetIndex();
    String footerMarker = config.getFooterMarker();

    // 보안: XXE 및 Zip Bomb 공격 방지를 위해 SecureExcelUtils 사용.
    // 설정된 제한과 보호 내용은 SecureExcelUtils를 참고.
    try {
      try (Workbook workbook = SecureExcelUtils.createWorkbook(xlsxFile)) {
        Sheet sheet = workbook.getSheetAt(sheetIndex);
        Row headerRow = sheet.getRow(headerRowNum);

        if (headerRow == null) {
          throw new IllegalStateException("Header row " + config.getHeaderRow() + " is empty");
        }

        List<ColumnMapping> columnMappings = resolveColumnMappings(dtoClass, sheet, config);
        List<RowError> parseErrors = new ArrayList<>();
        List<Integer> sourceRowNumbers = new ArrayList<>();
        List<T> rows =
            parseDataRows(
                sheet,
                dtoClass,
                columnMappings,
                dataStartRowNum,
                footerMarker,
                parseErrors,
                sourceRowNumbers,
                maxRows);

        return new ParseResult<>(rows, sourceRowNumbers, columnMappings, parseErrors);
      }
    } finally {
      DATA_FORMATTER.remove();
    }
  }

  private <T> List<ColumnMapping> resolveColumnMappings(
      Class<T> dtoClass, Sheet sheet, ExcelImportConfig config) {
    List<ColumnMapping> mappings = new ArrayList<>();
    List<ColumnResolutionException> errors = new ArrayList<>();

    for (Field field : getAllFields(dtoClass)) {
      ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
      if (annotation == null) {
        continue;
      }

      try {
        int resolvedIndex = resolveColumnIndex(annotation, field.getName(), sheet, config);

        if (resolvedIndex < 0) {
          // 선택 필드이며 찾지 못함
          continue;
        }

        ExcelColumnRef resolvedColumnRef =
            ExcelColumnRef.ofLetter(ExcelColumnUtil.indexToLetter(resolvedIndex));
        field.setAccessible(true);
        mappings.add(new ColumnMapping(field, annotation, resolvedIndex, resolvedColumnRef));
      } catch (ColumnResolutionException e) {
        errors.add(e);
      }
    }

    if (!errors.isEmpty()) {
      throw new ColumnResolutionBatchException(errors);
    }

    return mappings;
  }

  private int resolveColumnIndex(
      ExcelColumn annotation, String fieldName, Sheet sheet, ExcelImportConfig config) {
    // 모드 1: 헤더 검증이 포함된 고정 컬럼
    if (!annotation.column().isEmpty()) {
      int index = ExcelColumnUtil.letterToIndex(annotation.column());
      List<String> actualSegments = resolveHeaderSegments(sheet, annotation, config, index);
      String actual = formatResolvedHeader(actualSegments);
      String expected = expectedHeaderLabel(annotation);

      if (matchesResolvedHeader(annotation, actualSegments)) {
        return index;
      }

      // 헤더 불일치
      if (annotation.required()) {
        throw new ColumnResolutionException(
            fieldName,
            expected,
            actual,
            ExcelColumnRef.ofLetter(annotation.column()),
            annotation.matchMode());
      }
      log.warn(
          "Header mismatch for optional field '{}' at column {}: expected '{}', actual '{}'",
          fieldName,
          annotation.column(),
          expected,
          actual);
      return -1;
    }

    // 모드 2: 헤더 텍스트 기반 자동 탐지
    for (var entry : buildResolvedHeaderMap(sheet, annotation, config).entrySet()) {
      if (matchesResolvedHeader(annotation, entry.getValue())) {
        return entry.getKey();
      }
    }

    // 찾지 못함
    if (annotation.required()) {
      throw new ColumnResolutionException(
          fieldName, expectedHeaderLabel(annotation), null, null, annotation.matchMode());
    }
    log.warn(
        "Could not resolve column for optional field '{}' with header '{}'",
        fieldName,
        expectedHeaderLabel(annotation));
    return -1;
  }

  private Map<Integer, List<String>> buildResolvedHeaderMap(
      Sheet sheet, ExcelColumn annotation, ExcelImportConfig config) {
    HeaderRowRange range = resolveHeaderRowRange(annotation, config);
    int maxColumns = resolveHeaderScanColumnCount(sheet, range);
    var map = new LinkedHashMap<Integer, List<String>>();
    for (int i = 0; i < maxColumns; i++) {
      List<String> segments = resolveHeaderSegments(sheet, range, i);
      if (!segments.isEmpty()) {
        map.put(i, segments);
      }
    }
    return map;
  }

  private List<String> resolveHeaderSegments(
      Sheet sheet, ExcelColumn annotation, ExcelImportConfig config, int columnIndex) {
    return resolveHeaderSegments(sheet, resolveHeaderRowRange(annotation, config), columnIndex);
  }

  private List<String> resolveHeaderSegments(
      Sheet sheet, HeaderRowRange range, int columnIndex) {
    List<String> segments = new ArrayList<>();
    for (int rowIndex = range.startRowIndex(); rowIndex <= range.endRowIndex(); rowIndex++) {
      String value = resolveCellValue(sheet, rowIndex, columnIndex);
      if (value == null || value.isBlank()) {
        continue;
      }
      if (segments.isEmpty() || !segments.get(segments.size() - 1).equals(value)) {
        segments.add(value);
      }
    }
    return segments;
  }

  private HeaderRowRange resolveHeaderRowRange(
      ExcelColumn annotation, ExcelImportConfig config) {
    int start = annotation.headerRowStart();
    int end = annotation.headerRowEnd();
    if (start == -1 && end == -1) {
      int headerRow = config.getHeaderRow() - 1;
      return new HeaderRowRange(headerRow, headerRow);
    }
    if (start < 1 || end < 1 || start > end) {
      throw new IllegalStateException(
          "Invalid header row range for column '%s': start=%d, end=%d"
              .formatted(annotation.column(), start, end));
    }
    return new HeaderRowRange(start - 1, end - 1);
  }

  private int resolveHeaderScanColumnCount(Sheet sheet, HeaderRowRange range) {
    int maxColumns = 0;
    for (int rowIndex = range.startRowIndex(); rowIndex <= range.endRowIndex(); rowIndex++) {
      Row row = sheet.getRow(rowIndex);
      if (row != null && row.getLastCellNum() > maxColumns) {
        maxColumns = row.getLastCellNum();
      }
    }
    return maxColumns;
  }

  private boolean matchesResolvedHeader(ExcelColumn annotation, List<String> actualSegments) {
    if (actualSegments.isEmpty()) {
      return false;
    }

    List<String> expectedSegments = expectedHeaderSegments(annotation);
    if (annotation.headerPath().length == 0) {
      return matchHeader(
          actualSegments.get(actualSegments.size() - 1),
          expectedSegments.get(0),
          annotation.matchMode(),
          annotation.ignoreHeaderWhitespace());
    }

    if (actualSegments.size() != expectedSegments.size()) {
      return false;
    }

    for (int i = 0; i < expectedSegments.size(); i++) {
      if (!matchHeader(
          actualSegments.get(i),
          expectedSegments.get(i),
          annotation.matchMode(),
          annotation.ignoreHeaderWhitespace())) {
        return false;
      }
    }
    return true;
  }

  private List<String> expectedHeaderSegments(ExcelColumn annotation) {
    if (annotation.headerPath().length == 0) {
      return Collections.singletonList(annotation.header());
    }
    return Arrays.asList(annotation.headerPath());
  }

  private String expectedHeaderLabel(ExcelColumn annotation) {
    return String.join(" > ", expectedHeaderSegments(annotation));
  }

  private String formatResolvedHeader(List<String> segments) {
    if (segments.isEmpty()) {
      return null;
    }
    return String.join(" > ", segments);
  }

  private boolean matchHeader(
      String cellValue, String expected, HeaderMatchMode mode, boolean ignoreWhitespace) {
    if (cellValue == null || cellValue.isBlank()) {
      return false;
    }

    String normalizedCell = normalizeHeaderValue(cellValue, ignoreWhitespace);
    String normalizedExpected = normalizeHeaderValue(expected, ignoreWhitespace);

    return switch (mode) {
      case EXACT -> normalizedCell.equalsIgnoreCase(normalizedExpected);
      case CONTAINS -> normalizedCell.contains(normalizedExpected);
      case STARTS_WITH -> normalizedCell.startsWith(normalizedExpected);
      case REGEX -> Pattern.matches(normalizedExpected, normalizedCell);
    };
  }

  private String normalizeHeaderValue(String value, boolean ignoreWhitespace) {
    String normalized = value.trim();
    if (ignoreWhitespace) {
      normalized = WHITESPACE_PATTERN.matcher(normalized).replaceAll("");
    }
    return normalized;
  }

  private <T> List<T> parseDataRows(
      Sheet sheet,
      Class<T> dtoClass,
      List<ColumnMapping> columnMappings,
      int dataStartRowNum,
      String footerMarker,
      List<RowError> parseErrors,
      List<Integer> sourceRowNumbers,
      int maxRows) {

    List<T> rows = new ArrayList<>();
    int lastRowNum = sheet.getLastRowNum();

    for (int i = dataStartRowNum; i <= lastRowNum; i++) {
      Row row = sheet.getRow(i);
      if (row == null) {
        continue;
      }

      // 푸터 감지
      if (isFooterRow(row, footerMarker)) {
        log.debug("Footer marker found at row {}, stopping", i + 1);
        break;
      }

      // 빈 행 건너뛰기
      if (isBlankRow(row)) {
        continue;
      }

      T dto;
      try {
        dto = dtoClass.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new RuntimeException("Cannot instantiate DTO", e);
      }

      int excelRowNumber = i + 1; // 1-based 기준
      List<CellError> cellErrors = new ArrayList<>();

      for (ColumnMapping mapping : columnMappings) {
        Cell cell = resolveEffectiveCell(sheet, i, mapping.resolvedColumnIndex());

        Object value = getCellValue(cell, mapping, sheet, cellErrors);
        try {
          mapping.field().set(dto, value);
        } catch (IllegalAccessException e) {
          throw new RuntimeException("Cannot set field: " + mapping.field().getName(), e);
        }
      }

      if (!cellErrors.isEmpty()) {
        parseErrors.add(
            RowError.builder().rowNumber(excelRowNumber).cellErrors(cellErrors).build());
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

  private Object getCellValue(
      Cell cell, ColumnMapping mapping, Sheet sheet, List<CellError> parseErrors) {
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
      parseErrors.add(
          CellError.builder()
              .columnIndex(mapping.resolvedColumnIndex())
              .columnRef(mapping.resolvedColumnRef())
              .fieldName(mapping.field().getName())
              .headerName(mapping.annotation().header())
              .rejectedValue(rawValue)
              .message("'" + rawValue + "' 값을 " + fieldType.getSimpleName() + " 타입으로 변환할 수 없습니다")
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

  private String resolveCellValue(Sheet sheet, int rowIdx, int colIdx) {
    Cell cell = resolveEffectiveCell(sheet, rowIdx, colIdx);
    return getCellStringValue(cell);
  }

  private Cell resolveEffectiveCell(Sheet sheet, int rowIdx, int colIdx) {
    Row row = sheet.getRow(rowIdx);
    Cell cell = row != null ? row.getCell(colIdx) : null;
    if (cell == null || isBlankCellValue(cell)) {
      Cell mergedCell = resolveMergedCell(sheet, rowIdx, colIdx);
      if (mergedCell != null && !isBlankCellValue(mergedCell)) {
        return mergedCell;
      }
    }
    return cell;
  }

  private boolean isBlankCellValue(Cell cell) {
    String value = getCellStringValue(cell);
    return value == null || value.isBlank();
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
