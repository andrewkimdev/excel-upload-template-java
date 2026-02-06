package com.foo.excel.templates.samples.tariffexemption;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.ExcelCompositeUnique;
import static com.foo.excel.annotation.HeaderMatchMode.STARTS_WITH;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.math.BigDecimal;

@Data
@ExcelCompositeUnique(
    fields = {"itemName", "specification", "hsCode"},
    message = "물품명 + 규격 + HSK 조합이 중복됩니다"
)
public class TariffExemptionDto {

    // Column B: 순번
    @ExcelColumn(header = "순번", column = "B")
    private Integer sequenceNo;

    // Column C: 물품명
    @ExcelColumn(header = "물품명", column = "C")
    @NotBlank(message = "물품명은 필수 입력 항목입니다")
    @Size(max = 100, message = "물품명은 100자 이내로 입력하세요")
    private String itemName;

    // Column D: 규격1)
    @ExcelColumn(header = "규격", column = "D", matchMode = STARTS_WITH)
    @Size(max = 200, message = "규격은 200자 이내로 입력하세요")
    private String specification;

    // Column E: 모델명1)
    @ExcelColumn(header = "모델명", column = "E", matchMode = STARTS_WITH)
    @Size(max = 100, message = "모델명은 100자 이내로 입력하세요")
    private String modelName;

    // Column F: HSK No (merged F-G, read from F)
    @ExcelColumn(header = "HSK", column = "F")
    @Pattern(regexp = "^\\d{4}\\.\\d{2}-\\d{4}$", message = "HSK 형식이 올바르지 않습니다 (예: 8481.80-2000)")
    private String hsCode;

    // Column H: 관세율
    @ExcelColumn(header = "관세율", column = "H")
    @DecimalMin(value = "0", message = "관세율은 0 이상이어야 합니다")
    @DecimalMax(value = "100", message = "관세율은 100 이하여야 합니다")
    private BigDecimal tariffRate;

    // Column I: 단가($)
    @ExcelColumn(header = "단가", column = "I")
    @DecimalMin(value = "0", message = "단가는 0 이상이어야 합니다")
    private BigDecimal unitPrice;

    // Column J: 소요량(제조용) - merged J-K, read from J
    @ExcelColumn(header = "제조용", column = "J")
    @Min(value = 0, message = "제조용 소요량은 0 이상이어야 합니다")
    private Integer qtyForManufacturing;

    // Column L: 소요량(수리용) - merged L-M, read from L
    @ExcelColumn(header = "수리용", column = "L")
    @Min(value = 0, message = "수리용 소요량은 0 이상이어야 합니다")
    private Integer qtyForRepair;

    // Column N: 연간수입예상금액($)
    @ExcelColumn(header = "연간수입", column = "N")
    @DecimalMin(value = "0", message = "연간수입 예상금액은 0 이상이어야 합니다")
    private BigDecimal annualImportEstimate;

    // Column O: 심의결과 - merged O-P, read from O (output field, optional)
    @ExcelColumn(header = "심의결과", column = "O", required = false)
    private String reviewResult;

    // Column Q: 연간예상소요량
    @ExcelColumn(header = "연간 예상소요량", column = "Q")
    @Min(value = 0, message = "연간 예상소요량은 0 이상이어야 합니다")
    private Integer annualExpectedQty;
}
