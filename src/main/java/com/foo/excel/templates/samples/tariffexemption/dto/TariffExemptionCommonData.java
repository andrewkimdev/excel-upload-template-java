package com.foo.excel.templates.samples.tariffexemption.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.foo.excel.service.contract.CommonData;
import jakarta.validation.constraints.NotBlank;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

@Getter
@Setter
@NoArgsConstructor
public class TariffExemptionCommonData implements CommonData {

    private static final String DEFAULT_CREATED_BY = "user01";
    private static final String DEFAULT_APPROVED_YN = "N";

    @NotBlank(message = "comeYear는 필수입니다")
    private String comeYear;

    @NotBlank(message = "comeSequence는 필수입니다")
    private String comeSequence;

    @NotBlank(message = "uploadSequence는 필수입니다")
    private String uploadSequence;

    @NotBlank(message = "equipCode는 필수입니다")
    private String equipCode;

    @JsonIgnore
    public String getCreatedBy() {
        return DEFAULT_CREATED_BY;
    }

    @JsonIgnore
    public String getApprovedYn() {
        return DEFAULT_APPROVED_YN;
    }
}
