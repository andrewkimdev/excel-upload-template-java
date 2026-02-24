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
| `POST` | `/api/excel/upload/tariff-exemption` | Upload and process an Excel file (tariff template) |
| `GET` | `/api/excel/download/{fileId}` | Download an error report |
| `GET` | `/upload/tariff-exemption` | Template-specific upload form (Thymeleaf) |
| `POST` | `/upload/tariff-exemption` | Template-specific form submission (Thymeleaf) |
| `GET` | `/` | Template selector (Thymeleaf) |

### Upload Request Contract (REST)

- Endpoint: `POST /api/excel/upload/tariff-exemption`
- Content type: `multipart/form-data`
- Parts:
  - `file`: Excel file (`.xlsx` only)
  - `commonData`: JSON (`application/json`)
- `commonData` schema is template-specific (`TemplateDefinition<T, C>`의 `commonDataClass` 기준)
- Required `commonData` fields (현재 `tariff-exemption` 템플릿 기준):
  - `comeYear`
  - `comeSequence`
  - `uploadSequence`
  - `equipCode`
- Server-managed fields:
  - `createdBy`: forced to `user01` on server
  - `approvedYn`: forced to `N` on server
- `commonData` is parsed in strict mode:
  - reject unknown fields (`FAIL_ON_UNKNOWN_PROPERTIES`)
  - reject scalar coercion for textual fields (`ALLOW_COERCION_OF_SCALARS` off + textual coercion fail)

### Upload Request Contract (Thymeleaf)

