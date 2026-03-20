package com.foo.excel.templates.samples.aappcar.service;

import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetadata;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarEquipId;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItemId;
import org.springframework.stereotype.Component;

@Component
public class AAppcarItemKeyFactory {

  public AAppcarItemId buildItemId(AAppcarItemMetadata metadata, int rowNumber) {
    return new AAppcarItemId(
        metadata.getCompanyId(),
        metadata.getCustomId(),
        metadata.getComeYear(),
        metadata.getComeOrder(),
        metadata.getUploadSeq(),
        metadata.getEquipCode(),
        rowNumber);
  }

  public AAppcarEquipId buildEquipId(AAppcarItemMetadata metadata) {
    return new AAppcarEquipId(
        metadata.getCompanyId(),
        metadata.getCustomId(),
        metadata.getComeYear(),
        Integer.valueOf(metadata.getComeOrder()),
        Integer.valueOf(metadata.getUploadSeq()),
        metadata.getEquipCode());
  }
}
