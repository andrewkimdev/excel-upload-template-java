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
