# CLAUDE.md — Project Rules & Coding Standards

## Project Overview

Spring Boot 3.3.4 / Java 17 application for Excel file upload, parsing, validation, and import.
Uses Gradle 8+, Lombok, Apache POI 5.4.1, H2 (dev), Thymeleaf.

---

## Build & Test

```bash
./gradlew bootRun        # Run the app on http://localhost:8080
./gradlew test            # Run all tests
./gradlew build           # Full build with tests
```

---

## Java 17 Rules

### MUST use `Path.of()` — never `Paths.get()`
`Paths.get()` is effectively deprecated since Java 11. Always use `Path.of()`.
```java
// WRONG — do not introduce this
Path tempDir = Paths.get(tempDirectory);
// RIGHT
Path tempDir = Path.of(tempDirectory);
```

### MUST use records for immutable value carriers
When a class is a read-only data carrier (no mutation after construction), use Java 16+ records.
Static inner DTOs like `ParseResult`, `ColumnMapping`, and API response objects are good candidates.
**Only exception:** Classes that require Lombok `@Builder` or mutable state (e.g., `ExcelValidationResult.merge()`) remain as classes.

### MUST use switch expressions with arrow syntax
```java
return switch (mode) {
    case EXACT -> trimmedCell.equalsIgnoreCase(trimmedExpected);
    case CONTAINS -> trimmedCell.contains(trimmedExpected);
    // ...
};
```

### MUST use `var` for local variables where the type is obvious
Use `var` when the type is clear from context (e.g., `for (var hssfSheet : ...)`). Do NOT use `var` when the type is ambiguous.

### MUST use text blocks for multi-line strings
When constructing multi-line strings (error messages, SQL, etc.), use text blocks (`"""..."""`).

---

## Spring Boot 3.3.4 Rules

### MUST use Jakarta namespace — NEVER javax
Spring Boot 3.x requires `jakarta.*` packages. Any use of `javax.persistence`, `javax.validation`, etc. is a build-breaking error.
```java
// WRONG — will not compile
import javax.validation.constraints.NotBlank;
// RIGHT
import jakarta.validation.constraints.NotBlank;
```

### MUST use `@RequiredArgsConstructor` for constructor injection
All `@Service`, `@Component`, `@Controller`, and `@Configuration` beans MUST use Lombok `@RequiredArgsConstructor` with `private final` fields. Do NOT write explicit constructors unless custom initialization logic beyond field assignment is required.
```java
@Service
@RequiredArgsConstructor
public class MyService {
    private final SomeDependency dependency;
}
```

### `@ConfigurationProperties` binding
`ExcelImportProperties` uses `@Component` + `@ConfigurationProperties`. For any new config classes, MUST use `@EnableConfigurationProperties` or `@ConfigurationPropertiesScan` instead.

### MUST use `spring.servlet.multipart.*` prefix
Spring Boot 3.x uses `spring.servlet.multipart`. Never use the removed `spring.http.multipart` prefix.

---

## JPA / Entity Rules

### NEVER use `@Data` on JPA `@Entity` classes
Lombok `@Data` generates `equals()`/`hashCode()` using ALL fields (including `@Id`), which breaks JPA identity semantics and causes issues with detached entities, collections, and lazy loading. This rule has zero exceptions.

For entities, ALWAYS use:
```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class MyEntity {
    // MUST override equals/hashCode using business key or @Id only
}
```

### MUST use business key or `@Id` for equals/hashCode
Never include all fields. Use either the `@Id` field or the natural business key fields.

---

## Thread Safety Rules

### Apache POI `DataFormatter` is NOT thread-safe
NEVER use a shared static `DataFormatter` instance. In a singleton Spring `@Service`, this causes data corruption under concurrent requests.
```java
// WRONG — NEVER do this
private static final DataFormatter DATA_FORMATTER = new DataFormatter();

// RIGHT — option 1: local variable
DataFormatter formatter = new DataFormatter();

// RIGHT — option 2: ThreadLocal (used in this project)
private static final ThreadLocal<DataFormatter> DATA_FORMATTER =
    ThreadLocal.withInitial(DataFormatter::new);
```

