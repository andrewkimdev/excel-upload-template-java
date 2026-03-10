package com.foo.excel.validation;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;

class ExcelColumnRefTest {

  @Test
  void ofLetter_normalizesToUppercase() {
    ExcelColumnRef ref = ExcelColumnRef.ofLetter(" aa ");

    assertThat(ref.rawLetter()).isEqualTo("AA");
    assertThat(ref.toString()).isEqualTo("AA열");
  }

  @Test
  void unknown_rendersQuestionLabel() {
    ExcelColumnRef ref = ExcelColumnRef.unknown();

    assertThat(ref.isUnknown()).isTrue();
    assertThat(ref.rawLetter()).isNull();
    assertThat(ref.toString()).isEqualTo("?열");
  }

  @Test
  void toString_doesNotDuplicateSuffix() {
    assertThat(ExcelColumnRef.ofLetter("B").toString()).isEqualTo("B열").doesNotContain("열열");
  }

  @Test
  void ofLetter_rejectsInvalidInput() {
    assertThatThrownBy(() -> ExcelColumnRef.ofLetter("A1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ExcelColumnRef.ofLetter(" "))
        .isInstanceOf(IllegalArgumentException.class);
  }
}
