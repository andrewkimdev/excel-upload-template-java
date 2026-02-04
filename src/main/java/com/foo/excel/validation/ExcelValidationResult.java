package com.foo.excel.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelValidationResult {
    private boolean valid;
    private int totalRows;
    private int errorRowCount;
    private int totalErrorCount;
    @Builder.Default
    private List<RowError> rowErrors = new ArrayList<>();
    private String errorFileId;

    public static ExcelValidationResult success(int totalRows) {
        return ExcelValidationResult.builder()
                .valid(true)
                .totalRows(totalRows)
                .errorRowCount(0)
                .totalErrorCount(0)
                .build();
    }

    public static ExcelValidationResult failure(int totalRows, List<RowError> rowErrors) {
        int totalErrorCount = rowErrors.stream()
                .mapToInt(r -> r.getCellErrors().size())
                .sum();
        return ExcelValidationResult.builder()
                .valid(false)
                .totalRows(totalRows)
                .errorRowCount(rowErrors.size())
                .totalErrorCount(totalErrorCount)
                .rowErrors(rowErrors)
                .build();
    }
}
