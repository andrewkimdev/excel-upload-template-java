package com.foo.excel.validation;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.ExcelCompositeUnique;
import com.foo.excel.annotation.ExcelUnique;
import com.foo.excel.util.ExcelColumnUtil;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

@Slf4j
@Component
public class WithinFileUniqueConstraintValidator {

  public <T> List<RowError> checkWithinFileUniqueness(
      List<T> rows, Class<T> dtoClass, List<Integer> sourceRowNumbers) {
    List<RowError> errors = new ArrayList<>();

    checkSingleFieldUniqueness(rows, dtoClass, sourceRowNumbers, errors);
    checkCompositeUniqueness(rows, dtoClass, sourceRowNumbers, errors);

    return errors;
  }

  private <T> void checkSingleFieldUniqueness(
      List<T> rows, Class<T> dtoClass, List<Integer> sourceRowNumbers, List<RowError> errors) {

    for (Field field : dtoClass.getDeclaredFields()) {
      ExcelUnique uniqueAnnotation = field.getAnnotation(ExcelUnique.class);
      if (uniqueAnnotation == null || !uniqueAnnotation.checkWithinFile()) {
        continue;
      }

      field.setAccessible(true);
      ExcelColumn excelColumn = field.getAnnotation(ExcelColumn.class);

      // Map: value -> first row number where it appeared
      Map<Object, Integer> seenValues = new HashMap<>();

      for (int i = 0; i < rows.size(); i++) {
        T row = rows.get(i);
        Object value;
        try {
          value = field.get(row);
        } catch (IllegalAccessException e) {
          continue;
        }

        if (value == null) {
          continue;
        }

        int currentRowNum = sourceRowNumbers.get(i);

        if (seenValues.containsKey(value)) {
          int firstRowNum = seenValues.get(value);
          CellError cellError =
              CellError.builder()
                  .fieldName(field.getName())
                  .headerName(excelColumn != null ? excelColumn.header() : field.getName())
                  .columnIndex(excelColumn != null ? resolveColumnIndex(excelColumn) : -1)
                  .columnLetter(excelColumn != null ? resolveColumnLetter(excelColumn) : "?")
                  .rejectedValue(value)
                  .message(uniqueAnnotation.message() + " (행 " + firstRowNum + "과(와) 중복)")
                  .build();

          addErrorToRow(errors, currentRowNum, cellError);
        } else {
          seenValues.put(value, currentRowNum);
        }
      }
    }
  }

  private <T> void checkCompositeUniqueness(
      List<T> rows, Class<T> dtoClass, List<Integer> sourceRowNumbers, List<RowError> errors) {

    ExcelCompositeUnique[] compositeAnnotations =
        dtoClass.getAnnotationsByType(ExcelCompositeUnique.class);
    if (compositeAnnotations.length == 0) {
      return;
    }

    for (ExcelCompositeUnique composite : compositeAnnotations) {
      String[] fieldNames = composite.fields();
      List<Field> fields = new ArrayList<>();

      for (String fieldName : fieldNames) {
        try {
          Field f = dtoClass.getDeclaredField(fieldName);
          f.setAccessible(true);
          fields.add(f);
        } catch (NoSuchFieldException e) {
          log.warn("Composite unique field '{}' not found in {}", fieldName, dtoClass.getName());
        }
      }

      // Map: composite key -> first row number
      Map<List<Object>, Integer> seenKeys = new HashMap<>();

      for (int i = 0; i < rows.size(); i++) {
        T row = rows.get(i);
        List<Object> compositeKey = new ArrayList<>();

        for (Field f : fields) {
          try {
            compositeKey.add(f.get(row));
          } catch (IllegalAccessException e) {
            compositeKey.add(null);
          }
        }

        int currentRowNum = sourceRowNumbers.get(i);

        if (seenKeys.containsKey(compositeKey)) {
          int firstRowNum = seenKeys.get(compositeKey);

          // Add error to the first field in the composite
          Field firstField = fields.get(0);
          ExcelColumn excelColumn = firstField.getAnnotation(ExcelColumn.class);

          CellError cellError =
              CellError.builder()
                  .fieldName(firstField.getName())
                  .headerName(excelColumn != null ? excelColumn.header() : firstField.getName())
                  .columnIndex(excelColumn != null ? resolveColumnIndex(excelColumn) : -1)
                  .columnLetter(excelColumn != null ? resolveColumnLetter(excelColumn) : "?")
                  .rejectedValue(compositeKey.toString())
                  .message(composite.message() + " (행 " + firstRowNum + "과(와) 중복)")
                  .build();

          addErrorToRow(errors, currentRowNum, cellError);
        } else {
          seenKeys.put(compositeKey, currentRowNum);
        }
      }
    }
  }

  private void addErrorToRow(List<RowError> errors, int rowNumber, CellError cellError) {
    RowError existing =
        errors.stream().filter(r -> r.getRowNumber() == rowNumber).findFirst().orElse(null);

    if (existing != null) {
      existing.getCellErrors().add(cellError);
    } else {
      RowError rowError =
          RowError.builder()
              .rowNumber(rowNumber)
              .cellErrors(new ArrayList<>(List.of(cellError)))
              .build();
      errors.add(rowError);
    }
  }

  private int resolveColumnIndex(ExcelColumn annotation) {
    return annotation.column().isEmpty() ? -1 : ExcelColumnUtil.letterToIndex(annotation.column());
  }

  private String resolveColumnLetter(ExcelColumn annotation) {
    return annotation.column().isEmpty() ? "?" : annotation.column();
  }
}
