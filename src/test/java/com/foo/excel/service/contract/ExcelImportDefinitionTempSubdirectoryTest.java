package com.foo.excel.service.contract;

import static org.assertj.core.api.Assertions.assertThat;

import com.foo.excel.imports.samples.aappcar.config.AAppcarItemImportDefinition;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemImportRow;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemImportMetadata;
import org.junit.jupiter.api.Test;

class ExcelImportDefinitionTempSubdirectoryTest {

  @Test
  void baseImportDefinition_returnsEmptyTempSubdirectoryByDefault() {
    ExcelImportDefinition<AAppcarItemImportRow, AAppcarItemImportMetadata> definition =
        new ExcelImportDefinition<>(
            "test", AAppcarItemImportRow.class, AAppcarItemImportMetadata.class, null, null, null);

    assertThat(definition.resolveTempSubdirectory(new AAppcarItemImportMetadata())).isNull();
  }

  @Test
  void aappcarImportDefinition_resolvesTempSubdirectoryFromCustomId() {
    AAppcarItemImportMetadata metadata = new AAppcarItemImportMetadata();
    metadata.setCustomId("CUSTOM01");
    AAppcarItemImportDefinition definition = new AAppcarItemImportDefinition(null, null, null);

    assertThat(definition.resolveTempSubdirectory(metadata)).isEqualTo("CUSTOM01");
  }
}
