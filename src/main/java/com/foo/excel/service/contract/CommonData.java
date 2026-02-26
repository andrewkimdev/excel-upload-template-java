package com.foo.excel.service.contract;

/** 템플릿별 공통 데이터(commonData) 계약 인터페이스. */
public interface CommonData {

  /**
   * 업로드 임시 경로 분리를 위해 사용하는 customId를 반환한다.
   *
   * <p>템플릿별 CommonData 구현체는 유효한 비어 있지 않은 값을 제공해야 한다.
   */
  String getCustomId();
}
