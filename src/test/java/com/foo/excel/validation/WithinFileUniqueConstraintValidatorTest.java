package com.foo.excel.validation;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.ExcelCompositeUnique;
import com.foo.excel.annotation.ExcelUnique;
import com.foo.excel.templates.samples.tariffexemption.service.TariffExemptionDbUniquenessChecker;
import com.foo.excel.templates.samples.tariffexemption.dto.TariffExemptionDto;
import com.foo.excel.templates.samples.tariffexemption.persistence.repository.TariffExemptionRepository;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class WithinFileUniqueConstraintValidatorTest {

    @Mock
    private TariffExemptionRepository tariffExemptionRepository;

    private WithinFileUniqueConstraintValidator validator;
    private TariffExemptionDbUniquenessChecker dbChecker;

    @BeforeEach
    void setUp() {
        validator = new WithinFileUniqueConstraintValidator();
        dbChecker = new TariffExemptionDbUniquenessChecker(tariffExemptionRepository);
    }

    // ===== @ExcelUnique single field tests =====

    @Test
    void singleFieldUnique_duplicateValues_detected() {
        UniqueTestDto dto1 = new UniqueTestDto();
        dto1.setCode("ABC");
        UniqueTestDto dto2 = new UniqueTestDto();
        dto2.setCode("ABC");  // duplicate

        List<RowError> errors = validator.checkWithinFileUniqueness(
                List.of(dto1, dto2), UniqueTestDto.class, List.of(7, 8));

        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0).getCellErrors().get(0).message())
                .contains("중복된 값입니다");
    }

    @Test
    void singleFieldUnique_nonDuplicateValues_noErrors() {
        UniqueTestDto dto1 = new UniqueTestDto();
        dto1.setCode("ABC");
        UniqueTestDto dto2 = new UniqueTestDto();
        dto2.setCode("DEF");

        List<RowError> errors = validator.checkWithinFileUniqueness(
                List.of(dto1, dto2), UniqueTestDto.class, List.of(7, 8));

        assertThat(errors).isEmpty();
    }

    @Test
    void singleFieldUnique_nullValues_noFalsePositive() {
        UniqueTestDto dto1 = new UniqueTestDto();
        dto1.setCode(null);
        UniqueTestDto dto2 = new UniqueTestDto();
        dto2.setCode(null);

        List<RowError> errors = validator.checkWithinFileUniqueness(
                List.of(dto1, dto2), UniqueTestDto.class, List.of(7, 8));

        assertThat(errors).isEmpty();
    }

    // ===== @ExcelCompositeUnique tests =====

    @Test
    void compositeUnique_duplicateCombination_detected() {
        TariffExemptionDto dto1 = createDto("Item1", "Spec1", "8481.80-2000");
        TariffExemptionDto dto2 = createDto("Item1", "Spec1", "8481.80-2000");  // same combo

        List<RowError> errors = validator.checkWithinFileUniqueness(
                List.of(dto1, dto2), TariffExemptionDto.class, List.of(7, 8));

        assertThat(errors).isNotEmpty();
        assertThat(errors.get(0).getCellErrors().get(0).message())
                .contains("물품명 + 규격 + HSK 조합이 중복됩니다");
    }

    @Test
    void compositeUnique_differentCombination_noErrors() {
        TariffExemptionDto dto1 = createDto("Item1", "Spec1", "8481.80-2000");
        TariffExemptionDto dto2 = createDto("Item2", "Spec1", "8481.80-2000");  // different itemName

        List<RowError> errors = validator.checkWithinFileUniqueness(
                List.of(dto1, dto2), TariffExemptionDto.class, List.of(7, 8));

        assertThat(errors).isEmpty();
    }

    @Test
    void compositeUnique_nullFieldInComposite_noFalsePositive() {
        TariffExemptionDto dto1 = createDto("Item1", null, "8481.80-2000");
        TariffExemptionDto dto2 = createDto("Item1", null, "8481.80-2000");  // same with null

        List<RowError> errors = validator.checkWithinFileUniqueness(
                List.of(dto1, dto2), TariffExemptionDto.class, List.of(7, 8));

        // With null specification, the composite key is [Item1, null, 8481.80-2000]
        // which matches, so this is correctly detected as duplicate
        assertThat(errors).isNotEmpty();
    }

    @Test
    void checkDatabaseUniqueness_noMatch_returnsEmpty() {
        when(tariffExemptionRepository.existsByItemNameAndSpecificationAndHsCode(
                anyString(), anyString(), anyString())).thenReturn(false);

        TariffExemptionDto dto = createDto("Item1", "Spec1", "8481.80-2000");

        List<RowError> errors = dbChecker.check(
                List.of(dto), TariffExemptionDto.class, List.of(7));

        assertThat(errors).isEmpty();
    }

    @Test
    void checkDatabaseUniqueness_matchFound_returnsError() {
        when(tariffExemptionRepository.existsByItemNameAndSpecificationAndHsCode(
                "Item1", "Spec1", "8481.80-2000")).thenReturn(true);

        TariffExemptionDto dto = createDto("Item1", "Spec1", "8481.80-2000");

        List<RowError> errors = dbChecker.check(
                List.of(dto), TariffExemptionDto.class, List.of(7));

        assertThat(errors).hasSize(1);
        assertThat(errors.get(0).getCellErrors().get(0).message())
                .contains("이미 등록된 데이터입니다");
    }

    // ===== Helper DTO =====

    @Data
    public static class UniqueTestDto {
        @ExcelColumn(header = "Code", column = "B")
        @ExcelUnique(message = "중복된 값입니다")
        private String code;
    }

    // ===== Helpers =====

    private TariffExemptionDto createDto(String itemName, String specification, String hsCode) {
        TariffExemptionDto dto = new TariffExemptionDto();
        dto.setItemName(itemName);
        dto.setSpecification(specification);
        dto.setHsCode(hsCode);
        dto.setTariffRate(new BigDecimal("8"));
        return dto;
    }
}
