package com.foo.excel.service.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

class ExcelUploadFileServiceTest {

  private ExcelUploadFileService uploadFileService;

  @TempDir Path tempDir;

  @BeforeEach
  void setUp() {
    uploadFileService = new ExcelUploadFileService();
  }

  @Test
  void xlsxFile_passesThrough_unchanged() throws IOException {
    byte[] xlsxBytes = createXlsxBytes("TestValue");
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "test.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    Path result = uploadFileService.storeAndValidateXlsx(file, tempDir);

    assertThat(result.getFileName().toString()).isEqualTo("test.xlsx");
    try (Workbook wb = WorkbookFactory.create(result.toFile())) {
      Sheet sheet = wb.getSheetAt(0);
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("TestValue");
    }
  }

  @Test
  void xlsFile_rejected() throws IOException {
    byte[] xlsxBytes = createXlsxBytes("TestValue");
    MockMultipartFile file =
        new MockMultipartFile("file", "test.xls", "application/vnd.ms-excel", xlsxBytes);

    assertThatThrownBy(() -> uploadFileService.storeAndValidateXlsx(file, tempDir))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessageContaining(".xlsx");
  }

  @Test
  void unsupportedExtension_throws() {
    MockMultipartFile file =
        new MockMultipartFile("file", "test.csv", "text/csv", "a,b,c".getBytes());

    assertThatThrownBy(() -> uploadFileService.storeAndValidateXlsx(file, tempDir))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void nullFilename_throws() {
    MockMultipartFile file =
        new MockMultipartFile("file", null, "application/octet-stream", new byte[0]);

    assertThatThrownBy(() -> uploadFileService.storeAndValidateXlsx(file, tempDir))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void pathTraversalFilename_isSanitized_xlsxSavedWithCleanName() throws IOException {
    byte[] xlsxBytes = createXlsxBytes("TestValue");
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "../../../etc/passwd.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    Path result = uploadFileService.storeAndValidateXlsx(file, tempDir);

    assertThat(result.getParent()).isEqualTo(tempDir);
    assertThat(result.getFileName().toString()).doesNotContain("..");
    assertThat(result.getFileName().toString()).doesNotContain("/");
  }

  @Test
  void xlsxFile_withWrongMagicBytes_throwsSecurityException() {
    byte[] pdfBytes = {0x25, 0x50, 0x44, 0x46, 0x2D};
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "malicious.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            pdfBytes);

    assertThatThrownBy(() -> uploadFileService.storeAndValidateXlsx(file, tempDir))
        .isInstanceOf(SecurityException.class);
  }

  private byte[] createXlsxBytes(String value) throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");
      Row row = sheet.createRow(0);
      row.createCell(0).setCellValue(value);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      wb.write(bos);
      return bos.toByteArray();
    }
  }
}
