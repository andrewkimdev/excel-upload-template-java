package com.foo.excel.service.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foo.excel.annotation.ExcelSheet;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemDto;
import org.junit.jupiter.api.Test;

class ExcelSheetSpecResolverTest {

  @Test
  void resolve_aAppcarSheetSpecFromDtoAnnotation() {
    ExcelSheetSpec spec = ExcelSheetSpecResolver.resolve(AAppcarItemDto.class);

    assertThat(spec.sheetIndex()).isEqualTo(0);
    assertThat(spec.headerRow()).isEqualTo(4);
    assertThat(spec.dataStartRow()).isEqualTo(7);
    assertThat(spec.footerMarker()).isEqualTo("※");
    assertThat(spec.errorColumnName()).isEqualTo("_ERRORS");
  }

  @Test
  void resolve_usesAnnotationDefaultsWhenNotOverridden() {
    ExcelSheetSpec spec =
        ExcelSheetSpecResolver.resolve(DefaultAnnotatedDto.class);

    assertThat(spec.sheetIndex()).isEqualTo(0);
    assertThat(spec.headerRow()).isEqualTo(1);
    assertThat(spec.dataStartRow()).isEqualTo(2);
    assertThat(spec.footerMarker()).isEqualTo("※");
    assertThat(spec.errorColumnName()).isEqualTo("_ERRORS");
  }

  @Test
  void resolve_failsWhenDtoIsMissingExcelSheetAnnotation() {
    assertThatThrownBy(() -> ExcelSheetSpecResolver.resolve(MissingAnnotationDto.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("@ExcelSheet");
  }

  @ExcelSheet
  static class DefaultAnnotatedDto {}

  static class MissingAnnotationDto {}
}
