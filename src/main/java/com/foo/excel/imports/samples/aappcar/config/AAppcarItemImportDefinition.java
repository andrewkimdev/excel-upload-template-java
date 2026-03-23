package com.foo.excel.imports.samples.aappcar.config;

import com.foo.excel.service.contract.DatabaseUniquenessChecker;
import com.foo.excel.service.contract.ExcelImportDefinition;
import com.foo.excel.service.contract.ImportPrecheck;
import com.foo.excel.service.contract.PersistenceHandler;
import com.foo.excel.imports.ImportTypeNames;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemImportRow;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemImportMetadata;

public class AAppcarItemImportDefinition
    extends ExcelImportDefinition<AAppcarItemImportRow, AAppcarItemImportMetadata> {

  public AAppcarItemImportDefinition(
      PersistenceHandler<AAppcarItemImportRow, AAppcarItemImportMetadata> persistenceHandler,
      ImportPrecheck<AAppcarItemImportMetadata> importPrecheck,
      DatabaseUniquenessChecker<AAppcarItemImportRow, AAppcarItemImportMetadata> dbUniquenessChecker) {
    super(
        ImportTypeNames.AAPPCAR,
        AAppcarItemImportRow.class,
        AAppcarItemImportMetadata.class,
        persistenceHandler,
        importPrecheck,
        dbUniquenessChecker);
  }

  @Override
  public String resolveTempSubdirectory(AAppcarItemImportMetadata metadata) {
    return metadata.getCustomId();
  }
}
