package com.foo.excel.templates.samples.aappcar.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.foo.excel.service.contract.CommonData;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import java.math.BigDecimal;
import java.time.LocalDate;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AAppcarItemCommonData implements CommonData {

  private static final String DEFAULT_APPROVED_YN = "N";

  @NotBlank(message = "comeYear는 필수입니다")
  private String comeYear;

  @NotBlank(message = "comeOrder는 필수입니다")
  private String comeOrder;

  @NotBlank(message = "uploadSeq는 필수입니다")
  private String uploadSeq;

  @NotBlank(message = "equipCode는 필수입니다")
  private String equipCode;

  @NotBlank(message = "equipMean은 필수입니다")
  @Size(max = 70, message = "equipMean은 70자 이내여야 합니다")
  private String equipMean;

  @NotBlank(message = "hsno는 필수입니다")
  @Size(max = 12, message = "hsno는 12자 이내여야 합니다")
  private String hsno;

  @NotBlank(message = "spec은 필수입니다")
  @Size(max = 70, message = "spec은 70자 이내여야 합니다")
  private String spec;

  @NotNull(message = "taxRate는 필수입니다")
  @DecimalMin(value = "0", message = "taxRate는 0 이상이어야 합니다")
  private BigDecimal taxRate;

  @Size(max = 100, message = "filePath는 100자 이내여야 합니다")
  private String filePath;

  @Size(max = 1, message = "approvalYn은 1자 이내여야 합니다")
  private String approvalYn;

  private LocalDate approvalDate;

  private String companyId;

  private String customId;

  @JsonIgnore
  public String getApprovedYn() {
    if (approvalYn == null || approvalYn.isBlank()) {
      return DEFAULT_APPROVED_YN;
    }
    return approvalYn.trim().toUpperCase();
  }

  @JsonIgnore
  public boolean isApprovalYnYes() {
    String normalized = getApprovedYn();
    return "Y".equals(normalized);
  }
}
