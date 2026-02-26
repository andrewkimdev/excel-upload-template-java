package com.foo.excel.templates.samples.aappcar.config;

import com.foo.excel.service.contract.TemplateDefinition;
import com.foo.excel.templates.TemplateTypes;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemCommonData;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.templates.samples.aappcar.service.AAppcarItemDbUniquenessChecker;
import com.foo.excel.templates.samples.aappcar.service.AAppcarItemService;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class AAppcarItemTemplateConfig {

  @Bean
  public TemplateDefinition<AAppcarItemDto, AAppcarItemCommonData> tariffExemptionTemplate(
      AAppcarItemService persistenceHandler, AAppcarItemDbUniquenessChecker dbUniquenessChecker) {
    return new TemplateDefinition<>(
        TemplateTypes.AAPPCAR,
        AAppcarItemDto.class,
        AAppcarItemCommonData.class,
        new AAppcarItemImportConfig(),
        persistenceHandler,
        dbUniquenessChecker);
  }
}
