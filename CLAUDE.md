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

### Use `Path.of()` instead of `Paths.get()`
`Path.of()` is the preferred API since Java 11. `Paths.get()` is effectively deprecated.
```java
// WRONG
Path tempDir = Paths.get(tempDirectory);
// RIGHT
Path tempDir = Path.of(tempDirectory);
```

### Use records for immutable value carriers
When a class is a read-only data carrier (no mutation after construction), prefer Java 16+ records.
Static inner DTOs like `ParseResult`, `ColumnMapping`, and API response objects are good candidates.
**Exception:** Classes that require Lombok `@Builder` or mutable state (e.g., `ExcelValidationResult.merge()`) should remain as classes.

### Use switch expressions (already in use)
The project already uses Java 14+ switch expressions with arrow syntax. Continue this style:
```java
return switch (mode) {
    case EXACT -> trimmedCell.equalsIgnoreCase(trimmedExpected);
    case CONTAINS -> trimmedCell.contains(trimmedExpected);
    // ...
};
```

### Use `var` for local variables where the type is obvious
The project uses `var` in loops (e.g., `for (var hssfSheet : ...)`). Continue using it when the type is clear from context. Do NOT use `var` when the type is ambiguous.

### Use text blocks for multi-line strings
When constructing multi-line strings (error messages, SQL, etc.), prefer text blocks (`"""..."""`).

---

## Spring Boot 3.3.4 Rules

### Jakarta namespace (NOT javax)
Spring Boot 3.x requires `jakarta.*` packages. Never use `javax.persistence`, `javax.validation`, etc.
```java
// WRONG: import javax.validation.constraints.NotBlank;
// RIGHT:
import jakarta.validation.constraints.NotBlank;
```

### Constructor injection: use `@RequiredArgsConstructor` consistently
All Spring-managed beans should use Lombok `@RequiredArgsConstructor` with `private final` fields.
Do NOT write explicit constructors unless there is a specific reason (e.g., custom initialization logic beyond field assignment).
```java
// Standard pattern for all @Service, @Component, @Controller classes:
@Service
@RequiredArgsConstructor
public class MyService {
    private final SomeDependency dependency;
}
```
**Known inconsistency:** `ExcelImportOrchestrator` uses an explicit constructor where `@RequiredArgsConstructor` would suffice.

### `@ConfigurationProperties` binding
The project uses `@Component` + `@ConfigurationProperties` on `ExcelImportProperties`. This works, but the preferred Spring Boot 3.x pattern is `@EnableConfigurationProperties` or `@ConfigurationPropertiesScan`. For new config classes, prefer the newer approach.

### Multipart properties use `spring.servlet.multipart.*` prefix
Spring Boot 3.x uses the `spring.servlet.multipart` prefix. Never use the legacy `spring.http.multipart` prefix.

---

## JPA / Entity Rules

### NEVER use `@Data` on JPA `@Entity` classes
Lombok `@Data` generates `equals()`/`hashCode()` using ALL fields (including `@Id`), which breaks JPA identity semantics and can cause issues with detached entities, collections, and lazy loading.

For entities, use:
```java
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@Entity
public class MyEntity {
    // Override equals/hashCode using business key or @Id only
}
```
**Known inconsistency:** `TariffExemption.java` uses `@Data` on a JPA `@Entity`.

### Entity identity: use business key or `@Id` for equals/hashCode
Never include all fields. Either use the `@Id` field or the natural business key fields.

---

## Thread Safety Rules

### Apache POI `DataFormatter` is NOT thread-safe
Do not use a shared static `DataFormatter` instance in a singleton Spring `@Service`.
Either create a new instance per method call, or use `ThreadLocal<DataFormatter>`.
```java
// WRONG (current code in ExcelParserService):
private static final DataFormatter DATA_FORMATTER = new DataFormatter();

// RIGHT — option 1: local variable
DataFormatter formatter = new DataFormatter();

// RIGHT — option 2: ThreadLocal
private static final ThreadLocal<DataFormatter> DATA_FORMATTER =
    ThreadLocal.withInitial(DataFormatter::new);
```
**Known inconsistency:** `ExcelParserService` uses a shared static `DataFormatter`.

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

### New templates go under `templates/`
When adding a new Excel template type, create a subpackage under `com.foo.excel.templates` containing:
1. `*Dto.java` — DTO with `@ExcelColumn` + JSR-380 annotations
2. `*ImportConfig.java` — implements `ExcelImportConfig`
3. `*Entity.java` — JPA entity (if persistence needed)
4. `*Repository.java` — Spring Data JPA repository
5. `*Service.java` — implements `PersistenceHandler<Dto>`
6. `*DbUniquenessChecker.java` — (optional) implements `DatabaseUniquenessChecker<Dto>`
7. `*TemplateConfig.java` — `@Configuration` class producing `TemplateDefinition<Dto>` bean

### Logging
Use Lombok `@Slf4j` on all classes that need logging. Use SLF4J placeholder syntax:
```java
log.info("Processed {} rows", count);     // RIGHT
log.info("Processed " + count + " rows"); // WRONG
```

### Error messages
User-facing error messages should be in Korean. Internal log messages should be in English.
```java
throw new IllegalArgumentException("물품명은 필수 입력 항목입니다");  // User-facing
log.error("Failed to parse Excel file", e);                           // Internal
```

### Security — never expose internal details to users
Controllers must catch exceptions and return generic Korean error messages.
Log full error details at `error` level but return only safe messages.
```java
catch (Exception e) {
    log.error("Upload failed", e);
    // Do NOT: return e.getMessage()
    // DO: return generic message
    return "파일 처리 중 오류가 발생했습니다. 관리자에게 문의하세요.";
}
```

### Thymeleaf security
- Always use `th:text` (auto-escapes HTML). Never use `th:utext` with user data.
- For URLs, prefer Thymeleaf URL expressions: `th:href="@{/api/excel/download/{id}(id=${result.errorFileId})}"` instead of raw `th:href="${result.downloadUrl}"`.

---

## Resolved Issues (2026-02-05)

All previously tracked technical debt has been resolved:

1. `TariffExemption.java` — replaced `@Data` with `@Getter/@Setter` + business-key equals/hashCode
2. `ExcelParserService` — replaced static `DataFormatter` with `ThreadLocal<DataFormatter>`
3. `ExcelImportProperties` — replaced `Paths.get()` with `Path.of()`
4. `ExcelImportOrchestrator` — replaced explicit constructor with `@RequiredArgsConstructor`
5. `ExcelImportConfig` — removed dead `getSkipColumns()` method (never used by parser)
6. `result.html` — replaced raw `th:href` with Thymeleaf URL-built `@{...}` expression
7. `ExcelImportOrchestrator.doProcess()` — added `@SuppressWarnings("unchecked")` for inherent generic cast
8. `README.md` — updated architecture tree to match actual package structure

---

## Testing Conventions

- Unit tests: `*Test.java` in matching package under `src/test/java`
- Integration tests: in `com.foo.excel.integration` package
- Use `@SpringBootTest` + `MockMvc` for full integration tests
- Component-level tests create POI workbooks in-memory for test data
- Test class names match the class under test: `FooService` → `FooServiceTest`
