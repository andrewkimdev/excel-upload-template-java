package com.foo.excel.service.contract;

import java.util.List;

/**
 * 업로드 메타데이터가 기존 데이터와 충돌할 때 반환하는 충돌 정보를 표현한다.
 *
 * @param type 충돌 유형
 * @param description 충돌 설명
 * @param fields 충돌이 발생한 필드 목록
 */
public record MetadataConflict(String type, String description, List<FieldValue> fields) {

  /**
   * 메타데이터 충돌을 구성하는 개별 필드 값을 표현한다.
   *
   * @param fieldName 필드명
   * @param label 사용자 표시용 필드 라벨
   * @param value 충돌한 값
   */
  public record FieldValue(String fieldName, String label, String value) {}
}
