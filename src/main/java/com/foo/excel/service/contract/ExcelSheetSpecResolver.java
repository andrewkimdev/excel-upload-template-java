package com.foo.excel.service.contract;

import com.foo.excel.annotation.ExcelSheet;
import lombok.AccessLevel;
import lombok.NoArgsConstructor;

@NoArgsConstructor(access = AccessLevel.PRIVATE)
public final class ExcelSheetSpecResolver {

  public static ExcelSheetSpec resolve(Class<?> rowClass) {
    ExcelSheet annotation = rowClass.getAnnotation(ExcelSheet.class);
    if (annotation == null) {
      throw new IllegalStateException(
          "DTO '%s' must declare @ExcelSheet metadata".formatted(rowClass.getName()));
    }

    int declaredSheetNumber = annotation.sheetIndex();

    if (annotation.headerRow() < 1) {
      throw new IllegalStateException(
          "Invalid headerRow for DTO '%s': %d".formatted(rowClass.getName(), annotation.headerRow()));
    }
    if (annotation.dataStartRow() < 1) {
      throw new IllegalStateException(
          "Invalid dataStartRow for DTO '%s': %d"
              .formatted(rowClass.getName(), annotation.dataStartRow()));
    }
    if (declaredSheetNumber < 1) {
      throw new IllegalStateException(
          "Invalid sheetIndex for DTO '%s': %d (declared sheet number must be >= 1)"
              .formatted(rowClass.getName(), declaredSheetNumber));
    }
    if (annotation.footerMarker() == null) {
      throw new IllegalStateException(
          "Invalid footerMarker for DTO '%s': null".formatted(rowClass.getName()));
    }
    if (annotation.errorColumnName() == null || annotation.errorColumnName().isBlank()) {
      throw new IllegalStateException(
          "Invalid errorColumnName for DTO '%s': %s"
              .formatted(rowClass.getName(), annotation.errorColumnName()));
    }

    // Annotation contract is 1-based; resolved runtime/POI access remains 0-based.
    int resolvedSheetIndex = declaredSheetNumber - 1;

    return new ExcelSheetSpec(
        resolvedSheetIndex,
        annotation.headerRow(),
        annotation.dataStartRow(),
        annotation.footerMarker(),
        annotation.errorColumnName());
  }
}
