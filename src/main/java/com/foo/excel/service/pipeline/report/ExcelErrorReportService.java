package com.foo.excel.service.pipeline.report;

import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.service.pipeline.parse.ExcelParserService;
import com.foo.excel.util.SecureExcelUtils;
import com.foo.excel.util.WorkbookCopyUtils;
import com.foo.excel.validation.ExcelValidationResult;
import com.foo.excel.validation.RowError;
import java.io.IOException;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.UUID;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.Font;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.streaming.SXSSFSheet;
import org.apache.poi.xssf.streaming.SXSSFWorkbook;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelErrorReportService {

  private final ExcelImportProperties properties;

  public Path generateErrorReport(
      Path originalXlsx,
      ExcelValidationResult validationResult,
      List<ExcelParserService.ColumnMapping> columnMappings,
      ExcelImportConfig config,
      String originalFilename)
      throws IOException {

    // 1. Open source read-only via SecureExcelUtils (security: no write access needed)
    try (Workbook sourceWb = SecureExcelUtils.createWorkbook(originalXlsx)) {

      // 2. Create target workbook and wrap with SXSSF streaming window
      try (XSSFWorkbook targetXssf = new XSSFWorkbook();
          SXSSFWorkbook sxssfWb = new SXSSFWorkbook(targetXssf, 100)) {
        var styleMap = WorkbookCopyUtils.buildStyleMapping(sourceWb, targetXssf);

        // 3. Build error lookup: Map<Integer, RowError> keyed by 1-based row number
        var errorsByRow = new HashMap<Integer, RowError>();
        for (RowError rowError : validationResult.getRowErrors()) {
          errorsByRow.put(rowError.getRowNumber(), rowError);
        }

        int dataSheetIndex = config.getSheetIndex();
        int headerRowIdx = config.getHeaderRow() - 1;
        var errorStyleCache = new HashMap<Integer, CellStyle>();

        // 5. Copy ALL sheets
        for (int sheetIdx = 0; sheetIdx < sourceWb.getNumberOfSheets(); sheetIdx++) {
          Sheet srcSheet = sourceWb.getSheetAt(sheetIdx);
          SXSSFSheet tgtSheet = sxssfWb.createSheet(srcSheet.getSheetName());

          // Compute max column across all rows
          int maxCol = 0;
          for (Row row : srcSheet) {
            maxCol = Math.max(maxCol, row.getLastCellNum());
          }

          // Sheet-level metadata (delegates to underlying XSSFSheet, safe in SXSSF)
          WorkbookCopyUtils.copyColumnWidths(srcSheet, tgtSheet, maxCol);
          WorkbookCopyUtils.copyMergedRegions(srcSheet, tgtSheet);

          boolean isDataSheet = (sheetIdx == dataSheetIndex);
          int errorColIndex = isDataSheet ? maxCol : -1;
          int lastRowNum = srcSheet.getLastRowNum();

          // 6. Stream-copy all rows top-to-bottom (SXSSF: sequential writes only)
          for (int rowIdx = 0; rowIdx <= lastRowNum; rowIdx++) {
            Row srcRow = srcSheet.getRow(rowIdx);
            Row tgtRow = tgtSheet.createRow(rowIdx);

            if (srcRow == null) {
              continue;
            }

            tgtRow.setHeight(srcRow.getHeight());

            boolean isHeaderRow = isDataSheet && rowIdx == headerRowIdx;
            RowError rowError = isDataSheet ? errorsByRow.get(rowIdx + 1) : null;

            // Build set of error column indices for O(1) lookup
            var errorColIndices = new HashSet<Integer>();
            if (rowError != null) {
              for (var cellError : rowError.getCellErrors()) {
                if (cellError.columnIndex() >= 0) {
                  errorColIndices.add(cellError.columnIndex());
                }
              }
            }

            // Copy each cell with mapped style
            for (int colIdx = 0; colIdx < srcRow.getLastCellNum(); colIdx++) {
              Cell srcCell = srcRow.getCell(colIdx);
              Cell tgtCell = tgtRow.createCell(colIdx);

              if (srcCell != null) {
                WorkbookCopyUtils.copyCellValue(srcCell, tgtCell);

                CellStyle mappedStyle = styleMap.get((int) srcCell.getCellStyle().getIndex());
                if (mappedStyle != null) {
                  if (errorColIndices.contains(colIdx)) {
                    tgtCell.setCellStyle(
                        WorkbookCopyUtils.getOrCreateErrorStyle(
                            sxssfWb, mappedStyle, errorStyleCache));
                  } else {
                    tgtCell.setCellStyle(mappedStyle);
                  }
                }
              }
            }

            // Data sheet: add _ERRORS column
            if (isDataSheet) {
              if (isHeaderRow) {
                Cell errorHeaderCell = tgtRow.createCell(errorColIndex);
                errorHeaderCell.setCellValue(config.getErrorColumnName());

                CellStyle headerStyle = sxssfWb.createCellStyle();
                Font boldFont = sxssfWb.createFont();
                boldFont.setBold(true);
                headerStyle.setFont(boldFont);
                errorHeaderCell.setCellStyle(headerStyle);
              }

              if (rowError != null) {
                Cell errorMsgCell = tgtRow.createCell(errorColIndex);
                // SECURITY: Sanitize to prevent Excel formula injection
                String sanitizedMessage =
                    SecureExcelUtils.sanitizeForExcelCell(rowError.getFormattedMessage());
                errorMsgCell.setCellValue(sanitizedMessage);
              }
            }
          }

          // 7. Add disclaimer on data sheet (2 rows below last row)
          if (isDataSheet) {
            int disclaimerRowIdx = lastRowNum + 2;
            Row disclaimerRow = tgtSheet.createRow(disclaimerRowIdx);
            Cell disclaimerCell = disclaimerRow.createCell(0);
            disclaimerCell.setCellValue(
                "※ 본 파일은 오류 확인용으로 재생성되었습니다. " + "일부 서식 및 기능이 원본 파일과 다를 수 있습니다.");

            CellStyle disclaimerStyle = sxssfWb.createCellStyle();
            Font disclaimerFont = sxssfWb.createFont();
            disclaimerFont.setItalic(true);
            disclaimerFont.setColor(IndexedColors.GREY_50_PERCENT.getIndex());
            disclaimerStyle.setFont(disclaimerFont);
            disclaimerCell.setCellStyle(disclaimerStyle);

            if (errorColIndex > 0) {
              tgtSheet.addMergedRegion(
                  new CellRangeAddress(disclaimerRowIdx, disclaimerRowIdx, 0, errorColIndex - 1));
            }
          }
        }

        // 8. Save to temp directory
        String fileId = UUID.randomUUID().toString();
        Path errorsDir = properties.getTempDirectoryPath().resolve("errors");
        Files.createDirectories(errorsDir);
        Path errorFilePath = errorsDir.resolve(fileId + ".xlsx");

        try (OutputStream os = Files.newOutputStream(errorFilePath)) {
          sxssfWb.write(os);
        }

        // 9. Save .meta file with original filename
        if (originalFilename != null && !originalFilename.isBlank()) {
          Path metaFilePath = errorsDir.resolve(fileId + ".meta");
          Files.writeString(metaFilePath, originalFilename, StandardCharsets.UTF_8);
        }

        log.info("Error report generated: {}", errorFilePath);
        return errorFilePath;
      }
    }
  }
}