### General rule for non-thread-safe POI classes
Any POI class that is not documented as thread-safe MUST be used as a local variable or via `ThreadLocal`. Never store as a shared static field in a Spring singleton.

---

## Security Rules

### NEVER expose internal details to users
Controllers MUST catch all exceptions and return generic Korean error messages. Log full error details at `error` level but NEVER return exception messages, stack traces, or file paths to the user.
```java
catch (Exception e) {
    log.error("Upload failed", e);
    // NEVER: return e.getMessage()
    // ALWAYS: return generic message
    return "파일 처리 중 오류가 발생했습니다. 관리자에게 문의하세요.";
}
```

### MUST use `SecureExcelUtils` for all file operations
- Open workbooks via `SecureExcelUtils.createWorkbook()` — NEVER via raw `WorkbookFactory.create()`
- Sanitize user-provided filenames via `SecureExcelUtils.sanitizeFilename()`
- Validate file content (magic bytes) via `SecureExcelUtils.validateFileContent()`
- Sanitize cell values written to error reports via `SecureExcelUtils.sanitizeForExcelCell()`

### Thymeleaf security
- ALWAYS use `th:text` (auto-escapes HTML). NEVER use `th:utext` with user data.
- For URLs, MUST use Thymeleaf URL expressions: `th:href="@{/api/excel/download/{id}(id=${result.errorFileId})}"` — NEVER use raw `th:href="${...}"`.

---

## Code Conventions

### Package structure
```
com.foo.excel/
├── annotation/          # Custom annotations (@ExcelColumn, @ExcelUnique, etc.)
├── config/              # Framework config (ExcelImportConfig interface, ExcelImportProperties)
├── controller/          # Spring MVC controllers
├── service/             # Core services + strategy interfaces
├── util/                # Utility classes (ExcelColumnUtil, SecureExcelUtils)
├── validation/          # Validation result DTOs (CellError, RowError, etc.)
└── templates/samples/   # Sample template implementations
    └── tariffexemption/ # Entity, DTO, Config, Service, Repository, Checker
```

### New templates MUST go under `templates/`
When adding a new Excel template type, create a subpackage under `com.foo.excel.templates` containing:
1. `*Dto.java` — DTO with `@ExcelColumn` + JSR-380 annotations. MUST NOT implement `ExcelImportConfig`.
2. `*ImportConfig.java` — implements `ExcelImportConfig`
3. `*Entity.java` — JPA entity (if persistence needed)
4. `*Repository.java` — Spring Data JPA repository
5. `*Service.java` — implements `PersistenceHandler<Dto>`
6. `*DbUniquenessChecker.java` — (optional) implements `DatabaseUniquenessChecker<Dto>`
7. `*TemplateConfig.java` — `@Configuration` class producing `TemplateDefinition<Dto>` bean

### Logging
MUST use Lombok `@Slf4j` on all classes that need logging. MUST use SLF4J placeholder syntax:
```java
log.info("Processed {} rows", count);     // RIGHT
log.info("Processed " + count + " rows"); // WRONG — string concatenation in log calls
```

### Error messages
User-facing error messages MUST be in Korean. Internal log messages MUST be in English.
```java
throw new IllegalArgumentException("물품명은 필수 입력 항목입니다");  // User-facing
log.error("Failed to parse Excel file", e);                           // Internal
```

---

## Testing Conventions

- Unit tests: `*Test.java` in matching package under `src/test/java`
- Integration tests: in `com.foo.excel.integration` package
- MUST use `@SpringBootTest` + `MockMvc` for full integration tests
- Component-level tests create POI workbooks in-memory for test data
- Test class names MUST match the class under test: `FooService` → `FooServiceTest`
