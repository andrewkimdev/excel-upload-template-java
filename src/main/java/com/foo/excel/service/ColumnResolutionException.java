package com.foo.excel.service;

import com.foo.excel.annotation.HeaderMatchMode;
import lombok.Getter;

@Getter
public class ColumnResolutionException extends RuntimeException {

    private final String fieldName;
    private final String expectedHeader;
    private final String actualHeader;
    private final String columnLetter;
    private final HeaderMatchMode matchMode;

    public ColumnResolutionException(String fieldName, String expectedHeader,
                                     String actualHeader, String columnLetter,
                                     HeaderMatchMode matchMode) {
        super(buildMessage(fieldName, expectedHeader, actualHeader, columnLetter));
        this.fieldName = fieldName;
        this.expectedHeader = expectedHeader;
        this.actualHeader = actualHeader;
        this.columnLetter = columnLetter;
        this.matchMode = matchMode;
    }

    public String toKoreanMessage() {
        if (columnLetter != null && actualHeader != null && !actualHeader.isBlank()) {
            return "컬럼 %s의 헤더가 일치하지 않습니다. 예상: '%s', 실제: '%s'"
                    .formatted(columnLetter, expectedHeader, actualHeader);
        }
        if (columnLetter != null) {
            return "컬럼 %s의 헤더가 비어있습니다. 예상: '%s'"
                    .formatted(columnLetter, expectedHeader);
        }
        return "헤더 '%s'에 해당하는 컬럼을 찾을 수 없습니다".formatted(expectedHeader);
    }

    private static String buildMessage(String fieldName, String expectedHeader,
                                       String actualHeader, String columnLetter) {
        if (columnLetter != null) {
            return "Column %s header mismatch for field '%s': expected '%s', actual '%s'"
                    .formatted(columnLetter, fieldName, expectedHeader, actualHeader);
        }
        return "Could not find column for field '%s' with header '%s'"
                .formatted(fieldName, expectedHeader);
    }
}
