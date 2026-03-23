package com.foo.excel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.ExcelCompositeUnique;
import com.foo.excel.annotation.ExcelUnique;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemImportRow;
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

  @Test
  void duplicateValuesAcrossTwoUniqueFields_mergeIntoSingleRowError() {
    MultiUniqueTestDto dto1 = new MultiUniqueTestDto();
    dto1.setCode("ABC");
    dto1.setName("Item");

    MultiUniqueTestDto dto2 = new MultiUniqueTestDto();
    dto2.setCode("ABC");
    dto2.setName("Item");

    List<RowError> errors =
        validator.checkWithinFileUniqueness(
            List.of(dto1, dto2), MultiUniqueTestDto.class, List.of(7, 8));

    assertThat(errors).hasSize(1);
    assertThat(errors.get(0).getRowNumber()).isEqualTo(8);
    assertThat(errors.get(0).getCellErrors())
        .hasSize(2)
        .anySatisfy(error -> assertThat(error.fieldName()).isEqualTo("code"))
        .anySatisfy(error -> assertThat(error.fieldName()).isEqualTo("name"));
  }

  // ===== @ExcelCompositeUnique 테스트 =====

  @Test
  void compositeUnique_duplicateCombination_detected() {
    AAppcarItemImportRow dto1 = createDto("Item1", "Spec1", "8481.80-2000");
    AAppcarItemImportRow dto2 = createDto("Item1", "Spec1", "8481.80-2000"); // 같은 조합

    List<RowError> errors =
        validator.checkWithinFileUniqueness(
            List.of(dto1, dto2), AAppcarItemImportRow.class, List.of(7, 8));

    assertThat(errors).isNotEmpty();
    assertThat(errors.get(0).getCellErrors().get(0).message()).contains("물품명 + 규격 + HSK 조합이 중복됩니다");
  }

  @Test
  void compositeUnique_differentCombination_noErrors() {
    AAppcarItemImportRow dto1 = createDto("Item1", "Spec1", "8481.80-2000");
    AAppcarItemImportRow dto2 = createDto("Item2", "Spec1", "8481.80-2000"); // goodsDes가 다름

    List<RowError> errors =
        validator.checkWithinFileUniqueness(
            List.of(dto1, dto2), AAppcarItemImportRow.class, List.of(7, 8));

    assertThat(errors).isEmpty();
  }

  @Test
  void compositeUnique_nullFieldInComposite_noFalsePositive() {
    AAppcarItemImportRow dto1 = createDto("Item1", null, "8481.80-2000");
    AAppcarItemImportRow dto2 = createDto("Item1", null, "8481.80-2000"); // null 포함 동일

    List<RowError> errors =
        validator.checkWithinFileUniqueness(
            List.of(dto1, dto2), AAppcarItemImportRow.class, List.of(7, 8));

    // spec이 null이면 복합 키는 [Item1, null, 8481.80-2000]
    // 이 키가 일치하므로 중복으로 올바르게 감지된다
    assertThat(errors).isNotEmpty();
  }

  @Test
  void compositeUnique_missingDeclaredField_throwsIllegalStateException() {
    BrokenCompositeTestDto dto1 = new BrokenCompositeTestDto();
    dto1.setField1("A");
    BrokenCompositeTestDto dto2 = new BrokenCompositeTestDto();
    dto2.setField1("B");

    assertThatThrownBy(
            () ->
                validator.checkWithinFileUniqueness(
                    List.of(dto1, dto2), BrokenCompositeTestDto.class, List.of(7, 8)))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("복합 유니크 검증 설정이 올바르지 않습니다")
        .hasMessageContaining(BrokenCompositeTestDto.class.getName())
        .hasMessageContaining("missingField");
  }

  // ===== 헬퍼 DTO =====

  @Data
  public static class UniqueTestDto {
    @ExcelColumn(label = "Code", column = "B")
    @ExcelUnique(message = "중복된 값입니다")
    private String code;
  }

  @Data
  public static class MultiUniqueTestDto {
    @ExcelColumn(label = "Code", column = "B")
    @ExcelUnique(message = "코드가 중복되었습니다")
    private String code;

    @ExcelColumn(label = "Name", column = "C")
    @ExcelUnique(message = "이름이 중복되었습니다")
    private String name;
  }

  @Data
  @ExcelCompositeUnique(fields = {"field1", "missingField"}, message = "복합 중복입니다")
  public static class BrokenCompositeTestDto {
    @ExcelColumn(label = "Field1", column = "B")
    private String field1;
  }

  // ===== 헬퍼 =====

  private AAppcarItemImportRow createDto(String goodsDes, String spec, String hsno) {
    AAppcarItemImportRow dto = new AAppcarItemImportRow();
    dto.setGoodsDes(goodsDes);
    dto.setSpec(spec);
    dto.setHsno(hsno);
    dto.setTaxRate(new BigDecimal("8"));
    return dto;
  }
}
