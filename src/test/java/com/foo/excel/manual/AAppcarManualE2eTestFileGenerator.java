package com.foo.excel.manual;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Comparator;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

/**
 * Generates the checked-in manual E2E input workbooks referenced by the AAPPCAR manual test plan.
 */
public final class AAppcarManualE2eTestFileGenerator {

  private static final int CURRENT_DEFAULT_MAX_ROWS = 10_000;
  private static final int TOO_MANY_ROWS = CURRENT_DEFAULT_MAX_ROWS + 1;
  private static final Path OUTPUT_DIR = Path.of("docs", "aappcar-manual-e2e", "01_test-files");

  private AAppcarManualE2eTestFileGenerator() {}

  public static void main(String[] args) throws Exception {
    resetOutputDirectory();

    byte[] tc01Valid = createWorkbook(WorkbookType.XLSX, 2, null);
    byte[] tc08MissingRequiredCell =
        createWorkbook(
            WorkbookType.XLSX, 2, workbook -> workbook.getSheetAt(0).getRow(6).getCell(2).setCellValue(""));

    writeFile("TC-01_valid-upload_input.xlsx", tc01Valid);
    writeFile("TC-02_invalid-extension_input.xls", createWorkbook(WorkbookType.XLS, 2, null));
    writeFakeXlsx("TC-03_fake-xlsx_input.xlsx");
    writeFile("TC-04_valid-file_missing-metadata_input.xlsx", tc01Valid);
    writeFile("TC-05_valid-file_duplicate-metadata_input.xlsx", tc01Valid);
    writeFile(
        "TC-06_wrong-header_input.xlsx",
        createWorkbook(
            WorkbookType.XLSX,
            2,
            workbook -> workbook.getSheetAt(0).getRow(3).getCell(2).setCellValue("WRONG_C")));
    writeFile(
        "TC-07_missing-required-column_input.xlsx",
        createWorkbook(
            WorkbookType.XLSX,
            2,
            workbook -> {
              Sheet sheet = workbook.getSheetAt(0);
              sheet.getRow(3).getCell(5).setCellValue("");
              sheet.getRow(3).getCell(6).setCellValue("");
            }));
    writeFile("TC-08_missing-required-cell_input.xlsx", tc08MissingRequiredCell);
    writeFile(
        "TC-09_invalid-format_input.xlsx",
        createWorkbook(
            WorkbookType.XLSX,
            2,
            workbook -> workbook.getSheetAt(0).getRow(6).getCell(5).setCellValue("bad-hs-code")));
    writeFile(
        "TC-10_duplicate-composite-key_input.xlsx",
        createWorkbook(
            WorkbookType.XLSX,
            2,
            workbook -> {
              Sheet sheet = workbook.getSheetAt(0);
              Row row7 = sheet.getRow(6);
              Row row8 = sheet.getRow(7);
              row8.getCell(2).setCellValue(row7.getCell(2).getStringCellValue());
              row8.getCell(3).setCellValue(row7.getCell(3).getStringCellValue());
              row8.getCell(5).setCellValue(row7.getCell(5).getStringCellValue());
            }));
    writeFile("TC-11_reuse-error-report-source_input.xlsx", tc08MissingRequiredCell);
    writeFile("TC-12_too-many-rows_input.xlsx", createWorkbook(WorkbookType.XLSX, TOO_MANY_ROWS, null));
    writeManifest();
  }

  private static void resetOutputDirectory() throws IOException {
    if (Files.exists(OUTPUT_DIR)) {
      try (var walk = Files.walk(OUTPUT_DIR)) {
        walk.sorted(Comparator.reverseOrder())
            .forEach(
                path -> {
                  try {
                    Files.deleteIfExists(path);
                  } catch (IOException e) {
                    throw new IllegalStateException("Failed to reset output directory: " + path, e);
                  }
                });
      }
    }
    Files.createDirectories(OUTPUT_DIR);
  }

