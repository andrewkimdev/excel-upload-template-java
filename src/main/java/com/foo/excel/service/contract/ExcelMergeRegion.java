package com.foo.excel.service.contract;

public record ExcelMergeRegion(
    ExcelMergeScope scope,
    int rowOffset,
    int rowSpan,
    int startColumnIndex,
    int columnSpan,
    boolean repeatOnEveryDataRow) {}
