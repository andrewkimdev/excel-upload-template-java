# Excel Upload Feature

Spring Boot application for uploading, parsing, validating, and importing Excel files (`.xlsx` only) with configurable templates.

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

### Upload Request Contract (REST)

- Endpoint: `POST /api/excel/upload/{templateType}`
- Content type: `multipart/form-data`
- Parts:
  - `file`: Excel file (`.xlsx` only)
  - `commonData`: JSON (`application/json`)
- Required `commonData` fields:
  - `comeYear`
  - `comeSequence`
  - `uploadSequence`
  - `equipCode`
- Server-managed fields:
  - `createdBy`: forced to `user01` on server
  - `approvedYn`: forced to `N` on server
- If client sends `createdBy` or `approvedYn`, server ignores them.

### Upload Request Contract (Thymeleaf)

- Endpoint: `POST /upload`
- Form fields include:
  - `comeYear`
  - `comeSequence`
  - `uploadSequence`
  - `equipCode`
  - `file` (`.xlsx` only)
- Form does not expose `createdBy` or `approvedYn`.
- Server injects `createdBy=user01`, `approvedYn=N`.

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
│   ├── ExcelImportConfig.java               # Template layout interface (header row, data start row, footer marker)
│   └── ExcelImportProperties.java           # Global properties (file size, max rows, temp dir)
├── controller/          # ExcelUploadController (REST + Thymeleaf)
├── service/
│   ├── ExcelParserService.java                    # Excel -> List<DTO>; contains ColumnMapping + ParseResult records
│   ├── ColumnResolutionException.java             # Column header mismatch error
│   ├── ColumnResolutionBatchException.java        # Aggregated column resolution errors
│   ├── ExcelValidationService.java                # JSR-380 + within-file uniqueness validation
│   ├── ExcelErrorReportService.java               # Format-preserving error report generation (SXSSFWorkbook)
│   ├── ExcelImportOrchestrator.java               # End-to-end pipeline; contains ImportResult record
│   ├── TemplateDefinition.java                    # Type-safe bundle: DTO class + config + handlers
│   ├── PersistenceHandler.java                    # Strategy interface for saving parsed rows
│   ├── DatabaseUniquenessChecker.java             # Strategy interface for DB-level duplicate checks
│   └── TempFileCleanupService.java                # Scheduled cleanup of expired temp/error files
├── templates/samples/tariffexemption/             # Example template implementation
│   ├── TariffExemptionDto.java                    # DTO with @ExcelColumn + JSR-380 annotations
│   ├── TariffExemptionImportConfig.java           # ExcelImportConfig for tariff exemption
│   ├── TariffExemption.java                       # JPA entity
│   ├── TariffExemptionRepository.java             # Spring Data JPA repository
│   ├── TariffExemptionService.java                # Implements PersistenceHandler
│   ├── TariffExemptionDbUniquenessChecker.java    # Implements DatabaseUniquenessChecker
│   └── TariffExemptionTemplateConfig.java         # Wires the TemplateDefinition bean
├── util/
│   ├── ExcelColumnUtil.java                       # Column letter/index conversion
│   ├── SecureExcelUtils.java                      # Security utilities (XXE, zip bomb, path traversal protection)
│   └── WorkbookCopyUtils.java                     # Stateless workbook copy helpers (styles, values, metadata)
└── validation/          # CellError (record), RowError, ExcelValidationResult, UniqueConstraintValidator
```

## Processing Pipeline

1. **File size check** -- reject files over 10 MB
2. **Filename sanitization** -- prevent path traversal attacks
3. **Content validation** -- verify file magic bytes and allow only OOXML `.xlsx`; reject legacy `.xls`
4. **Row count pre-check** -- lightweight SAX/StAX count rejects obviously oversized files before full parsing
5. **Secure parsing** -- Excel opened with XXE and zip bomb protections; parser exits early if rows exceed limit
6. **Header verification** -- verify actual headers match `@ExcelColumn` expectations at declared positions; fail-fast with Korean error messages if required columns mismatch
7. **Row parsing** -- read data rows, skip blanks, stop at footer marker (`※`)
8. **Type coercion** -- String (trimmed), Integer, BigDecimal, LocalDate, LocalDateTime, Boolean (`Y`/`true` -> true); parse errors are collected per cell
9. **JSR-380 validation** -- `@NotBlank`, `@Size`, `@Pattern`, `@DecimalMin`/`@DecimalMax`, `@Min`
10. **Within-file uniqueness** -- `@ExcelUnique` (single field), `@ExcelCompositeUnique` (composite key)
11. **Database uniqueness** -- optional per-template check via `DatabaseUniquenessChecker`
12. **Error merge** -- parse errors, validation errors, and DB uniqueness errors are combined
13. **Result** -- success with row counts, or format-preserving error report Excel with `_ERRORS` column, red-highlighted error cells, original filename in download, and disclaimer footer

## Security

This module includes built-in protections against common file upload vulnerabilities:

| Threat | Protection | Implementation |
|--------|------------|----------------|
| **XXE Attack** | Secure Apache POI configuration | `SecureExcelUtils.createWorkbook()` |
| **Zip Bomb** | Memory allocation limits | `IOUtils.setByteArrayMaxOverride()` |
| **Path Traversal** | Filename sanitization | `SecureExcelUtils.sanitizeFilename()` |
| **File Disguise** | Magic bytes validation | `SecureExcelUtils.validateFileContent()` |
| **Legacy Format Risk** | Reject legacy `.xls`; accept `.xlsx` only | Upload validation flow |
| **Formula Injection** | Cell value sanitization | `SecureExcelUtils.sanitizeForExcelCell()` |
| **Resource exhaustion** | Two-tier row limit (SAX pre-count + parser early-exit) | `SecureExcelUtils.countRows()`, `ExcelParserService` |
| **Info Disclosure** | Generic Korean error messages | All controller catch blocks return safe messages; never `e.getMessage()` |

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
2. **Config** -- Create an `ExcelImportConfig` implementation defining header row, data start row, and footer marker
3. **Persistence** -- Implement `PersistenceHandler<T>` with `saveAll(List<T> rows, List<Integer> sourceRowNumbers, UploadCommonData commonData)` to save parsed rows merged with common fields
4. **DB uniqueness** _(optional)_ -- Implement `DatabaseUniquenessChecker<T>` if duplicates should be checked against existing data
5. **Wire** -- Create a `@Configuration` class that produces a `TemplateDefinition<T>` `@Bean`; the orchestrator discovers it automatically

See the `TariffExemption*` classes for a complete example (`TariffExemptionDto`, `TariffExemptionImportConfig`, `TariffExemptionService`, `TariffExemptionDbUniquenessChecker`, `TariffExemptionTemplateConfig`).

## Configuration

Key properties in `application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `excel.import.max-file-size-mb` | `10` | Maximum upload file size in MB |
| `excel.import.max-rows` | `10000` | Maximum data rows per file |
| `excel.import.pre-count-buffer` | `100` | Extra rows allowed in SAX pre-count to account for headers/blanks |
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
| `WorkbookCopyUtilsTest` | Unit | Style mapping (same-format, cross-format), error styles, cell value copying, column widths, merged regions |
| `ExcelConversionServiceTest` | Component | Legacy `.xls` rejection or format-policy checks (if conversion service remains, it must not enable `.xls` upload support) |
| `ExcelParserServiceTest` | Component | Row parsing, footer detection, blank row skipping, merged cells, type coercion, header matching, early-exit on row limit |
| `ExcelValidationServiceTest` | Component | JSR-380 constraints, boundary values, Korean error messages |
| `UniqueConstraintValidatorTest` | Component | Single-field and composite uniqueness, null handling, DB uniqueness via checker |
| `ExcelErrorReportServiceTest` | Component | `_ERRORS` column, red styling, format preservation, multi-sheet copy, disclaimer footer, `.meta` file, valid output |
| `ExcelImportIntegrationTest` | Integration | Full upload/download flow via MockMvc, `commonData` required validation, `.xlsx` success path, `.xls` rejection, error handling |
