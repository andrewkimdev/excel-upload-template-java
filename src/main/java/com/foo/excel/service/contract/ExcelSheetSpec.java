package com.foo.excel.service.contract;

/**
 * 엑셀 시트 파싱에 필요한 시트 구조 정보를 정의한다.
 *
 * @param sheetIndex 대상 시트 인덱스
 * @param headerRow 헤더 시작 행 번호
 * @param dataStartRow 데이터 시작 행 번호
 * @param footerMarker 데이터 종료를 판단하는 푸터 마커
 * @param errorColumnName 오류 리포트에 사용할 컬럼명
 */
public record ExcelSheetSpec(
    int sheetIndex,
    int headerRow,
    int dataStartRow,
    String footerMarker,
    String errorColumnName) {}
