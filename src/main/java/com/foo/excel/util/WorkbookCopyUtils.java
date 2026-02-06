package com.foo.excel.util;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.ss.util.CellRangeAddress;

import java.util.HashMap;
import java.util.Map;

/**
 * Stateless utility for copying workbook content (styles, cell values, sheet metadata)
 * between POI Workbook instances. Used by both error report generation and XLS→XLSX conversion.
 */
public final class WorkbookCopyUtils {

    private WorkbookCopyUtils() {
        // Utility class
    }

    /**
     * Builds a mapping from source workbook style indices to cloned styles in the target workbook.
     * Index 0 (default style) is modified in-place since every new workbook already has one.
     * Handles both same-format (XSSF→XSSF) via cloneStyleFrom() and cross-format (HSSF→XSSF)
     * via manual property copying.
     */
    public static Map<Integer, CellStyle> buildStyleMapping(Workbook source, Workbook target) {
        var styleMap = new HashMap<Integer, CellStyle>();
        boolean crossFormat = isCrossFormat(source, target);

        // For cross-format, build font mapping first
        Map<Integer, Font> fontMap = crossFormat ? buildFontMapping(source, target) : null;

        for (int i = 0; i < source.getNumCellStyles(); i++) {
            CellStyle srcStyle = source.getCellStyleAt(i);
            CellStyle tgtStyle;
            if (i == 0) {
                tgtStyle = target.getCellStyleAt(0);
            } else {
                tgtStyle = target.createCellStyle();
            }

            if (crossFormat) {
                copyStyleProperties(srcStyle, tgtStyle, source, target, fontMap);
            } else {
                tgtStyle.cloneStyleFrom(srcStyle);
            }
            styleMap.put(i, tgtStyle);
        }

        return styleMap;
    }

    /**
     * Returns a CellStyle that clones the base style and adds ROSE fill.
     * Results are cached by base style index to prevent style explosion (POI 64K limit).
     *
     * @param cache mutated by this method; caller should pass a method-local HashMap for thread safety
     */
    public static CellStyle getOrCreateErrorStyle(Workbook wb, CellStyle base,
            Map<Integer, CellStyle> cache) {
        int key = base.getIndex();
        return cache.computeIfAbsent(key, k -> {
            CellStyle errorStyle = wb.createCellStyle();
            errorStyle.cloneStyleFrom(base);
            errorStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
            errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
            return errorStyle;
        });
    }

    /**
     * Copies the value from a source cell to a target cell.
     * Handles all CellType values including date-formatted numerics and ERROR cells.
     */
    public static void copyCellValue(Cell source, Cell target) {
        switch (source.getCellType()) {
            case STRING -> target.setCellValue(source.getStringCellValue());
            case NUMERIC -> {
                if (DateUtil.isCellDateFormatted(source)) {
                    target.setCellValue(source.getDateCellValue());
                } else {
                    target.setCellValue(source.getNumericCellValue());
                }
            }
            case BOOLEAN -> target.setCellValue(source.getBooleanCellValue());
            case FORMULA -> target.setCellFormula(source.getCellFormula());
            case ERROR -> target.setCellErrorValue(source.getErrorCellValue());
            case BLANK -> target.setBlank();
            default -> { /* _NONE or unknown — leave target empty */ }
        }
    }

    /**
     * Copies column widths, hidden state, and default dimensions from source to target sheet.
     *
     * @param maxCol exclusive upper bound of columns to copy
     */
    public static void copyColumnWidths(Sheet source, Sheet target, int maxCol) {
        target.setDefaultColumnWidth(source.getDefaultColumnWidth());
        target.setDefaultRowHeight(source.getDefaultRowHeight());

        for (int col = 0; col < maxCol; col++) {
            target.setColumnWidth(col, source.getColumnWidth(col));
            target.setColumnHidden(col, source.isColumnHidden(col));
        }
    }

    /**
     * Copies all merged regions from source sheet to target sheet.
     */
    public static void copyMergedRegions(Sheet source, Sheet target) {
        for (CellRangeAddress region : source.getMergedRegions()) {
            target.addMergedRegion(region);
        }
    }

    // ===== Private helpers =====

    private static boolean isCrossFormat(Workbook source, Workbook target) {
        boolean srcHssf = source instanceof HSSFWorkbook;
        boolean tgtHssf = target instanceof HSSFWorkbook;
        return srcHssf != tgtHssf;
    }

    private static Map<Integer, Font> buildFontMapping(Workbook source, Workbook target) {
        var fontMap = new HashMap<Integer, Font>();

        // Collect unique font indices actually referenced by styles
        // (HSSF font indices may not be contiguous — index 4 is reserved)
        var fontIndices = new java.util.LinkedHashSet<Integer>();
        for (int i = 0; i < source.getNumCellStyles(); i++) {
            fontIndices.add(source.getCellStyleAt(i).getFontIndex());
        }

        for (int fontIdx : fontIndices) {
            Font srcFont = source.getFontAt(fontIdx);
            Font tgtFont;
            if (fontIdx == 0) {
                tgtFont = target.getFontAt(0);
            } else {
                tgtFont = target.createFont();
            }
            tgtFont.setBold(srcFont.getBold());
            tgtFont.setItalic(srcFont.getItalic());
            tgtFont.setFontName(srcFont.getFontName());
            tgtFont.setFontHeightInPoints(srcFont.getFontHeightInPoints());
            tgtFont.setColor(srcFont.getColor());
            tgtFont.setStrikeout(srcFont.getStrikeout());
            tgtFont.setTypeOffset(srcFont.getTypeOffset());
            tgtFont.setUnderline(srcFont.getUnderline());
            fontMap.put(fontIdx, tgtFont);
        }
        return fontMap;
    }

    private static void copyStyleProperties(CellStyle src, CellStyle tgt,
            Workbook srcWb, Workbook tgtWb, Map<Integer, Font> fontMap) {
        // Alignment
        tgt.setAlignment(src.getAlignment());
        tgt.setVerticalAlignment(src.getVerticalAlignment());
        tgt.setWrapText(src.getWrapText());
        tgt.setRotation(src.getRotation());
        tgt.setIndention(src.getIndention());

        // Borders
        tgt.setBorderTop(src.getBorderTop());
        tgt.setBorderBottom(src.getBorderBottom());
        tgt.setBorderLeft(src.getBorderLeft());
        tgt.setBorderRight(src.getBorderRight());
        tgt.setTopBorderColor(src.getTopBorderColor());
        tgt.setBottomBorderColor(src.getBottomBorderColor());
        tgt.setLeftBorderColor(src.getLeftBorderColor());
        tgt.setRightBorderColor(src.getRightBorderColor());

        // Fill
        tgt.setFillPattern(src.getFillPattern());
        tgt.setFillForegroundColor(src.getFillForegroundColor());
        tgt.setFillBackgroundColor(src.getFillBackgroundColor());

        // Data format (by format string, not index — indices differ between HSSF and XSSF)
        String formatString = src.getDataFormatString();
        if (formatString != null && !"General".equals(formatString)) {
            tgt.setDataFormat(tgtWb.getCreationHelper().createDataFormat().getFormat(formatString));
        }

        // Font
        if (fontMap != null) {
            Font mappedFont = fontMap.get(src.getFontIndex());
            if (mappedFont != null) {
                tgt.setFont(mappedFont);
            }
        }

        // Protection
        tgt.setLocked(src.getLocked());
        tgt.setHidden(src.getHidden());
    }
}
