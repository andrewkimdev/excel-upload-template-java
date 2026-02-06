package com.foo.excel.service;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.HeaderMatchMode;
import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.templates.samples.tariffexemption.TariffExemptionDto;
import com.foo.excel.templates.samples.tariffexemption.TariffExemptionImportConfig;
import lombok.Data;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.io.OutputStream;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;

import static org.assertj.core.api.Assertions.*;

class ExcelParserServiceTest {

    private ExcelParserService parserService;
    private ExcelImportConfig tariffConfig;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        parserService = new ExcelParserService();
        tariffConfig = new TariffExemptionImportConfig();
    }

    @Test
    void parse_correctNumberOfRows() throws IOException {
        Path file = createTariffExemptionFile(3, false, false);

        ExcelParserService.ParseResult<TariffExemptionDto> result =
                parserService.parse(file, TariffExemptionDto.class, tariffConfig);

        assertThat(result.rows()).hasSize(3);
        assertThat(result.sourceRowNumbers()).hasSize(3);
    }

    @Test
    void parse_footerMarker_stopsReading() throws IOException {
        Path file = createTariffExemptionFile(3, true, false);

        ExcelParserService.ParseResult<TariffExemptionDto> result =
                parserService.parse(file, TariffExemptionDto.class, tariffConfig);

        // Footer after 3 data rows means only 3 rows read
        assertThat(result.rows()).hasSize(3);
    }

    @Test
    void parse_blankRows_skipped() throws IOException {
        Path file = createTariffExemptionFile(3, false, true);

        ExcelParserService.ParseResult<TariffExemptionDto> result =
                parserService.parse(file, TariffExemptionDto.class, tariffConfig);

        // 3 data rows + 1 blank row inserted = only 3 parsed
        assertThat(result.rows()).hasSize(3);
    }

    @Test
    void parse_mergedCellResolution() throws IOException {
        // Create a file with merged cell F-G (HSK No column)
        Path file = createFileWithMergedCells();

        ExcelParserService.ParseResult<TariffExemptionDto> result =
                parserService.parse(file, TariffExemptionDto.class, tariffConfig);

        assertThat(result.rows()).isNotEmpty();
        // The merged cell value should be readable from column F
        assertThat(result.rows().get(0).getHsCode()).isEqualTo("8481.80-2000");
    }

    @Test
    void parse_typeCoercion_string_trimmed() throws IOException {
        Path file = createTariffExemptionFile(1, false, false);

        ExcelParserService.ParseResult<TariffExemptionDto> result =
                parserService.parse(file, TariffExemptionDto.class, tariffConfig);

        assertThat(result.rows().get(0).getItemName()).isEqualTo("TestItem1");
    }

    @Test
    void parse_typeCoercion_integer() throws IOException {
        Path file = createTariffExemptionFile(1, false, false);

        ExcelParserService.ParseResult<TariffExemptionDto> result =
                parserService.parse(file, TariffExemptionDto.class, tariffConfig);

        assertThat(result.rows().get(0).getSequenceNo()).isEqualTo(1);
    }

    @Test
    void parse_typeCoercion_bigDecimal() throws IOException {
        Path file = createTariffExemptionFile(1, false, false);

        ExcelParserService.ParseResult<TariffExemptionDto> result =
                parserService.parse(file, TariffExemptionDto.class, tariffConfig);

        assertThat(result.rows().get(0).getTariffRate()).isNotNull();
        assertThat(result.rows().get(0).getTariffRate().doubleValue()).isCloseTo(8.0, within(0.01));
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
        Path file = createTariffExemptionFile(1, false, false);

        ExcelParserService.ParseResult<TariffExemptionDto> result =
                parserService.parse(file, TariffExemptionDto.class, tariffConfig);

        // Column A is decorative — the parser skips it, so no field maps to column A
        // Verify that column mappings don't include column A (index 0)
        assertThat(result.columnMappings())
                .noneMatch(m -> m.resolvedColumnIndex() == 0);
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
    void parse_parseErrors_reportedForInvalidTypes() throws IOException {
        Path file = createFileWithInvalidTypeData();
        ExcelImportConfig simpleConfig = new SimpleConfig();

        ExcelParserService.ParseResult<IntegerDto> result =
                parserService.parse(file, IntegerDto.class, simpleConfig);

        assertThat(result.rows()).hasSize(1);
        assertThat(result.rows().get(0).getCount()).isNull();
        assertThat(result.parseErrors()).hasSize(1);
        assertThat(result.parseErrors().get(0).getCellErrors().get(0).message())
                .contains("Integer");
    }

    // ===== Header verification tests =====

    @Test
    void parse_fixedColumn_headerMismatch_requiredColumn_throwsException() throws Exception {
        Path file = createFileWithWrongHeaders();
        ExcelImportConfig config = new SimpleConfig();

        assertThatThrownBy(() -> parserService.parse(file, SimpleDto.class, config))
                .isInstanceOf(ColumnResolutionBatchException.class)
                .satisfies(ex -> {
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
                .satisfies(ex -> {
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
                .satisfies(ex -> {
                    var batch = (ColumnResolutionBatchException) ex;
                    assertThat(batch.toKoreanMessage()).contains("컬럼을 찾을 수 없습니다");
                });
    }

    // ===== Helper DTOs =====

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
        @Override public int getHeaderRow() { return 1; }
        @Override public int getDataStartRow() { return 2; }
        @Override public String[] getNaturalKeyFields() { return new String[]{""}; }
    }

    // ===== File creation helpers =====

    private Path createTariffExemptionFile(int dataRows, boolean addFooter, boolean addBlankRow)
            throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");

            // Header row at row 4 (0-based: row 3)
            Row headerRow = sheet.createRow(3);
            headerRow.createCell(0).setCellValue("No");  // Column A - decorative
            headerRow.createCell(1).setCellValue("순번");  // Column B
            headerRow.createCell(2).setCellValue("물품명");  // Column C
            headerRow.createCell(3).setCellValue("규격1)");  // Column D
            headerRow.createCell(4).setCellValue("모델명1)");  // Column E
            headerRow.createCell(5).setCellValue("HSK No");  // Column F
            headerRow.createCell(6).setCellValue("");  // Column G - merged slave
            headerRow.createCell(7).setCellValue("관세율");  // Column H
            headerRow.createCell(8).setCellValue("단가($)");  // Column I
            headerRow.createCell(9).setCellValue("제조용");  // Column J
            headerRow.createCell(10).setCellValue("");  // Column K - merged slave
            headerRow.createCell(11).setCellValue("수리용");  // Column L
            headerRow.createCell(12).setCellValue("");  // Column M - merged slave
            headerRow.createCell(13).setCellValue("연간수입예상금액($)");  // Column N
            headerRow.createCell(14).setCellValue("심의결과");  // Column O
            headerRow.createCell(15).setCellValue("");  // Column P - merged slave
            headerRow.createCell(16).setCellValue("연간 예상소요량");  // Column Q

            // Data rows start at row 7 (0-based: row 6)
            int currentRow = 6;
            for (int i = 0; i < dataRows; i++) {
                if (addBlankRow && i == 1) {
                    // Insert a blank row
                    sheet.createRow(currentRow);
                    currentRow++;
                }
                Row row = sheet.createRow(currentRow);
                row.createCell(0).setCellValue(i + 1);  // Column A
                row.createCell(1).setCellValue(i + 1);  // Column B - sequenceNo
                row.createCell(2).setCellValue("TestItem" + (i + 1));  // Column C - itemName
                row.createCell(3).setCellValue("Spec" + (i + 1));  // Column D - specification
                row.createCell(4).setCellValue("Model" + (i + 1));  // Column E - modelName
                row.createCell(5).setCellValue("8481.80-200" + i);  // Column F - hsCode
                row.createCell(7).setCellValue(8.0);  // Column H - tariffRate
                row.createCell(8).setCellValue(100.0);  // Column I - unitPrice
                row.createCell(9).setCellValue(10);  // Column J - qtyForManufacturing
                row.createCell(11).setCellValue(5);  // Column L - qtyForRepair
                row.createCell(13).setCellValue(50000.0);  // Column N - annualImportEstimate
                row.createCell(14).setCellValue("통과");  // Column O - reviewResult
                row.createCell(16).setCellValue(100);  // Column Q - annualExpectedQty
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

            // Header row at row 4 (0-based: row 3)
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

            // Data row at row 7 (0-based: row 6) with merged F-G
            Row dataRow = sheet.createRow(6);
            dataRow.createCell(0).setCellValue(1);
            dataRow.createCell(1).setCellValue(1);
            dataRow.createCell(2).setCellValue("MergedItem");
            dataRow.createCell(3).setCellValue("Spec1");
            dataRow.createCell(4).setCellValue("Model1");
            dataRow.createCell(5).setCellValue("8481.80-2000");  // Master cell of F-G merge
            // Column G is merged slave - don't write to it
            dataRow.createCell(7).setCellValue(5.0);
            dataRow.createCell(8).setCellValue(200.0);
            dataRow.createCell(9).setCellValue(20);
            dataRow.createCell(11).setCellValue(10);
            dataRow.createCell(13).setCellValue(100000.0);
            dataRow.createCell(14).setCellValue("통과");
            dataRow.createCell(16).setCellValue(50);

            // Add merged region for F-G in data row
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
            dataRow2.createCell(1).setCellValue("");  // blank -> false

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
            headerRow.createCell(1).setCellValue("WRONG_HEADER");  // Column B: expected "ExactHeader"
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
            headerRow.createCell(2).setCellValue("WRONG");  // Column C: expected "Optional"

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
            headerRow.createCell(1).setCellValue("WRONG_B");  // Expected "First"
            headerRow.createCell(2).setCellValue("WRONG_C");  // Expected "Second"

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
