package com.foo.excel.templates.samples.aappcar.mapper;

import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemCommonData;
import org.springframework.stereotype.Component;

@Component
public class AAppcarItemCommonDataFormMapper {

  public AAppcarItemCommonData toCommonData(
      String comeYear, String comeOrder, String uploadSeq, String equipCode) {
    AAppcarItemCommonData commonData = new AAppcarItemCommonData();
    commonData.setComeYear(comeYear);
    commonData.setComeOrder(comeOrder);
    commonData.setUploadSeq(uploadSeq);
    commonData.setEquipCode(equipCode);
    return commonData;
  }
}
