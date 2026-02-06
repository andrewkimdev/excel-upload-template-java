# Excel Upload Feature — Final Specification (As Implemented)

> **This document reflects the actual implementation as of 2026-02-05.**
> For the original design-time spec, see [SPEC.md](SPEC.md).
> The "Changes from Original Spec" section at the end catalogs every divergence.

---

## Core Decisions

### Data Operations
| Decision | Value |
|----------|-------|
| Row identity strategy | Upsert on natural key |
| Validation failure scope | Reject entire file (all-or-nothing) |
| Primary format | `.xlsx` |
| Legacy format | `.xls` (auto-convert to `.xlsx` on upload) |

### Excel Template Structure
| Decision | Value |
|----------|-------|
| Structure pattern | Title → Grouped Headers → Primary Header → Data → Footer Notes |
| Header row | Configurable per template (via `ExcelImportConfig`) |
| Data start row | Configurable per template |
| Footer detection | Marker-based (default: `※`) |
| Header footnotes | Always present (e.g., 규격1), 모델명1)) — use CONTAINS/STARTS_WITH matching |
| Column skipping | Not a framework feature — the parser reads only columns with `@ExcelColumn` annotations |

### Data Types
| Type | Format | Notes |
|------|--------|-------|
| Date | `yyyy-MM-dd` | Strict |
| DateTime | `yyyy-MM-dd HH:mm:ss` | Strict |
| Boolean | `true`/`false` | Blank → false, `Y`/`y` → true |
| String | As-is | Trimmed |
| Numeric | Java BigDecimal | For precision |

### File Handling
| Decision | Value |
|----------|-------|
| Processing storage | Temp file on disk |
| Retention period | 30 days (configurable via `application.properties`) |
| Max file size | 10 MB (configurable, enforced at both Spring multipart and application levels) |
| Max rows | 10,000 (configurable) |
| Auto-conversion | `.xls` → `.xlsx` on upload |
| Security | Magic-byte validation, XXE/Zip Bomb protection, filename sanitization, formula injection prevention |

---

## Column Reference System

**Excel letter notation** (A, B, ..., Z, AA, AB, ...) is the only supported column reference format.

```java
@ExcelColumn(header = "물품명", column = "C")   // Letter: Excel notation
```

When `column` is set, the parser **verifies** that the actual header at that position matches `header` using `matchMode`.
When `column` is omitted, the parser **auto-detects** the column by scanning the header row.

### Column Letter Utility Class

```java
package com.foo.excel.util;

public final class ExcelColumnUtil {
    private ExcelColumnUtil() {}
    public static int letterToIndex(String column) { ... }     // A=0, B=1, ..., AA=26
    public static String indexToLetter(int index) { ... }      // 0=A, 1=B, ..., 26=AA
}
```

### Quick Reference Table

| Letter | Index | Letter | Index | Letter | Index |
|--------|-------|--------|-------|--------|-------|
| A | 0 | J | 9 | S | 18 |
| B | 1 | K | 10 | T | 19 |
| C | 2 | L | 11 | U | 20 |
| D | 3 | M | 12 | V | 21 |
| E | 4 | N | 13 | W | 22 |
| F | 5 | O | 14 | X | 23 |
| G | 6 | P | 15 | Y | 24 |
| H | 7 | Q | 16 | Z | 25 |
| I | 8 | R | 17 | AA | 26 |

---

## Annotations

### @ExcelColumn

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {
    String header();                                          // Header text (verification + display name)
    String column() default "";                               // Letter notation ("C"); verified against header
    String dateFormat() default "yyyy-MM-dd";                 // Date/DateTime format pattern
    HeaderMatchMode matchMode() default HeaderMatchMode.CONTAINS;  // Header matching/verification strategy
    String errorPrefix() default "";                          // Custom error message prefix
    boolean required() default true;                          // Fail-fast if column unresolvable
}
```

> **`required` vs JSR-380:** `required` controls whether the **column must exist** in the template.
> JSR-380 annotations (`@NotBlank`, etc.) control whether the **cell value must be present** in each row.
> These are orthogonal concerns.

### HeaderMatchMode

```java
public enum HeaderMatchMode {
    EXACT,        // Case-insensitive, trimmed exact match
    CONTAINS,     // Header contains this text (default)
    STARTS_WITH,  // Header starts with this text
    REGEX         // Header matches this regex pattern
}
```

### @ExcelUnique

```java
@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelUnique {
    String message() default "중복된 값입니다";
    boolean checkDatabase() default true;
    boolean checkWithinFile() default true;
}
```

### @ExcelCompositeUnique

```java
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ExcelCompositeUniques.class)
public @interface ExcelCompositeUnique {
    String[] fields();
    String message() default "중복된 조합입니다";
}
```

---

## ExcelImportConfig Interface

```java
package com.foo.excel.config;

