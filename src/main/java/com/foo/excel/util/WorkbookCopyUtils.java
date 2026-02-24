package com.foo.excel.util;

import java.util.HashMap;
import java.util.Map;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.ss.usermodel.DateUtil;
import org.apache.poi.ss.usermodel.FillPatternType;
import org.apache.poi.ss.usermodel.IndexedColors;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.util.CellRangeAddress;

/**
 * Stateless utility for copying workbook content (styles, cell values, sheet metadata) between POI
 * Workbook instances. Used by error report generation.
 */
public final class WorkbookCopyUtils {

  private WorkbookCopyUtils() {
    // Utility class
  }

  /**
   * Builds a mapping from source workbook style indices to cloned styles in the target workbook.
   * Index 0 (default style) is modified in-place since every new workbook already has one.
   */
  public static Map<Integer, CellStyle> buildStyleMapping(Workbook source, Workbook target) {
    var styleMap = new HashMap<Integer, CellStyle>();

    for (int i = 0; i < source.getNumCellStyles(); i++) {
      CellStyle srcStyle = source.getCellStyleAt(i);
      CellStyle tgtStyle;
      if (i == 0) {
        tgtStyle = target.getCellStyleAt(0);
      } else {
        tgtStyle = target.createCellStyle();
      }

      tgtStyle.cloneStyleFrom(srcStyle);
      styleMap.put(i, tgtStyle);
    }

    return styleMap;
  }

  /**
   * Returns a CellStyle that clones the base style and adds ROSE fill. Results are cached by base
   * style index to prevent style explosion (POI 64K limit).
   *
   * @param cache mutated by this method; caller should pass a method-local HashMap for thread
   *     safety
   */
  public static CellStyle getOrCreateErrorStyle(
      Workbook wb, CellStyle base, Map<Integer, CellStyle> cache) {
    int key = base.getIndex();
    return cache.computeIfAbsent(
        key,
        k -> {
          CellStyle errorStyle = wb.createCellStyle();
          errorStyle.cloneStyleFrom(base);
          errorStyle.setFillForegroundColor(IndexedColors.ROSE.getIndex());
          errorStyle.setFillPattern(FillPatternType.SOLID_FOREGROUND);
          return errorStyle;
        });
  }

  /**
   * Copies the value from a source cell to a target cell. Handles all CellType values including
   * date-formatted numerics and ERROR cells.
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
      default -> {
        /* _NONE or unknown — leave target empty */
      }
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

  /** Copies all merged regions from source sheet to target sheet. */
  public static void copyMergedRegions(Sheet source, Sheet target) {
    for (CellRangeAddress region : source.getMergedRegions()) {
      target.addMergedRegion(region);
    }
  }
}
