package com.foo.excel.util;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.HashMap;

import static org.assertj.core.api.Assertions.*;

class WorkbookCopyUtilsTest {

    @Test
    void buildStyleMapping_xssfToXssf_preservesFontBoldAndFill() throws IOException {
        try (var source = new XSSFWorkbook(); var target = new XSSFWorkbook()) {
            CellStyle srcStyle = source.createCellStyle();
            Font boldFont = source.createFont();
            boldFont.setBold(true);
            srcStyle.setFont(boldFont);
            srcStyle.setFillForegroundColor(IndexedColors.LIGHT_GREEN.getIndex());
            srcStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);

            var styleMap = WorkbookCopyUtils.buildStyleMapping(source, target);

            CellStyle mapped = styleMap.get((int) srcStyle.getIndex());
            assertThat(mapped).isNotNull();
            assertThat(target.getFontAt(mapped.getFontIndex()).getBold()).isTrue();
            assertThat(mapped.getFillForegroundColor()).isEqualTo(IndexedColors.LIGHT_GREEN.getIndex());
            assertThat(mapped.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
        }
    }

    @Test
    void buildStyleMapping_hssfToXssf_preservesBorders() throws IOException {
        try (var source = new HSSFWorkbook(); var target = new XSSFWorkbook()) {
            CellStyle srcStyle = source.createCellStyle();
            srcStyle.setBorderBottom(BorderStyle.THIN);
            srcStyle.setBorderTop(BorderStyle.DOUBLE);

            var styleMap = WorkbookCopyUtils.buildStyleMapping(source, target);

            CellStyle mapped = styleMap.get((int) srcStyle.getIndex());
            assertThat(mapped).isNotNull();
            assertThat(mapped.getBorderBottom()).isEqualTo(BorderStyle.THIN);
            assertThat(mapped.getBorderTop()).isEqualTo(BorderStyle.DOUBLE);
        }
    }

    @Test
    void buildStyleMapping_defaultStyleIndex0_modifiedInPlace() throws IOException {
        try (var source = new XSSFWorkbook(); var target = new XSSFWorkbook()) {
            // Source has 1 default + 1 custom = 2 styles
            source.createCellStyle();

            int targetStylesBefore = target.getNumCellStyles();
            var styleMap = WorkbookCopyUtils.buildStyleMapping(source, target);

            // Target should have gained exactly (source styles - 1) new styles
            // because index 0 is reused, not duplicated
            assertThat(target.getNumCellStyles())
                    .isEqualTo(targetStylesBefore + source.getNumCellStyles() - 1);
            // Verify index 0 maps to the default style (index 0 in target)
            assertThat(styleMap.get(0).getIndex()).isEqualTo((short) 0);
        }
    }

    @Test
    void getOrCreateErrorStyle_hasRoseFill_plusOriginalFont() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            CellStyle base = wb.createCellStyle();
            Font boldFont = wb.createFont();
            boldFont.setBold(true);
            base.setFont(boldFont);

            var cache = new HashMap<Integer, CellStyle>();
            CellStyle errorStyle = WorkbookCopyUtils.getOrCreateErrorStyle(wb, base, cache);

