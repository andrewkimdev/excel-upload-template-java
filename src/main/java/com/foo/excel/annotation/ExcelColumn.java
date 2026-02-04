package com.foo.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {

    /**
     * Expected header text.
     * Matched against the header row using the specified matchMode.
     */
    String header();

    /**
     * Column letter (Excel notation): "A", "B", ..., "Z", "AA", "AB", ...
     * Takes precedence over index() if both are specified.
     * Empty string means "auto-detect from header".
     */
    String column() default "";

    /**
     * Column index (0-based): 0=A, 1=B, 2=C, ...
     * Used if column() is not specified.
     * -1 means "auto-detect from header".
     */
    int index() default -1;

    /**
     * Date/DateTime format pattern.
     */
    String dateFormat() default "yyyy-MM-dd";

    /**
     * Header matching mode.
     */
    HeaderMatchMode matchMode() default HeaderMatchMode.CONTAINS;

    /**
     * Custom error message prefix for validation errors on this column.
     */
    String errorPrefix() default "";
}
