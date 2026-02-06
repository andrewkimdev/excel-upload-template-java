# Codebase Consistency Report

**Generated:** 2026-02-06
**Scope:** All Java files under `src/`

---

## 1. Naming Conventions

| Category | Status | Issues |
|----------|--------|--------|
| Class naming (PascalCase) | PASS | 0 |
| Method naming (camelCase) | PASS | 0 |
| Field naming (camelCase / UPPER_SNAKE_CASE) | PASS | 0 |
| Package naming (lowercase) | PASS | 0 |
| Test class naming (`*Test`) | PASS | 0 |
| Template naming pattern | PASS | 0 |

**All naming conventions are fully compliant.** No violations detected.

---

## 2. Java 17 Feature Usage

| Category | Status | Issues |
|----------|--------|--------|
| `var` usage | PASS | Consistent and idiomatic |
| Switch expressions | PASS | Already using modern syntax |
| Text blocks | PASS | No candidates found |
| `Path.of()` vs `Paths.get()` | PASS | No deprecated usage |
| `instanceof` pattern matching | PASS | No candidates found |
| Records vs classes | **5 ISSUES** | See below |
| Old-style for-loops | Acceptable | Index-based loops justified by POI API |

### Records candidates (classes that should be records per CLAUDE.md)

| File | Line | Class | Details |
|------|------|-------|---------|
| src/main/java/com/foo/excel/validation/CellError.java | 8 | `CellError` | Read-only data carrier with `@Data` + `@Builder` |
| src/main/java/com/foo/excel/validation/RowError.java | 12 | `RowError` | Read-only with `@Data` + `@Builder`, only has `getFormattedMessage()` |
| src/main/java/com/foo/excel/service/ExcelParserService.java | 34 | `ColumnMapping` | Static inner class, read-only |
| src/main/java/com/foo/excel/service/ExcelParserService.java | 43 | `ParseResult` | Static inner class, read-only |
| src/main/java/com/foo/excel/service/ExcelImportOrchestrator.java | 34 | `ImportResult` | Static inner class with `@Data` + `@Builder`, read-only |

**Note:** `ExcelValidationResult` correctly remains a class (has mutable `merge()` method).

---

## 3. Spring Boot Annotation Consistency

| Category | Status | Issues |
|----------|--------|--------|
| DI via `@RequiredArgsConstructor` + `private final` | PASS | 0 |
| No `@Data` on `@Entity` classes | PASS | 0 |
| `@Slf4j` for logging | PASS | 0 |
| `@ConfigurationProperties` pattern | PASS | 0 |
| Jakarta namespace (no javax) | PASS | 0 |
| `@SpringBootTest` in integration tests | PASS | 0 |
| Component stereotypes | PASS | 0 |

**All Spring Boot annotations are fully compliant.** No violations detected.

---

## 4. Exception Handling Patterns

| Category | Status | Issues |
|----------|--------|--------|
| Empty catch blocks | PASS | 0 |
| Raw `WorkbookFactory.create()` in production | PASS | 0 |
| Custom exception base classes | PASS | All extend `RuntimeException` |
| SLF4J placeholder syntax | PASS | 0 |
| Error message language (Korean/English) | PASS | 0 |
| `SecureExcelUtils` usage | PASS | 0 |
| Controller exposing internal details | **2 ISSUES** | See below |

### Security violations: controller exposes `e.getMessage()`

| File | Line | Severity | Details |
|------|------|----------|---------|
| src/main/java/com/foo/excel/controller/ExcelUploadController.java | 116 | HIGH | `catch (IllegalArgumentException e)` returns `e.getMessage()` in REST API response |
| src/main/java/com/foo/excel/controller/ExcelUploadController.java | 241 | HIGH | `catch (IllegalArgumentException e)` returns `e.getMessage()` in Thymeleaf model |

**CLAUDE.md rule:** "Controllers MUST catch all exceptions and return generic Korean error messages. NEVER return `e.getMessage()`, stack traces, or file paths."

While the current `IllegalArgumentException` messages from `SecureExcelUtils.sanitizeFilename()` are user-friendly Korean text, this pattern is fragile. Future code changes could accidentally expose internal details through this path.

**Recommended fix:** Replace `e.getMessage()` with a generic Korean error message and log the actual exception at WARN level.

---

## Summary

| Category | Pass | Issues |
|----------|------|--------|
| Naming conventions | 6/6 | 0 |
| Java 17 features | 6/7 | 5 (records candidates) |
| Spring Boot annotations | 7/7 | 0 |
| Exception handling | 6/7 | 2 (security) |
| **Total** | **25/27** | **7** |

### Priority action items

1. **HIGH** — Replace `e.getMessage()` exposure in `ExcelUploadController` lines 116 and 241 with generic Korean messages
2. **MEDIUM** — Convert 5 read-only data carriers to Java records (`CellError`, `RowError`, `ColumnMapping`, `ParseResult`, `ImportResult`)
