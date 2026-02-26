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

    // 1. SecureExcelUtils로 원본을 읽기 전용으로 열기(보안: 쓰기 권한 불필요)
    try (Workbook sourceWb = SecureExcelUtils.createWorkbook(originalXlsx)) {

      // 2. 대상 워크북 생성 후 SXSSF 스트리밍 윈도우로 래핑
      try (XSSFWorkbook targetXssf = new XSSFWorkbook();
          SXSSFWorkbook sxssfWb = new SXSSFWorkbook(targetXssf, 100)) {
        var styleMap = WorkbookCopyUtils.buildStyleMapping(sourceWb, targetXssf);

        // 3. 오류 조회 맵 생성: 1-based 행 번호를 키로 하는 Map<Integer, RowError>
        var errorsByRow = new HashMap<Integer, RowError>();
        for (RowError rowError : validationResult.getRowErrors()) {
          errorsByRow.put(rowError.getRowNumber(), rowError);
        }

        int dataSheetIndex = config.getSheetIndex();
        int headerRowIdx = config.getHeaderRow() - 1;
        var errorStyleCache = new HashMap<Integer, CellStyle>();

        // 5. 모든 시트 복사
        for (int sheetIdx = 0; sheetIdx < sourceWb.getNumberOfSheets(); sheetIdx++) {
          Sheet srcSheet = sourceWb.getSheetAt(sheetIdx);
          SXSSFSheet tgtSheet = sxssfWb.createSheet(srcSheet.getSheetName());

          // 모든 행을 기준으로 최대 컬럼 계산
          int maxCol = 0;
          for (Row row : srcSheet) {
            maxCol = Math.max(maxCol, row.getLastCellNum());
          }

          // 시트 수준 메타데이터 복사(내부 XSSFSheet에 위임되며 SXSSF에서 안전)
          WorkbookCopyUtils.copyColumnWidths(srcSheet, tgtSheet, maxCol);
          WorkbookCopyUtils.copyMergedRegions(srcSheet, tgtSheet);

          boolean isDataSheet = (sheetIdx == dataSheetIndex);
          int errorColIndex = isDataSheet ? maxCol : -1;
          int lastRowNum = srcSheet.getLastRowNum();

          // 6. 모든 행을 위에서 아래로 스트림 복사(SXSSF: 순차 쓰기만 지원)
          for (int rowIdx = 0; rowIdx <= lastRowNum; rowIdx++) {
            Row srcRow = srcSheet.getRow(rowIdx);
            Row tgtRow = tgtSheet.createRow(rowIdx);

            if (srcRow == null) {
              continue;
            }

            tgtRow.setHeight(srcRow.getHeight());

            boolean isHeaderRow = isDataSheet && rowIdx == headerRowIdx;
            RowError rowError = isDataSheet ? errorsByRow.get(rowIdx + 1) : null;

            // O(1) 조회를 위한 오류 컬럼 인덱스 집합 구성
            var errorColIndices = new HashSet<Integer>();
            if (rowError != null) {
              for (var cellError : rowError.getCellErrors()) {
                if (cellError.columnIndex() >= 0) {
                  errorColIndices.add(cellError.columnIndex());
                }
              }
            }

            // 매핑된 스타일로 각 셀 복사
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

            // 데이터 시트: _ERRORS 컬럼 추가
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
                // 보안: Excel 수식 인젝션 방지를 위해 정규화
                String sanitizedMessage =
                    SecureExcelUtils.sanitizeForExcelCell(rowError.getFormattedMessage());
                errorMsgCell.setCellValue(sanitizedMessage);
              }
            }
          }

          // 7. 데이터 시트에 안내문 추가(마지막 행 아래 2행)
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

        // 8. 임시 디렉터리에 저장
        String fileId = UUID.randomUUID().toString();
        Path errorsDir = properties.getTempDirectoryPath().resolve("errors");
        Files.createDirectories(errorsDir);
        Path errorFilePath = errorsDir.resolve(fileId + ".xlsx");

        try (OutputStream os = Files.newOutputStream(errorFilePath)) {
          sxssfWb.write(os);
        }

        // 9. 원본 파일명으로 .meta 파일 저장
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
