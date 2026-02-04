package com.foo.excel.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CellError {
    private int columnIndex;
    private String columnLetter;
    private String fieldName;
    private String headerName;
    private Object rejectedValue;
    private String message;
}