public interface ExcelImportConfig {
    default int getHeaderRow() { return 1; }            // 1-indexed
    default int getDataStartRow() { return 2; }         // 1-indexed
    default int getSheetIndex() { return 0; }           // 0-indexed
    default String getFooterMarker() { return "※"; }
    String[] getNaturalKeyFields();                     // Fields forming the natural key for upsert
    default String getErrorColumnName() { return "_ERRORS"; }
}
```

> **Note:** The original spec included `getSkipColumnIndices()` and `getSkipColumns()` methods.
> These were removed because the parser never used them — it reads only columns that have
> matching `@ExcelColumn` annotations on the DTO, making explicit skip lists unnecessary.

---

## Template Registration System

Templates are auto-discovered via Spring bean registration. Each template type is represented
by a `TemplateDefinition<T>` bean that ties together:

```java
public class TemplateDefinition<T> {
    private final String templateType;                        // e.g., "tariff-exemption"
    private final Class<T> dtoClass;                          // e.g., TariffExemptionDto.class
    private final ExcelImportConfig config;                   // Import configuration
    private final PersistenceHandler<T> persistenceHandler;   // Save logic
    private final DatabaseUniquenessChecker<T> dbUniquenessChecker; // DB uniqueness (nullable)
}
```

### SPI Interfaces

```java
public interface PersistenceHandler<T> {
    SaveResult saveAll(List<T> rows);
    record SaveResult(int created, int updated) {}
}

public interface DatabaseUniquenessChecker<T> {
    List<RowError> check(List<T> rows, Class<T> dtoClass, List<Integer> sourceRowNumbers);
}
```

### Registering a New Template

Create a `@Configuration` class that produces a `TemplateDefinition<T>` bean:

```java
@Configuration
public class MyTemplateConfig {
    @Bean
    public TemplateDefinition<MyDto> myTemplate(
            MyService persistenceHandler,
            MyDbUniquenessChecker dbUniquenessChecker) {
        return new TemplateDefinition<>(
                "my-template",
                MyDto.class,
                new MyImportConfig(),
                persistenceHandler,
                dbUniquenessChecker
        );
    }
}
```

---

## Security Layer: SecureExcelUtils

The implementation includes a comprehensive security utility class not in the original spec:

| Protection | Method | Description |
|-----------|--------|-------------|
| XXE / Zip Bomb | `createWorkbook(Path)` | Opens XLSX via `OPCPackage` with read-only access; configures `IOUtils` byte limits |
| Magic-byte validation | `validateFileContent(Path)` | Checks XLSX (`PK`) and XLS (`D0 CF 11 E0`) magic bytes |
| Path traversal | `sanitizeFilename(String)` | Strips directory components, control chars, dangerous chars; allows Korean |
| Formula injection | `sanitizeForExcelCell(String)` | Prefixes cells starting with `=`, `+`, `-`, `@`, `\t`, `\r`, `\n` with `'` |
| Stream validation | `validateStreamContent(InputStream, String)` | Validates magic bytes from a stream |

Global POI limits (set in `static` initializer):
- `MAX_BYTE_ARRAY_SIZE` = 200 MB
- `MAX_RECORD_LENGTH` = 100 MB
- `MAX_TEXT_SIZE` = 10 MB

---

## Example DTO (Tariff Exemption)

The DTO is a **pure data carrier** — it does NOT implement `ExcelImportConfig`. The config is a separate class.

