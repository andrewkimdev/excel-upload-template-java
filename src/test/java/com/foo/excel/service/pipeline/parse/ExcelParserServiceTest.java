package com.foo.excel.service.pipeline.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.foo.excel.annotation.ExcelSheet;
import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.HeaderMatchMode;
import com.foo.excel.service.contract.ExcelSheetSpec;
import com.foo.excel.service.contract.ExcelSheetSpecResolver;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemDto;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.LocalDate;
import lombok.Data;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelParserServiceTest {

  private ExcelParserService parserService;
  private ExcelSheetSpec tariffSheetSpec;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    parserService = new ExcelParserService();
    tariffSheetSpec = ExcelSheetSpecResolver.resolve(AAppcarItemDto.class);
  }

  @Test
  void parse_correctNumberOfRows() throws IOException {
    Path file = createAAppcarItemFile(3, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffSheetSpec);

    assertThat(result.rows()).hasSize(3);
    assertThat(result.sourceRowNumbers()).hasSize(3);
  }

  @Test
  void parse_footerMarker_stopsReading() throws IOException {
    Path file = createAAppcarItemFile(3, true, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffSheetSpec);

    // 데이터 3행 뒤 푸터가 오므로 3행만 읽음
    assertThat(result.rows()).hasSize(3);
  }

  @Test
  void parse_blankRows_skipped() throws IOException {
    Path file = createAAppcarItemFile(3, false, true);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffSheetSpec);

    // 데이터 3행 + 빈 행 1개 삽입 = 3행만 파싱
    assertThat(result.rows()).hasSize(3);
  }

  @Test
  void parse_mergedCellResolution() throws IOException {
    // 병합 셀 F-G(HSK No 컬럼)가 있는 파일 생성
    Path file = createFileWithMergedCells();

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffSheetSpec);

    assertThat(result.rows()).isNotEmpty();
    // 병합 셀 값은 F열에서 읽혀야 함
    assertThat(result.rows().get(0).getHsno()).isEqualTo("8481.80-2000");
  }

  @Test
  void parse_multiRowMergedHeaders_realSample() throws IOException {
    Path file = copySampleFile("samples/tariff_exemption_sample_merged_cols.xlsx");

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffSheetSpec);

    assertThat(result.rows()).hasSize(3);
    assertThat(result.rows().get(0).getProdQty()).isEqualTo(10);
    assertThat(result.rows().get(0).getRepairQty()).isEqualTo(2);
  }

  @Test
  void parse_typeCoercion_string_trimmed() throws IOException {
    Path file = createAAppcarItemFile(1, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffSheetSpec);

    assertThat(result.rows().get(0).getGoodsDes()).isEqualTo("TestItem1");
  }

  @Test
  void parse_typeCoercion_integer() throws IOException {
    Path file = createAAppcarItemFile(1, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffSheetSpec);

    assertThat(result.rows().get(0).getGoodsSeqNo()).isEqualTo(1);
  }

  @Test
  void parse_typeCoercion_bigDecimal() throws IOException {
    Path file = createAAppcarItemFile(1, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffSheetSpec);

    assertThat(result.rows().get(0).getTaxRate()).isNotNull();
    assertThat(result.rows().get(0).getTaxRate().doubleValue()).isCloseTo(8.0, within(0.01));
  }

  @Test
  void parse_headerMatchMode_exact_and_contains_and_startsWith() throws IOException {
    Path file = createFileWithAnnotatedHeaders();
    ExcelSheetSpec simpleSheetSpec = ExcelSheetSpecResolver.resolve(SimpleDto.class);

    ExcelParserService.ParseResult<SimpleDto> result =
        parserService.parse(file, SimpleDto.class, simpleSheetSpec);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getExactField()).isEqualTo("exactVal");
    assertThat(result.rows().get(0).getContainsField()).isEqualTo("containsVal");
    assertThat(result.rows().get(0).getStartsWithField()).isEqualTo("startsVal");
  }

  @Test
  void parse_headerWhitespaceIgnoredWhenConfigured() throws IOException {
    Path file = createFileWithWhitespaceSeparatedHeader();
    ExcelSheetSpec sheetSpec =
        ExcelSheetSpecResolver.resolve(WhitespaceEqualsDto.class);

    ExcelParserService.ParseResult<WhitespaceEqualsDto> result =
        parserService.parse(file, WhitespaceEqualsDto.class, sheetSpec);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getValue()).isEqualTo("value");
  }

  @Test
  void parse_headerWhitespaceNotIgnoredForExactByDefault() throws IOException {
    Path file = createFileWithWhitespaceSeparatedHeader();
    ExcelSheetSpec sheetSpec =
        ExcelSheetSpecResolver.resolve(WhitespaceExactDto.class);

    assertThatThrownBy(() -> parserService.parse(file, WhitespaceExactDto.class, sheetSpec))
        .isInstanceOf(ColumnResolutionBatchException.class)
        .satisfies(
            ex -> {
              var batch = (ColumnResolutionBatchException) ex;
              assertThat(batch.toKoreanMessage()).contains("헤더가 일치하지 않습니다");
            });
  }

  @Test
  void parse_columnA_skipped() throws IOException {
    Path file = createAAppcarItemFile(1, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffSheetSpec);

    // A열은 장식용이라 파서가 건너뛰며 어떤 필드도 매핑되지 않음
    // 컬럼 매핑에 A열(index 0)이 없는지 확인
    assertThat(result.columnMappings()).noneMatch(m -> m.resolvedColumnIndex() == 0);
  }

  @Test
  void parse_typeCoercion_boolean_Y_true_blank_preservesNull() throws IOException {
    Path file = createFileWithBooleanColumn();
    ExcelSheetSpec boolSheetSpec =
        ExcelSheetSpecResolver.resolve(BooleanDto.class);

    ExcelParserService.ParseResult<BooleanDto> result =
        parserService.parse(file, BooleanDto.class, boolSheetSpec);

    assertThat(result.rows()).hasSize(2);
    assertThat(result.rows().get(0).getActive()).isTrue();
    assertThat(result.rows().get(1).getActive()).isNull();
  }

  @Test
  void parse_blankCells_preserveDtoDefaultInitializers() throws IOException {
    Path file = createFileWithDefaultedFields("", "");
    ExcelSheetSpec sheetSpec =
        ExcelSheetSpecResolver.resolve(DefaultValueDto.class);

    ExcelParserService.ParseResult<DefaultValueDto> result =
        parserService.parse(file, DefaultValueDto.class, sheetSpec);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getName()).isEqualTo("DEFAULT_NAME");
    assertThat(result.rows().get(0).getActive()).isTrue();
  }

  @Test
  void parse_missingCells_preserveDtoDefaultInitializers() throws IOException {
    Path file = createFileWithDefaultedFields(null, null);
    ExcelSheetSpec sheetSpec =
        ExcelSheetSpecResolver.resolve(DefaultValueDto.class);

    ExcelParserService.ParseResult<DefaultValueDto> result =
        parserService.parse(file, DefaultValueDto.class, sheetSpec);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getName()).isEqualTo("DEFAULT_NAME");
    assertThat(result.rows().get(0).getActive()).isTrue();
  }

  @Test
  void parse_nonBlankCells_overrideDtoDefaultInitializers() throws IOException {
    Path file = createFileWithDefaultedFields("updated", "Y");
    ExcelSheetSpec sheetSpec =
        ExcelSheetSpecResolver.resolve(DefaultValueDto.class);

    ExcelParserService.ParseResult<DefaultValueDto> result =
        parserService.parse(file, DefaultValueDto.class, sheetSpec);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getName()).isEqualTo("updated");
    assertThat(result.rows().get(0).getActive()).isTrue();
  }

  @Test
  void parse_typeCoercion_localDate() throws IOException {
    Path file = createFileWithDateColumn();
    ExcelSheetSpec dateSheetSpec =
        ExcelSheetSpecResolver.resolve(DateDto.class);

    ExcelParserService.ParseResult<DateDto> result =
        parserService.parse(file, DateDto.class, dateSheetSpec);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getDate()).isEqualTo(LocalDate.of(2024, 1, 15));
  }

  @Test
  void parse_maxRows_stopsEarly() throws IOException {
    Path file = createAAppcarItemFile(20, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffSheetSpec, 5);

    // maxRows + 1 = 6행에서 중단되어야 함
    assertThat(result.rows()).hasSize(6);
  }

  @Test
  void parse_maxRows_noEffectWhenUnderLimit() throws IOException {
    Path file = createAAppcarItemFile(3, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffSheetSpec, 100);

    assertThat(result.rows()).hasSize(3);
  }

  @Test
  void parse_parseErrors_reportedForInvalidTypes() throws IOException {
    Path file = createFileWithInvalidTypeData();
    ExcelSheetSpec integerSheetSpec =
        ExcelSheetSpecResolver.resolve(IntegerDto.class);

    ExcelParserService.ParseResult<IntegerDto> result =
        parserService.parse(file, IntegerDto.class, integerSheetSpec);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getCount()).isNull();
    assertThat(result.parseErrors()).hasSize(1);
    assertThat(result.parseErrors().get(0).getCellErrors().get(0).message()).contains("Integer");
  }

  // ===== 헤더 검증 테스트 =====

  @Test
  void parse_fixedColumn_headerMismatch_requiredColumn_throwsException() throws Exception {
    Path file = createFileWithWrongHeaders();
    ExcelSheetSpec sheetSpec = ExcelSheetSpecResolver.resolve(SimpleDto.class);

    assertThatThrownBy(() -> parserService.parse(file, SimpleDto.class, sheetSpec))
        .isInstanceOf(ColumnResolutionBatchException.class)
        .satisfies(
            ex -> {
              var batch = (ColumnResolutionBatchException) ex;
              assertThat(batch.getExceptions()).isNotEmpty();
              assertThat(batch.toKoreanMessage()).contains("헤더가 일치하지 않습니다");
            });
  }

  @Test
  void parse_fixedColumn_headerMismatch_optionalColumn_skipsField() throws Exception {
    Path file = createFileWithOptionalMismatch();
    ExcelSheetSpec sheetSpec =
        ExcelSheetSpecResolver.resolve(OptionalFieldDto.class);

    ExcelParserService.ParseResult<OptionalFieldDto> result =
        parserService.parse(file, OptionalFieldDto.class, sheetSpec);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getName()).isEqualTo("TestName");
    assertThat(result.rows().get(0).getOptionalField()).isNull();
  }

  @Test
  void parse_multipleHeaderMismatches_allReportedInBatch() throws Exception {
    Path file = createFileWithAllWrongHeaders();
    ExcelSheetSpec sheetSpec =
        ExcelSheetSpecResolver.resolve(TwoRequiredDto.class);

    assertThatThrownBy(() -> parserService.parse(file, TwoRequiredDto.class, sheetSpec))
        .isInstanceOf(ColumnResolutionBatchException.class)
        .satisfies(
            ex -> {
              var batch = (ColumnResolutionBatchException) ex;
              assertThat(batch.getExceptions()).hasSize(2);
              assertThat(batch.toKoreanMessage()).contains("B열").contains("C열");
            });
  }

  // ===== 헬퍼 DTO =====

  @Data
  @ExcelSheet
  public static class SimpleDto {
    @ExcelColumn(label = "ExactHeader", column = "B", matchMode = HeaderMatchMode.EXACT)
    private String exactField;

    @ExcelColumn(label = "Contains", column = "C", matchMode = HeaderMatchMode.CONTAINS)
    private String containsField;

    @ExcelColumn(label = "Starts", column = "D", matchMode = HeaderMatchMode.STARTS_WITH)
    private String startsWithField;
  }

  @Data
  @ExcelSheet
  public static class BooleanDto {
    @ExcelColumn(label = "Active", column = "B")
    private Boolean active;
  }

  @Data
  @ExcelSheet
  public static class DefaultValueDto {
    @ExcelColumn(label = "Name", column = "B", required = false)
    private String name = "DEFAULT_NAME";

    @ExcelColumn(label = "Active", column = "C", required = false)
    private Boolean active = true;
  }

  @Data
  @ExcelSheet
  public static class DateDto {
    @ExcelColumn(label = "Date", column = "B")
    private LocalDate date;
  }

  @Data
  @ExcelSheet
  public static class IntegerDto {
    @ExcelColumn(label = "Count", column = "B")
    private Integer count;
  }

  @Data
  @ExcelSheet
  public static class OptionalFieldDto {
    @ExcelColumn(label = "Name", column = "B")
    private String name;

    @ExcelColumn(label = "Optional", column = "C", required = false)
    private String optionalField;
  }

  @Data
  @ExcelSheet
  public static class TwoRequiredDto {
    @ExcelColumn(label = "First", column = "B")
    private String first;

    @ExcelColumn(label = "Second", column = "C")
    private String second;
  }

  @Data
  @ExcelSheet
  public static class WhitespaceEqualsDto {
    @ExcelColumn(
        label = "순번",
        column = "B",
        matchMode = HeaderMatchMode.EXACT,
        ignoreHeaderWhitespace = true)
    private String value;
  }

  @Data
  @ExcelSheet
  public static class WhitespaceExactDto {
    @ExcelColumn(label = "순번", column = "B", matchMode = HeaderMatchMode.EXACT)
    private String value;
  }

  // ===== 파일 생성 헬퍼 =====

  private Path createAAppcarItemFile(int dataRows, boolean addFooter, boolean addBlankRow)
      throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      createTariffHeaderRows(sheet);

      // 데이터 행은 7행(0-based: 6행)부터 시작
      int currentRow = 6;
      for (int i = 0; i < dataRows; i++) {
        if (addBlankRow && i == 1) {
          // 빈 행 삽입
          sheet.createRow(currentRow);
          currentRow++;
        }
        Row row = sheet.createRow(currentRow);
        row.createCell(0).setCellValue(i + 1); // Column A
        row.createCell(1).setCellValue(i + 1); // B열 - goodsSeqNo
        row.createCell(2).setCellValue("TestItem" + (i + 1)); // C열 - goodsDes
        row.createCell(3).setCellValue("Spec" + (i + 1)); // D열 - spec
        row.createCell(4).setCellValue("Model" + (i + 1)); // E열 - modelDes
        row.createCell(5).setCellValue("8481.80-200" + i); // F열 - hsno
        row.createCell(7).setCellValue(8.0); // H열 - taxRate
        row.createCell(8).setCellValue(100.0); // I열 - unitprice
        row.createCell(9).setCellValue(10); // J열 - prodQty
        row.createCell(11).setCellValue(5); // L열 - repairQty
        row.createCell(13).setCellValue(50000.0); // N열 - importAmt
        row.createCell(14).setCellValue("통과"); // O열 - approvalYn
        row.createCell(16).setCellValue(100); // Q열 - importQty
        currentRow++;
      }

      if (addFooter) {
        Row footerRow = sheet.createRow(currentRow);
        footerRow.createCell(0).setCellValue("※ 주의사항");
      }

      Path file = tempDir.resolve("tariff_test.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path createFileWithMergedCells() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      createTariffHeaderRows(sheet);

      // F-G 병합이 있는 데이터 행은 7행(0-based: 6행)
      Row dataRow = sheet.createRow(6);
      dataRow.createCell(0).setCellValue(1);
      dataRow.createCell(1).setCellValue(1);
      dataRow.createCell(2).setCellValue("MergedItem");
      dataRow.createCell(3).setCellValue("Spec1");
      dataRow.createCell(4).setCellValue("Model1");
      dataRow.createCell(5).setCellValue("8481.80-2000"); // F-G 병합의 마스터 셀
      // G열은 병합 종속 셀이므로 쓰지 않음
      dataRow.createCell(7).setCellValue(5.0);
      dataRow.createCell(8).setCellValue(200.0);
      dataRow.createCell(9).setCellValue(20);
      dataRow.createCell(11).setCellValue(10);
      dataRow.createCell(13).setCellValue(100000.0);
      dataRow.createCell(14).setCellValue("통과");
      dataRow.createCell(16).setCellValue(50);

      // 데이터 행에 F-G 병합 영역 추가
      sheet.addMergedRegion(new CellRangeAddress(6, 6, 5, 6));

      Path file = tempDir.resolve("merged_test.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private void createTariffHeaderRows(Sheet sheet) {
    Row row4 = sheet.createRow(3);
    row4.createCell(0).setCellValue("No");
    row4.createCell(1).setCellValue("순번");
    row4.createCell(2).setCellValue("물품명");
    row4.createCell(3).setCellValue("규격1)");
    row4.createCell(4).setCellValue("모델명1)");
    row4.createCell(5).setCellValue("HSK No");
    row4.createCell(7).setCellValue("관세율");
    row4.createCell(8).setCellValue("단가($)");
    row4.createCell(9).setCellValue("소요량");
    row4.createCell(13).setCellValue("연간수입예상금액($)");
    row4.createCell(14).setCellValue("심의결과");
    row4.createCell(16).setCellValue("연간 예상소요량");

    Row row5 = sheet.createRow(4);
    row5.createCell(9).setCellValue("제조용");
    row5.createCell(11).setCellValue("수리용");

    sheet.createRow(5);

    sheet.addMergedRegion(new CellRangeAddress(3, 3, 9, 12));
    sheet.addMergedRegion(new CellRangeAddress(4, 4, 9, 10));
    sheet.addMergedRegion(new CellRangeAddress(4, 4, 11, 12));
    sheet.addMergedRegion(new CellRangeAddress(5, 5, 9, 10));
    sheet.addMergedRegion(new CellRangeAddress(5, 5, 11, 12));
  }

  private Path createFileWithAnnotatedHeaders() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("Deco");
      headerRow.createCell(1).setCellValue("ExactHeader");
      headerRow.createCell(2).setCellValue("SomeContainsText");
      headerRow.createCell(3).setCellValue("StartsWithSuffix");

      Row dataRow = sheet.createRow(1);
      dataRow.createCell(0).setCellValue("skip");
      dataRow.createCell(1).setCellValue("exactVal");
      dataRow.createCell(2).setCellValue("containsVal");
      dataRow.createCell(3).setCellValue("startsVal");

      Path file = tempDir.resolve("headers_test.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path createFileWithBooleanColumn() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("Deco");
      headerRow.createCell(1).setCellValue("Active");

      Row dataRow1 = sheet.createRow(1);
      dataRow1.createCell(0).setCellValue(1);
      dataRow1.createCell(1).setCellValue("Y");

      Row dataRow2 = sheet.createRow(2);
      dataRow2.createCell(0).setCellValue(2);
      dataRow2.createCell(1).setCellValue(""); // 빈 값 -> false

      Path file = tempDir.resolve("boolean_test.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path createFileWithDefaultedFields(String nameValue, String activeValue) throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("Deco");
      headerRow.createCell(1).setCellValue("Name");
      headerRow.createCell(2).setCellValue("Active");

      Row dataRow = sheet.createRow(1);
      dataRow.createCell(0).setCellValue("skip");
      if (nameValue != null) {
        dataRow.createCell(1).setCellValue(nameValue);
      }
      if (activeValue != null) {
        dataRow.createCell(2).setCellValue(activeValue);
      }

      Path file = tempDir.resolve("defaulted_fields_test.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path createFileWithWhitespaceSeparatedHeader() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("Deco");
      headerRow.createCell(1).setCellValue("순\n번");

      Row dataRow = sheet.createRow(1);
      dataRow.createCell(0).setCellValue("skip");
      dataRow.createCell(1).setCellValue("value");

      Path file = tempDir.resolve("whitespace_header_test.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path createFileWithDateColumn() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("Deco");
      headerRow.createCell(1).setCellValue("Date");

      Row dataRow = sheet.createRow(1);
      dataRow.createCell(0).setCellValue(1);
      dataRow.createCell(1).setCellValue("2024-01-15");

      Path file = tempDir.resolve("date_test.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path createFileWithInvalidTypeData() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("Deco");
      headerRow.createCell(1).setCellValue("Count");

      Row dataRow = sheet.createRow(1);
      dataRow.createCell(0).setCellValue(1);
      dataRow.createCell(1).setCellValue("not-a-number");

      Path file = tempDir.resolve("invalid_type_test.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path createFileWithWrongHeaders() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("Deco");
      headerRow.createCell(1).setCellValue("WRONG_HEADER"); // B열: expected "ExactHeader"
      headerRow.createCell(2).setCellValue("SomeContainsText");
      headerRow.createCell(3).setCellValue("StartsWithSuffix");

      Row dataRow = sheet.createRow(1);
      dataRow.createCell(1).setCellValue("val1");
      dataRow.createCell(2).setCellValue("val2");
      dataRow.createCell(3).setCellValue("val3");

      Path file = tempDir.resolve("wrong_headers_test.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path createFileWithOptionalMismatch() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("Deco");
      headerRow.createCell(1).setCellValue("Name");
      headerRow.createCell(2).setCellValue("WRONG"); // C열: expected "Optional"

      Row dataRow = sheet.createRow(1);
      dataRow.createCell(1).setCellValue("TestName");
      dataRow.createCell(2).setCellValue("ignored");

      Path file = tempDir.resolve("optional_mismatch_test.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path createFileWithAllWrongHeaders() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("Deco");
      headerRow.createCell(1).setCellValue("WRONG_B"); // "First" 기대
      headerRow.createCell(2).setCellValue("WRONG_C"); // "Second" 기대

      Row dataRow = sheet.createRow(1);
      dataRow.createCell(1).setCellValue("val1");
      dataRow.createCell(2).setCellValue("val2");

      Path file = tempDir.resolve("all_wrong_headers_test.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path copySampleFile(String relativePath) throws IOException {
    Path source = Path.of(relativePath);
    Path target = tempDir.resolve(source.getFileName().toString());
    Files.copy(source, target, StandardCopyOption.REPLACE_EXISTING);
    return target;
  }
}
