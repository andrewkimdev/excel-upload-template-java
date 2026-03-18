package com.foo.excel.templates.samples.aappcar.config;

import com.foo.excel.service.contract.TemplateDefinition;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetaData;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.templates.samples.aappcar.service.AAppcarItemService;
import com.foo.excel.templates.samples.aappcar.service.AAppcarItemUploadPrecheck;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AAppcarItemTemplateConfig {

  @Bean
  public TemplateDefinition<AAppcarItemDto, AAppcarItemMetaData> tariffExemptionTemplate(
      AAppcarItemService persistenceHandler,
      AAppcarItemUploadPrecheck uploadPrecheck) {
    return new AAppcarItemTemplateDefinition(persistenceHandler, uploadPrecheck, null);
  }
}
