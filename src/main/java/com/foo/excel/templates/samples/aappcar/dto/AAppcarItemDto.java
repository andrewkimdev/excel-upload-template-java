package com.foo.excel.templates.samples.aappcar.dto;

import static com.foo.excel.annotation.HeaderMatchMode.STARTS_WITH;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.ExcelCompositeUnique;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import lombok.Data;

@Data
@ExcelCompositeUnique(
    fields = {"itemName", "specification", "hsCode"},
    message = "물품명 + 규격 + HSK 조합이 중복됩니다")
public class AAppcarItemDto {

  // B열: 순번
  @ExcelColumn(header = "순번", column = "B")
  private Integer sequenceNo;

  // C열: 물품명
  @ExcelColumn(header = "물품명", column = "C")
  @NotBlank(message = "물품명은 필수 입력 항목입니다")
  @Size(max = 100, message = "물품명은 100자 이내로 입력하세요")
  private String itemName;

  // D열: 규격1)
  @ExcelColumn(header = "규격", column = "D", matchMode = STARTS_WITH)
  @Size(max = 200, message = "규격은 200자 이내로 입력하세요")
  private String specification;

  // E열: 모델명1)
  @ExcelColumn(header = "모델명", column = "E", matchMode = STARTS_WITH)
  @Size(max = 100, message = "모델명은 100자 이내로 입력하세요")
  private String modelName;

  // F열: HSK No(F-G 병합, F에서 읽음)
  @ExcelColumn(header = "HSK", column = "F")
  @Pattern(regexp = "^\\d{4}\\.\\d{2}-\\d{4}$", message = "HSK 형식이 올바르지 않습니다 (예: 8481.80-2000)")
  private String hsCode;

  // H열: 관세율
  @ExcelColumn(header = "관세율", column = "H")
  @DecimalMin(value = "0", message = "관세율은 0 이상이어야 합니다")
  @DecimalMax(value = "100", message = "관세율은 100 이하여야 합니다")
  private BigDecimal tariffRate;

  // I열: 단가($)
  @ExcelColumn(header = "단가", column = "I")
  @DecimalMin(value = "0", message = "단가는 0 이상이어야 합니다")
  private BigDecimal unitPrice;

  // J열: 소요량(제조용) - J-K 병합, J에서 읽음
  @ExcelColumn(header = "제조용", column = "J")
  @Min(value = 0, message = "제조용 소요량은 0 이상이어야 합니다")
  private Integer qtyForManufacturing;

  // L열: 소요량(수리용) - L-M 병합, L에서 읽음
  @ExcelColumn(header = "수리용", column = "L")
  @Min(value = 0, message = "수리용 소요량은 0 이상이어야 합니다")
  private Integer qtyForRepair;

  // N열: 연간수입예상금액($)
  @ExcelColumn(header = "연간수입", column = "N")
  @DecimalMin(value = "0", message = "연간수입 예상금액은 0 이상이어야 합니다")
  private BigDecimal annualImportEstimate;

  // O열: 심의결과 - O-P 병합, O에서 읽음(출력 필드, 선택)
  @ExcelColumn(header = "심의결과", column = "O", required = false)
  private String reviewResult;

  // Q열: 연간예상소요량
  @ExcelColumn(header = "연간 예상소요량", column = "Q")
  @Min(value = 0, message = "연간 예상소요량은 0 이상이어야 합니다")
  private Integer annualExpectedQty;
}
