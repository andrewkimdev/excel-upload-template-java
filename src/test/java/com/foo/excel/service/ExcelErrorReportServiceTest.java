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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

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
                originalFile, validationResult, mappings, config, "test-original.xlsx");

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
                originalFile, validationResult, mappings, config, "test-original.xlsx");

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
                originalFile, validationResult, mappings, config, "test-original.xlsx");

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
                originalFile, validationResult, mappings, config, "test-original.xlsx");

        assertThat(errorFile).exists();
        assertThat(errorFile.toString()).endsWith(".xlsx");
        // Verify it can be opened as a valid workbook
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

        Path errorFile = errorReportService.generateErrorReport(
                originalFile, validationResult, mappings, config, "test-original.xlsx");

        try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            Row dataRow = sheet.getRow(1);
            int lastCol = sheet.getRow(0).getLastCellNum();
            Cell errMsgCell = dataRow.getCell(lastCol - 1);

            assertThat(errMsgCell).isNotNull();
            String cellValue = errMsgCell.getStringCellValue();
            // The formatted message starts with "[B]" which is safe
            assertThat(cellValue).startsWith("[");
            // Verify the formula content is still present (not stripped)
            assertThat(cellValue).contains("SUM");
        }
    }

    @Test
    void errorReport_errorMessagePreserved_inFormattedOutput() throws IOException {
        Path originalFile = createSimpleXlsx();
        ExcelValidationResult validationResult = createValidationResultWithAtSign();
        TestConfig config = new TestConfig();
        List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

        Path errorFile = errorReportService.generateErrorReport(
                originalFile, validationResult, mappings, config, "test-original.xlsx");

        try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            Row dataRow = sheet.getRow(1);
            int lastCol = sheet.getRow(0).getLastCellNum();
            Cell errMsgCell = dataRow.getCell(lastCol - 1);

            assertThat(errMsgCell).isNotNull();
            String cellValue = errMsgCell.getStringCellValue();
            // The formatted message includes the column letter prefix
            assertThat(cellValue).startsWith("[B]");
        }
    }

    @Test
    void errorReport_normalErrorMessage_preservedCorrectly() throws IOException {
        Path originalFile = createSimpleXlsx();
        ExcelValidationResult validationResult = createValidationResult();
        TestConfig config = new TestConfig();
        List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

        Path errorFile = errorReportService.generateErrorReport(
                originalFile, validationResult, mappings, config, "test-original.xlsx");

        try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            Row dataRow = sheet.getRow(1);
            int lastCol = sheet.getRow(0).getLastCellNum();
            Cell errMsgCell = dataRow.getCell(lastCol - 1);

            assertThat(errMsgCell).isNotNull();
            String cellValue = errMsgCell.getStringCellValue();
            // Normal messages should be formatted with column prefix
            assertThat(cellValue).startsWith("[B]");
            assertThat(cellValue).contains("필수 입력 항목입니다");
        }
    }

    // ===== New tests for format preservation =====

    @Test
    void errorReport_preservesOriginalCellFormatting_withRoseFillAdded() throws IOException {
        Path originalFile = createStyledXlsx();
        ExcelValidationResult validationResult = createValidationResult();
        TestConfig config = new TestConfig();
        List<ExcelParserService.ColumnMapping> mappings = Collections.emptyList();

        Path errorFile = errorReportService.generateErrorReport(
                originalFile, validationResult, mappings, config, "test-styled.xlsx");

        try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            Row dataRow = sheet.getRow(1);
            Cell errorCell = dataRow.getCell(1); // column B has error AND bold+border style

            // Error cell should have ROSE fill
            assertThat(errorCell.getCellStyle().getFillForegroundColor())
                    .isEqualTo(IndexedColors.ROSE.getIndex());
            assertThat(errorCell.getCellStyle().getFillPattern())
                    .isEqualTo(FillPatternType.SOLID_FOREGROUND);

            // Original formatting should be preserved
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

        Path errorFile = errorReportService.generateErrorReport(
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

        Path errorFile = errorReportService.generateErrorReport(
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

        Path errorFile = errorReportService.generateErrorReport(
                originalFile, validationResult, mappings, config, "test-disclaimer.xlsx");

        try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
            Sheet sheet = wb.getSheetAt(0);
            // Source has rows 0-2 (lastRowNum=2), disclaimer at row 4 (2+2)
            Row disclaimerRow = sheet.getRow(4);
            assertThat(disclaimerRow).isNotNull();
            Cell disclaimerCell = disclaimerRow.getCell(0);
            assertThat(disclaimerCell.getStringCellValue()).startsWith("※");
            assertThat(disclaimerCell.getStringCellValue()).contains("오류 확인용");

            // Verify italic gray style
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

        Path errorFile = errorReportService.generateErrorReport(
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

        Path errorFile = errorReportService.generateErrorReport(
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

        Path errorFile = errorReportService.generateErrorReport(
                originalFile, validationResult, mappings, config, "test.xlsx");

        // Verify output is valid XLSX (WorkbookFactory returns XSSFWorkbook for .xlsx)
        try (Workbook wb = WorkbookFactory.create(errorFile.toFile())) {
            assertThat(wb).isInstanceOf(XSSFWorkbook.class);
            assertThat(wb.getNumberOfSheets()).isGreaterThan(0);
            assertThat((Object) wb.getSheetAt(0).getRow(0)).isNotNull();
        }
    }

    // ===== Helpers =====

    static class TestConfig implements ExcelImportConfig {
        @Override public int getHeaderRow() { return 1; }
        @Override public int getDataStartRow() { return 2; }
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
            // Data sheet (index 0)
            Sheet dataSheet = wb.createSheet("Data");
            Row headerRow = dataSheet.createRow(0);
            headerRow.createCell(0).setCellValue("No");
            headerRow.createCell(1).setCellValue("Name");
            headerRow.createCell(2).setCellValue("Value");
            Row dataRow = dataSheet.createRow(1);
            dataRow.createCell(0).setCellValue(1);
            dataRow.createCell(1).setCellValue("Test");
            dataRow.createCell(2).setCellValue("abc");

            // Reference sheet (index 1)
            Sheet refSheet = wb.createSheet("Reference");
            refSheet.createRow(0).createCell(0).setCellValue("Ref Data");

            // Instructions sheet (index 2)
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

    private ExcelValidationResult createValidationResultWithFormulaInjection() {
        CellError cellError = CellError.builder()
                .columnIndex(1)
                .columnLetter("B")
                .fieldName("name")
                .headerName("Name")
                .rejectedValue("=cmd|'/C calc'!A0")
                .message("=SUM(A1:A10)")
                .build();

        RowError rowError = RowError.builder()
                .rowNumber(2)
                .cellErrors(new ArrayList<>(List.of(cellError)))
                .build();

        return ExcelValidationResult.failure(2, List.of(rowError));
    }

    private ExcelValidationResult createValidationResultWithAtSign() {
        CellError cellError = CellError.builder()
                .columnIndex(1)
                .columnLetter("B")
                .fieldName("name")
                .headerName("Name")
                .rejectedValue("@attack")
                .message("@SUM(A1:A10)")
                .build();

        RowError rowError = RowError.builder()
                .rowNumber(2)
                .cellErrors(new ArrayList<>(List.of(cellError)))
                .build();

        return ExcelValidationResult.failure(2, List.of(rowError));
    }
}
