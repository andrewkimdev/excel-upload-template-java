package com.foo.excel.service.pipeline.report;

import static org.assertj.core.api.Assertions.assertThat;

import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.service.pipeline.parse.ExcelParserService;
import com.foo.excel.validation.CellError;
import com.foo.excel.validation.ExcelValidationResult;
import com.foo.excel.validation.RowError;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.BorderStyle;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

class ExcelErrorReportServiceTest {

  private ExcelErrorReportService errorReportService;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() throws IOException {
    ExcelImportProperties properties = new ExcelImportProperties();
    properties.setTempDirectory(tempDir.toString());
    // 오류 하위 디렉터리 생성
    Files.createDirectories(tempDir.resolve("errors"));
    errorReportService = new ExcelErrorReportService(properties);
  }

  @Test
  void errorReport_addsErrorColumn_withName_ERRORS() throws IOException {
    Path originalFile = createSimpleXlsx();
    ExcelValidationResult validationResult = createValidationResult();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "test-original.xlsx");

    try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
      Sheet sheet = wb.getSheetAt(0);
      Row headerRow = sheet.getRow(0); // 헤더 1행(0-based: 0)
      // _ERRORS 컬럼 찾기
      boolean found = false;
      for (Cell cell : headerRow) {
        if ("_ERRORS".equals(cell.getStringCellValue())) {
          found = true;
          break;
        }
      }
      assertThat(found).isTrue();
    }
  }

  @Test
  void errorReport_errorCells_haveRedBackground() throws IOException {
    Path originalFile = createSimpleXlsx();
    ExcelValidationResult validationResult = createValidationResult();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "test-original.xlsx");

    try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
      Sheet sheet = wb.getSheetAt(0);
      Row dataRow = sheet.getRow(1); // 2행(0-based: 1)의 1번 컬럼에 오류 존재
      Cell errorCell = dataRow.getCell(1);
      assertThat(errorCell).isNotNull();
      assertThat(errorCell.getCellStyle().getFillPattern())
          .isEqualTo(FillPatternType.SOLID_FOREGROUND);
      assertThat(errorCell.getCellStyle().getFillForegroundColor())
          .isEqualTo(IndexedColors.ROSE.getIndex());
    }
  }

  @Test
  void errorReport_formattedMessages_perRow() throws IOException {
    Path originalFile = createSimpleXlsx();
    ExcelValidationResult validationResult = createValidationResultMultipleErrors();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "test-original.xlsx");

    try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
      Sheet sheet = wb.getSheetAt(0);
      Row dataRow = sheet.getRow(1);
      // 오류 컬럼은 마지막 컬럼에 있어야 함
      int lastCol = sheet.getRow(0).getLastCellNum();
      Cell errMsgCell = dataRow.getCell(lastCol - 1);
      assertThat(errMsgCell).isNotNull();
      String msg = errMsgCell.getStringCellValue();
      assertThat(msg).contains("[B]");
      assertThat(msg).contains("[C]");
    }
  }

  @Test
  void errorReport_outputFile_isValidXlsx() throws IOException {
    Path originalFile = createSimpleXlsx();
    ExcelValidationResult validationResult = createValidationResult();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "test-original.xlsx");

    assertThat(errorFile).exists();
    assertThat(errorFile.toString()).endsWith(".xlsx");
    // 유효한 워크북으로 열 수 있는지 확인
    try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
      assertThat(wb.getNumberOfSheets()).isGreaterThan(0);
    }
  }

  @Test
  void errorReport_messageWithFormulaContent_isSafelyFormatted() throws IOException {
    Path originalFile = createSimpleXlsx();
    ExcelValidationResult validationResult = createValidationResultWithFormulaInjection();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "test-original.xlsx");

    try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
      Sheet sheet = wb.getSheetAt(0);
      Row dataRow = sheet.getRow(1);
      int lastCol = sheet.getRow(0).getLastCellNum();
      Cell errMsgCell = dataRow.getCell(lastCol - 1);

      assertThat(errMsgCell).isNotNull();
      String cellValue = errMsgCell.getStringCellValue();
      // 포맷된 메시지는 안전한 "[B]"로 시작
      assertThat(cellValue).startsWith("[");
      // 수식 내용이 제거되지 않고 유지되는지 확인
      assertThat(cellValue).contains("SUM");
    }
  }

  @Test
  void errorReport_errorMessagePreserved_inFormattedOutput() throws IOException {
    Path originalFile = createSimpleXlsx();
    ExcelValidationResult validationResult = createValidationResultWithAtSign();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "test-original.xlsx");

    try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
      Sheet sheet = wb.getSheetAt(0);
      Row dataRow = sheet.getRow(1);
      int lastCol = sheet.getRow(0).getLastCellNum();
      Cell errMsgCell = dataRow.getCell(lastCol - 1);

      assertThat(errMsgCell).isNotNull();
      String cellValue = errMsgCell.getStringCellValue();
      // 포맷된 메시지에 컬럼 문자 접두어가 포함되는지 확인
      assertThat(cellValue).startsWith("[B]");
    }
  }

  @Test
  void errorReport_normalErrorMessage_preservedCorrectly() throws IOException {
    Path originalFile = createSimpleXlsx();
    ExcelValidationResult validationResult = createValidationResult();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "test-original.xlsx");

    try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
      Sheet sheet = wb.getSheetAt(0);
      Row dataRow = sheet.getRow(1);
      int lastCol = sheet.getRow(0).getLastCellNum();
      Cell errMsgCell = dataRow.getCell(lastCol - 1);

      assertThat(errMsgCell).isNotNull();
      String cellValue = errMsgCell.getStringCellValue();
      // 일반 메시지는 컬럼 접두어와 함께 포맷되어야 함
      assertThat(cellValue).startsWith("[B]");
      assertThat(cellValue).contains("필수 입력 항목입니다");
    }
  }

  // ===== 서식 보존 신규 테스트 =====

  @Test
  void errorReport_preservesOriginalCellFormatting_withRoseFillAdded() throws IOException {
    Path originalFile = createStyledXlsx();
    ExcelValidationResult validationResult = createValidationResult();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "test-styled.xlsx");

    try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
      Sheet sheet = wb.getSheetAt(0);
      Row dataRow = sheet.getRow(1);
      Cell errorCell = dataRow.getCell(1); // B열은 오류가 있고 굵게+테두리 스타일 적용

      // 오류 셀에는 ROSE 채우기가 적용되어야 함
      assertThat(errorCell.getCellStyle().getFillForegroundColor())
          .isEqualTo(IndexedColors.ROSE.getIndex());
      assertThat(errorCell.getCellStyle().getFillPattern())
          .isEqualTo(FillPatternType.SOLID_FOREGROUND);

      // 원본 서식이 보존되어야 함
      assertThat(wb.getFontAt(errorCell.getCellStyle().getFontIndex()).getBold()).isTrue();
      assertThat(errorCell.getCellStyle().getBorderBottom()).isEqualTo(BorderStyle.THIN);
    }
  }

  @Test
  void errorReport_copiesAllSheets_fromMultiSheetSource() throws IOException {
    Path originalFile = createMultiSheetXlsx();
    ExcelValidationResult validationResult = createValidationResult();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "test-multi.xlsx");

    try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
      assertThat(wb.getNumberOfSheets()).isEqualTo(3);
      assertThat(wb.getSheetName(0)).isEqualTo("Data");
      assertThat(wb.getSheetName(1)).isEqualTo("Reference");
      assertThat(wb.getSheetName(2)).isEqualTo("Instructions");
    }
  }

  @Test
  void errorReport_preservesColumnWidths() throws IOException {
    Path originalFile = createStyledXlsx();
    ExcelValidationResult validationResult = createValidationResult();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "test-widths.xlsx");

    try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
      Sheet sheet = wb.getSheetAt(0);
      assertThat(sheet.getColumnWidth(0)).isEqualTo(5000);
    }
  }

  @Test
  void errorReport_disclaimerRow_existsWithCorrectText() throws IOException {
    Path originalFile = createSimpleXlsx();
    ExcelValidationResult validationResult = createValidationResult();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "test-disclaimer.xlsx");

    try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
      Sheet sheet = wb.getSheetAt(0);
      // 원본은 0-2행(lastRowNum=2), 안내문은 4행(2+2)
      Row disclaimerRow = sheet.getRow(4);
      assertThat(disclaimerRow).isNotNull();
      Cell disclaimerCell = disclaimerRow.getCell(0);
      assertThat(disclaimerCell.getStringCellValue()).startsWith("※");
      assertThat(disclaimerCell.getStringCellValue()).contains("오류 확인용");

      // 이탤릭 회색 스타일 확인
      Font font = wb.getFontAt(disclaimerCell.getCellStyle().getFontIndex());
      assertThat(font.getItalic()).isTrue();
      assertThat(font.getColor()).isEqualTo(IndexedColors.GREY_50_PERCENT.getIndex());
    }
  }

  @Test
  void errorReport_metaFileWritten_whenOriginalFilenameProvided() throws IOException {
    Path originalFile = createSimpleXlsx();
    ExcelValidationResult validationResult = createValidationResult();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "original-file.xlsx");

    String fileId = errorFile.getFileName().toString().replace(".xlsx", "");
    Path metaFile = tempDir.resolve("errors").resolve(fileId + ".meta");
    assertThat(metaFile).exists();
    assertThat(Files.readString(metaFile, StandardCharsets.UTF_8)).isEqualTo("original-file.xlsx");
  }

  @Test
  void errorReport_noMetaFile_whenOriginalFilenameIsNull() throws IOException {
    Path originalFile = createSimpleXlsx();
    ExcelValidationResult validationResult = createValidationResult();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, null);

    String fileId = errorFile.getFileName().toString().replace(".xlsx", "");
    Path metaFile = tempDir.resolve("errors").resolve(fileId + ".meta");
    assertThat(metaFile).doesNotExist();
  }

  @Test
  void errorReport_outputIsValidXlsx_afterSxssfWrite() throws IOException {
    Path originalFile = createSimpleXlsx();
    ExcelValidationResult validationResult = createValidationResult();
    TestConfig config = new TestConfig();
    List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

    Path errorFile =
        errorReportService.generateErrorReport(
            originalFile, validationResult, mappings, config, "test.xlsx");

    // 출력이 유효한 XLSX인지 확인(WorkbookFactory는 .xlsx에서 XSSFWorkbook 반환)
    try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
      assertThat(wb).isInstanceOf(XSSFWorkbook.class);
      assertThat(wb.getNumberOfSheets()).isGreaterThan(0);
      assertThat((Object) wb.getSheetAt(0).getRow(0)).isNotNull();
    }
  }

  @Test
  void errorReport_repeatedGeneration_doesNotLeakSxssfTempFiles() throws IOException {
    Path poiTempDir = Path.of(System.getProperty("java.io.tmpdir"), "poifiles");
    Files.createDirectories(poiTempDir);
    long beforeCount = countSxssfTempFiles(poiTempDir);

    for (int i = 0; i < 10; i++) {
      Path originalFile = createSimpleXlsx();
      ExcelValidationResult validationResult = createValidationResult();
      TestConfig config = new TestConfig();
      List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

      Path errorFile =
          errorReportService.generateErrorReport(
              originalFile, validationResult, mappings, config, "cleanup-check.xlsx");

      String fileId = errorFile.getFileName().toString().replace(".xlsx", "");
      Files.deleteIfExists(errorFile);
      Files.deleteIfExists(tempDir.resolve("errors").resolve(fileId + ".meta"));
    }

    long afterCount = countSxssfTempFiles(poiTempDir);
    assertThat(afterCount).isEqualTo(beforeCount);
  }

  // ===== 헬퍼 =====

  static class TestConfig implements ExcelImportConfig {
    @Override
    public int getHeaderRow() {
      return 1;
    }

    @Override
    public int getDataStartRow() {
      return 2;
    }

  }

  private Path createSimpleXlsx() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");
      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("No");
      headerRow.createCell(1).setCellValue("Name");
      headerRow.createCell(2).setCellValue("Value");

      Row dataRow = sheet.createRow(1);
      dataRow.createCell(0).setCellValue(1);
      dataRow.createCell(1).setCellValue("Test");
      dataRow.createCell(2).setCellValue("abc");

      Row dataRow2 = sheet.createRow(2);
      dataRow2.createCell(0).setCellValue(2);
      dataRow2.createCell(1).setCellValue("Test2");
      dataRow2.createCell(2).setCellValue("def");

      Path file = tempDir.resolve("original.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path createStyledXlsx() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      CellStyle boldBorderStyle = wb.createCellStyle();
      Font boldFont = wb.createFont();
      boldFont.setBold(true);
      boldBorderStyle.setFont(boldFont);
      boldBorderStyle.setBorderBottom(BorderStyle.THIN);
      boldBorderStyle.setFillForegroundColor(IndexedColors.LIGHT_YELLOW.getIndex());
      boldBorderStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

      Sheet sheet = wb.createSheet("Sheet1");
      sheet.setColumnWidth(0, 5000);

      Row headerRow = sheet.createRow(0);
      headerRow.createCell(0).setCellValue("No");
      headerRow.createCell(1).setCellValue("Name");
      headerRow.createCell(2).setCellValue("Value");

      Row dataRow = sheet.createRow(1);
      dataRow.createCell(0).setCellValue(1);
      Cell styledCell = dataRow.createCell(1);
      styledCell.setCellValue("StyledValue");
      styledCell.setCellStyle(boldBorderStyle);
      dataRow.createCell(2).setCellValue("abc");

      Row dataRow2 = sheet.createRow(2);
      dataRow2.createCell(0).setCellValue(2);
      dataRow2.createCell(1).setCellValue("Test2");
      dataRow2.createCell(2).setCellValue("def");

      Path file = tempDir.resolve("styled.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path createMultiSheetXlsx() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      // 데이터 시트(index 0)
      Sheet dataSheet = wb.createSheet("Data");
      Row headerRow = dataSheet.createRow(0);
      headerRow.createCell(0).setCellValue("No");
      headerRow.createCell(1).setCellValue("Name");
      headerRow.createCell(2).setCellValue("Value");
      Row dataRow = dataSheet.createRow(1);
      dataRow.createCell(0).setCellValue(1);
      dataRow.createCell(1).setCellValue("Test");
      dataRow.createCell(2).setCellValue("abc");

      // 참조 시트(index 1)
      Sheet refSheet = wb.createSheet("Reference");
      refSheet.createRow(0).createCell(0).setCellValue("Ref Data");

      // 안내 시트(index 2)
      Sheet instrSheet = wb.createSheet("Instructions");
      instrSheet.createRow(0).createCell(0).setCellValue("Instructions");

      Path file = tempDir.resolve("multi-sheet.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private ExcelValidationResult createValidationResult() {
    CellError cellError =
        CellError.builder()
            .columnIndex(1)
            .columnLetter("B")
            .fieldName("name")
            .headerName("Name")
            .rejectedValue("")
            .message("필수 입력 항목입니다")
            .build();

    RowError rowError =
        RowError.builder().rowNumber(2).cellErrors(new ArrayList<>(List.of(cellError))).build();

    return ExcelValidationResult.failure(2, List.of(rowError));
  }

  private ExcelValidationResult createValidationResultMultipleErrors() {
    CellError cellError1 =
        CellError.builder()
            .columnIndex(1)
            .columnLetter("B")
            .fieldName("name")
            .headerName("Name")
            .rejectedValue("")
            .message("필수 입력 항목입니다")
            .build();

    CellError cellError2 =
        CellError.builder()
            .columnIndex(2)
            .columnLetter("C")
            .fieldName("value")
            .headerName("Value")
            .rejectedValue("abc")
            .message("형식이 올바르지 않습니다")
            .build();

    RowError rowError =
        RowError.builder()
            .rowNumber(2)
            .cellErrors(new ArrayList<>(List.of(cellError1, cellError2)))
            .build();

    return ExcelValidationResult.failure(2, List.of(rowError));
  }

  private ExcelValidationResult createValidationResultWithFormulaInjection() {
    CellError cellError =
        CellError.builder()
            .columnIndex(1)
            .columnLetter("B")
            .fieldName("name")
            .headerName("Name")
            .rejectedValue("=cmd|'/C calc'!A0")
            .message("=SUM(A1:A10)")
            .build();

    RowError rowError =
        RowError.builder().rowNumber(2).cellErrors(new ArrayList<>(List.of(cellError))).build();

    return ExcelValidationResult.failure(2, List.of(rowError));
  }

  private ExcelValidationResult createValidationResultWithAtSign() {
    CellError cellError =
        CellError.builder()
            .columnIndex(1)
            .columnLetter("B")
            .fieldName("name")
            .headerName("Name")
            .rejectedValue("@attack")
            .message("@SUM(A1:A10)")
            .build();

    RowError rowError =
        RowError.builder().rowNumber(2).cellErrors(new ArrayList<>(List.of(cellError))).build();

    return ExcelValidationResult.failure(2, List.of(rowError));
  }

  private long countSxssfTempFiles(Path poiTempDir) throws IOException {
    try (Stream<Path> paths = Files.walk(poiTempDir)) {
      return paths
          .filter(Files::isRegularFile)
          .map(path -> path.getFileName().toString())
          .filter(name -> name.contains("poi-sxssf-sheet"))
          .count();
    }
  }
}
