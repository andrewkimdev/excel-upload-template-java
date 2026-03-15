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
  String label();

  /**
   * Excel 문자 표기 기반 고정 컬럼 위치: "A", "B", ..., "Z", "AA", "AB", ... 설정 시:
   * 파서는 이 위치에서 값을 읽고 헤더 일치 여부도 검증한다. 비어 있으면(기본값):
   * 자동 탐지 모드로 헤더 행을 스캔한다.
   */
  String column() default "";

  /**
   * 필드가 가로로 소유하는 컬럼 수.
   *
   * <p>예: 병합된 `F:G` 셀에서 `F`가 값을 소유하면 `column = "F"`, `columnSpan = 2`로 표현한다.
   * 템플릿 병합 메타데이터 해석과 오류 리포트 병합 복원에 사용된다.
   */
  int columnSpan() default 1;

  /** LocalDate/LocalDateTime 필드용 날짜/일시 포맷 패턴. */
  String dateFormat() default "yyyy-MM-dd";

  /**
   * 헤더 매칭 모드. 자동 탐지 스캔과 고정 컬럼 헤더 검증 모두에 사용된다.
   * 기본값: CONTAINS.
   */
  HeaderMatchMode matchMode() default HeaderMatchMode.CONTAINS;

  /**
   * 헤더 비교 시 공백류(스페이스, 탭, 줄바꿈 등)를 무시할지 여부.
   *
   * <p>true이면 실제 Excel 헤더와 애너테이션 기대값 모두에서 공백류를 제거한 뒤
   * {@link #matchMode()}를 적용한다.
   */
  boolean ignoreHeaderWhitespace() default false;

  /**
   * 멀티 행 헤더가 시작하는 행 번호(1부터 시작, 포함).
   *
   * <p>기본값(-1)이면 DTO의 {@link com.foo.excel.annotation.ExcelSheet#headerRow()}를 사용한다.
   * {@link #headerRowCount()}와 함께 설정해야 한다.
   */
  int headerRowStart() default -1;

  /**
   * 멀티 행 헤더가 차지하는 행 수.
   *
   * <p>기본값(-1)이면 DTO의 {@link com.foo.excel.annotation.ExcelSheet#headerRow()}를 사용한다.
   * {@link #headerRowStart()}와 함께 설정해야 한다.
   */
  int headerRowCount() default -1;

  /**
   * 멀티 행 헤더 검증에 사용할 기대 헤더 경로.
   *
   * <p>예: {"소요량", "제조용"}. 비어 있으면 최종 세그먼트(leaf)와 {@link #label()}를 비교한다.
   */
  String[] headerLabels() default {};

  /** 이 컬럼의 검증 오류에 사용할 사용자 정의 메시지 접두사. */
  String errorPrefix() default "";

  /**
   * 이 컬럼이 필수인지 여부. 기본값: true. true이면 해석할 수 없는 컬럼이나 헤더
   * 불일치 시 즉시 실패 오류를 발생시킨다. false이면 해석 불가/불일치 컬럼을 건너뛰며(필드는
   * null로 유지됨).
   */
  boolean required() default true;
}
