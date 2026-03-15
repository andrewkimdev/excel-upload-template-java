package com.foo.excel.service.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foo.excel.annotation.ExcelSheet;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import org.junit.jupiter.api.Test;

class TemplateSheetMetadataResolverTest {

  @Test
  void resolve_aAppcarSheetMetadataFromDtoAnnotation() {
    TemplateSheetMetadata metadata = TemplateSheetMetadataResolver.resolve(AAppcarItemDto.class);

    assertThat(metadata.sheetIndex()).isEqualTo(0);
    assertThat(metadata.headerRow()).isEqualTo(4);
    assertThat(metadata.dataStartRow()).isEqualTo(7);
    assertThat(metadata.footerMarker()).isEqualTo("※");
    assertThat(metadata.errorColumnName()).isEqualTo("_ERRORS");
  }

  @Test
  void resolve_usesAnnotationDefaultsWhenNotOverridden() {
    TemplateSheetMetadata metadata =
        TemplateSheetMetadataResolver.resolve(DefaultAnnotatedDto.class);

    assertThat(metadata.sheetIndex()).isEqualTo(0);
    assertThat(metadata.headerRow()).isEqualTo(1);
    assertThat(metadata.dataStartRow()).isEqualTo(2);
    assertThat(metadata.footerMarker()).isEqualTo("※");
    assertThat(metadata.errorColumnName()).isEqualTo("_ERRORS");
  }

  @Test
  void resolve_failsWhenDtoIsMissingExcelSheetAnnotation() {
    assertThatThrownBy(() -> TemplateSheetMetadataResolver.resolve(MissingAnnotationDto.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("@ExcelSheet");
  }

  @ExcelSheet
  static class DefaultAnnotatedDto {}

  static class MissingAnnotationDto {}
}
