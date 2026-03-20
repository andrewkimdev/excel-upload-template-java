package com.foo.excel.templates.samples.aappcar.config;

import com.foo.excel.service.contract.ExcelImportDefinition;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetadata;
import com.foo.excel.templates.samples.aappcar.service.AAppcarItemImportPrecheck;
import com.foo.excel.templates.samples.aappcar.service.AAppcarItemService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AAppcarItemImportConfig {

  @Bean
  public ExcelImportDefinition<AAppcarItemDto, AAppcarItemMetadata> aappcarImportDefinition(
      AAppcarItemService persistenceHandler,
      AAppcarItemImportPrecheck importPrecheck) {
    return new AAppcarItemImportDefinition(persistenceHandler, importPrecheck, null);
  }
}
