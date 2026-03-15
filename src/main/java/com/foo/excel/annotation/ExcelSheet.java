package com.foo.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelSheet {

  int sheetIndex() default 0;

  int headerRow() default 1;

  int dataStartRow() default 2;

  String footerMarker() default "※";

  String errorColumnName() default "_ERRORS";
}
