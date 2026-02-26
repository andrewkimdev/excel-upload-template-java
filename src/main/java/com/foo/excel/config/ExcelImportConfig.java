package com.foo.excel.config;

public interface ExcelImportConfig {

  /**
   * 기본 컬럼 헤더가 위치한 행 번호(1부터 시작).
   *
   * <p>헤더 매칭에 사용하는 행이다.
   */
  default int getHeaderRow() {
    return 1;
  }

  /** 데이터가 시작되는 행 번호(1부터 시작). */
  default int getDataStartRow() {
    return 2;
  }

  /** 읽을 시트 인덱스(0부터 시작). */
  default int getSheetIndex() {
    return 0;
  }

  /**
   * 푸터/메모 구간 시작을 나타내는 마커 문자열. 행의 어느 셀에서든 발견되면
   * 데이터 읽기를 중단한다.
   */
  default String getFooterMarker() {
    return "※";
  }

  /** 오류 리포트 Excel에 추가되는 오류 컬럼명. */
  default String getErrorColumnName() {
    return "_ERRORS";
  }
}
