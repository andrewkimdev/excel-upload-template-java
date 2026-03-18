package com.foo.excel.templates.samples.aappcar.config;

import com.foo.excel.service.contract.DatabaseUniquenessChecker;
import com.foo.excel.service.contract.PersistenceHandler;
import com.foo.excel.service.contract.TemplateDefinition;
import com.foo.excel.service.contract.UploadPrecheck;
import com.foo.excel.templates.TemplateTypes;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetaData;

public class AAppcarItemTemplateDefinition
    extends TemplateDefinition<AAppcarItemDto, AAppcarItemMetaData> {

  public AAppcarItemTemplateDefinition(
      PersistenceHandler<AAppcarItemDto, AAppcarItemMetaData> persistenceHandler,
      UploadPrecheck<AAppcarItemMetaData> uploadPrecheck,
      DatabaseUniquenessChecker<AAppcarItemDto, AAppcarItemMetaData> dbUniquenessChecker) {
    super(
        TemplateTypes.AAPPCAR,
        AAppcarItemDto.class,
        AAppcarItemMetaData.class,
        persistenceHandler,
        uploadPrecheck,
        dbUniquenessChecker);
  }

  @Override
  public String resolveTempSubdirectory(AAppcarItemMetaData metaData) {
    return metaData.getCustomId();
  }
}
