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
│   ├── ExcelConversionService.java                # .xls -> .xlsx auto-conversion
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
├── util/                # ExcelColumnUtil (column letter/index conversion)
└── validation/          # CellError, RowError, ExcelValidationResult, UniqueConstraintValidator
```

## Processing Pipeline

1. **File size check** -- reject files over 10 MB
2. **Format conversion** -- `.xls` files are auto-converted to `.xlsx`
3. **Header validation** -- match DTO `@ExcelColumn` annotations against the header row
4. **Row parsing** -- read data rows, skip blanks, stop at footer marker (`※`)
5. **Type coercion** -- String (trimmed), Integer, BigDecimal, LocalDate, LocalDateTime, Boolean (`Y`/`true` -> true); parse errors are collected per cell
6. **JSR-380 validation** -- `@NotBlank`, `@Size`, `@Pattern`, `@DecimalMin`/`@DecimalMax`, `@Min`
7. **Within-file uniqueness** -- `@ExcelUnique` (single field), `@ExcelCompositeUnique` (composite key)
8. **Database uniqueness** -- optional per-template check via `DatabaseUniquenessChecker`
9. **Error merge** -- parse errors, validation errors, and DB uniqueness errors are combined
10. **Result** -- success with row counts, or error report Excel with `_ERRORS` column and red-highlighted cells

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
| `excel.import.retention-days` | `30` | Days to keep temp/error files |
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
