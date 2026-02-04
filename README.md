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
├── config/              # ExcelImportConfig interface, ExcelImportProperties
├── controller/          # ExcelUploadController (REST + Thymeleaf)
├── dto/                 # TariffExemptionDto (example template)
├── service/
│   ├── ExcelConversionService.java      # .xls -> .xlsx auto-conversion
│   ├── ExcelParserService.java          # Excel -> List<DTO> with type coercion
│   ├── ExcelValidationService.java      # JSR-380 + uniqueness validation
│   ├── ExcelErrorReportService.java     # Error-annotated Excel generation
│   └── ExcelImportOrchestrator.java     # End-to-end pipeline
├── util/                # ExcelColumnUtil (column letter/index conversion)
└── validation/          # CellError, RowError, ExcelValidationResult, UniqueConstraintValidator
```

## Processing Pipeline

1. **File size check** -- reject files over 10 MB
2. **Format conversion** -- `.xls` files are auto-converted to `.xlsx`
3. **Header validation** -- match DTO `@ExcelColumn` annotations against the header row
4. **Row parsing** -- read data rows, skip blanks, stop at footer marker (`※`)
5. **Type coercion** -- String (trimmed), Integer, BigDecimal, LocalDate, LocalDateTime, Boolean (`Y`/`true` -> true)
6. **JSR-380 validation** -- `@NotBlank`, `@Size`, `@Pattern`, `@DecimalMin`/`@DecimalMax`, `@Min`
7. **Uniqueness validation** -- `@ExcelUnique` (single field), `@ExcelCompositeUnique` (composite key)
8. **Result** -- success with row counts, or error report Excel with `_ERRORS` column and red-highlighted cells

## Adding a New Template

1. Create a DTO class implementing `ExcelImportConfig`
2. Annotate fields with `@ExcelColumn` (and validation annotations)
3. Register the template type in `ExcelImportOrchestrator.TEMPLATE_REGISTRY`

See `TariffExemptionDto` for a complete example.

## Configuration

Key properties in `application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `excel.import.max-file-size-mb` | `10` | Maximum upload file size in MB |
| `excel.import.max-rows` | `10000` | Maximum data rows per file |
| `excel.import.retention-days` | `30` | Days to keep temp/error files |
| `excel.import.temp-directory` | `${java.io.tmpdir}/excel-imports` | Temp file storage path |
| `excel.import.error-column-name` | `_ERRORS` | Name of the error column in error reports |
| `excel.import.error-cell-color` | `#FFCCCC` | Background color for error cells |

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
| `UniqueConstraintValidatorTest` | Component | Single-field and composite uniqueness, null handling |
| `ExcelErrorReportServiceTest` | Component | `_ERRORS` column, red styling, formatted messages, valid output |
| `ExcelImportIntegrationTest` | Integration | Full upload/download flow via MockMvc, .xls auto-conversion, error handling |
