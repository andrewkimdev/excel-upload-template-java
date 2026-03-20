package com.foo.excel.service.contract;

public record ExcelSheetSpec(
    int sheetIndex,
    int headerRow,
    int dataStartRow,
    String footerMarker,
    String errorColumnName) {}
