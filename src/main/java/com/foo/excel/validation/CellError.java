package com.foo.excel.validation;

import lombok.Builder;

@Builder
public record CellError(
    int columnIndex,
    ExcelColumnRef columnRef,
    String fieldName,
    String headerName,
    Object rejectedValue,
    String message) {}
