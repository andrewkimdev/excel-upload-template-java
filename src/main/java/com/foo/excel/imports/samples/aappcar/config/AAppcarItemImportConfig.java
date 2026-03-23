package com.foo.excel.imports.samples.aappcar.config;

import com.foo.excel.service.contract.ExcelImportDefinition;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemImportRow;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemImportMetadata;
import com.foo.excel.imports.samples.aappcar.service.AAppcarItemImportPrecheck;
import com.foo.excel.imports.samples.aappcar.service.AAppcarItemPersistenceService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AAppcarItemImportConfig {

  @Bean
  public ExcelImportDefinition<AAppcarItemImportRow, AAppcarItemImportMetadata> aappcarImportDefinition(
      AAppcarItemPersistenceService persistenceHandler,
      AAppcarItemImportPrecheck importPrecheck) {
    return new AAppcarItemImportDefinition(persistenceHandler, importPrecheck, null);
  }
}
