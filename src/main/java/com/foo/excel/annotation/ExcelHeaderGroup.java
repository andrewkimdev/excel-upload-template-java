package com.foo.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * 엑셀 멀티 헤더에서 여러 컬럼을 하나의 상위 그룹 헤더로 묶을 때 사용하는 애너테이션이다.
 *
 * <p>현재 설계에서는 그룹에 속한 모든 필드에 반복 선언하지 않고, 그룹의 기준이 되는 첫 번째
 * 필드(anchor field)에만 선언한다. 런타임 해석기는 {@link #fields()}에 선언된 필드 목록을
 * 기준으로 상위 헤더 병합 영역을 계산한다.
 *
 * <p>사용 규칙:
 *
 * <p>1. 이 애너테이션은 그룹의 첫 번째 필드에만 선언해야 한다.
 *
 * <p>2. {@link #fields()}의 첫 번째 원소는 반드시 애너테이션이 선언된 anchor 필드 자신이어야 한다.
 *
 * <p>3. {@link #fields()}는 실제 엑셀 컬럼 순서와 동일하게, 인접한 필드만 순서대로 나열해야 한다.
 * 중간 컬럼을 건너뛰거나 순서를 바꾸면 안 된다.
 *
 * <p>4. 같은 그룹에 포함되는 모든 필드는 {@link ExcelColumn#headerRowStart()}와
 * {@link ExcelColumn#headerRowCount()} 값이 서로 동일해야 한다.
 *
 * <p>5. 하나의 필드는 둘 이상의 헤더 그룹에 동시에 포함될 수 없다.
 *
 * <p>주의: {@link #fields()}는 필드명을 문자열로 선언하므로, IDE Rename 리팩토링 시 참조가 자동으로
 * 추적되지 않을 수 있다. 필드명 변경 시 그룹 선언도 함께 점검해야 한다.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.FIELD)
public @interface ExcelHeaderGroup {

  /** 병합된 상위 헤더 셀에 표시할 그룹 라벨이다. */
  String label();

  /**
   * 같은 그룹에 포함할 필드 이름 목록이다.
   *
   * <p>첫 번째 원소는 반드시 anchor 필드 자신이어야 하며, 이후 원소도 실제 컬럼 배치 순서대로
   * 인접 필드를 선언해야 한다.
   */
  String[] fields();
}
