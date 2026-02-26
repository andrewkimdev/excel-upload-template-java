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
 * POI 워크북 인스턴스 간 워크북 내용(스타일, 셀 값, 시트 메타데이터)을 복사하는 무상태 유틸리티
 * 오류 리포트 생성에 사용된다.
 */
public final class WorkbookCopyUtils {

  private WorkbookCopyUtils() {
    // 유틸리티 클래스
  }

  /**
   * 원본 워크북 스타일 인덱스를 대상 워크북의 복제 스타일로 매핑하는 맵을 만든다.
   * 인덱스 0(기본 스타일)은 모든 새 워크북에 이미 존재하므로 해당 객체를 그대로 수정한다.
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
   * 기본 스타일을 복제하고 ROSE 채우기를 추가한 CellStyle을 반환한다. 결과는 기본
   * 스타일 인덱스 기준으로 캐시해 스타일 폭증(POI 64K 제한)을 방지한다.
   *
   * @param cache 이 메서드에서 변경됨; 호출자는 스레드 안전성을 위해 메서드 로컬 HashMap을
   *    전달해야 한다
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
   * 원본 셀 값을 대상 셀로 복사한다. 날짜 형식 숫자와 ERROR 셀을 포함한
   * 모든 CellType 값을 처리한다.
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
        /* _NONE 또는 알 수 없는 타입 - 대상 셀은 비워 둠 */
      }
    }
  }

  /**
   * 원본 시트의 컬럼 너비, 숨김 상태, 기본 크기를 대상 시트로 복사한다.
   *
   * @param maxCol 복사할 컬럼의 배타적 상한
   */
  public static void copyColumnWidths(Sheet source, Sheet target, int maxCol) {
    target.setDefaultColumnWidth(source.getDefaultColumnWidth());
    target.setDefaultRowHeight(source.getDefaultRowHeight());

    for (int col = 0; col < maxCol; col++) {
      target.setColumnWidth(col, source.getColumnWidth(col));
      target.setColumnHidden(col, source.isColumnHidden(col));
    }
  }

  /** 원본 시트의 모든 병합 영역을 대상 시트로 복사한다. */
  public static void copyMergedRegions(Sheet source, Sheet target) {
    for (CellRangeAddress region : source.getMergedRegions()) {
      target.addMergedRegion(region);
    }
  }
}
