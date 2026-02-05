# Excel Upload Feature

Spring Boot application for uploading, parsing, validating, and importing Excel files (.xlsx/.xls) with configurable templates.

## Requirements

- JDK 17+
- Gradle 8+

## Quick Start

```bash
./gradlew bootRun
```

Open http://localhost:8080 to access the upload form.

## API Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `POST` | `/api/excel/upload/{templateType}` | Upload and process an Excel file |
| `GET` | `/api/excel/download/{fileId}` | Download an error report |
| `GET` | `/api/excel/template/{templateType}` | Download a blank template (not yet implemented) |
| `POST` | `/upload` | Form submission (Thymeleaf) |
| `GET` | `/` | Upload form (Thymeleaf) |

### Upload Response (success)

```json
{
  "success": true,
  "rowsProcessed": 150,
  "rowsCreated": 150,
  "rowsUpdated": 0,
  "message": "데이터 업로드 완료"
}
```

### Upload Response (validation errors)

```json
{
  "success": false,
  "rowsProcessed": 150,
  "errorRows": 8,
  "errorCount": 12,
  "downloadUrl": "/api/excel/download/{errorFileId}",
  "message": "8개 행에서 12개 오류가 발견되었습니다"
}
```

## Architecture

```
src/main/java/com/foo/excel/
├── ExcelUploadApplication.java
├── annotation/          # @ExcelColumn, @ExcelUnique, @ExcelCompositeUnique, HeaderMatchMode
├── config/
│   ├── ExcelImportConfig.java               # Template layout interface (header row, data start, skip columns)
│   ├── ExcelImportProperties.java           # Global properties (file size, max rows, temp dir)
│   ├── TariffExemptionImportConfig.java     # Example: config for tariff exemption template
│   └── TariffExemptionTemplateConfig.java   # Example: wires the TemplateDefinition bean
├── controller/          # ExcelUploadController (REST + Thymeleaf)
├── dto/                 # TariffExemptionDto (example template DTO)
├── service/
│   ├── ExcelConversionService.java                # .xls -> .xlsx auto-conversion (with security checks)
│   ├── ExcelParserService.java                    # Excel -> List<DTO> with type coercion
│   ├── ExcelValidationService.java                # JSR-380 + within-file uniqueness validation
│   ├── ExcelErrorReportService.java               # Error-annotated Excel generation
│   ├── ExcelImportOrchestrator.java               # End-to-end pipeline (discovers templates via Spring)
│   ├── TemplateDefinition.java                    # Type-safe bundle: DTO class + config + handlers
│   ├── PersistenceHandler.java                    # Strategy interface for saving parsed rows
│   ├── DatabaseUniquenessChecker.java             # Strategy interface for DB-level duplicate checks
│   ├── TempFileCleanupService.java                # Scheduled cleanup of expired temp/error files
│   ├── TariffExemptionService.java                # Example: implements PersistenceHandler
│   └── TariffExemptionDbUniquenessChecker.java    # Example: implements DatabaseUniquenessChecker
├── util/
│   ├── ExcelColumnUtil.java                       # Column letter/index conversion
│   └── SecureExcelUtils.java                      # Security utilities (XXE, zip bomb, path traversal protection)
└── validation/          # CellError, RowError, ExcelValidationResult, UniqueConstraintValidator
```

## Processing Pipeline

1. **File size check** -- reject files over 10 MB
2. **Filename sanitization** -- prevent path traversal attacks
3. **Content validation** -- verify file magic bytes match extension (XLSX/XLS)
4. **Format conversion** -- `.xls` files are auto-converted to `.xlsx`
5. **Secure parsing** -- Excel opened with XXE and zip bomb protections
6. **Header validation** -- match DTO `@ExcelColumn` annotations against the header row
7. **Row parsing** -- read data rows, skip blanks, stop at footer marker (`※`)
8. **Type coercion** -- String (trimmed), Integer, BigDecimal, LocalDate, LocalDateTime, Boolean (`Y`/`true` -> true); parse errors are collected per cell
9. **JSR-380 validation** -- `@NotBlank`, `@Size`, `@Pattern`, `@DecimalMin`/`@DecimalMax`, `@Min`
10. **Within-file uniqueness** -- `@ExcelUnique` (single field), `@ExcelCompositeUnique` (composite key)
11. **Database uniqueness** -- optional per-template check via `DatabaseUniquenessChecker`
12. **Error merge** -- parse errors, validation errors, and DB uniqueness errors are combined
13. **Result** -- success with row counts, or error report Excel with `_ERRORS` column and red-highlighted cells

