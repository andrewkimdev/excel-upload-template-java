package com.foo.excel.service.pipeline.parse;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.HeaderMatchMode;
import com.foo.excel.service.contract.ExcelSheetSpec;
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
import java.util.HashMap;
import java.util.IdentityHashMap;
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

  /**
   * DTO 필드와 실제 엑셀 컬럼 사이의 해석 결과를 보관한다.
   *
   * @param field 매핑 대상 필드
   * @param annotation 필드에 선언된 엑셀 컬럼 애너테이션
   * @param resolvedColumnIndex 해석된 실제 컬럼 인덱스
   * @param resolvedColumnRef 해석된 실제 컬럼 참조값
   */
  public record ColumnMapping(
      Field field,
      ExcelColumn annotation,
      int resolvedColumnIndex,
      ExcelColumnRef resolvedColumnRef) {}

  /**
   * 엑셀 파싱 결과와 파싱 중 수집된 부가 정보를 함께 반환한다.
   *
   * @param rows 파싱된 행 데이터
   * @param sourceRowNumbers 원본 엑셀 행 번호 목록
   * @param columnMappings 해석된 컬럼 매핑 목록
   * @param parseErrors 파싱 중 발생한 오류 목록
   * @param <T> 행 DTO 타입
   */
  public record ParseResult<T>(
      List<T> rows,
      List<Integer> sourceRowNumbers,
      List<ColumnMapping> columnMappings,
      List<RowError> parseErrors) {}

  private record HeaderRowRange(int startRowIndex, int endRowIndex) {}

  private record CellRef(int rowIndex, int columnIndex) {}

  public <T> ParseResult<T> parse(
      Path xlsxFile, Class<T> rowClass, ExcelSheetSpec sheetSpec)
      throws IOException {
    return parse(xlsxFile, rowClass, sheetSpec, Integer.MAX_VALUE, Integer.MAX_VALUE);
  }

  public <T> ParseResult<T> parse(
      Path xlsxFile, Class<T> rowClass, ExcelSheetSpec sheetSpec, int maxRows)
      throws IOException {
    return parse(xlsxFile, rowClass, sheetSpec, maxRows, Integer.MAX_VALUE);
  }

  public <T> ParseResult<T> parse(
      Path xlsxFile,
      Class<T> rowClass,
      ExcelSheetSpec sheetSpec,
      int maxRows,
      int maxErrorRows)
      throws IOException {

    int headerRowNum = sheetSpec.headerRow() - 1; // 0-based로 변환
    int dataStartRowNum = sheetSpec.dataStartRow() - 1;
    int resolvedSheetIndex = sheetSpec.resolvedSheetIndex();
    String footerMarker = sheetSpec.footerMarker();

    // 보안: XXE 및 Zip Bomb 공격 방지를 위해 SecureExcelUtils 사용.
    // 설정된 제한과 보호 내용은 SecureExcelUtils를 참고.
    try {
      try (Workbook workbook = SecureExcelUtils.createWorkbook(xlsxFile)) {
        // ExcelSheetSpec already stores the resolver-converted 0-based sheet index.
        Sheet sheet = workbook.getSheetAt(resolvedSheetIndex);
        Row headerRow = sheet.getRow(headerRowNum);

        if (headerRow == null) {
          throw new IllegalStateException("Header row " + sheetSpec.headerRow() + " is empty");
        }

        Map<CellRef, CellRef> mergedCellLookup = buildMergedCellLookup(sheet);
        Map<Cell, String> formattedCellCache = new IdentityHashMap<>();
        List<ColumnMapping> columnMappings = resolveColumnMappings(rowClass, sheet, sheetSpec);
        List<RowError> parseErrors = new ArrayList<>();
        List<Integer> sourceRowNumbers = new ArrayList<>();
        List<T> rows =
            parseDataRows(
                sheet,
                rowClass,
                columnMappings,
                dataStartRowNum,
                footerMarker,
                parseErrors,
                sourceRowNumbers,
                maxRows,
                maxErrorRows,
                mergedCellLookup,
                formattedCellCache);

        return new ParseResult<>(rows, sourceRowNumbers, columnMappings, parseErrors);
      }
    } finally {
      DATA_FORMATTER.remove();
    }
  }

  private <T> List<ColumnMapping> resolveColumnMappings(
      Class<T> rowClass, Sheet sheet, ExcelSheetSpec sheetSpec) {
    List<ColumnMapping> mappings = new ArrayList<>();
    List<ColumnResolutionException> errors = new ArrayList<>();

    for (Field field : getAllFields(rowClass)) {
      ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
      if (annotation == null) {
        continue;
      }

      try {
        int resolvedIndex = resolveColumnIndex(annotation, field.getName(), sheet, sheetSpec);

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
      ExcelColumn annotation, String fieldName, Sheet sheet, ExcelSheetSpec sheetSpec) {
    int index = ExcelColumnUtil.letterToIndex(annotation.column());
    List<String> actualSegments = resolveHeaderSegments(sheet, annotation, sheetSpec, index);
    String actual = formatResolvedHeader(actualSegments);
    String expected = expectedHeaderLabel(annotation);

    if (matchesResolvedHeader(annotation, actualSegments)) {
      return index;
    }

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

  private List<String> resolveHeaderSegments(
      Sheet sheet, ExcelColumn annotation, ExcelSheetSpec sheetSpec, int columnIndex) {
    return resolveHeaderSegments(
        sheet, resolveHeaderRowRange(annotation, sheetSpec), columnIndex);
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
      ExcelColumn annotation, ExcelSheetSpec sheetSpec) {
    int start = annotation.headerRowStart();
    int count = annotation.headerRowCount();
    if (start == -1 && count == -1) {
      int headerRow = sheetSpec.headerRow() - 1;
      return new HeaderRowRange(headerRow, headerRow);
    }
    if (start < 1 || count < 1) {
      throw new IllegalStateException(
          "Invalid header row range for column '%s': start=%d, count=%d"
              .formatted(annotation.column(), start, count));
    }
    return new HeaderRowRange(start - 1, start + count - 2);
  }

  private boolean matchesResolvedHeader(ExcelColumn annotation, List<String> actualSegments) {
    if (actualSegments.isEmpty()) {
      return false;
    }

    List<String> expectedSegments = expectedHeaderSegments(annotation);
    if (annotation.headerLabels().length == 0) {
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
    if (annotation.headerLabels().length == 0) {
      return Collections.singletonList(annotation.label());
    }
    return Arrays.asList(annotation.headerLabels());
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
      Class<T> rowClass,
      List<ColumnMapping> columnMappings,
      int dataStartRowNum,
      String footerMarker,
      List<RowError> parseErrors,
      List<Integer> sourceRowNumbers,
      int maxRows,
      int maxErrorRows,
      Map<CellRef, CellRef> mergedCellLookup,
      Map<Cell, String> formattedCellCache) {

    List<T> rows = new ArrayList<>();
    int lastRowNum = sheet.getLastRowNum();

    for (int i = dataStartRowNum; i <= lastRowNum; i++) {
      Row row = sheet.getRow(i);
      if (row == null) {
        continue;
      }

      // 푸터 감지
      if (isFooterRow(row, footerMarker, formattedCellCache)) {
        log.debug("Footer marker found at row {}, stopping", i + 1);
        break;
      }

      // 빈 행 건너뛰기
      if (isBlankRow(row, formattedCellCache)) {
        continue;
      }

      T dto;
      try {
        dto = rowClass.getDeclaredConstructor().newInstance();
      } catch (Exception e) {
        throw new RuntimeException("Cannot instantiate DTO", e);
      }

      int excelRowNumber = i + 1; // 1-based 기준
      List<CellError> cellErrors = new ArrayList<>();

      for (ColumnMapping mapping : columnMappings) {
        Cell cell =
            resolveEffectiveCell(
                sheet, i, mapping.resolvedColumnIndex(), mergedCellLookup, formattedCellCache);

        if (cell == null || isBlankCellValue(cell, formattedCellCache)) {
          continue;
        }

        Object value = getCellValue(cell, mapping, cellErrors, formattedCellCache);
        try {
          mapping.field().set(dto, value);
        } catch (IllegalAccessException e) {
          throw new RuntimeException("Cannot set field: " + mapping.field().getName(), e);
        }
      }

      if (!cellErrors.isEmpty()) {
        parseErrors.add(
            RowError.builder().rowNumber(excelRowNumber).cellErrors(cellErrors).build());
        if (hasReachedErrorLimit(parseErrors.size(), maxErrorRows)) {
          log.info("Parse error row limit reached at row {}, stopping early", excelRowNumber);
          sourceRowNumbers.add(excelRowNumber);
          rows.add(dto);
          break;
        }
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

  private boolean hasReachedErrorLimit(int errorRows, int maxErrorRows) {
    return maxErrorRows > 0 && maxErrorRows != Integer.MAX_VALUE && errorRows >= maxErrorRows;
  }

  private boolean isFooterRow(Row row, String footerMarker, Map<Cell, String> formattedCellCache) {
    if (footerMarker == null || footerMarker.isEmpty()) {
      return false;
    }
    for (Cell cell : row) {
      String value = getCellStringValue(cell, formattedCellCache);
      if (value != null && value.contains(footerMarker)) {
        return true;
      }
    }
    return false;
  }

  private boolean isBlankRow(Row row, Map<Cell, String> formattedCellCache) {
    for (Cell cell : row) {
      if (cell != null && cell.getCellType() != CellType.BLANK) {
        String value = getCellStringValue(cell, formattedCellCache);
        if (value != null && !value.isBlank()) {
          return false;
        }
      }
    }
    return true;
  }

  private Object getCellValue(
      Cell cell,
      ColumnMapping mapping,
      List<CellError> parseErrors,
      Map<Cell, String> formattedCellCache) {
    if (cell == null) {
      return null;
    }

    Class<?> fieldType = mapping.field().getType();
    String dateFormat = mapping.annotation().dateFormat();

    try {
      if (fieldType == String.class) {
        return getStringValue(cell, formattedCellCache);
      } else if (fieldType == Integer.class || fieldType == int.class) {
        return getIntegerValue(cell, formattedCellCache);
      } else if (fieldType == BigDecimal.class) {
        return getBigDecimalValue(cell, formattedCellCache);
      } else if (fieldType == LocalDate.class) {
        return getLocalDateValue(cell, dateFormat, formattedCellCache);
      } else if (fieldType == LocalDateTime.class) {
        return getLocalDateTimeValue(cell, dateFormat, formattedCellCache);
      } else if (fieldType == Boolean.class || fieldType == boolean.class) {
        return getBooleanValue(cell, formattedCellCache);
      }
      return getStringValue(cell, formattedCellCache);
    } catch (Exception e) {
      String rawValue = getCellStringValue(cell, formattedCellCache);
      parseErrors.add(
          CellError.builder()
              .columnIndex(mapping.resolvedColumnIndex())
              .columnRef(mapping.resolvedColumnRef())
              .fieldName(mapping.field().getName())
              .headerName(mapping.annotation().label())
              .rejectedValue(rawValue)
              .message("'" + rawValue + "' 값을 " + fieldType.getSimpleName() + " 타입으로 변환할 수 없습니다")
              .build());
      return null;
    }
  }

  private String getStringValue(Cell cell, Map<Cell, String> formattedCellCache) {
    String value = getCellStringValue(cell, formattedCellCache);
    return value != null ? value.trim() : null;
  }

  private Integer getIntegerValue(Cell cell, Map<Cell, String> formattedCellCache) {
    if (cell.getCellType() == CellType.NUMERIC) {
      return (int) cell.getNumericCellValue();
    }
    String value = getStringValue(cell, formattedCellCache);
    if (value == null || value.isBlank()) {
      return null;
    }
    return Integer.parseInt(value.replaceAll("[,\\s]", ""));
  }

  private BigDecimal getBigDecimalValue(Cell cell, Map<Cell, String> formattedCellCache) {
    if (cell.getCellType() == CellType.NUMERIC) {
      return BigDecimal.valueOf(cell.getNumericCellValue());
    }
    String value = getStringValue(cell, formattedCellCache);
    if (value == null || value.isBlank()) {
      return null;
    }
    return new BigDecimal(value.replaceAll("[,\\s]", ""));
  }

  private LocalDate getLocalDateValue(
      Cell cell, String dateFormat, Map<Cell, String> formattedCellCache) {
    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
      return cell.getLocalDateTimeCellValue().toLocalDate();
    }
    String value = getStringValue(cell, formattedCellCache);
    if (value == null || value.isBlank()) {
      return null;
    }
    return LocalDate.parse(value, DateTimeFormatter.ofPattern(dateFormat));
  }

  private LocalDateTime getLocalDateTimeValue(
      Cell cell, String dateFormat, Map<Cell, String> formattedCellCache) {
    if (cell.getCellType() == CellType.NUMERIC && DateUtil.isCellDateFormatted(cell)) {
      return cell.getLocalDateTimeCellValue();
    }
    String value = getStringValue(cell, formattedCellCache);
    if (value == null || value.isBlank()) {
      return null;
    }
    return LocalDateTime.parse(value, DateTimeFormatter.ofPattern(dateFormat));
  }

  private Boolean getBooleanValue(Cell cell, Map<Cell, String> formattedCellCache) {
    if (cell.getCellType() == CellType.BOOLEAN) {
      return cell.getBooleanCellValue();
    }
    String value = getStringValue(cell, formattedCellCache);
    if (value == null || value.isBlank()) {
      return false;
    }
    return "Y".equalsIgnoreCase(value) || "true".equalsIgnoreCase(value);
  }

  private Map<CellRef, CellRef> buildMergedCellLookup(Sheet sheet) {
    Map<CellRef, CellRef> mergedCellLookup = new HashMap<>();
    for (CellRangeAddress range : sheet.getMergedRegions()) {
      CellRef anchor = new CellRef(range.getFirstRow(), range.getFirstColumn());
      for (int rowIndex = range.getFirstRow(); rowIndex <= range.getLastRow(); rowIndex++) {
        for (int columnIndex = range.getFirstColumn();
            columnIndex <= range.getLastColumn();
            columnIndex++) {
          mergedCellLookup.put(new CellRef(rowIndex, columnIndex), anchor);
        }
      }
    }
    return mergedCellLookup;
  }

  private Cell resolveMergedCell(Sheet sheet, int rowIdx, int colIdx, Map<CellRef, CellRef> mergedCellLookup) {
    CellRef anchor = mergedCellLookup.get(new CellRef(rowIdx, colIdx));
    if (anchor == null) {
      return null;
    }
    Row topRow = sheet.getRow(anchor.rowIndex());
    return topRow != null ? topRow.getCell(anchor.columnIndex()) : null;
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

  private String resolveCellValue(
      Sheet sheet,
      int rowIdx,
      int colIdx,
      Map<CellRef, CellRef> mergedCellLookup,
      Map<Cell, String> formattedCellCache) {
    Cell cell = resolveEffectiveCell(sheet, rowIdx, colIdx, mergedCellLookup, formattedCellCache);
    return getCellStringValue(cell, formattedCellCache);
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

  private Cell resolveEffectiveCell(
      Sheet sheet,
      int rowIdx,
      int colIdx,
      Map<CellRef, CellRef> mergedCellLookup,
      Map<Cell, String> formattedCellCache) {
    Row row = sheet.getRow(rowIdx);
    Cell cell = row != null ? row.getCell(colIdx) : null;
    if (cell == null || isBlankCellValue(cell, formattedCellCache)) {
      Cell mergedCell = resolveMergedCell(sheet, rowIdx, colIdx, mergedCellLookup);
      if (mergedCell != null && !isBlankCellValue(mergedCell, formattedCellCache)) {
        return mergedCell;
      }
    }
    return cell;
  }

  private boolean isBlankCellValue(Cell cell, Map<Cell, String> formattedCellCache) {
    String value = getCellStringValue(cell, formattedCellCache);
    return value == null || value.isBlank();
  }

  private boolean isBlankCellValue(Cell cell) {
    String value = getCellStringValue(cell);
    return value == null || value.isBlank();
  }

  private String getCellStringValue(Cell cell, Map<Cell, String> formattedCellCache) {
    if (cell == null) {
      return null;
    }
    return formattedCellCache.computeIfAbsent(cell, current -> DATA_FORMATTER.get().formatCellValue(current).trim());
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
