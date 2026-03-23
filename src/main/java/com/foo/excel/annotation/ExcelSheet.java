package com.foo.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelSheet {

  /** 선언 시트 번호(1부터 시작). 1이면 첫 번째 시트이다. */
  int sheetIndex() default 1;

  int headerRow() default 1;

  int dataStartRow() default 2;

  String footerMarker() default "※";

  String errorColumnName() default "_ERRORS";
}
