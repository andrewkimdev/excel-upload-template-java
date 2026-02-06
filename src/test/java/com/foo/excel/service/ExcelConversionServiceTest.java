package com.foo.excel.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class ExcelConversionServiceTest {

    private ExcelConversionService conversionService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        conversionService = new ExcelConversionService();
    }

    @Test
    void xlsxFile_passesThrough_unchanged() throws IOException {
        byte[] xlsxBytes = createXlsxBytes("TestValue");
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        Path result = conversionService.ensureXlsxFormat(file, tempDir);

        assertThat(result.getFileName().toString()).isEqualTo("test.xlsx");
        try (Workbook wb = WorkbookFactory.create(result.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("TestValue");
        }
    }

    @Test
    void xlsFile_convertsToXlsx_preservingCellValues() throws IOException {
        byte[] xlsBytes = createXlsBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xls",
                "application/vnd.ms-excel",
                xlsBytes);

        Path result = conversionService.ensureXlsxFormat(file, tempDir);

        assertThat(result.getFileName().toString()).isEqualTo("test.xlsx");
        try (Workbook wb = WorkbookFactory.create(result.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getRow(0).getCell(0).getStringCellValue()).isEqualTo("StringVal");
            assertThat(sheet.getRow(0).getCell(1).getNumericCellValue()).isEqualTo(42.5);
            assertThat(sheet.getRow(0).getCell(2).getBooleanCellValue()).isTrue();
        }
    }

    @Test
    void xlsConversion_preservesMergedRegions() throws IOException {
        byte[] xlsBytes;
        try (HSSFWorkbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("Merged");
            sheet.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            xlsBytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "merged.xls", "application/vnd.ms-excel", xlsBytes);

        Path result = conversionService.ensureXlsxFormat(file, tempDir);

        try (Workbook wb = WorkbookFactory.create(result.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getNumMergedRegions()).isEqualTo(1);
            CellRangeAddress region = sheet.getMergedRegion(0);
            assertThat(region.getFirstColumn()).isEqualTo(0);
            assertThat(region.getLastColumn()).isEqualTo(2);
        }
    }

    @Test
    void unsupportedExtension_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.csv", "text/csv", "a,b,c".getBytes());

        assertThatThrownBy(() -> conversionService.ensureXlsxFormat(file, tempDir))
                .isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void nullFilename_throws() {
        MockMultipartFile file = new MockMultipartFile(
                "file", null, "application/octet-stream", new byte[0]);

        assertThatThrownBy(() -> conversionService.ensureXlsxFormat(file, tempDir))
                .isInstanceOf(IllegalArgumentException.class);
    }

    // ===== Security tests =====

    @Test
    void pathTraversalFilename_isSanitized_xlsxSavedWithCleanName() throws IOException {
        byte[] xlsxBytes = createXlsxBytes("TestValue");
        MockMultipartFile file = new MockMultipartFile(
                "file", "../../../etc/passwd.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        Path result = conversionService.ensureXlsxFormat(file, tempDir);

        // The path should be within tempDir, not escaping it
        assertThat(result.getParent()).isEqualTo(tempDir);
        assertThat(result.getFileName().toString()).doesNotContain("..");
        assertThat(result.getFileName().toString()).doesNotContain("/");
    }

    @Test
    void windowsPathTraversalFilename_isSanitized() throws IOException {
        byte[] xlsxBytes = createXlsxBytes("TestValue");
        MockMultipartFile file = new MockMultipartFile(
                "file", "..\\..\\Windows\\System32\\data.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        Path result = conversionService.ensureXlsxFormat(file, tempDir);

        assertThat(result.getParent()).isEqualTo(tempDir);
        assertThat(result.getFileName().toString()).doesNotContain("..");
        assertThat(result.getFileName().toString()).doesNotContain("\\");
    }

    @Test
    void xlsxFile_withWrongMagicBytes_throwsSecurityException() {
        // File claims to be XLSX but contains PDF magic bytes
        byte[] pdfBytes = {0x25, 0x50, 0x44, 0x46, 0x2D};  // %PDF-
        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                pdfBytes);

        assertThatThrownBy(() -> conversionService.ensureXlsxFormat(file, tempDir))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void xlsFile_withWrongMagicBytes_throwsSecurityException() {
        // File claims to be XLS but contains text
        byte[] textBytes = "This is not an Excel file".getBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "malicious.xls",
                "application/vnd.ms-excel",
                textBytes);

        assertThatThrownBy(() -> conversionService.ensureXlsxFormat(file, tempDir))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void xlsFile_withXlsxContent_throwsSecurityException() {
        // File has .xls extension but contains XLSX magic bytes (PK/ZIP)
        byte[] xlsxMagic = {0x50, 0x4B, 0x03, 0x04, 0x00, 0x00, 0x00, 0x00};
        MockMultipartFile file = new MockMultipartFile(
                "file", "wrong_format.xls",
                "application/vnd.ms-excel",
                xlsxMagic);

        assertThatThrownBy(() -> conversionService.ensureXlsxFormat(file, tempDir))
                .isInstanceOf(SecurityException.class);
    }

    @Test
    void filenameWithNullByte_isSanitized() throws IOException {
        byte[] xlsxBytes = createXlsxBytes("TestValue");
        MockMultipartFile file = new MockMultipartFile(
                "file", "test\u0000.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        Path result = conversionService.ensureXlsxFormat(file, tempDir);

        assertThat(result.getFileName().toString()).doesNotContain("\u0000");
    }

    @Test
    void absolutePathFilename_extractsOnlyFilename() throws IOException {
        byte[] xlsxBytes = createXlsxBytes("TestValue");
        MockMultipartFile file = new MockMultipartFile(
                "file", "/var/tmp/uploads/secret.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        Path result = conversionService.ensureXlsxFormat(file, tempDir);

        assertThat(result.getFileName().toString()).isEqualTo("secret.xlsx");
        assertThat(result.getParent()).isEqualTo(tempDir);
    }

    // ===== Style preservation tests =====

    @Test
    void xlsConversion_preservesCellStyles_boldFontAndBorders() throws IOException {
        byte[] xlsBytes = createStyledXlsBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "styled.xls", "application/vnd.ms-excel", xlsBytes);

        Path result = conversionService.ensureXlsxFormat(file, tempDir);

        try (Workbook wb = WorkbookFactory.create(result.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            Cell cell = sheet.getRow(0).getCell(0);
            assertThat(wb.getFontAt(cell.getCellStyle().getFontIndex()).getBold()).isTrue();
            assertThat(cell.getCellStyle().getBorderBottom()).isEqualTo(BorderStyle.THIN);
        }
    }

    @Test
    void xlsConversion_preservesColumnWidths() throws IOException {
        byte[] xlsBytes = createStyledXlsBytes();
        MockMultipartFile file = new MockMultipartFile(
                "file", "widths.xls", "application/vnd.ms-excel", xlsBytes);

        Path result = conversionService.ensureXlsxFormat(file, tempDir);

        try (Workbook wb = WorkbookFactory.create(result.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            assertThat(sheet.getColumnWidth(0)).isEqualTo(5000);
        }
    }

    @Test
    void xlsConversion_preservesRowHeights() throws IOException {
        byte[] xlsBytes;
        try (HSSFWorkbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row row = sheet.createRow(0);
            row.setHeight((short) 600);
            row.createCell(0).setCellValue("Tall");
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            xlsBytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "heights.xls", "application/vnd.ms-excel", xlsBytes);

        Path result = conversionService.ensureXlsxFormat(file, tempDir);

        try (Workbook wb = WorkbookFactory.create(result.toFile())) {
            assertThat(wb.getSheetAt(0).getRow(0).getHeight()).isEqualTo((short) 600);
        }
    }

    @Test
    void xlsConversion_dateFormattedCells_surviveConversion() throws IOException {
        byte[] xlsBytes;
        try (HSSFWorkbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            CellStyle dateStyle = wb.createCellStyle();
            dateStyle.setDataFormat(wb.getCreationHelper().createDataFormat().getFormat("yyyy-MM-dd"));
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue(new java.util.Date());
            cell.setCellStyle(dateStyle);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            xlsBytes = bos.toByteArray();
        }

        MockMultipartFile file = new MockMultipartFile(
                "file", "dates.xls", "application/vnd.ms-excel", xlsBytes);

        Path result = conversionService.ensureXlsxFormat(file, tempDir);

        try (Workbook wb = WorkbookFactory.create(result.toFile())) {
            Cell cell = wb.getSheetAt(0).getRow(0).getCell(0);
            assertThat(DateUtil.isCellDateFormatted(cell)).isTrue();
        }
    }

    // ===== Helper methods =====

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

    private byte[] createStyledXlsBytes() throws IOException {
        try (HSSFWorkbook wb = new HSSFWorkbook()) {
            CellStyle boldStyle = wb.createCellStyle();
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            boldStyle.setFont(boldFont);
            boldStyle.setBorderBottom(BorderStyle.THIN);

            Sheet sheet = wb.createSheet("Sheet1");
            sheet.setColumnWidth(0, 5000);
            Row row = sheet.createRow(0);
            Cell cell = row.createCell(0);
            cell.setCellValue("Bold");
            cell.setCellStyle(boldStyle);

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private byte[] createXlsBytes() throws IOException {
        try (HSSFWorkbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            Row row = sheet.createRow(0);
            row.createCell(0).setCellValue("StringVal");
            row.createCell(1).setCellValue(42.5);
            row.createCell(2).setCellValue(true);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }
}
