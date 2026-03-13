package com.foo.excel.validation;

import java.util.ArrayList;
import java.util.List;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExcelValidationResult {
  public static final String DEFAULT_TRUNCATION_MESSAGE = "검증이 조기 중단되어 일부 오류만 표시됩니다.";

  private boolean valid;
  private int totalRows;
  private int errorRowCount;
  private int totalErrorCount;
  @Builder.Default private List<RowError> rowErrors = new ArrayList<>();
  private String errorFileId;
  private boolean truncated;
  private String truncationMessage;

  public static ExcelValidationResult success(int totalRows) {
    return ExcelValidationResult.builder()
        .valid(true)
        .totalRows(totalRows)
        .errorRowCount(0)
        .totalErrorCount(0)
        .build();
  }

  public static ExcelValidationResult failure(int totalRows, List<RowError> rowErrors) {
    return failure(totalRows, rowErrors, false, null);
  }

  public static ExcelValidationResult truncatedFailure(int totalRows, List<RowError> rowErrors) {
    return failure(totalRows, rowErrors, true, DEFAULT_TRUNCATION_MESSAGE);
  }

  public static ExcelValidationResult failure(
      int totalRows, List<RowError> rowErrors, boolean truncated, String truncationMessage) {
    int totalErrorCount = rowErrors.stream().mapToInt(r -> r.getCellErrors().size()).sum();
    return ExcelValidationResult.builder()
        .valid(false)
        .totalRows(totalRows)
        .errorRowCount(rowErrors.size())
        .totalErrorCount(totalErrorCount)
        .rowErrors(rowErrors)
        .truncated(truncated)
        .truncationMessage(truncationMessage)
        .build();
  }

  public void merge(List<RowError> additionalErrors) {
    RowErrorAccumulator accumulator = new RowErrorAccumulator(rowErrors);
    accumulator.addAll(additionalErrors);
    rowErrors = accumulator.toList();

    if (!rowErrors.isEmpty()) {
      this.valid = false;
      this.errorRowCount = rowErrors.size();
      this.totalErrorCount = rowErrors.stream().mapToInt(r -> r.getCellErrors().size()).sum();
    }
  }

  public void markTruncated(String message) {
    this.truncated = true;
    this.truncationMessage = message;
  }
}
