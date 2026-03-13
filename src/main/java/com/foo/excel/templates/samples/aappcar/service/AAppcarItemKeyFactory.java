package com.foo.excel.templates.samples.aappcar.service;

import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetaData;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarEquipId;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItemId;
import org.springframework.stereotype.Component;

@Component
public class AAppcarItemKeyFactory {

  public AAppcarItemId buildItemId(AAppcarItemMetaData metaData, int rowNumber) {
    return new AAppcarItemId(
        metaData.getCompanyId(),
        metaData.getCustomId(),
        metaData.getComeYear(),
        metaData.getComeOrder(),
        metaData.getUploadSeq(),
        metaData.getEquipCode(),
        rowNumber);
  }

  public AAppcarEquipId buildEquipId(AAppcarItemMetaData metaData) {
    return new AAppcarEquipId(
        metaData.getCompanyId(),
        metaData.getCustomId(),
        metaData.getComeYear(),
        Integer.valueOf(metaData.getComeOrder()),
        Integer.valueOf(metaData.getUploadSeq()),
        metaData.getEquipCode());
  }
}
