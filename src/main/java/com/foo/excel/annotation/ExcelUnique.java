package com.foo.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelUnique {

    /**
     * Error message when uniqueness is violated.
     */
    String message() default "중복된 값입니다";

    /**
     * Whether to check against existing database records.
     */
    boolean checkDatabase() default true;

    /**
     * Whether to check for duplicates within the uploaded file.
     */
    boolean checkWithinFile() default true;
}