            assertThat(errorStyle.getFillForegroundColor()).isEqualTo(IndexedColors.ROSE.getIndex());
            assertThat(errorStyle.getFillPattern()).isEqualTo(FillPatternType.SOLID_FOREGROUND);
            assertThat(wb.getFontAt(errorStyle.getFontIndex()).getBold()).isTrue();
        }
    }

    @Test
    void getOrCreateErrorStyle_sameBaseIndex_returnsCached() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            CellStyle base = wb.createCellStyle();
            var cache = new HashMap<Integer, CellStyle>();

            CellStyle first = WorkbookCopyUtils.getOrCreateErrorStyle(wb, base, cache);
            CellStyle second = WorkbookCopyUtils.getOrCreateErrorStyle(wb, base, cache);

            assertThat(first).isSameAs(second);
        }
    }

    @Test
    void copyCellValue_allTypes_copiedCorrectly() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row srcRow = sheet.createRow(0);
            Row tgtRow = sheet.createRow(1);

            // STRING
            srcRow.createCell(0).setCellValue("hello");
            Cell tgtString = tgtRow.createCell(0);
            WorkbookCopyUtils.copyCellValue(srcRow.getCell(0), tgtString);
            assertThat(tgtString.getStringCellValue()).isEqualTo("hello");

            // NUMERIC
            srcRow.createCell(1).setCellValue(42.5);
            Cell tgtNumeric = tgtRow.createCell(1);
            WorkbookCopyUtils.copyCellValue(srcRow.getCell(1), tgtNumeric);
            assertThat(tgtNumeric.getNumericCellValue()).isEqualTo(42.5);

            // BOOLEAN
            srcRow.createCell(2).setCellValue(true);
            Cell tgtBool = tgtRow.createCell(2);
            WorkbookCopyUtils.copyCellValue(srcRow.getCell(2), tgtBool);
            assertThat(tgtBool.getBooleanCellValue()).isTrue();

            // FORMULA
            srcRow.createCell(3).setCellFormula("SUM(A1:B1)");
            Cell tgtFormula = tgtRow.createCell(3);
            WorkbookCopyUtils.copyCellValue(srcRow.getCell(3), tgtFormula);
            assertThat(tgtFormula.getCellFormula()).isEqualTo("SUM(A1:B1)");

            // BLANK
            srcRow.createCell(4).setBlank();
            Cell tgtBlank = tgtRow.createCell(4);
            WorkbookCopyUtils.copyCellValue(srcRow.getCell(4), tgtBlank);
            assertThat(tgtBlank.getCellType()).isEqualTo(CellType.BLANK);

            // ERROR
            srcRow.createCell(5).setCellErrorValue(FormulaError.DIV0.getCode());
            Cell tgtError = tgtRow.createCell(5);
            WorkbookCopyUtils.copyCellValue(srcRow.getCell(5), tgtError);
            assertThat(tgtError.getCellType()).isEqualTo(CellType.ERROR);
            assertThat(tgtError.getErrorCellValue()).isEqualTo(FormulaError.DIV0.getCode());
        }
    }

    @Test
    void copyCellValue_dateFormattedNumeric_copiedAsDate() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet();
            Row srcRow = sheet.createRow(0);
            Row tgtRow = sheet.createRow(1);

            CellStyle dateStyle = wb.createCellStyle();
            CreationHelper createHelper = wb.getCreationHelper();
            dateStyle.setDataFormat(createHelper.createDataFormat().getFormat("yyyy-MM-dd"));

            Cell srcCell = srcRow.createCell(0);
            srcCell.setCellValue(new java.util.Date());
            srcCell.setCellStyle(dateStyle);

            Cell tgtCell = tgtRow.createCell(0);
            tgtCell.setCellStyle(dateStyle); // need same format for DateUtil check
            WorkbookCopyUtils.copyCellValue(srcCell, tgtCell);

            assertThat(tgtCell.getCellType()).isEqualTo(CellType.NUMERIC);
            assertThat(DateUtil.isCellDateFormatted(tgtCell)).isTrue();
            assertThat(tgtCell.getDateCellValue()).isEqualTo(srcCell.getDateCellValue());
        }
    }

    @Test
    void copyColumnWidths_andMergedRegions_copiedCorrectly() throws IOException {
        try (var wb = new XSSFWorkbook()) {
            Sheet src = wb.createSheet("src");
            src.setColumnWidth(0, 5000);
            src.setColumnWidth(1, 8000);
            src.setColumnHidden(2, true);
            src.setDefaultColumnWidth(12);
            src.setDefaultRowHeight((short) 400);
            src.addMergedRegion(new CellRangeAddress(0, 0, 0, 2));
            src.addMergedRegion(new CellRangeAddress(1, 2, 1, 3));
            // Create rows so the sheet is valid
            src.createRow(0).createCell(0).setCellValue("merged");
            src.createRow(1).createCell(1).setCellValue("merged2");

            Sheet tgt = wb.createSheet("tgt");
            WorkbookCopyUtils.copyColumnWidths(src, tgt, 3);
            WorkbookCopyUtils.copyMergedRegions(src, tgt);

            assertThat(tgt.getColumnWidth(0)).isEqualTo(5000);
            assertThat(tgt.getColumnWidth(1)).isEqualTo(8000);
            assertThat(tgt.isColumnHidden(2)).isTrue();
            assertThat(tgt.getDefaultColumnWidth()).isEqualTo(12);
            assertThat(tgt.getDefaultRowHeight()).isEqualTo((short) 400);
            assertThat(tgt.getNumMergedRegions()).isEqualTo(2);

            CellRangeAddress region0 = tgt.getMergedRegion(0);
            assertThat(region0.getFirstColumn()).isEqualTo(0);
            assertThat(region0.getLastColumn()).isEqualTo(2);
        }
    }
}
