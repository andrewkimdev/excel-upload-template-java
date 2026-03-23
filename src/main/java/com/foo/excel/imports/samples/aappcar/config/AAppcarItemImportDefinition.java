package com.foo.excel.imports.samples.aappcar.config;

import com.foo.excel.service.contract.DatabaseUniquenessChecker;
import com.foo.excel.service.contract.ExcelImportDefinition;
import com.foo.excel.service.contract.ImportPrecheck;
import com.foo.excel.service.contract.PersistenceHandler;
import com.foo.excel.imports.ImportTypeNames;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemRow;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemImportMetadata;

public class AAppcarItemImportDefinition
    extends ExcelImportDefinition<AAppcarItemRow, AAppcarItemImportMetadata> {

  public AAppcarItemImportDefinition(
      PersistenceHandler<AAppcarItemRow, AAppcarItemImportMetadata> persistenceHandler,
      ImportPrecheck<AAppcarItemImportMetadata> importPrecheck,
      DatabaseUniquenessChecker<AAppcarItemRow, AAppcarItemImportMetadata> dbUniquenessChecker) {
    super(
        ImportTypeNames.AAPPCAR,
        AAppcarItemRow.class,
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
