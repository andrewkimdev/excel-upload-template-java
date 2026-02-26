package com.foo.excel.util;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;

class ExcelColumnUtilTest {

  // ===== letterToIndex 테스트 =====

  @ParameterizedTest
  @CsvSource({"A, 0", "B, 1", "Z, 25", "AA, 26", "AB, 27"})
  void letterToIndex_validLetters(String letter, int expected) {
    assertThat(ExcelColumnUtil.letterToIndex(letter)).isEqualTo(expected);
  }

  @Test
  void letterToIndex_null_returnsNegative() {
    assertThat(ExcelColumnUtil.letterToIndex(null)).isEqualTo(-1);
  }

  @Test
  void letterToIndex_blank_returnsNegative() {
    assertThat(ExcelColumnUtil.letterToIndex("")).isEqualTo(-1);
    assertThat(ExcelColumnUtil.letterToIndex("  ")).isEqualTo(-1);
  }

  @Test
  void letterToIndex_invalidChars_throws() {
    assertThatThrownBy(() -> ExcelColumnUtil.letterToIndex("1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ExcelColumnUtil.letterToIndex("A1"))
        .isInstanceOf(IllegalArgumentException.class);
    assertThatThrownBy(() -> ExcelColumnUtil.letterToIndex("@"))
        .isInstanceOf(IllegalArgumentException.class);
  }

  // ===== indexToLetter 테스트 =====

  @ParameterizedTest
  @CsvSource({"0, A", "25, Z", "26, AA"})
  void indexToLetter_validIndices(int index, String expected) {
    assertThat(ExcelColumnUtil.indexToLetter(index)).isEqualTo(expected);
  }

  @Test
  void indexToLetter_negativeIndex_throws() {
    assertThatThrownBy(() -> ExcelColumnUtil.indexToLetter(-1))
        .isInstanceOf(IllegalArgumentException.class);
  }

  @Test
  void letterToIndex_indexToLetter_roundTrip() {
    for (int i = 0; i < 100; i++) {
      String letter = ExcelColumnUtil.indexToLetter(i);
      assertThat(ExcelColumnUtil.letterToIndex(letter)).isEqualTo(i);
    }
  }
}
