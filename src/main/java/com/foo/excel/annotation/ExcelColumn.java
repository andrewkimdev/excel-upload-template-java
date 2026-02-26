package com.foo.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {

  /**
   * 기대하는 헤더 텍스트. 고정 컬럼 모드(column 값이 설정됨)에서는 해당 위치의 실제 헤더와
   * 검증한다. 자동 탐지 모드(column 값이 비어 있음)에서는 헤더 행 셀과 매칭한다. 또한
   * 오류 메시지의 표시 이름으로도 사용된다.
   */
  String header();

  /**
   * Excel 문자 표기 기반 고정 컬럼 위치: "A", "B", ..., "Z", "AA", "AB", ... 설정 시:
   * 파서는 이 위치에서 값을 읽고 헤더 일치 여부도 검증한다. 비어 있으면(기본값):
   * 자동 탐지 모드로 헤더 행을 스캔한다.
   */
  String column() default "";

  /** LocalDate/LocalDateTime 필드용 날짜/일시 포맷 패턴. */
  String dateFormat() default "yyyy-MM-dd";

  /**
   * 헤더 매칭 모드. 자동 탐지 스캔과 고정 컬럼 헤더 검증 모두에 사용된다.
   * 기본값: CONTAINS.
   */
  HeaderMatchMode matchMode() default HeaderMatchMode.CONTAINS;

  /** 이 컬럼의 검증 오류에 사용할 사용자 정의 메시지 접두사. */
  String errorPrefix() default "";

  /**
   * 이 컬럼이 필수인지 여부. 기본값: true. true이면 해석할 수 없는 컬럼이나 헤더
   * 불일치 시 즉시 실패 오류를 발생시킨다. false이면 해석 불가/불일치 컬럼을 건너뛰며(필드는
   * null로 유지됨).
   */
  boolean required() default true;
}
