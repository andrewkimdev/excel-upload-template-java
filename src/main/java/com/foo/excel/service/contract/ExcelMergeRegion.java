package com.foo.excel.service.contract;

/**
 * 오류 리포트 등에서 사용할 병합 셀 영역 정보를 표현한다.
 *
 * @param scope 병합이 적용되는 범위
 * @param rowOffset 기준 행으로부터의 시작 오프셋
 * @param rowSpan 병합할 행 수
 * @param startColumnIndex 시작 컬럼 인덱스
 * @param columnSpan 병합할 컬럼 수
 * @param repeatOnEveryDataRow 각 데이터 행마다 반복 적용 여부
 */
public record ExcelMergeRegion(
    ExcelMergeScope scope,
    int rowOffset,
    int rowSpan,
    int startColumnIndex,
    int columnSpan,
    boolean repeatOnEveryDataRow) {}