```java
package com.foo.excel.templates.samples.tariffexemption;

@Data
@ExcelCompositeUnique(
    fields = {"itemName", "specification", "hsCode"},
    message = "물품명 + 규격 + HSK 조합이 중복됩니다"
)
public class TariffExemptionDto {

    @ExcelColumn(header = "순번", column = "B")
    private Integer sequenceNo;

    @ExcelColumn(header = "물품명", column = "C")
    @NotBlank(message = "물품명은 필수 입력 항목입니다")
    @Size(max = 100, message = "물품명은 100자 이내로 입력하세요")
    private String itemName;

    @ExcelColumn(header = "규격", column = "D", matchMode = STARTS_WITH)
    @Size(max = 200, message = "규격은 200자 이내로 입력하세요")
    private String specification;

    @ExcelColumn(header = "모델명", column = "E", matchMode = STARTS_WITH)
    @Size(max = 100, message = "모델명은 100자 이내로 입력하세요")
    private String modelName;

    @ExcelColumn(header = "HSK", column = "F")
    @Pattern(regexp = "^\\d{4}\\.\\d{2}-\\d{4}$", message = "HSK 형식이 올바르지 않습니다 (예: 8481.80-2000)")
    private String hsCode;

    @ExcelColumn(header = "관세율", column = "H")
    @DecimalMin(value = "0") @DecimalMax(value = "100")
    private BigDecimal tariffRate;

    @ExcelColumn(header = "단가", column = "I")
    @DecimalMin(value = "0")
    private BigDecimal unitPrice;

    @ExcelColumn(header = "제조용", column = "J")
    @Min(value = 0)
    private Integer qtyForManufacturing;

    @ExcelColumn(header = "수리용", column = "L")
    @Min(value = 0)
    private Integer qtyForRepair;

    @ExcelColumn(header = "연간수입", column = "N")
    @DecimalMin(value = "0")
    private BigDecimal annualImportEstimate;

    @ExcelColumn(header = "심의결과", column = "O", required = false)
    private String reviewResult;

    @ExcelColumn(header = "연간 예상소요량", column = "Q")
    @Min(value = 0)
    private Integer annualExpectedQty;
}
```

### Separate Import Config

```java
public class TariffExemptionImportConfig implements ExcelImportConfig {
    @Override public int getHeaderRow() { return 4; }
    @Override public int getDataStartRow() { return 7; }
    @Override public String getFooterMarker() { return "※"; }
    @Override public String[] getNaturalKeyFields() {
        return new String[]{"itemName", "specification", "hsCode"};
    }
}
```

---

## Processing Flow

```
User uploads .xls/.xlsx
        │
        ▼
┌─────────────────┐
│ Controller       │  File size check (application-level, in addition to Spring multipart limits)
│ checkFileSize()  │  SecurityException → generic Korean error
└────────┬────────┘
         ▼
┌─────────────────┐
│ Orchestrator     │  Finds TemplateDefinition by templateType string
│ processUpload()  │  Creates UUID-based temp subdirectory
└────────┬────────┘
         ▼
┌─────────────────┐
│ Conversion       │  sanitizeFilename() → validateFileContent() (magic bytes)
│ ensureXlsxFormat │  .xls → convert via HSSFWorkbook → XSSFWorkbook
│                  │  .xlsx → save directly (after validation)
└────────┬────────┘
         ▼
┌─────────────────┐
│ Parser           │  SecureExcelUtils.createWorkbook() (XXE/Zip Bomb protection)
│ parse()          │  Header verification: actual headers vs @ExcelColumn expectations
│                  │  Fail-fast on required column mismatch (ColumnResolutionBatchException)
│                  │  ThreadLocal<DataFormatter> for thread safety
│                  │  Footer marker detection stops reading
│                  │  Returns ParseResult<T> (rows, sourceRowNumbers, columnMappings, parseErrors)
└────────┬────────┘
         ▼
┌─────────────────┐
│ Max row check    │  Reject if rows > maxRows (configurable)
└────────┬────────┘
         ▼
┌─────────────────┐
│ Validation       │  Pass 1: JSR-380 (@NotBlank, @Size, @Pattern, @Min, @DecimalMin, etc.)
│ validate()       │  Pass 2: Within-file uniqueness (@ExcelUnique, @ExcelCompositeUnique)
└────────┬────────┘
         ▼
┌─────────────────┐
│ DB Uniqueness    │  Pass 3: Database-level uniqueness via DatabaseUniquenessChecker
│ checkDbUnique()  │  (optional — null checker is safe, returns empty list)
└────────┬────────┘
         ▼
┌─────────────────┐
│ Merge errors     │  Parse errors + validation errors + DB errors → ExcelValidationResult
└────────┬────────┘
         │
    ┌────┴────┐
    │         │
    ▼         ▼
 VALID     HAS ERRORS
    │         │
    ▼         ▼
 Persist   Generate error report
 (upsert)  (copy original + add _ERRORS column, highlight error cells in rose)
    │         │
    ▼         ▼
 Success   Summary page + error file download link
```

