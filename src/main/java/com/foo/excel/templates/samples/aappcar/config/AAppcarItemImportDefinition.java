package com.foo.excel.templates.samples.aappcar.config;

import com.foo.excel.service.contract.DatabaseUniquenessChecker;
import com.foo.excel.service.contract.ExcelImportDefinition;
import com.foo.excel.service.contract.ImportPrecheck;
import com.foo.excel.service.contract.PersistenceHandler;
import com.foo.excel.templates.ImportTypes;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetadata;

public class AAppcarItemImportDefinition
    extends ExcelImportDefinition<AAppcarItemDto, AAppcarItemMetadata> {

  public AAppcarItemImportDefinition(
      PersistenceHandler<AAppcarItemDto, AAppcarItemMetadata> persistenceHandler,
      ImportPrecheck<AAppcarItemMetadata> importPrecheck,
      DatabaseUniquenessChecker<AAppcarItemDto, AAppcarItemMetadata> dbUniquenessChecker) {
    super(
        ImportTypes.AAPPCAR,
        AAppcarItemDto.class,
        AAppcarItemMetadata.class,
        persistenceHandler,
        importPrecheck,
        dbUniquenessChecker);
  }

  @Override
  public String resolveTempSubdirectory(AAppcarItemMetadata metadata) {
    return metadata.getCustomId();
  }
}
