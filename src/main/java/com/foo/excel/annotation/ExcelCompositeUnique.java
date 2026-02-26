package com.foo.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ExcelCompositeUniques.class)
public @interface ExcelCompositeUnique {

  /** 함께 유일 제약을 구성하는 필드명 목록. */
  String[] fields();

  /** 복합 유일성이 위반되었을 때의 오류 메시지. */
  String message() default "중복된 조합입니다";
}
