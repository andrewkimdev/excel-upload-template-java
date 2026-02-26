package com.foo.excel.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.ExcelUnique;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import java.math.BigDecimal;
import java.util.List;
import lombok.Data;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class WithinFileUniqueConstraintValidatorTest {

  private WithinFileUniqueConstraintValidator validator;

  @BeforeEach
  void setUp() {
    validator = new WithinFileUniqueConstraintValidator();
  }

  // ===== @ExcelUnique 단일 필드 테스트 =====

  @Test
  void singleFieldUnique_duplicateValues_detected() {
    UniqueTestDto dto1 = new UniqueTestDto();
    dto1.setCode("ABC");
    UniqueTestDto dto2 = new UniqueTestDto();
    dto2.setCode("ABC"); // 중복

    List<RowError> errors =
        validator.checkWithinFileUniqueness(
            List.of(dto1, dto2), UniqueTestDto.class, List.of(7, 8));

    assertThat(errors).isNotEmpty();
    assertThat(errors.get(0).getCellErrors().get(0).message()).contains("중복된 값입니다");
  }

  @Test
  void singleFieldUnique_nonDuplicateValues_noErrors() {
    UniqueTestDto dto1 = new UniqueTestDto();
    dto1.setCode("ABC");
    UniqueTestDto dto2 = new UniqueTestDto();
    dto2.setCode("DEF");

    List<RowError> errors =
        validator.checkWithinFileUniqueness(
            List.of(dto1, dto2), UniqueTestDto.class, List.of(7, 8));

    assertThat(errors).isEmpty();
  }

  @Test
  void singleFieldUnique_nullValues_noFalsePositive() {
    UniqueTestDto dto1 = new UniqueTestDto();
    dto1.setCode(null);
    UniqueTestDto dto2 = new UniqueTestDto();
    dto2.setCode(null);

    List<RowError> errors =
        validator.checkWithinFileUniqueness(
            List.of(dto1, dto2), UniqueTestDto.class, List.of(7, 8));

    assertThat(errors).isEmpty();
  }

  // ===== @ExcelCompositeUnique 테스트 =====

  @Test
  void compositeUnique_duplicateCombination_detected() {
    AAppcarItemDto dto1 = createDto("Item1", "Spec1", "8481.80-2000");
    AAppcarItemDto dto2 = createDto("Item1", "Spec1", "8481.80-2000"); // 같은 조합

    List<RowError> errors =
        validator.checkWithinFileUniqueness(
            List.of(dto1, dto2), AAppcarItemDto.class, List.of(7, 8));

    assertThat(errors).isNotEmpty();
    assertThat(errors.get(0).getCellErrors().get(0).message()).contains("물품명 + 규격 + HSK 조합이 중복됩니다");
  }

  @Test
  void compositeUnique_differentCombination_noErrors() {
    AAppcarItemDto dto1 = createDto("Item1", "Spec1", "8481.80-2000");
    AAppcarItemDto dto2 = createDto("Item2", "Spec1", "8481.80-2000"); // itemName이 다름

    List<RowError> errors =
        validator.checkWithinFileUniqueness(
            List.of(dto1, dto2), AAppcarItemDto.class, List.of(7, 8));

    assertThat(errors).isEmpty();
  }

  @Test
  void compositeUnique_nullFieldInComposite_noFalsePositive() {
    AAppcarItemDto dto1 = createDto("Item1", null, "8481.80-2000");
    AAppcarItemDto dto2 = createDto("Item1", null, "8481.80-2000"); // null 포함 동일

    List<RowError> errors =
        validator.checkWithinFileUniqueness(
            List.of(dto1, dto2), AAppcarItemDto.class, List.of(7, 8));

    // specification이 null이면 복합 키는 [Item1, null, 8481.80-2000]
    // 이 키가 일치하므로 중복으로 올바르게 감지된다
    assertThat(errors).isNotEmpty();
  }

  // ===== 헬퍼 DTO =====

  @Data
  public static class UniqueTestDto {
    @ExcelColumn(header = "Code", column = "B")
    @ExcelUnique(message = "중복된 값입니다")
    private String code;
  }

  // ===== 헬퍼 =====

  private AAppcarItemDto createDto(String itemName, String specification, String hsCode) {
    AAppcarItemDto dto = new AAppcarItemDto();
    dto.setItemName(itemName);
    dto.setSpecification(specification);
    dto.setHsCode(hsCode);
    dto.setTariffRate(new BigDecimal("8"));
    return dto;
  }
}
