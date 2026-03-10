package com.foo.excel.validation;

import java.util.Objects;

/**
 * 엑셀 컬럼 참조를 표현하는 값 객체다.
 *
 * <p>내부적으로는 원본 컬럼 문자(예: {@code A}, {@code AA})를 보관하고, 사용자 메시지에는
 * {@code A열}, {@code AA열} 같은 형식으로 노출한다. 컬럼을 특정할 수 없는 경우에는 별도의
 * {@link #unknown()} 인스턴스로 표현한다.
 */
public final class ExcelColumnRef {

  /** 컬럼 정보를 확인할 수 없을 때 재사용하는 단일 인스턴스다. */
  private static final ExcelColumnRef UNKNOWN = new ExcelColumnRef(null, true);

  private final String rawLetter;
  private final boolean unknown;

  private ExcelColumnRef(String rawLetter, boolean unknown) {
    this.rawLetter = rawLetter;
    this.unknown = unknown;
  }

  /**
   * 엑셀 컬럼 문자로부터 참조 객체를 생성한다.
   *
   * <p>입력값은 공백을 제거한 뒤 대문자로 정규화하며, {@code A-Z} 이외의 문자가 포함되면 예외를
   * 발생시킨다.
   *
   * @param rawLetter 엑셀 컬럼 문자
   * @return 정규화된 컬럼 참조 객체
   * @throws IllegalArgumentException 입력값이 비어 있거나 유효한 컬럼 문자가 아닐 때
   */
  public static ExcelColumnRef ofLetter(String rawLetter) {
    if (rawLetter == null || rawLetter.isBlank()) {
      throw new IllegalArgumentException("Column letter must not be blank");
    }

    // 엑셀 컬럼 문자는 대소문자를 구분하지 않으므로 내부 표현은 대문자로 통일한다.
    String normalized = rawLetter.trim().toUpperCase();
    for (int i = 0; i < normalized.length(); i++) {
      char c = normalized.charAt(i);
      if (c < 'A' || c > 'Z') {
        throw new IllegalArgumentException("Invalid column letter: " + rawLetter);
      }
    }

    return new ExcelColumnRef(normalized, false);
  }

  /**
   * 컬럼 정보를 알 수 없음을 나타내는 참조를 반환한다.
   *
   * @return 알 수 없는 컬럼 참조
   */
  public static ExcelColumnRef unknown() {
    return UNKNOWN;
  }

  /** @return 정규화된 원본 컬럼 문자. 알 수 없는 컬럼이면 {@code null}이다. */
  public String rawLetter() {
    return rawLetter;
  }

  /** @return 컬럼 정보를 알 수 없는 참조인지 여부 */
  public boolean isUnknown() {
    return unknown;
  }

  @Override
  public String toString() {
    // 사용자 메시지에서 바로 사용할 수 있도록 "A열" 형식으로 렌더링한다.
    return unknown ? "?열" : rawLetter + "열";
  }

  @Override
  public boolean equals(Object o) {
    if (!(o instanceof ExcelColumnRef that)) {
      return false;
    }
    return unknown == that.unknown && Objects.equals(rawLetter, that.rawLetter);
  }

  @Override
  public int hashCode() {
    return Objects.hash(rawLetter, unknown);
  }
}
