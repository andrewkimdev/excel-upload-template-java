package com.foo.excel.annotation;

public enum HeaderMatchMode {

    /**
     * Header must match exactly (case-insensitive, trimmed).
     */
    EXACT,

    /**
     * Header contains this text (useful for "규격1)" matching "규격").
     */
    CONTAINS,

    /**
     * Header starts with this text.
     */
    STARTS_WITH,

    /**
     * Header matches this regex pattern.
     */
    REGEX
}
