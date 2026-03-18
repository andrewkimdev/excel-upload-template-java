package com.foo.excel.service.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.foo.excel.templates.samples.aappcar.config.AAppcarItemTemplateDefinition;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetaData;
import org.junit.jupiter.api.Test;

class TemplateDefinitionTempSubdirectoryTest {

  @Test
  void baseTemplateDefinition_returnsEmptyTempSubdirectoryByDefault() {
    TemplateDefinition<AAppcarItemDto, AAppcarItemMetaData> template =
        new TemplateDefinition<>("test", AAppcarItemDto.class, AAppcarItemMetaData.class, null, null, null);

    assertThat(template.resolveTempSubdirectory(new AAppcarItemMetaData())).isNull();
  }

  @Test
  void aappcarTemplateDefinition_resolvesTempSubdirectoryFromCustomId() {
    AAppcarItemMetaData metaData = new AAppcarItemMetaData();
    metaData.setCustomId("CUSTOM01");
    AAppcarItemTemplateDefinition template =
        new AAppcarItemTemplateDefinition(null, null, null);

    assertThat(template.resolveTempSubdirectory(metaData)).isEqualTo("CUSTOM01");
  }
}
