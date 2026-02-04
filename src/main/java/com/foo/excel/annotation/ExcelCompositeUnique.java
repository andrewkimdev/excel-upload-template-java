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

    /**
     * Field names that together form a unique constraint.
     */
    String[] fields();

    /**
     * Error message when composite uniqueness is violated.
     */
    String message() default "중복된 조합입니다";
}
