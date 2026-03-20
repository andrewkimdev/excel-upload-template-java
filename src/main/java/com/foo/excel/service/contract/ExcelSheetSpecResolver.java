package com.foo.excel.service.contract;

import com.foo.excel.annotation.ExcelSheet;

public final class ExcelSheetSpecResolver {

  private ExcelSheetSpecResolver() {}

  public static ExcelSheetSpec resolve(Class<?> dtoClass) {
    ExcelSheet annotation = dtoClass.getAnnotation(ExcelSheet.class);
    if (annotation == null) {
      throw new IllegalStateException(
          "DTO '%s' must declare @ExcelSheet metadata".formatted(dtoClass.getName()));
    }

    if (annotation.headerRow() < 1) {
      throw new IllegalStateException(
          "Invalid headerRow for DTO '%s': %d".formatted(dtoClass.getName(), annotation.headerRow()));
    }
    if (annotation.dataStartRow() < 1) {
      throw new IllegalStateException(
          "Invalid dataStartRow for DTO '%s': %d"
              .formatted(dtoClass.getName(), annotation.dataStartRow()));
    }
    if (annotation.sheetIndex() < 0) {
      throw new IllegalStateException(
          "Invalid sheetIndex for DTO '%s': %d".formatted(dtoClass.getName(), annotation.sheetIndex()));
    }
    if (annotation.footerMarker() == null) {
      throw new IllegalStateException(
          "Invalid footerMarker for DTO '%s': null".formatted(dtoClass.getName()));
    }
    if (annotation.errorColumnName() == null || annotation.errorColumnName().isBlank()) {
      throw new IllegalStateException(
          "Invalid errorColumnName for DTO '%s': %s"
              .formatted(dtoClass.getName(), annotation.errorColumnName()));
    }

    return new ExcelSheetSpec(
        annotation.sheetIndex(),
        annotation.headerRow(),
        annotation.dataStartRow(),
        annotation.footerMarker(),
        annotation.errorColumnName());
  }
}
