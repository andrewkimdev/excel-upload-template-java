package com.foo.excel.service.contract;

public record TemplateSheetMetadata(
    int sheetIndex,
    int headerRow,
    int dataStartRow,
    String footerMarker,
    String errorColumnName) {}
