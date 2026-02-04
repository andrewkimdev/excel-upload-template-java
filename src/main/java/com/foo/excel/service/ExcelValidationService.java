package com.foo.excel.service;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.util.ExcelColumnUtil;
import com.foo.excel.validation.*;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.lang.reflect.Field;
import java.util.*;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelValidationService {

    private final Validator validator;
    private final UniqueConstraintValidator uniqueConstraintValidator;

    public <T> ExcelValidationResult validate(List<T> rows, Class<T> dtoClass, int dataStartRow) {
        List<RowError> allErrors = new ArrayList<>();

        // Pass 1: JSR-380 validation
        for (int i = 0; i < rows.size(); i++) {
            T row = rows.get(i);
            int rowNumber = dataStartRow + i;

            Set<ConstraintViolation<T>> violations = validator.validate(row);
            if (!violations.isEmpty()) {
                List<CellError> cellErrors = new ArrayList<>();

                for (ConstraintViolation<T> violation : violations) {
                    String fieldName = violation.getPropertyPath().toString();
                    CellError cellError = mapViolationToCellError(fieldName, violation, dtoClass);
                    cellErrors.add(cellError);
                }

                allErrors.add(RowError.builder()
                        .rowNumber(rowNumber)
                        .cellErrors(cellErrors)
                        .build());
            }
        }

        // Pass 2: Within-file uniqueness
        List<RowError> uniqueErrors = uniqueConstraintValidator.checkWithinFileUniqueness(
                rows, dtoClass, dataStartRow);
        mergeErrors(allErrors, uniqueErrors);

        // Pass 3: Database uniqueness (placeholder)
        List<RowError> dbErrors = uniqueConstraintValidator.checkDatabaseUniqueness(
                rows, dtoClass, dataStartRow);
        mergeErrors(allErrors, dbErrors);

        if (allErrors.isEmpty()) {
            return ExcelValidationResult.success(rows.size());
        }

        return ExcelValidationResult.failure(rows.size(), allErrors);
    }

    private <T> CellError mapViolationToCellError(String fieldName,
            ConstraintViolation<T> violation, Class<T> dtoClass) {

        ExcelColumn excelColumn = findExcelColumn(fieldName, dtoClass);

        int columnIndex = -1;
        String columnLetter = "?";
        String headerName = fieldName;

        if (excelColumn != null) {
            headerName = excelColumn.header();
            if (!excelColumn.column().isEmpty()) {
                columnIndex = ExcelColumnUtil.letterToIndex(excelColumn.column());
                columnLetter = excelColumn.column();
            } else if (excelColumn.index() >= 0) {
                columnIndex = excelColumn.index();
                columnLetter = ExcelColumnUtil.indexToLetter(excelColumn.index());
            }
        }

        String message = violation.getMessage();
        if (excelColumn != null && !excelColumn.errorPrefix().isEmpty()) {
            message = excelColumn.errorPrefix() + ": " + message;
        }

        return CellError.builder()
                .columnIndex(columnIndex)
                .columnLetter(columnLetter)
                .fieldName(fieldName)
                .headerName(headerName)
                .rejectedValue(violation.getInvalidValue())
                .message(message)
                .build();
    }

    private ExcelColumn findExcelColumn(String fieldName, Class<?> dtoClass) {
        try {
            Field field = dtoClass.getDeclaredField(fieldName);
            return field.getAnnotation(ExcelColumn.class);
        } catch (NoSuchFieldException e) {
            return null;
        }
    }

    private void mergeErrors(List<RowError> target, List<RowError> source) {
        for (RowError srcError : source) {
            RowError existing = target.stream()
                    .filter(r -> r.getRowNumber() == srcError.getRowNumber())
                    .findFirst()
                    .orElse(null);

            if (existing != null) {
                existing.getCellErrors().addAll(srcError.getCellErrors());
            } else {
                target.add(srcError);
            }
        }
    }
}
