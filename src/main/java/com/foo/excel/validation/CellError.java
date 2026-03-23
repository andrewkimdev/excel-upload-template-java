package com.foo.excel.validation;

import lombok.Builder;

/**
 * 개별 셀 단위의 검증 오류 정보를 표현한다.
 *
 * @param columnIndex 오류가 발생한 컬럼 인덱스
 * @param columnRef 엑셀 컬럼 참조값
 * @param fieldName 매핑된 필드명
 * @param headerName 헤더명
 * @param rejectedValue 거부된 값
 * @param message 오류 메시지
 */
@Builder
public record CellError(
    int columnIndex,
    ExcelColumnRef columnRef,
    String fieldName,
    String headerName,
    Object rejectedValue,
    String message) {}
