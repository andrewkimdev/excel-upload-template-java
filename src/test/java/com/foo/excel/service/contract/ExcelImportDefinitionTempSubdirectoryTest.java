package com.foo.excel.service.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.foo.excel.templates.samples.aappcar.config.AAppcarItemImportDefinition;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetadata;
import org.junit.jupiter.api.Test;

class ExcelImportDefinitionTempSubdirectoryTest {

  @Test
  void baseImportDefinition_returnsEmptyTempSubdirectoryByDefault() {
    ExcelImportDefinition<AAppcarItemDto, AAppcarItemMetadata> definition =
        new ExcelImportDefinition<>(
            "test", AAppcarItemDto.class, AAppcarItemMetadata.class, null, null, null);

    assertThat(definition.resolveTempSubdirectory(new AAppcarItemMetadata())).isNull();
  }

  @Test
  void aappcarImportDefinition_resolvesTempSubdirectoryFromCustomId() {
    AAppcarItemMetadata metadata = new AAppcarItemMetadata();
    metadata.setCustomId("CUSTOM01");
    AAppcarItemImportDefinition definition = new AAppcarItemImportDefinition(null, null, null);

    assertThat(definition.resolveTempSubdirectory(metadata)).isEqualTo("CUSTOM01");
  }
}