---

## API Endpoints

### REST API

```
POST /api/excel/upload/{templateType}
  Request: multipart/form-data with "file" parameter
  Accepts: .xls or .xlsx

  Response 200 (success):
    {
      "success": true,
      "rowsProcessed": 150,
      "rowsCreated": 100,
      "rowsUpdated": 50,
      "message": "데이터 업로드 완료"
    }

  Response 400 (validation errors):
    {
      "success": false,
      "rowsProcessed": 150,
      "errorRows": 8,
      "errorCount": 12,
      "downloadUrl": "/api/excel/download/{errorFileId}",
      "message": "8개 행에서 12개 오류가 발견되었습니다"
    }

  Response 400 (bad input):
    { "success": false, "message": "..." }

  Response 400 (security violation):
    { "success": false, "message": "파일 보안 검증에 실패했습니다" }

  Response 500 (internal error):
    { "success": false, "message": "파일 처리 중 오류가 발생했습니다. 관리자에게 문의하세요." }

GET /api/excel/download/{fileId}
  - fileId must be a valid UUID (strict regex validation prevents path traversal)
  - Returns error-annotated Excel file
  - Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet
  - 404 if file not found, 400 if UUID format invalid

GET /api/excel/template/{templateType}
  - NOT YET IMPLEMENTED (returns 501)
```

### Thymeleaf UI Endpoints

```
GET  /          → upload.html (upload form with template type selector)
POST /upload    → result.html (success/error summary with download link)
```

---

## Configuration (application.properties)

```properties
# Server
server.port=8080

# Excel Import Settings
excel.import.max-file-size-mb=10
excel.import.max-rows=10000
excel.import.retention-days=30
excel.import.temp-directory=${java.io.tmpdir}/excel-imports
excel.import.error-column-name=_ERRORS

# H2 Database (prototype only)
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.h2.console.enabled=true
spring.h2.console.settings.web-allow-others=false
spring.jpa.hibernate.ddl-auto=create-drop

# File Upload Limits
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

> **Note:** The original spec included `excel.import.error-cell-color=#FFCCCC`.
> This is not configurable — the error cell color is hardcoded as `IndexedColors.ROSE` in `ExcelErrorReportService`.

---

## Project Structure (Actual)

```
src/main/java/com/foo/excel/
├── ExcelUploadApplication.java          # Spring Boot entry point
├── annotation/
│   ├── ExcelColumn.java                 # Core column mapping annotation
│   ├── ExcelCompositeUnique.java        # Multi-field uniqueness (class-level, repeatable)
│   ├── ExcelCompositeUniques.java       # Container for @ExcelCompositeUnique
│   ├── ExcelUnique.java                 # Single-field uniqueness (field-level)
│   └── HeaderMatchMode.java             # EXACT | CONTAINS | STARTS_WITH | REGEX
├── config/
│   ├── ExcelImportConfig.java           # Per-template config interface
│   └── ExcelImportProperties.java       # @ConfigurationProperties for global settings
├── controller/
│   └── ExcelUploadController.java       # REST API + Thymeleaf endpoints
├── service/
│   ├── DatabaseUniquenessChecker.java   # SPI: DB-level uniqueness check
│   ├── ExcelConversionService.java      # .xls → .xlsx with security validation
│   ├── ExcelErrorReportService.java     # Generates error-annotated Excel files
│   ├── ExcelImportOrchestrator.java     # Orchestrates full upload → persist/error flow
│   ├── ExcelParserService.java          # POI workbook → List<DTO> parsing
│   ├── ColumnResolutionException.java   # Column header mismatch error
│   ├── ColumnResolutionBatchException.java  # Aggregated column resolution errors
│   ├── ExcelValidationService.java      # JSR-380 + within-file uniqueness
│   ├── PersistenceHandler.java          # SPI: save/upsert logic
│   ├── TempFileCleanupService.java      # Scheduled cleanup of expired temp files
│   └── TemplateDefinition.java          # Ties DTO class + config + handlers together
├── templates/samples/tariffexemption/
│   ├── TariffExemption.java             # JPA entity
│   ├── TariffExemptionDbUniquenessChecker.java
│   ├── TariffExemptionDto.java          # DTO with @ExcelColumn + JSR-380
│   ├── TariffExemptionImportConfig.java # ExcelImportConfig implementation
│   ├── TariffExemptionRepository.java   # Spring Data JPA repository
│   ├── TariffExemptionService.java      # PersistenceHandler implementation
│   └── TariffExemptionTemplateConfig.java # @Configuration producing TemplateDefinition bean
├── util/
│   ├── ExcelColumnUtil.java             # Letter ↔ index conversion
│   └── SecureExcelUtils.java            # XXE, Zip Bomb, magic bytes, filename sanitization
└── validation/
    ├── CellError.java                   # Single cell error DTO
    ├── ExcelValidationResult.java       # Aggregated validation result (mutable via merge())
    ├── RowError.java                    # Row-level error with cell errors
    └── UniqueConstraintValidator.java   # Programmatic uniqueness validation component

src/main/resources/
├── application.properties
├── static/
│   └── style.css
└── templates/
    ├── upload.html                      # Upload form + H2 console button (dev only)
    └── result.html                      # Success/error summary
```

