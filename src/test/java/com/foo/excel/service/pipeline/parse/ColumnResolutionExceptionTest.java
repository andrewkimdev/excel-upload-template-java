package com.foo.excel.service.pipeline.parse;

import static org.assertj.core.api.Assertions.assertThat;

import com.foo.excel.annotation.HeaderMatchMode;
import java.util.List;
import org.junit.jupiter.api.Test;

class ColumnResolutionExceptionTest {

  @Test
  void toKoreanMessage_headerMismatch_includesExpectedAndActual() {
    var ex =
        new ColumnResolutionException("tariffRate", "관세율", "단가", "H", HeaderMatchMode.CONTAINS);

    assertThat(ex.toKoreanMessage()).contains("컬럼 H").contains("예상: '관세율'").contains("실제: '단가'");
  }

  @Test
  void toKoreanMessage_emptyHeader_mentionsEmpty() {
    var ex =
        new ColumnResolutionException("tariffRate", "관세율", null, "H", HeaderMatchMode.CONTAINS);

    assertThat(ex.toKoreanMessage()).contains("컬럼 H").contains("비어있습니다").contains("예상: '관세율'");
  }

  @Test
  void toKoreanMessage_autoDetectFailure_mentionsHeaderNotFound() {
    var ex =
        new ColumnResolutionException("somefield", "찾을수없는헤더", null, null, HeaderMatchMode.CONTAINS);

    assertThat(ex.toKoreanMessage()).contains("찾을수없는헤더").contains("컬럼을 찾을 수 없습니다");
  }

  @Test
  void batchException_aggregatesMessages() {
    var ex1 =
        new ColumnResolutionException("field1", "헤더1", "wrong1", "B", HeaderMatchMode.CONTAINS);
    var ex2 = new ColumnResolutionException("field2", "헤더2", "wrong2", "C", HeaderMatchMode.EXACT);

    var batch = new ColumnResolutionBatchException(List.of(ex1, ex2));

    assertThat(batch.getExceptions()).hasSize(2);
    assertThat(batch.toKoreanMessage()).contains("컬럼 B").contains("컬럼 C");
    assertThat(batch.getMessage()).contains("2 field(s)");
  }

  @Test
  void batchException_immutableList() {
    var ex = new ColumnResolutionException("f", "h", "a", "B", HeaderMatchMode.CONTAINS);
    var batch = new ColumnResolutionBatchException(List.of(ex));

    assertThat(batch.getExceptions()).isUnmodifiable();
  }
}
