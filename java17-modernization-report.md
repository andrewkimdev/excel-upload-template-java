# Java 17 Modernization Report

Analysis of `src/main/java` for Java 17 feature usage inconsistencies.

---

## Summary

| Feature | Status | Files to Update |
|---------|--------|-----------------|
| Records | 2 candidates | ExcelParserService (inner classes) |
| Switch expressions | Compliant | - |
| Pattern matching instanceof | 1 candidate | TariffExemption |
| Text blocks | 2 candidates | ExcelConversionService, ExcelErrorReportService |
| `var` keyword | 5+ candidates | WorkbookCopyUtils, UniqueConstraintValidator, ExcelValidationService |
| `Path.of()` | Compliant | - |
| `Stream.toList()` | Compliant | - |
| `String.isBlank()` | Compliant | - |

---

## 1. Records

### Candidates for Conversion

#### `ExcelParserService.ColumnMapping` (inner class)
**File:** `com/foo/excel/service/ExcelParserService.java:34-41`

```java
// Current
@Data @AllArgsConstructor
public static class ColumnMapping {
    private final Field field;
    private final ExcelColumn annotation;
    private final int resolvedColumnIndex;
    private final String resolvedColumnLetter;
}

// Suggested
public record ColumnMapping(
    Field field,
    ExcelColumn annotation,
    int resolvedColumnIndex,
    String resolvedColumnLetter
) {}
```

#### `ExcelParserService.ParseResult` (inner class)
**File:** `com/foo/excel/service/ExcelParserService.java:43-50`

```java
// Current
@Data @AllArgsConstructor
public static class ParseResult<T> {
    private final List<T> rows;
    private final List<Integer> sourceRowNumbers;
    private final List<ColumnMapping> columnMappings;
    private final List<RowError> parseErrors;
}

// Suggested
public record ParseResult<T>(
    List<T> rows,
    List<Integer> sourceRowNumbers,
    List<ColumnMapping> columnMappings,
    List<RowError> parseErrors
) {}
```

### Already Compliant

- `PersistenceHandler.SaveResult` - already a record

### Excluded (per CLAUDE.md rules)

- `CellError` - uses `@Builder`
- `RowError` - uses `@Builder`, has mutable state
- `ExcelValidationResult` - has mutable state and `merge()` method
- `ImportResult` - uses `@Builder`

---

## 2. Pattern Matching for instanceof

#### `TariffExemption.equals()`
**File:** `com/foo/excel/templates/samples/tariffexemption/TariffExemption.java:74`

```java
// Current
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (o == null || getClass() != o.getClass()) return false;
    TariffExemption that = (TariffExemption) o;
    return Objects.equals(itemName, that.itemName) && ...;
}

// Suggested
@Override
public boolean equals(Object o) {
    if (this == o) return true;
    if (!(o instanceof TariffExemption that)) return false;
    return Objects.equals(itemName, that.itemName) && ...;
}
```

---

## 3. Text Blocks

#### `ExcelConversionService`
**File:** `com/foo/excel/service/ExcelConversionService.java:98-100`

```java
// Current
throw new SecurityException(
    "File content does not match XLS format. " +
    "The file may be corrupted or disguised.");

// Suggested
throw new SecurityException("""
    File content does not match XLS format. \
    The file may be corrupted or disguised.""");
```

#### `ExcelErrorReportService`
**File:** `com/foo/excel/service/ExcelErrorReportService.java:150-152`

```java
// Current
disclaimerCell.setCellValue(
    "※ 본 파일은 오류 확인용으로 재생성되었습니다. "
    + "일부 서식 및 기능이 원본 파일과 다를 수 있습니다.");

// Suggested
disclaimerCell.setCellValue("""
    ※ 본 파일은 오류 확인용으로 재생성되었습니다. \
    일부 서식 및 기능이 원본 파일과 다를 수 있습니다.""");
```

---

## 4. `var` Keyword

### `WorkbookCopyUtils`
**File:** `com/foo/excel/util/WorkbookCopyUtils.java`

| Line | Current | Suggested |
|------|---------|-----------|
| 26 | `Map<Integer, CellStyle> styleMap = new HashMap<>();` | `var styleMap = new HashMap<Integer, CellStyle>();` |
| 27 | `boolean crossFormat = isCrossFormat(...)` | `var crossFormat = isCrossFormat(...)` |
| 28 | `Map<Integer, Font> fontMap = crossFormat ? ...` | `var fontMap = crossFormat ? ...` |
| 126 | `Map<Integer, Font> fontMap = new HashMap<>();` | `var fontMap = new HashMap<Integer, Font>();` |

### `UniqueConstraintValidator`
**File:** `com/foo/excel/validation/UniqueConstraintValidator.java`

| Line | Current | Suggested |
|------|---------|-----------|
| 40 | `Map<Object, Integer> seenValues = new HashMap<>();` | `var seenValues = new HashMap<Object, Integer>();` |
| 99 | `Map<List<Object>, Integer> seenKeys = new HashMap<>();` | `var seenKeys = new HashMap<List<Object>, Integer>();` |
| 103 | `List<Object> compositeKey = new ArrayList<>();` | `var compositeKey = new ArrayList<Object>();` |

### `ExcelValidationService`
**File:** `com/foo/excel/service/ExcelValidationService.java`

| Line | Current | Suggested |
|------|---------|-----------|
| 25 | `List<RowError> allErrors = new ArrayList<>();` | `var allErrors = new ArrayList<RowError>();` |
| 34 | `List<CellError> cellErrors = new ArrayList<>();` | `var cellErrors = new ArrayList<CellError>();` |

---

## 5. Already Compliant

The codebase already uses these Java 17 features consistently:

- **Switch expressions** - `ExcelParserService:142-147`, `WorkbookCopyUtils:76-90`
- **`Path.of()`** - No instances of deprecated `Paths.get()`
- **`Stream.toList()`** - No instances of `.collect(Collectors.toList())`
- **`String.isBlank()`** - Used throughout the codebase
- **`@RequiredArgsConstructor`** - Consistent constructor injection

---

## 6. Minor / Optional

### `String.strip()` vs `String.trim()`
**File:** `com/foo/excel/service/ExcelParserService.java:139-140, 278, 352`

`strip()` handles Unicode whitespace better than `trim()`. Consider updating if Unicode input is expected. Low priority.

### Optional API improvements
**File:** `com/foo/excel/templates/samples/tariffexemption/TariffExemptionService.java:26-30`

Could use `ifPresentOrElse()` instead of `isPresent()` + `get()`. Low priority - current code is readable.

---

## Recommendations

**High Priority:**
1. Convert `ColumnMapping` and `ParseResult` inner classes to records

**Medium Priority:**
2. Add `var` keyword to local variables where type is obvious from RHS
3. Update `TariffExemption.equals()` to use pattern matching

**Low Priority:**
4. Consider text blocks for multi-line strings (cosmetic)
5. Consider `strip()` over `trim()` for Unicode support
