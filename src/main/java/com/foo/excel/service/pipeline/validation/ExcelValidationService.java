package com.foo.excel.service.pipeline.validation;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.util.ExcelColumnUtil;
import com.foo.excel.validation.CellError;
import com.foo.excel.validation.ExcelColumnRef;
import com.foo.excel.validation.ExcelValidationResult;
import com.foo.excel.validation.RowError;
import com.foo.excel.validation.RowErrorAccumulator;
import com.foo.excel.validation.WithinFileUniqueConstraintValidator;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelValidationService {

  private final Validator validator;
  private final WithinFileUniqueConstraintValidator withinFileUniqueConstraintValidator;

  public <T> ExcelValidationResult validate(
      List<T> rows, Class<T> dtoClass, List<Integer> sourceRowNumbers, int maxErrorRows) {
    List<RowError> allErrors = new ArrayList<>();
    boolean truncated = false;

    // 1차: JSR-380 검증
    for (int i = 0; i < rows.size(); i++) {
      T row = rows.get(i);
      int rowNumber = sourceRowNumbers.get(i);

      Set<ConstraintViolation<T>> violations = validator.validate(row);
      if (!violations.isEmpty()) {
        List<CellError> cellErrors = new ArrayList<>();

        for (ConstraintViolation<T> violation : violations) {
          String fieldName = violation.getPropertyPath().toString();
          CellError cellError = mapViolationToCellError(fieldName, violation, dtoClass);
          cellErrors.add(cellError);
        }

        allErrors.add(RowError.builder().rowNumber(rowNumber).cellErrors(cellErrors).build());
        if (hasReachedErrorLimit(allErrors.size(), maxErrorRows)) {
          truncated = true;
          break;
        }
      }
    }

    if (!hasReachedErrorLimit(allErrors.size(), maxErrorRows)) {
      // 2차: 파일 내 유일성 검증
      List<RowError> uniqueErrors =
          withinFileUniqueConstraintValidator.checkWithinFileUniqueness(
              rows, dtoClass, sourceRowNumbers, remainingErrorRowBudget(maxErrorRows, allErrors.size()));
      mergeErrors(allErrors, uniqueErrors);
      if (hasReachedErrorLimit(allErrors.size(), maxErrorRows) && !uniqueErrors.isEmpty()) {
        truncated = true;
      }
    }

    if (allErrors.isEmpty()) {
      return ExcelValidationResult.success(rows.size());
    }

    if (truncated) {
      return ExcelValidationResult.truncatedFailure(rows.size(), allErrors);
    }
    return ExcelValidationResult.failure(rows.size(), allErrors);
  }

  private int remainingErrorRowBudget(int maxErrorRows, int currentErrorRows) {
    if (!isErrorLimitEnabled(maxErrorRows)) {
      return Integer.MAX_VALUE;
    }
    return Math.max(0, maxErrorRows - currentErrorRows);
  }

  private boolean hasReachedErrorLimit(int errorRows, int maxErrorRows) {
    return isErrorLimitEnabled(maxErrorRows) && errorRows >= maxErrorRows;
  }

  private boolean isErrorLimitEnabled(int maxErrorRows) {
    return maxErrorRows > 0 && maxErrorRows != Integer.MAX_VALUE;
  }

  private <T> CellError mapViolationToCellError(
      String fieldName, ConstraintViolation<T> violation, Class<T> dtoClass) {

    ExcelColumn excelColumn = findExcelColumn(fieldName, dtoClass);

    int columnIndex = -1;
    ExcelColumnRef columnRef = ExcelColumnRef.unknown();
    String headerName = fieldName;

    if (excelColumn != null) {
      headerName = excelColumn.label();
      if (!excelColumn.column().isEmpty()) {
        columnIndex = ExcelColumnUtil.letterToIndex(excelColumn.column());
        columnRef = ExcelColumnRef.ofLetter(excelColumn.column());
      }
    }

    String message = violation.getMessage();
    if (excelColumn != null && !excelColumn.errorPrefix().isEmpty()) {
      message = excelColumn.errorPrefix() + ": " + message;
    }

    return CellError.builder()
        .columnIndex(columnIndex)
        .columnRef(columnRef)
        .fieldName(fieldName)
        .headerName(headerName)
        .rejectedValue(violation.getInvalidValue())
        .message(message)
        .build();
  }

  private ExcelColumn findExcelColumn(String fieldName, Class<?> dtoClass) {
    Class<?> current = dtoClass;
    while (current != null && current != Object.class) {
      try {
        Field field = current.getDeclaredField(fieldName);
        return field.getAnnotation(ExcelColumn.class);
      } catch (NoSuchFieldException e) {
        current = current.getSuperclass();
      }
    }
    return null;
  }

  private void mergeErrors(List<RowError> target, List<RowError> source) {
    RowErrorAccumulator accumulator = new RowErrorAccumulator(target);
    accumulator.addAll(source);
    target.clear();
    target.addAll(accumulator.toList());
  }
}
