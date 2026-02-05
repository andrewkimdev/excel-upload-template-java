package com.foo.excel.templates.samples.tariffexemption;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.service.DatabaseUniquenessChecker;
import com.foo.excel.util.ExcelColumnUtil;
import com.foo.excel.validation.CellError;
import com.foo.excel.validation.RowError;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class TariffExemptionDbUniquenessChecker implements DatabaseUniquenessChecker<TariffExemptionDto> {

    private final TariffExemptionRepository repository;

    @Override
    public List<RowError> check(List<TariffExemptionDto> rows, Class<TariffExemptionDto> dtoClass,
                                List<Integer> sourceRowNumbers) {
        List<RowError> errors = new ArrayList<>();

        try {
            Field itemNameField = TariffExemptionDto.class.getDeclaredField("itemName");
            ExcelColumn excelColumn = itemNameField.getAnnotation(ExcelColumn.class);

            for (int i = 0; i < rows.size(); i++) {
                TariffExemptionDto dto = rows.get(i);
                if (dto.getItemName() == null) {
                    continue;
                }

                boolean exists = repository.existsByItemNameAndSpecificationAndHsCode(
                        dto.getItemName(), dto.getSpecification(), dto.getHsCode());

                if (exists) {
                    int currentRowNum = sourceRowNumbers.get(i);
                    int colIndex = excelColumn != null && !excelColumn.column().isEmpty()
                            ? ExcelColumnUtil.letterToIndex(excelColumn.column()) : -1;
                    String colLetter = excelColumn != null && !excelColumn.column().isEmpty()
                            ? excelColumn.column() : "?";

                    CellError cellError = CellError.builder()
                            .fieldName("itemName")
                            .headerName(excelColumn != null ? excelColumn.header() : "itemName")
                            .columnIndex(colIndex)
                            .columnLetter(colLetter)
                            .rejectedValue(dto.getItemName())
                            .message("이미 등록된 데이터입니다 (물품명 + 규격 + HSK 조합)")
                            .build();

                    RowError existing = errors.stream()
                            .filter(r -> r.getRowNumber() == currentRowNum)
                            .findFirst()
                            .orElse(null);

                    if (existing != null) {
                        existing.getCellErrors().add(cellError);
                    } else {
                        errors.add(RowError.builder()
                                .rowNumber(currentRowNum)
                                .cellErrors(new ArrayList<>(List.of(cellError)))
                                .build());
                    }
                }
            }
        } catch (NoSuchFieldException e) {
            log.error("Failed to resolve itemName field for DB uniqueness check", e);
        }

        return errors;
    }
}
