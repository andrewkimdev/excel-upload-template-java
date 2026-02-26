package com.foo.excel.service.pipeline.parse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.within;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.HeaderMatchMode;
import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.templates.samples.aappcar.config.AAppcarItemImportConfig;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
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
  private ExcelImportConfig tariffConfig;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    parserService = new ExcelParserService();
    tariffConfig = new AAppcarItemImportConfig();
  }

  @Test
  void parse_correctNumberOfRows() throws IOException {
    Path file = createAAppcarItemFile(3, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffConfig);

    assertThat(result.rows()).hasSize(3);
    assertThat(result.sourceRowNumbers()).hasSize(3);
  }

  @Test
  void parse_footerMarker_stopsReading() throws IOException {
    Path file = createAAppcarItemFile(3, true, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffConfig);

    // 데이터 3행 뒤 푸터가 오므로 3행만 읽음
    assertThat(result.rows()).hasSize(3);
  }

  @Test
  void parse_blankRows_skipped() throws IOException {
    Path file = createAAppcarItemFile(3, false, true);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffConfig);

    // 데이터 3행 + 빈 행 1개 삽입 = 3행만 파싱
    assertThat(result.rows()).hasSize(3);
  }

  @Test
  void parse_mergedCellResolution() throws IOException {
    // 병합 셀 F-G(HSK No 컬럼)가 있는 파일 생성
    Path file = createFileWithMergedCells();

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffConfig);

    assertThat(result.rows()).isNotEmpty();
    // 병합 셀 값은 F열에서 읽혀야 함
    assertThat(result.rows().get(0).getHsno()).isEqualTo("8481.80-2000");
  }

  @Test
  void parse_typeCoercion_string_trimmed() throws IOException {
    Path file = createAAppcarItemFile(1, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffConfig);

    assertThat(result.rows().get(0).getGoodsDes()).isEqualTo("TestItem1");
  }

  @Test
  void parse_typeCoercion_integer() throws IOException {
    Path file = createAAppcarItemFile(1, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffConfig);

    assertThat(result.rows().get(0).getGoodsSeqNo()).isEqualTo(1);
  }

  @Test
  void parse_typeCoercion_bigDecimal() throws IOException {
    Path file = createAAppcarItemFile(1, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffConfig);

    assertThat(result.rows().get(0).getTaxRate()).isNotNull();
    assertThat(result.rows().get(0).getTaxRate().doubleValue()).isCloseTo(8.0, within(0.01));
  }

  @Test
  void parse_headerMatchMode_exact_and_contains_and_startsWith() throws IOException {
    Path file = createFileWithAnnotatedHeaders();
    ExcelImportConfig simpleConfig = new SimpleConfig();

    ExcelParserService.ParseResult<SimpleDto> result =
        parserService.parse(file, SimpleDto.class, simpleConfig);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getExactField()).isEqualTo("exactVal");
    assertThat(result.rows().get(0).getContainsField()).isEqualTo("containsVal");
    assertThat(result.rows().get(0).getStartsWithField()).isEqualTo("startsVal");
  }

  @Test
  void parse_columnA_skipped() throws IOException {
    Path file = createAAppcarItemFile(1, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffConfig);

    // A열은 장식용이라 파서가 건너뛰며 어떤 필드도 매핑되지 않음
    // 컬럼 매핑에 A열(index 0)이 없는지 확인
    assertThat(result.columnMappings()).noneMatch(m -> m.resolvedColumnIndex() == 0);
  }

  @Test
  void parse_typeCoercion_boolean_Y_true_blank_false() throws IOException {
    Path file = createFileWithBooleanColumn();
    ExcelImportConfig boolConfig = new SimpleConfig();

    ExcelParserService.ParseResult<BooleanDto> result =
        parserService.parse(file, BooleanDto.class, boolConfig);

    assertThat(result.rows()).hasSize(2);
    assertThat(result.rows().get(0).getActive()).isTrue();
    assertThat(result.rows().get(1).getActive()).isFalse();
  }

  @Test
  void parse_typeCoercion_localDate() throws IOException {
    Path file = createFileWithDateColumn();
    ExcelImportConfig dateConfig = new SimpleConfig();

    ExcelParserService.ParseResult<DateDto> result =
        parserService.parse(file, DateDto.class, dateConfig);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getDate()).isEqualTo(LocalDate.of(2024, 1, 15));
  }

  @Test
  void parse_maxRows_stopsEarly() throws IOException {
    Path file = createAAppcarItemFile(20, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffConfig, 5);

    // maxRows + 1 = 6행에서 중단되어야 함
    assertThat(result.rows()).hasSize(6);
  }

  @Test
  void parse_maxRows_noEffectWhenUnderLimit() throws IOException {
    Path file = createAAppcarItemFile(3, false, false);

    ExcelParserService.ParseResult<AAppcarItemDto> result =
        parserService.parse(file, AAppcarItemDto.class, tariffConfig, 100);

    assertThat(result.rows()).hasSize(3);
  }

  @Test
  void parse_parseErrors_reportedForInvalidTypes() throws IOException {
    Path file = createFileWithInvalidTypeData();
    ExcelImportConfig simpleConfig = new SimpleConfig();

    ExcelParserService.ParseResult<IntegerDto> result =
        parserService.parse(file, IntegerDto.class, simpleConfig);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getCount()).isNull();
    assertThat(result.parseErrors()).hasSize(1);
    assertThat(result.parseErrors().get(0).getCellErrors().get(0).message()).contains("Integer");
  }

  // ===== 헤더 검증 테스트 =====

  @Test
  void parse_fixedColumn_headerMismatch_requiredColumn_throwsException() throws Exception {
    Path file = createFileWithWrongHeaders();
    ExcelImportConfig config = new SimpleConfig();

    assertThatThrownBy(() -> parserService.parse(file, SimpleDto.class, config))
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
    ExcelImportConfig config = new SimpleConfig();

    ExcelParserService.ParseResult<OptionalFieldDto> result =
        parserService.parse(file, OptionalFieldDto.class, config);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getName()).isEqualTo("TestName");
    assertThat(result.rows().get(0).getOptionalField()).isNull();
  }

  @Test
  void parse_multipleHeaderMismatches_allReportedInBatch() throws Exception {
    Path file = createFileWithAllWrongHeaders();
    ExcelImportConfig config = new SimpleConfig();

    assertThatThrownBy(() -> parserService.parse(file, TwoRequiredDto.class, config))
        .isInstanceOf(ColumnResolutionBatchException.class)
        .satisfies(
            ex -> {
              var batch = (ColumnResolutionBatchException) ex;
              assertThat(batch.getExceptions()).hasSize(2);
              assertThat(batch.toKoreanMessage()).contains("컬럼 B").contains("컬럼 C");
            });
  }

  @Test
  void parse_autoDetect_findsColumn() throws Exception {
    Path file = createFileForAutoDetect();
    ExcelImportConfig config = new SimpleConfig();

    ExcelParserService.ParseResult<AutoDetectDto> result =
        parserService.parse(file, AutoDetectDto.class, config);

    assertThat(result.rows()).hasSize(1);
    assertThat(result.rows().get(0).getValue()).isEqualTo("found");
  }

  @Test
  void parse_autoDetect_notFound_requiredColumn_throwsException() throws Exception {
    Path file = createFileForAutoDetect();
    ExcelImportConfig config = new SimpleConfig();

    assertThatThrownBy(() -> parserService.parse(file, AutoDetectMissingDto.class, config))
        .isInstanceOf(ColumnResolutionBatchException.class)
        .satisfies(
            ex -> {
              var batch = (ColumnResolutionBatchException) ex;
              assertThat(batch.toKoreanMessage()).contains("컬럼을 찾을 수 없습니다");
            });
  }

  // ===== 헬퍼 DTO =====

  @Data
  public static class SimpleDto {
    @ExcelColumn(header = "ExactHeader", column = "B", matchMode = HeaderMatchMode.EXACT)
    private String exactField;

    @ExcelColumn(header = "Contains", column = "C", matchMode = HeaderMatchMode.CONTAINS)
    private String containsField;

    @ExcelColumn(header = "Starts", column = "D", matchMode = HeaderMatchMode.STARTS_WITH)
    private String startsWithField;
  }

  @Data
  public static class BooleanDto {
    @ExcelColumn(header = "Active", column = "B")
    private Boolean active;
  }

  @Data
  public static class DateDto {
    @ExcelColumn(header = "Date", column = "B")
    private LocalDate date;
  }

  @Data
  public static class IntegerDto {
    @ExcelColumn(header = "Count", column = "B")
    private Integer count;
  }

  @Data
  public static class OptionalFieldDto {
    @ExcelColumn(header = "Name", column = "B")
    private String name;

    @ExcelColumn(header = "Optional", column = "C", required = false)
    private String optionalField;
  }

  @Data
  public static class TwoRequiredDto {
    @ExcelColumn(header = "First", column = "B")
    private String first;

    @ExcelColumn(header = "Second", column = "C")
    private String second;
  }

  @Data
  public static class AutoDetectDto {
    @ExcelColumn(header = "Target")
    private String value;
  }

  @Data
  public static class AutoDetectMissingDto {
    @ExcelColumn(header = "NonExistentHeader")
    private String value;
  }

  static class SimpleConfig implements ExcelImportConfig {
    @Override
    public int getHeaderRow() {
      return 1;
    }

    @Override
    public int getDataStartRow() {
      return 2;
    }

  }

  // ===== 파일 생성 헬퍼 =====

  private Path createAAppcarItemFile(int dataRows, boolean addFooter, boolean addBlankRow)
      throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      // 헤더 행은 4행(0-based: 3행)
      Row headerRow = sheet.createRow(3);
      headerRow.createCell(0).setCellValue("No"); // A열 - 장식용
      headerRow.createCell(1).setCellValue("순번"); // B열
      headerRow.createCell(2).setCellValue("물품명"); // C열
      headerRow.createCell(3).setCellValue("규격1)"); // D열
      headerRow.createCell(4).setCellValue("모델명1)"); // E열
      headerRow.createCell(5).setCellValue("HSK No"); // F열
      headerRow.createCell(6).setCellValue(""); // G열 - 병합 종속 셀
      headerRow.createCell(7).setCellValue("관세율"); // H열
      headerRow.createCell(8).setCellValue("단가($)"); // I열
      headerRow.createCell(9).setCellValue("제조용"); // J열
      headerRow.createCell(10).setCellValue(""); // K열 - 병합 종속 셀
      headerRow.createCell(11).setCellValue("수리용"); // L열
      headerRow.createCell(12).setCellValue(""); // M열 - 병합 종속 셀
      headerRow.createCell(13).setCellValue("연간수입예상금액($)"); // N열
      headerRow.createCell(14).setCellValue("심의결과"); // O열
      headerRow.createCell(15).setCellValue(""); // P열 - 병합 종속 셀
      headerRow.createCell(16).setCellValue("연간 예상소요량"); // Q열

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

      // 헤더 행은 4행(0-based: 3행)
      Row headerRow = sheet.createRow(3);
      headerRow.createCell(0).setCellValue("No");
      headerRow.createCell(1).setCellValue("순번");
      headerRow.createCell(2).setCellValue("물품명");
      headerRow.createCell(3).setCellValue("규격1)");
      headerRow.createCell(4).setCellValue("모델명1)");
      headerRow.createCell(5).setCellValue("HSK No");
      headerRow.createCell(7).setCellValue("관세율");
      headerRow.createCell(8).setCellValue("단가($)");
      headerRow.createCell(9).setCellValue("제조용");
      headerRow.createCell(11).setCellValue("수리용");
      headerRow.createCell(13).setCellValue("연간수입예상금액($)");
      headerRow.createCell(14).setCellValue("심의결과");
      headerRow.createCell(16).setCellValue("연간 예상소요량");

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

  private Path createFileForAutoDetect() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");

      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("Irrelevant");
      headerRow.createCell(1).setCellValue("Also Irrelevant");
      headerRow.createCell(2).setCellValue("Target");

      Row dataRow = sheet.createRow(1);
      dataRow.createCell(0).setCellValue("skip");
      dataRow.createCell(1).setCellValue("skip");
      dataRow.createCell(2).setCellValue("found");

      Path file = tempDir.resolve("auto_detect_test.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }
}
