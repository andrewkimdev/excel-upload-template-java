package com.foo.excel.templates.samples.aappcar.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.foo.excel.service.contract.CommonData;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class AAppcarItemCommonData implements CommonData {

  private static final String DEFAULT_CREATED_BY = "user01";
  private static final String DEFAULT_APPROVED_YN = "N";

  @NotBlank(message = "comeYear는 필수입니다")
  private String comeYear;

  @NotBlank(message = "comeOrder는 필수입니다")
  private String comeOrder;

  @NotBlank(message = "uploadSeq는 필수입니다")
  private String uploadSeq;

  @NotBlank(message = "equipCode는 필수입니다")
  private String equipCode;

  private String companyId;

  private String customId;

  @JsonIgnore
  public String getCreatedBy() {
    return DEFAULT_CREATED_BY;
  }

  @JsonIgnore
  public String getApprovedYn() {
    return DEFAULT_APPROVED_YN;
  }
}
