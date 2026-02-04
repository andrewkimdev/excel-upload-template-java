package com.foo.excel.validation;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.ExcelCompositeUnique;
import com.foo.excel.annotation.ExcelUnique;
import com.foo.excel.dto.TariffExemptionDto;
import com.foo.excel.repository.TariffExemptionRepository;
import com.foo.excel.util.ExcelColumnUtil;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.*;

@Slf4j
@Component
@RequiredArgsConstructor
public class UniqueConstraintValidator {

    private final TariffExemptionRepository tariffExemptionRepository;

    public <T> List<RowError> checkWithinFileUniqueness(List<T> rows, Class<T> dtoClass, int dataStartRow) {
        List<RowError> errors = new ArrayList<>();

        checkSingleFieldUniqueness(rows, dtoClass, dataStartRow, errors);
        checkCompositeUniqueness(rows, dtoClass, dataStartRow, errors);

        return errors;
    }

    private <T> void checkSingleFieldUniqueness(List<T> rows, Class<T> dtoClass,
            int dataStartRow, List<RowError> errors) {

        for (Field field : dtoClass.getDeclaredFields()) {
            ExcelUnique uniqueAnnotation = field.getAnnotation(ExcelUnique.class);
            if (uniqueAnnotation == null || !uniqueAnnotation.checkWithinFile()) {
                continue;
            }

            field.setAccessible(true);
            ExcelColumn excelColumn = field.getAnnotation(ExcelColumn.class);

            // Map: value -> first row index where it appeared
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

                int currentRowNum = dataStartRow + i;

                if (seenValues.containsKey(value)) {
                    int firstRowNum = seenValues.get(value);
                    CellError cellError = CellError.builder()
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

    private <T> void checkCompositeUniqueness(List<T> rows, Class<T> dtoClass,
            int dataStartRow, List<RowError> errors) {

        ExcelCompositeUnique[] compositeAnnotations = dtoClass.getAnnotationsByType(ExcelCompositeUnique.class);
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

            // Map: composite key string -> first row number
            Map<String, Integer> seenKeys = new HashMap<>();

            for (int i = 0; i < rows.size(); i++) {
                T row = rows.get(i);
                StringBuilder keyBuilder = new StringBuilder();

                for (Field f : fields) {
                    try {
                        Object val = f.get(row);
                        keyBuilder.append(val != null ? val.toString() : "NULL").append("|");
                    } catch (IllegalAccessException e) {
                        keyBuilder.append("ERROR|");
                    }
                }

                String compositeKey = keyBuilder.toString();
                int currentRowNum = dataStartRow + i;

                if (seenKeys.containsKey(compositeKey)) {
                    int firstRowNum = seenKeys.get(compositeKey);

                    // Add error to the first field in the composite
                    Field firstField = fields.get(0);
                    ExcelColumn excelColumn = firstField.getAnnotation(ExcelColumn.class);

                    CellError cellError = CellError.builder()
                            .fieldName(firstField.getName())
                            .headerName(excelColumn != null ? excelColumn.header() : firstField.getName())
                            .columnIndex(excelColumn != null ? resolveColumnIndex(excelColumn) : -1)
                            .columnLetter(excelColumn != null ? resolveColumnLetter(excelColumn) : "?")
                            .rejectedValue(compositeKey)
                            .message(composite.message() + " (행 " + firstRowNum + "과(와) 중복)")
                            .build();

                    addErrorToRow(errors, currentRowNum, cellError);
                } else {
                    seenKeys.put(compositeKey, currentRowNum);
                }
            }
        }
    }

    @SuppressWarnings("unchecked")
    public <T> List<RowError> checkDatabaseUniqueness(List<T> rows, Class<T> dtoClass, int dataStartRow) {
        if (dtoClass == TariffExemptionDto.class) {
            return checkTariffExemptionDbUniqueness((List<TariffExemptionDto>) rows, dataStartRow);
        }
        return Collections.emptyList();
    }

    private List<RowError> checkTariffExemptionDbUniqueness(List<TariffExemptionDto> rows, int dataStartRow) {
        List<RowError> errors = new ArrayList<>();

        try {
            Field itemNameField = TariffExemptionDto.class.getDeclaredField("itemName");
            ExcelColumn excelColumn = itemNameField.getAnnotation(ExcelColumn.class);

            for (int i = 0; i < rows.size(); i++) {
                TariffExemptionDto dto = rows.get(i);
                if (dto.getItemName() == null) {
                    continue;
                }

                boolean exists = tariffExemptionRepository.existsByItemNameAndSpecificationAndHsCode(
                        dto.getItemName(), dto.getSpecification(), dto.getHsCode());

                if (exists) {
                    int currentRowNum = dataStartRow + i;
                    CellError cellError = CellError.builder()
                            .fieldName("itemName")
                            .headerName(excelColumn != null ? excelColumn.header() : "itemName")
                            .columnIndex(excelColumn != null ? resolveColumnIndex(excelColumn) : -1)
                            .columnLetter(excelColumn != null ? resolveColumnLetter(excelColumn) : "?")
                            .rejectedValue(dto.getItemName())
                            .message("이미 등록된 데이터입니다 (물품명 + 규격 + HSK 조합)")
                            .build();
                    addErrorToRow(errors, currentRowNum, cellError);
                }
            }
        } catch (NoSuchFieldException e) {
            log.error("Failed to resolve itemName field for DB uniqueness check", e);
        }

        return errors;
    }

    private void addErrorToRow(List<RowError> errors, int rowNumber, CellError cellError) {
        RowError existing = errors.stream()
                .filter(r -> r.getRowNumber() == rowNumber)
                .findFirst()
                .orElse(null);

        if (existing != null) {
            existing.getCellErrors().add(cellError);
        } else {
            RowError rowError = RowError.builder()
                    .rowNumber(rowNumber)
                    .cellErrors(new ArrayList<>(List.of(cellError)))
                    .build();
            errors.add(rowError);
        }
    }

    private int resolveColumnIndex(ExcelColumn annotation) {
        if (!annotation.column().isEmpty()) {
            return ExcelColumnUtil.letterToIndex(annotation.column());
        }
        return annotation.index();
    }

    private String resolveColumnLetter(ExcelColumn annotation) {
        if (!annotation.column().isEmpty()) {
            return annotation.column();
        }
        if (annotation.index() >= 0) {
            return ExcelColumnUtil.indexToLetter(annotation.index());
        }
        return "?";
    }
}