  private static byte[] createWorkbook(
      WorkbookType workbookType, int dataRows, WorkbookMutation mutation) throws IOException {
    try (Workbook workbook = workbookType.createWorkbook()) {
      Sheet sheet = workbook.createSheet("Sheet1");
      fillSheet(sheet, dataRows);
      if (mutation != null) {
        mutation.apply(workbook);
      }

      ByteArrayOutputStream output = new ByteArrayOutputStream();
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private static void fillSheet(Sheet sheet, int dataRows) {
    createHeaderRows(sheet);
    for (int i = 0; i < dataRows; i++) {
      int rowNumber = 6 + i;
      Row row = sheet.createRow(rowNumber);
      row.createCell(0).setCellValue(i + 1);
      row.createCell(1).setCellValue(i + 1);
      row.createCell(2).setCellValue("Item" + (i + 1));
      row.createCell(3).setCellValue("Spec" + (i + 1));
      row.createCell(4).setCellValue("Model" + (i + 1));
      row.createCell(5).setCellValue(String.format("8481.80-%04d", 2000 + i));
      row.createCell(7).setCellValue(8.0);
      row.createCell(8).setCellValue(100.0 + i);
      row.createCell(9).setCellValue(10 + i);
      row.createCell(11).setCellValue(5 + i);
      row.createCell(13).setCellValue(50000.0 + (1000.0 * i));
      row.createCell(14).setCellValue("통과");
      row.createCell(16).setCellValue(100 + i);
    }

    Row footerRow = sheet.createRow(6 + dataRows);
    footerRow.createCell(0).setCellValue("※ 작성 예시 끝");
  }

  private static void createHeaderRows(Sheet sheet) {
    Row row4 = sheet.createRow(3);
    row4.createCell(0).setCellValue("No");
    row4.createCell(1).setCellValue("순번");
    row4.createCell(2).setCellValue("물품명");
    row4.createCell(3).setCellValue("규격1)");
    row4.createCell(4).setCellValue("모델명1)");
    row4.createCell(5).setCellValue("HSK No");
    row4.createCell(6).setCellValue("");
    row4.createCell(7).setCellValue("관세율");
    row4.createCell(8).setCellValue("단가($)");
    row4.createCell(9).setCellValue("소요량");
    row4.createCell(10).setCellValue("");
    row4.createCell(11).setCellValue("");
    row4.createCell(12).setCellValue("");
    row4.createCell(13).setCellValue("연간수입예상금액($)");
    row4.createCell(14).setCellValue("심의결과");
    row4.createCell(15).setCellValue("");
    row4.createCell(16).setCellValue("연간 예상소요량");

    Row row5 = sheet.createRow(4);
    row5.createCell(9).setCellValue("제조용");
    row5.createCell(10).setCellValue("");
    row5.createCell(11).setCellValue("수리용");
    row5.createCell(12).setCellValue("");

    Row row6 = sheet.createRow(5);
    row6.createCell(9).setCellValue("");
    row6.createCell(10).setCellValue("");
    row6.createCell(11).setCellValue("");
    row6.createCell(12).setCellValue("");

    sheet.addMergedRegion(new CellRangeAddress(3, 3, 9, 12));
    sheet.addMergedRegion(new CellRangeAddress(4, 4, 9, 10));
    sheet.addMergedRegion(new CellRangeAddress(4, 4, 11, 12));
    sheet.addMergedRegion(new CellRangeAddress(5, 5, 9, 10));
    sheet.addMergedRegion(new CellRangeAddress(5, 5, 11, 12));
  }

  private static void writeFakeXlsx(String filename) throws IOException {
    String content =
        "This is not a real xlsx workbook.\n"
            + "The file exists to verify magic-byte validation for manual E2E testing.\n";
    Files.writeString(OUTPUT_DIR.resolve(filename), content, StandardCharsets.UTF_8);
  }

  private static void writeFile(String filename, byte[] bytes) throws IOException {
    Files.write(OUTPUT_DIR.resolve(filename), bytes);
  }

  private static void writeManifest() throws IOException {
    String content =
        """
        # AAPPCAR manual E2E input files

        Generated by `./gradlew generateAappcarManualTestFiles`.

        Base workbook contract:

        - first sheet only
        - header row 4, data starts at row 7
        - grouped headers for `소요량`
        - footer marker row after the last data row starts with `※`

        Shared metadata defaults for manual test references:

        - `comeYear=2026`
        - `comeOrder=001`
        - `uploadSeq=1`
        - `equipCode=EQ-01`
        - `equipMean=설비A`
        - `hsno=8481802000`
        - `spec=규격A`
        - `taxRate=8.50`

        File notes:

        - `TC-04` and `TC-05` reuse the valid workbook bytes because those scenarios are metadata/DB-state driven.
        - `TC-11` reuses the same workbook bytes as `TC-08` because it only validates error-report download.
        - `TC-12` contains %d valid data rows based on the current repo default `excel.import.max-rows=%d`.
        """
            .formatted(TOO_MANY_ROWS, CURRENT_DEFAULT_MAX_ROWS);
    Files.writeString(OUTPUT_DIR.resolve("README.md"), content, StandardCharsets.UTF_8);
  }

  @FunctionalInterface
  private interface WorkbookMutation {
    void apply(Workbook workbook) throws IOException;
  }

  private enum WorkbookType {
    XLSX {
      @Override
      Workbook createWorkbook() {
        return new XSSFWorkbook();
      }
    },
    XLS {
      @Override
      Workbook createWorkbook() {
        return new HSSFWorkbook();
      }
    };

    abstract Workbook createWorkbook();
  }
}
