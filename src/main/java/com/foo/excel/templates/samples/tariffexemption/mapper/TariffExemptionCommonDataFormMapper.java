package com.foo.excel.templates.samples.tariffexemption.mapper;

import com.foo.excel.templates.samples.tariffexemption.dto.TariffExemptionCommonData;
import org.springframework.stereotype.Component;

@Component
public class TariffExemptionCommonDataFormMapper {

  public TariffExemptionCommonData toCommonData(
      String comeYear, String comeSequence, String uploadSequence, String equipCode) {
    TariffExemptionCommonData commonData = new TariffExemptionCommonData();
    commonData.setComeYear(comeYear);
    commonData.setComeSequence(comeSequence);
    commonData.setUploadSequence(uploadSequence);
    commonData.setEquipCode(equipCode);
    return commonData;
  }
}
