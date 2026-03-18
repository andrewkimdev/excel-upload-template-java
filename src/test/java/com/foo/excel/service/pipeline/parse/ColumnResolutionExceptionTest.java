package com.foo.excel.service.pipeline.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.foo.excel.annotation.HeaderMatchMode;
import com.foo.excel.validation.ExcelColumnRef;
import java.util.List;
import org.junit.jupiter.api.Test;

class ColumnResolutionExceptionTest {

  @Test
  void toKoreanMessage_headerMismatch_includesExpectedAndActual() {
    var ex =
        new ColumnResolutionException(
            "taxRate", "관세율", "단가", ExcelColumnRef.ofLetter("H"), HeaderMatchMode.CONTAINS);

    assertThat(ex.toKoreanMessage())
        .contains("H열")
        .doesNotContain("H열열")
        .contains("예상: '관세율'")
        .contains("실제: '단가'");
  }

  @Test
  void toKoreanMessage_emptyHeader_mentionsEmpty() {
    var ex =
        new ColumnResolutionException(
            "taxRate", "관세율", null, ExcelColumnRef.ofLetter("H"), HeaderMatchMode.CONTAINS);

    assertThat(ex.toKoreanMessage()).contains("H열").contains("비어있습니다").contains("예상: '관세율'");
  }

  @Test
  void batchException_aggregatesMessages() {
    var ex1 =
        new ColumnResolutionException(
            "field1", "헤더1", "wrong1", ExcelColumnRef.ofLetter("B"), HeaderMatchMode.CONTAINS);
    var ex2 =
        new ColumnResolutionException(
            "field2", "헤더2", "wrong2", ExcelColumnRef.ofLetter("C"), HeaderMatchMode.EXACT);

    var batch = new ColumnResolutionBatchException(List.of(ex1, ex2));

    assertThat(batch.getExceptions()).hasSize(2);
    assertThat(batch.toKoreanMessage()).contains("B열").contains("C열");
    assertThat(batch.getMessage()).contains("2 field(s)");
  }

  @Test
  void batchException_immutableList() {
    var ex =
        new ColumnResolutionException(
            "f", "h", "a", ExcelColumnRef.ofLetter("B"), HeaderMatchMode.CONTAINS);
    var batch = new ColumnResolutionBatchException(List.of(ex));

    assertThat(batch.getExceptions()).isUnmodifiable();
  }
}
