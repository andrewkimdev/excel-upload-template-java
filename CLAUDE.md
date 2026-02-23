# CLAUDE.md — Project Rules & Coding Standards

## Project Overview

Spring Boot 3.3.4 / Java 17 application for Excel file upload, parsing, validation, and import (`.xlsx` only).
Uses Gradle 8+, Lombok, Apache POI 5.4.1, H2 (dev), Thymeleaf.

---

## Build & Test

```bash
./gradlew bootRun        # Run the app on http://localhost:8080
./gradlew test            # Run all tests
./gradlew build           # Full build with tests
```

---

## Language & Framework Baseline

MUST use Java 17 idioms throughout: records, switch expressions, `var`, text blocks, `Path.of()`, etc. Always prefer modern API over deprecated equivalents.

**Records exception:** Classes that require Lombok `@Builder` or mutable state (e.g., `ExcelValidationResult.merge()`) remain as classes. All other read-only data carriers MUST be records.

MUST use Spring Boot 3.3.4 conventions: `jakarta.*` namespace, `spring.servlet.multipart.*` prefix, etc.

---

## Dependency Injection

MUST use Lombok `@RequiredArgsConstructor` with `private final` fields for all Spring-managed beans. Do NOT write explicit constructors unless custom initialization logic beyond field assignment is required.

`ExcelImportProperties` uses `@Component` + `@ConfigurationProperties`. For new config classes, MUST use `@EnableConfigurationProperties` or `@ConfigurationPropertiesScan` instead.

---

## JPA / Entity Rules

NEVER use `@Data` on JPA `@Entity` classes — it breaks JPA identity semantics. Zero exceptions.

For entities, ALWAYS use `@Getter`/`@Setter` and override `equals()`/`hashCode()` using business key or `@Id` only:

```java
@Getter @Setter @NoArgsConstructor @AllArgsConstructor @Builder
@Entity
public class MyEntity {
    // equals/hashCode on business key or @Id only
}
```

---

## Apache POI / Excel Guidelines

- Upload pipeline is OOXML `.xlsx` only; legacy `.xls` input MUST be rejected in early validation.
- Use `SecureExcelUtils` validation/opening path consistently for all uploaded files.
- Preserve existing formatting behavior for `.xlsx` error reports without introducing `.xls` compatibility paths.

---

## Thread Safety

Spring Boot processes requests concurrently. Non-thread-safe POI objects (e.g., `DataFormatter`) MUST use `ThreadLocal` or method-local instances — never shared static fields. The fix is trivial and prevents a class of bug that is nearly impossible to diagnose.

---

## Security Rules

### NEVER expose internal details to users

Controllers MUST catch all exceptions and return generic Korean error messages. NEVER return `e.getMessage()`, stack traces, or file paths.

### MUST use `SecureExcelUtils` for all file operations

- Open workbooks via `SecureExcelUtils.createWorkbook()` — NEVER via raw `WorkbookFactory.create()`
- Lightweight row counting via `SecureExcelUtils.countRows()` (StAX streaming, constant memory)
- Sanitize filenames via `SecureExcelUtils.sanitizeFilename()`
- Validate file content (magic bytes) via `SecureExcelUtils.validateFileContent()`
- Sanitize cell values for error reports via `SecureExcelUtils.sanitizeForExcelCell()`

### Upload contract (`POST /api/excel/upload/{templateType}`)

- Request MUST be `multipart/form-data` with:
  - `file` (`.xlsx` only)
  - `commonData` (`application/json`)
- Required `commonData` fields: `comeYear`, `comeSequence`, `uploadSequence`, `equipCode`
- `createdBy` and `approvedYn` are server-managed:
  - force `createdBy=user01`
  - force `approvedYn=N`
- If client sends `createdBy` or `approvedYn`, server MUST ignore them.

### Thymeleaf

- ALWAYS use `th:text`. NEVER use `th:utext` with user data.
- MUST use Thymeleaf URL expressions (`@{...}`) — NEVER raw `th:href="${...}"`.

---

## Code Conventions

### Package structure

```
com.foo.excel/
├── annotation/          # @ExcelColumn, @ExcelUnique, etc.
├── config/              # ExcelImportConfig interface, ExcelImportProperties
├── controller/          # Spring MVC controllers
├── service/             # Core services + strategy interfaces
├── util/                # ExcelColumnUtil, SecureExcelUtils, WorkbookCopyUtils
├── validation/          # CellError, RowError, ExcelValidationResult, UniqueConstraintValidator
└── templates/samples/   # Sample template implementations
    └── tariffexemption/ # Entity, DTO, Config, Service, Repository, Checker
```

### New templates MUST go under `templates/`

Create a subpackage under `com.foo.excel.templates` containing:

1. `*Dto.java` — `@ExcelColumn` + JSR-380 annotations. MUST NOT implement `ExcelImportConfig`.
2. `*ImportConfig.java` — implements `ExcelImportConfig`
3. `*Entity.java` — JPA entity (if persistence needed)
4. `*Repository.java` — Spring Data JPA repository
5. `*Service.java` — implements `PersistenceHandler<Dto>` and the current save contract `saveAll(List<T> rows, List<Integer> sourceRowNumbers, UploadCommonData commonData)`
6. `*DbUniquenessChecker.java` — (optional) implements `DatabaseUniquenessChecker<Dto>`
7. `*TemplateConfig.java` — `@Configuration` producing `TemplateDefinition<Dto>` bean

### Logging

MUST use `@Slf4j` and SLF4J placeholder syntax (`log.info("x={}", x)`). No string concatenation in log calls.

### Error messages

User-facing messages and internal log messages MUST be in Korean.

---

## Testing Conventions

- Unit tests: `*Test.java` in matching package under `src/test/java`
- Integration tests: `com.foo.excel.integration` package
- MUST use `@SpringBootTest` + `MockMvc` for full integration tests
- Component-level tests create POI workbooks in-memory
- Test class names MUST match: `FooService` → `FooServiceTest`

---

## Testing Rules

- Always add `throws Exception` to test methods when working with checked exceptions
- After any code change, run the relevant test suite before committing
- When using assertion libraries, prefer explicit typed assertions to avoid ambiguous overload issues (e.g., use `assertEquals(expected, actual)` with matching types)

---

## Session Management

- For multi-phase implementation plans, commit after each phase is complete and tests pass
- Break large tasks into explicit phases and confirm each before proceeding
