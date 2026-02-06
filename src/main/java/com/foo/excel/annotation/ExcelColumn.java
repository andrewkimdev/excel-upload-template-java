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
     * In fixed-column mode (column is set): verified against the actual header at that position.
     * In auto-detect mode (column is empty): matched against header row cells.
     * Also used as the display name in error messages.
     */
    String header();

    /**
     * Fixed column position in Excel letter notation: "A", "B", ..., "Z", "AA", "AB", ...
     * When set: parser reads from this position AND verifies the header matches.
     * When empty (default): auto-detect mode scans the header row.
     */
    String column() default "";

    /**
     * Date/DateTime format pattern for LocalDate/LocalDateTime fields.
     */
    String dateFormat() default "yyyy-MM-dd";

    /**
     * Header matching mode. Used for BOTH auto-detect scanning AND
     * fixed-column header verification. Default: CONTAINS.
     */
    HeaderMatchMode matchMode() default HeaderMatchMode.CONTAINS;

    /**
     * Custom error message prefix for validation errors on this column.
     */
    String errorPrefix() default "";

    /**
     * Whether this column is required. Default: true.
     * When true: unresolvable column or header mismatch causes a fail-fast error.
     * When false: unresolvable/mismatched column is skipped (field stays null).
     */
    boolean required() default true;
}
