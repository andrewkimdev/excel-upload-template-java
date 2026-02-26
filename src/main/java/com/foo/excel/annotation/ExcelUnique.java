package com.foo.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelUnique {

  /** 유일성이 위반되었을 때의 오류 메시지. */
  String message() default "중복된 값입니다";

  /** 기존 데이터베이스 레코드와 비교 검사할지 여부. */
  boolean checkDatabase() default true;

  /** 업로드된 파일 내부 중복을 검사할지 여부. */
  boolean checkWithinFile() default true;
}
