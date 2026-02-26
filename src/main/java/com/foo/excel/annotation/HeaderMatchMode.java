package com.foo.excel.annotation;

public enum HeaderMatchMode {

  /** 헤더가 정확히 일치해야 한다(대소문자 무시, 공백 제거). */
  EXACT,

  /** 헤더에 이 텍스트가 포함되어야 한다("규격1)"이 "규격"과 매칭되는 경우에 유용). */
  CONTAINS,

  /** 헤더가 이 텍스트로 시작해야 한다. */
  STARTS_WITH,

  /** 헤더가 이 정규식 패턴과 일치해야 한다. */
  REGEX
}
