package com.foo.excel.service;

import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.validation.CellError;
import com.foo.excel.validation.ExcelValidationResult;
import com.foo.excel.validation.RowError;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.*;

class ExcelErrorReportServiceTest {

    private ExcelErrorReportService errorReportService;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        ExcelImportProperties properties = new ExcelImportProperties();
        properties.setTempDirectory(tempDir.toString());
        // Create the errors subdirectory
        Files.createDirectories(tempDir.resolve("errors"));
        errorReportService = new ExcelErrorReportService(properties);
    }

    @Test
    void errorReport_addsErrorColumn_withName_ERRORS() throws IOException {
        Path originalFile = createSimpleXlsx();
        ExcelValidationResult validationResult = createValidationResult();
        TestConfig config = new TestConfig();
        List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

        Path errorFile = errorReportService.generateErrorReport(
                originalFile, validationResult, mappings, config);

        try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            Row headerRow = sheet.getRow(0);  // header row 1 (0-based: 0)
            // Find _ERRORS column
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

        Path errorFile = errorReportService.generateErrorReport(
                originalFile, validationResult, mappings, config);

        try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            Row dataRow = sheet.getRow(1);  // row 2 (0-based: 1) has error at column 1
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

        Path errorFile = errorReportService.generateErrorReport(
                originalFile, validationResult, mappings, config);

        try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            Row dataRow = sheet.getRow(1);
            // Error column should be at last column
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

        Path errorFile = errorReportService.generateErrorReport(
                originalFile, validationResult, mappings, config);

        assertThat(errorFile).exists();
        assertThat(errorFile.toString()).endsWith(".xlsx");
        // Verify it can be opened as a valid workbook
        try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
            assertThat(wb.getNumberOfSheets()).isGreaterThan(0);
        }
    }

    // ===== Helpers =====

    static class TestConfig implements ExcelImportConfig {
        @Override public int getHeaderRow() { return 1; }
        @Override public int getDataStartRow() { return 2; }
        @Override public Set<String> getSkipColumns() { return Set.of("A"); }
        @Override public String[] getNaturalKeyFields() { return new String[]{"name"}; }
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

    private ExcelValidationResult createValidationResult() {
        CellError cellError = CellError.builder()
                .columnIndex(1)
                .columnLetter("B")
                .fieldName("name")
                .headerName("Name")
                .rejectedValue("")
                .message("필수 입력 항목입니다")
                .build();

        RowError rowError = RowError.builder()
                .rowNumber(2)
                .cellErrors(new ArrayList<>(List.of(cellError)))
                .build();

        return ExcelValidationResult.failure(2, List.of(rowError));
    }

    private ExcelValidationResult createValidationResultMultipleErrors() {
        CellError cellError1 = CellError.builder()
                .columnIndex(1)
                .columnLetter("B")
                .fieldName("name")
                .headerName("Name")
                .rejectedValue("")
                .message("필수 입력 항목입니다")
                .build();

        CellError cellError2 = CellError.builder()
                .columnIndex(2)
                .columnLetter("C")
                .fieldName("value")
                .headerName("Value")
                .rejectedValue("abc")
                .message("형식이 올바르지 않습니다")
                .build();

        RowError rowError = RowError.builder()
                .rowNumber(2)
                .cellErrors(new ArrayList<>(List.of(cellError1, cellError2)))
                .build();

        return ExcelValidationResult.failure(2, List.of(rowError));
    }
}
