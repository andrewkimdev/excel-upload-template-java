package com.foo.excel.service.pipeline.validation;

import static org.assertj.core.api.Assertions.assertThat;

import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.validation.ExcelValidationResult;
import com.foo.excel.validation.WithinFileUniqueConstraintValidator;
import jakarta.validation.Validation;
import jakarta.validation.Validator;
import jakarta.validation.ValidatorFactory;
import java.math.BigDecimal;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

class ExcelValidationServiceTest {

  private ExcelValidationService validationService;

  @BeforeEach
  void setUp() {
    ValidatorFactory factory = Validation.buildDefaultValidatorFactory();
    Validator validator = factory.getValidator();
    WithinFileUniqueConstraintValidator uniqueValidator = new WithinFileUniqueConstraintValidator();
    validationService = new ExcelValidationService(validator, uniqueValidator);
  }

  @Test
  void validRow_passesWithZeroErrors() {
    AAppcarItemDto dto = createValidDto();
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isTrue();
    assertThat(result.getTotalErrorCount()).isEqualTo(0);
  }

  @Test
  void notBlank_itemName_blank_producesError() {
    AAppcarItemDto dto = createValidDto();
    dto.setItemName(""); // 빈 값
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
    assertThat(result.getRowErrors()).isNotEmpty();
    assertThat(findErrorMessage(result, "itemName")).contains("물품명은 필수 입력 항목입니다");
  }

  @Test
  void size_itemName_over100_producesError() {
    AAppcarItemDto dto = createValidDto();
    dto.setItemName("a".repeat(101));
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
    assertThat(findErrorMessage(result, "itemName")).contains("물품명은 100자 이내로 입력하세요");
  }

  @Test
  void pattern_hsCode_valid_passes() {
    AAppcarItemDto dto = createValidDto();
    dto.setHsCode("1234.56-7890");
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void pattern_hsCode_invalid_fails() {
    AAppcarItemDto dto = createValidDto();
    dto.setHsCode("invalid");
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
    assertThat(findErrorMessage(result, "hsCode")).contains("HSK 형식이 올바르지 않습니다");
  }

  @Test
  void decimalMin_tariffRate_belowZero_producesError() {
    AAppcarItemDto dto = createValidDto();
    dto.setTariffRate(new BigDecimal("-1"));
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
    assertThat(findErrorMessage(result, "tariffRate")).contains("관세율은 0 이상이어야 합니다");
  }

  @Test
  void decimalMax_tariffRate_above100_producesError() {
    AAppcarItemDto dto = createValidDto();
    dto.setTariffRate(new BigDecimal("101"));
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
    assertThat(findErrorMessage(result, "tariffRate")).contains("관세율은 100 이하여야 합니다");
  }

  @Test
  void decimalMin_tariffRate_boundary_zero_passes() {
    AAppcarItemDto dto = createValidDto();
    dto.setTariffRate(BigDecimal.ZERO);
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void decimalMax_tariffRate_boundary_100_passes() {
    AAppcarItemDto dto = createValidDto();
    dto.setTariffRate(new BigDecimal("100"));
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void required_itemName_null_producesError() {
    AAppcarItemDto dto = createValidDto();
    dto.setItemName(null);
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
  }

  @Test
  void errorMessages_matchKoreanText() {
    AAppcarItemDto dto = createValidDto();
    dto.setItemName("");
    dto.setHsCode("bad");
    dto.setTariffRate(new BigDecimal("-5"));
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
    List<String> allMessages =
        result.getRowErrors().stream()
            .flatMap(r -> r.getCellErrors().stream())
            .map(c -> c.message())
            .toList();

    assertThat(allMessages).anyMatch(m -> m.contains("물품명은 필수 입력 항목입니다"));
    assertThat(allMessages).anyMatch(m -> m.contains("HSK 형식이 올바르지 않습니다"));
    assertThat(allMessages).anyMatch(m -> m.contains("관세율은 0 이상이어야 합니다"));
  }

  // ===== 헬퍼 =====

  private AAppcarItemDto createValidDto() {
    AAppcarItemDto dto = new AAppcarItemDto();
    dto.setSequenceNo(1);
    dto.setItemName("테스트 물품");
    dto.setSpecification("규격A");
    dto.setModelName("모델A");
    dto.setHsCode("8481.80-2000");
    dto.setTariffRate(new BigDecimal("8"));
    dto.setUnitPrice(new BigDecimal("100"));
    dto.setQtyForManufacturing(10);
    dto.setQtyForRepair(5);
    dto.setAnnualImportEstimate(new BigDecimal("50000"));
    dto.setReviewResult("통과");
    dto.setAnnualExpectedQty(100);
    return dto;
  }

  private String findErrorMessage(ExcelValidationResult result, String fieldName) {
    return result.getRowErrors().stream()
        .flatMap(r -> r.getCellErrors().stream())
        .filter(c -> c.fieldName().equals(fieldName))
        .map(c -> c.message())
        .findFirst()
        .orElse("");
  }
}