---

## Adding a New Template Type

Create a subpackage under `com.foo.excel.templates` containing:

| File | Role |
|------|------|
| `*Dto.java` | DTO with `@ExcelColumn` + JSR-380 annotations |
| `*ImportConfig.java` | Implements `ExcelImportConfig` (header row, data start row, etc.) |
| `*Entity.java` | JPA entity (if persistence needed) |
| `*Repository.java` | Spring Data JPA repository |
| `*Service.java` | Implements `PersistenceHandler<Dto>` |
| `*DbUniquenessChecker.java` | Implements `DatabaseUniquenessChecker<Dto>` (optional) |
| `*TemplateConfig.java` | `@Configuration` producing `TemplateDefinition<Dto>` bean |

The orchestrator auto-discovers templates via `List<TemplateDefinition<?>>` injection.

---

## Changes from Original Spec

| # | Area | Original Spec | Actual Implementation | Reason |
|---|------|---------------|----------------------|--------|
| 1 | `@ExcelColumn.required()` | Present | **Restored** | Controls whether the column must exist in the template; orthogonal to JSR-380 cell-value validation |
| 2 | `ExcelImportConfig.getSkipColumnIndices()` | Present | **Removed** | Parser reads only `@ExcelColumn`-annotated columns, making skip lists unnecessary |
| 3 | `ExcelImportConfig.getSkipColumns()` | Present | **Removed** | Same as above |
| 4 | DTO implements `ExcelImportConfig` | Yes | **No** — separate config class | Cleaner separation of concerns: DTO is a pure data carrier |
| 5 | DTO package | `com.foo.excel.dto` | `com.foo.excel.templates.samples.tariffexemption` | Template-based subpackage structure for better modularity |
| 6 | Template registration | Not specified | `TemplateDefinition<T>` bean pattern | Enables pluggable multi-template support via Spring auto-discovery |
| 7 | `PersistenceHandler` interface | Not specified | Implemented with `SaveResult` record | SPI for template-specific save/upsert logic |
| 8 | `DatabaseUniquenessChecker` interface | Not specified | Implemented | SPI for template-specific DB uniqueness checks |
| 9 | `SecureExcelUtils` | Not specified | 236 lines of security utilities | XXE, Zip Bomb, magic bytes, path traversal, formula injection |
| 10 | `TempFileCleanupService` | Not specified | `@Scheduled` cleanup service | Enforces retention policy |
| 11 | JPA entity + repository | Not specified | Fully implemented for tariff exemption | Required for actual persistence |
| 12 | `error-cell-color` config | Configurable (`#FFCCCC`) | Hardcoded (`IndexedColors.ROSE`) | Simplification |
| 13 | `GET /api/excel/template/{type}` | Functional | Returns 501 Not Implemented | Not yet built |
| 14 | Thymeleaf UI endpoints | Not specified | `GET /` + `POST /upload` | Additional consumer interface for browser-based use |
| 15 | API error response field | `totalRows` | `rowsProcessed` | Unified field name for both success and error responses |
| 16 | API error response field | — | `errorFileId` added | Used by Thymeleaf template for URL construction |
| 17 | `spring.h2.console.settings.web-allow-others` | Not specified | `false` | Security hardening |
| 18 | Controller security exceptions | Not specified | Separate `SecurityException` handler | Returns generic Korean message, logs details |
| 19 | Controller file size check | Not specified | Application-level check before processing | In addition to Spring multipart limits |
| 20 | Download endpoint UUID validation | Not specified | Strict UUID regex check | Prevents path traversal |
