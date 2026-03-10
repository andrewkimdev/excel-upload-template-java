package com.foo.excel.service.pipeline.parse;

import com.foo.excel.annotation.HeaderMatchMode;
import com.foo.excel.validation.ExcelColumnRef;
import lombok.Getter;

@Getter
public class ColumnResolutionException extends RuntimeException {

  private final String fieldName;
  private final String expectedHeader;
  private final String actualHeader;
  private final ExcelColumnRef columnRef;
  private final HeaderMatchMode matchMode;

  public ColumnResolutionException(
      String fieldName,
      String expectedHeader,
      String actualHeader,
      ExcelColumnRef columnRef,
      HeaderMatchMode matchMode) {
    super(buildMessage(fieldName, expectedHeader, actualHeader, columnRef));
    this.fieldName = fieldName;
    this.expectedHeader = expectedHeader;
    this.actualHeader = actualHeader;
    this.columnRef = columnRef;
    this.matchMode = matchMode;
  }

  public String toKoreanMessage() {
    if (columnRef != null && actualHeader != null && !actualHeader.isBlank()) {
      return "%s 헤더가 일치하지 않습니다. 예상: '%s', 실제: '%s'"
          .formatted(columnRef, expectedHeader, actualHeader);
    }
    if (columnRef != null) {
      return "%s 헤더가 비어있습니다. 예상: '%s'".formatted(columnRef, expectedHeader);
    }
    return "헤더 '%s'에 해당하는 컬럼을 찾을 수 없습니다".formatted(expectedHeader);
  }

  private static String buildMessage(
      String fieldName, String expectedHeader, String actualHeader, ExcelColumnRef columnRef) {
    if (columnRef != null) {
      return "Column %s header mismatch for field '%s': expected '%s', actual '%s'"
          .formatted(columnRef.rawLetter(), fieldName, expectedHeader, actualHeader);
    }
    return "Could not find column for field '%s' with header '%s'"
        .formatted(fieldName, expectedHeader);
  }
}
