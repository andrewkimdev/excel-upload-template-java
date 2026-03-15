package com.foo.excel.service.contract;

public record TemplateMergeRegion(
    TemplateMergeScope scope,
    int rowOffset,
    int rowSpan,
    int startColumnIndex,
    int columnSpan,
    boolean repeatOnEveryDataRow) {}
