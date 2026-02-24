package com.foo.excel.validation;

import lombok.Builder;

@Builder
public record CellError(
    int columnIndex,
    String columnLetter,
    String fieldName,
    String headerName,
    Object rejectedValue,
    String message) {}
