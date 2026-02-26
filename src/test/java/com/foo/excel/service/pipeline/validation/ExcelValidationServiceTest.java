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
  void notBlank_goodsDes_blank_producesError() {
    AAppcarItemDto dto = createValidDto();
    dto.setGoodsDes(""); // 빈 값
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
    assertThat(result.getRowErrors()).isNotEmpty();
    assertThat(findErrorMessage(result, "goodsDes")).contains("물품명은 필수 입력 항목입니다");
  }

  @Test
  void size_goodsDes_over100_producesError() {
    AAppcarItemDto dto = createValidDto();
    dto.setGoodsDes("a".repeat(101));
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
    assertThat(findErrorMessage(result, "goodsDes")).contains("물품명은 100자 이내로 입력하세요");
  }

  @Test
  void pattern_hsno_valid_passes() {
    AAppcarItemDto dto = createValidDto();
    dto.setHsno("1234.56-7890");
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void pattern_hsno_invalid_fails() {
    AAppcarItemDto dto = createValidDto();
    dto.setHsno("invalid");
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
    assertThat(findErrorMessage(result, "hsno")).contains("HSK 형식이 올바르지 않습니다");
  }

  @Test
  void decimalMin_taxRate_belowZero_producesError() {
    AAppcarItemDto dto = createValidDto();
    dto.setTaxRate(new BigDecimal("-1"));
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
    assertThat(findErrorMessage(result, "taxRate")).contains("관세율은 0 이상이어야 합니다");
  }

  @Test
  void decimalMax_taxRate_above100_producesError() {
    AAppcarItemDto dto = createValidDto();
    dto.setTaxRate(new BigDecimal("101"));
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
    assertThat(findErrorMessage(result, "taxRate")).contains("관세율은 100 이하여야 합니다");
  }

  @Test
  void decimalMin_taxRate_boundary_zero_passes() {
    AAppcarItemDto dto = createValidDto();
    dto.setTaxRate(BigDecimal.ZERO);
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void decimalMax_taxRate_boundary_100_passes() {
    AAppcarItemDto dto = createValidDto();
    dto.setTaxRate(new BigDecimal("100"));
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isTrue();
  }

  @Test
  void required_goodsDes_null_producesError() {
    AAppcarItemDto dto = createValidDto();
    dto.setGoodsDes(null);
    List<AAppcarItemDto> rows = List.of(dto);

    ExcelValidationResult result =
        validationService.validate(rows, AAppcarItemDto.class, List.of(7));

    assertThat(result.isValid()).isFalse();
  }

  @Test
  void errorMessages_matchKoreanText() {
    AAppcarItemDto dto = createValidDto();
    dto.setGoodsDes("");
    dto.setHsno("bad");
    dto.setTaxRate(new BigDecimal("-5"));
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
    dto.setGoodsSeqNo(1);
    dto.setGoodsDes("테스트 물품");
    dto.setSpec("규격A");
    dto.setModelDes("모델A");
    dto.setHsno("8481.80-2000");
    dto.setTaxRate(new BigDecimal("8"));
    dto.setUnitprice(new BigDecimal("100"));
    dto.setProdQty(10);
    dto.setRepairQty(5);
    dto.setImportAmt(new BigDecimal("50000"));
    dto.setApprovalYn("통과");
    dto.setImportQty(100);
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
