package com.foo.excel.service.file;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foo.excel.config.ExcelImportProperties;
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
  void setUp() throws IOException {
    ExcelImportProperties properties = new ExcelImportProperties();
    properties.setTempDirectory(tempDir.toString());
    properties.init();
    uploadFileService = new ExcelUploadFileService(properties);
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

    ExcelUploadFileService.StoredUpload result =
        uploadFileService.storeAndValidateXlsx(file, null);

    assertThat(result.sanitizedFilename()).isEqualTo("test.xlsx");
    assertThat(result.path().getParent()).isEqualTo(tempDir);
    assertThat(result.path().getFileName().toString()).isEqualTo(result.sanitizedFilename());
    try (Workbook wb = WorkbookFactory.create(result.path().toFile())) {
      Sheet sheet = wb.getSheetAt(0);
      assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("TestValue");
    }
  }

  @Test
  void xlsFile_rejected() throws IOException {
    byte[] xlsxBytes = createXlsxBytes("TestValue");
    MockMultipartFile file =
        new MockMultipartFile("file", "test.xls", "application/vnd.ms-excel", xlsxBytes);

    assertThatThrownBy(() -> uploadFileService.storeAndValidateXlsx(file, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("지원하지 않는 파일 형식입니다. .xlsx 파일만 업로드 가능합니다.");
  }

  @Test
  void unsupportedExtension_throws() {
    MockMultipartFile file =
        new MockMultipartFile("file", "test.csv", "text/csv", "a,b,c".getBytes());

    assertThatThrownBy(() -> uploadFileService.storeAndValidateXlsx(file, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("유효하지 않은 파일 확장자입니다.");
  }

  @Test
  void nullFilename_throws() {
    MockMultipartFile file =
        new MockMultipartFile("file", null, "application/octet-stream", new byte[0]);

    assertThatThrownBy(() -> uploadFileService.storeAndValidateXlsx(file, null))
        .isInstanceOf(IllegalArgumentException.class)
        .hasMessage("파일명이 없습니다");
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

    ExcelUploadFileService.StoredUpload result =
        uploadFileService.storeAndValidateXlsx(file, "CUSTOM01");

    assertThat(result.path().getParent()).isEqualTo(tempDir.resolve("CUSTOM01"));
    assertThat(result.path().getFileName().toString()).isEqualTo(result.sanitizedFilename());
    assertThat(result.path().getFileName().toString()).doesNotContain("..");
    assertThat(result.path().getFileName().toString()).doesNotContain("/");
  }

  @Test
  void tempSubdirectory_whenPresent_storesFileUnderResolvedSubdirectory() throws IOException {
    byte[] xlsxBytes = createXlsxBytes("TestValue");
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "test.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    ExcelUploadFileService.StoredUpload result =
        uploadFileService.storeAndValidateXlsx(file, "ARCHIVE01");

    assertThat(result.path()).isEqualTo(tempDir.resolve("ARCHIVE01").resolve("test.xlsx"));
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

    assertThatThrownBy(() -> uploadFileService.storeAndValidateXlsx(file, null))
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
