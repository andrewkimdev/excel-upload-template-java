package com.foo.excel.service;

import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.validation.ExcelValidationResult;
import com.foo.excel.validation.RowError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.apache.poi.ss.usermodel.*;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelErrorReportService {

    private final ExcelImportProperties properties;

    public Path generateErrorReport(Path originalXlsx, ExcelValidationResult validationResult,
            List<ExcelParserService.ColumnMapping> columnMappings, ExcelImportConfig config) throws IOException {

        try (Workbook workbook = WorkbookFactory.create(originalXlsx.toFile())) {
            Sheet sheet = workbook.getSheetAt(config.getSheetIndex());

            // Find last column
            int headerRowNum = config.getHeaderRow() - 1;
            Row headerRow = sheet.getRow(headerRowNum);
            int lastCol = headerRow != null ? headerRow.getLastCellNum() : 0;
            int errorColIndex = lastCol;

            // Write error column header
            if (headerRow != null) {
                Cell errorHeaderCell = headerRow.createCell(errorColIndex);
                errorHeaderCell.setCellValue(config.getErrorColumnName());

                CellStyle headerStyle = workbook.createCellStyle();
                Font boldFont = workbook.createFont();
                boldFont.setBold(true);
                headerStyle.setFont(boldFont);
                errorHeaderCell.setCellStyle(headerStyle);
            }

            // Create red background style for error cells
            CellStyle errorCellStyle = workbook.createCellStyle();
            errorCellStyle.setFillForegroundColor(parseColor(workbook, properties.getErrorCellColor()));
            errorCellStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            // Write errors for each row
            for (RowError rowError : validationResult.getRowErrors()) {
                int rowIdx = rowError.getRowNumber() - 1; // Convert to 0-based
                Row row = sheet.getRow(rowIdx);
                if (row == null) {
                    row = sheet.createRow(rowIdx);
                }

                // Highlight error cells
                for (var cellError : rowError.getCellErrors()) {
                    if (cellError.getColumnIndex() >= 0) {
                        Cell cell = row.getCell(cellError.getColumnIndex());
                        if (cell == null) {
                            cell = row.createCell(cellError.getColumnIndex());
                        }
                        cell.setCellStyle(errorCellStyle);
                    }
                }

                // Write concatenated error messages in error column
                Cell errorCell = row.createCell(errorColIndex);
                errorCell.setCellValue(rowError.getFormattedMessage());
            }

            // Save to temp directory
            String fileId = UUID.randomUUID().toString();
            Path errorsDir = properties.getTempDirectoryPath().resolve("errors");
            Files.createDirectories(errorsDir);
            Path errorFilePath = errorsDir.resolve(fileId + ".xlsx");

            try (OutputStream os = Files.newOutputStream(errorFilePath)) {
                workbook.write(os);
            }

            log.info("Error report generated: {}", errorFilePath);
            return errorFilePath;
        }
    }

    private short parseColor(Workbook workbook, String hexColor) {
        // Use IndexedColors for simplicity; POI's custom color support varies by format
        // #FFCCCC is close to ROSE
        return IndexedColors.ROSE.getIndex();
    }
}