## Security

This module includes built-in protections against common file upload vulnerabilities:

| Threat | Protection | Implementation |
|--------|------------|----------------|
| **XXE Attack** | Secure Apache POI configuration | `SecureExcelUtils.createWorkbook()` |
| **Zip Bomb** | Memory allocation limits | `IOUtils.setByteArrayMaxOverride()` |
| **Path Traversal** | Filename sanitization | `SecureExcelUtils.sanitizeFilename()` |
| **File Disguise** | Magic bytes validation | `SecureExcelUtils.validateFileContent()` |
| **Formula Injection** | Cell value sanitization | `SecureExcelUtils.sanitizeForExcelCell()` |
| **Info Disclosure** | Generic error messages | Controller exception handling |

### Consumer Security Checklist

When integrating this module into your application, you **must** implement:

- [ ] **Authentication & Authorization** -- Add Spring Security, restrict upload endpoints to authorized users
- [ ] **CSRF Protection** -- Enable CSRF tokens in forms (Spring Security default)
- [ ] **Rate Limiting** -- Prevent abuse via Bucket4j, Spring Cloud Gateway, or WAF
- [ ] **Security Headers** -- Configure CSP, X-Frame-Options, X-Content-Type-Options
- [ ] **HTTPS** -- Deploy with TLS only, disable HTTP
- [ ] **H2 Console** -- Disable before production (`spring.h2.console.enabled=false`)

See `application.properties` for a detailed security checklist and configuration guidance.

## Adding a New Template

1. **DTO** -- Create a DTO class with `@ExcelColumn` and JSR-380 validation annotations on each field
2. **Config** -- Create an `ExcelImportConfig` implementation defining header row, data start row, skip columns, and footer marker
3. **Persistence** -- Implement `PersistenceHandler<T>` to save parsed rows (e.g., via a JPA repository)
4. **DB uniqueness** _(optional)_ -- Implement `DatabaseUniquenessChecker<T>` if duplicates should be checked against existing data
5. **Wire** -- Create a `@Configuration` class that produces a `TemplateDefinition<T>` `@Bean`; the orchestrator discovers it automatically

See the `TariffExemption*` classes for a complete example (`TariffExemptionDto`, `TariffExemptionImportConfig`, `TariffExemptionService`, `TariffExemptionDbUniquenessChecker`, `TariffExemptionTemplateConfig`).

## Configuration

Key properties in `application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `excel.import.max-file-size-mb` | `10` | Maximum upload file size in MB |
| `excel.import.max-rows` | `10000` | Maximum data rows per file |
| `excel.import.retention-days` | `30` | Days to keep temp/error files (shorter = more secure) |
| `excel.import.temp-directory` | `${java.io.tmpdir}/excel-imports` | Temp file storage path |
| `excel.import.error-column-name` | `_ERRORS` | Name of the error column in error reports |

## Testing

```bash
./gradlew test
```

Tests cover all layers:

| Test Class | Scope | What it verifies |
|------------|-------|------------------|
| `ExcelColumnUtilTest` | Unit | Column letter/index conversion, round-trip consistency |
| `ExcelConversionServiceTest` | Component | .xlsx passthrough, .xls conversion, merged regions, error cases |
| `ExcelParserServiceTest` | Component | Row parsing, footer detection, blank row skipping, merged cells, type coercion, header matching |
| `ExcelValidationServiceTest` | Component | JSR-380 constraints, boundary values, Korean error messages |
| `UniqueConstraintValidatorTest` | Component | Single-field and composite uniqueness, null handling, DB uniqueness via checker |
| `ExcelErrorReportServiceTest` | Component | `_ERRORS` column, red styling, formatted messages, valid output |
| `ExcelImportIntegrationTest` | Integration | Full upload/download flow via MockMvc, .xls auto-conversion, error handling |
