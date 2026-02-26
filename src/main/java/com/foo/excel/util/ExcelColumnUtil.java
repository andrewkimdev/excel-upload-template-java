package com.foo.excel.util;

public final class ExcelColumnUtil {

  private ExcelColumnUtil() {}

  /** Excel 컬럼 문자를 0-based 인덱스로 변환한다. A=0, B=1, ..., Z=25, AA=26, AB=27, ... */
  public static int letterToIndex(String column) {
    if (column == null || column.isBlank()) {
      return -1;
    }

    String col = column.toUpperCase().trim();
    int index = 0;

    for (int i = 0; i < col.length(); i++) {
      char c = col.charAt(i);
      if (c < 'A' || c > 'Z') {
        throw new IllegalArgumentException("Invalid column letter: " + column);
      }
      index = index * 26 + (c - 'A' + 1);
    }

    return index - 1; // 0-based로 변환
  }

  /** 0-based 인덱스를 Excel 컬럼 문자로 변환한다. 0=A, 1=B, ..., 25=Z, 26=AA, 27=AB, ... */
  public static String indexToLetter(int index) {
    if (index < 0) {
      throw new IllegalArgumentException("Column index must be non-negative: " + index);
    }

    StringBuilder sb = new StringBuilder();
    int col = index + 1; // 계산을 위해 1-based로 변환

    while (col > 0) {
      int remainder = (col - 1) % 26;
      sb.insert(0, (char) ('A' + remainder));
      col = (col - 1) / 26;
    }

    return sb.toString();
  }
}