- Endpoint: `POST /upload/tariff-exemption`
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
├── controller/          # Thin split controllers (REST + Thymeleaf) + shared download endpoint
├── service/
│   ├── contract/
│   │   ├── CommonData.java                        # Marker interface for template-specific commonData DTOs
│   │   ├── TemplateDefinition.java                # Type-safe bundle: DTO + commonData + config + handlers
│   │   ├── PersistenceHandler.java                # Strategy interface for saving parsed rows with typed commonData
│   │   └── DatabaseUniquenessChecker.java         # Strategy interface for DB-level duplicate checks
│   ├── file/
│   │   ├── ExcelUploadFileService.java            # Multipart file handling + secure temp storage
│   │   └── TempFileCleanupService.java            # Scheduled cleanup of expired temp/error files
│   └── pipeline/
│       ├── ExcelImportOrchestrator.java           # End-to-end pipeline; contains ImportResult record
│       ├── ExcelUploadRequestService.java         # Request-level orchestration (size/commonData strict parsing + validation)
│       ├── parse/
│       │   ├── ExcelParserService.java            # Excel -> List<DTO>; contains ColumnMapping + ParseResult records
│       │   ├── ColumnResolutionException.java     # Column header mismatch error
│       │   └── ColumnResolutionBatchException.java# Aggregated column resolution errors
│       ├── report/
│       │   └── ExcelErrorReportService.java       # Format-preserving error report generation (SXSSFWorkbook)
│       └── validation/
│           └── ExcelValidationService.java        # JSR-380 + within-file uniqueness validation
├── templates/samples/tariffexemption/             # Example template implementation
│   ├── config/
│   │   ├── TariffExemptionImportConfig.java       # ExcelImportConfig for tariff exemption
│   │   └── TariffExemptionTemplateConfig.java     # Wires the TemplateDefinition bean (currently checker is null)
│   ├── dto/
│   │   ├── TariffExemptionDto.java                # DTO with @ExcelColumn + JSR-380 annotations
│   │   └── TariffExemptionCommonData.java         # Tariff template-specific commonData DTO
│   ├── mapper/
│   │   └── TariffExemptionCommonDataFormMapper.java # Thymeleaf form -> TariffExemptionCommonData mapper
│   ├── persistence/
│   │   ├── entity/
│   │   │   ├── TariffExemptionId.java             # Detail composite PK (@Embeddable)
│   │   │   ├── TariffExemptionSummaryId.java      # Summary composite PK (@Embeddable)
│   │   │   ├── TariffExemption.java               # Detail JPA entity (@EmbeddedId)
│   │   │   └── TariffExemptionSummary.java        # Summary JPA entity (@EmbeddedId, upload aggregate)
│   │   └── repository/
│   │       ├── TariffExemptionRepository.java     # Spring Data JPA repository
│   │       └── TariffExemptionSummaryRepository.java # Summary repository
│   └── service/
│       ├── TariffExemptionService.java            # Implements PersistenceHandler
│       └── TariffExemptionDbUniquenessChecker.java# Optional DatabaseUniquenessChecker implementation
├── util/
│   ├── ExcelColumnUtil.java                       # Column letter/index conversion
│   ├── SecureExcelUtils.java                      # Security utilities (XXE, zip bomb, path traversal protection)
│   └── WorkbookCopyUtils.java                     # Stateless workbook copy helpers (styles, values, metadata)
└── validation/          # CellError (record), RowError, ExcelValidationResult, WithinFileUniqueConstraintValidator
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
11. **Database uniqueness** -- optional per-template check via `DatabaseUniquenessChecker` (for current tariff wiring, this step is skipped because checker is `null`)
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
| **Info Disclosure** | Internal exception details hidden for unexpected/system errors | `SecurityException`/generic exception paths return safe Korean messages |

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
3. **CommonData** -- 템플릿별 DTO를 만들고 `CommonData`를 구현한다 (strict JSON + Bean Validation 대상)
4. **Persistence** -- Implement `PersistenceHandler<T, C>` with `saveAll(List<T> rows, List<Integer> sourceRowNumbers, C commonData)` to save parsed rows merged with common fields
5. **DB uniqueness** _(optional)_ -- Implement `DatabaseUniquenessChecker<T>` if duplicates should be checked against existing data
6. **Wire** -- Create a `@Configuration` class that produces a `TemplateDefinition<T, C>` `@Bean` with `commonDataClass` (and checker bean if enabled); the orchestrator discovers it automatically

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
| `SecureExcelUtilsTest` | Unit | Filename sanitization, magic-byte validation, workbook safety utilities |
| `WorkbookCopyUtilsTest` | Unit | Style mapping (same-format, cross-format), error styles, cell value copying, column widths, merged regions |
| `ColumnResolutionExceptionTest` | Unit | Header mismatch diagnostics and Korean error rendering for column resolution failures |
| `ExcelApiExceptionHandlerTest` | Controller | API 예외 처리 매핑(400/413/500)과 내부 오류 메시지 비노출 보장 |
| `ExcelUploadFileServiceTest` | Component | `.xlsx` 전용 업로드 파일 저장 및 형식/매직바이트 검증 유지 |
| `ExcelParserServiceTest` | Component | Row parsing, footer detection, blank row skipping, merged cells, type coercion, header matching, early-exit on row limit |
| `ExcelValidationServiceTest` | Component | JSR-380 constraints, boundary values, Korean error messages |
| `WithinFileUniqueConstraintValidatorTest` | Component | Single-field and composite uniqueness, null handling, DB uniqueness via checker |
| `ExcelErrorReportServiceTest` | Component | `_ERRORS` column, red styling, format preservation, multi-sheet copy, disclaimer footer, `.meta` file, valid output |
| `ExcelImportIntegrationTest` | Integration | Full upload/download flow via MockMvc, route removal 404 checks, Thymeleaf split routes, strict `commonData`, `.xlsx` success path, `.xls` rejection, exact `413` oversize handling |
| `TariffUploadPlanContractTest` | Contract/Plan | Upload domain contract checks for `CommonData`/`TemplateDefinition<T, C>` signature and tariff persistence mapping |
| `TariffExemptionEmbeddedIdMappingTest` | Template Unit | Detail/Summary embedded ID 매핑 및 엔티티 구성 검증 |
| `TariffExemptionEmbeddedIdPersistenceTest` | Template Integration | Embedded ID 기반 upsert/요약 집계 persistence 동작 검증 |
