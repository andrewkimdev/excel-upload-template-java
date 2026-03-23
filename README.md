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
| `POST` | `/api/excel/upload/aappcar` | Upload and process an Excel file (tariff import) |
| `GET` | `/api/excel/download/{fileId}` | Download an error report |
| `GET` | `/upload/aappcar` | Import-specific upload form (Thymeleaf) |
| `POST` | `/upload/aappcar` | Import-specific form submission (Thymeleaf) |
| `GET` | `/` | Import selector (Thymeleaf) |

### Upload Request Contract (REST)

- Endpoint: `POST /api/excel/upload/aappcar`
- Content type: `multipart/form-data`
- Parts:
  - `file`: Excel file (`.xlsx` only)
  - `metadata`: JSON (`application/json`)
- `metadata` schema is import-specific (`ExcelImportDefinition<T, M>`의 `metadataClass` 기준)
- Required `metadata` fields (현재 `aappcar` 템플릿 기준):
  - `comeYear`
  - `comeOrder`
  - `uploadSeq`
  - `equipCode`
  - `equipMean`
  - `hsno`
  - `spec`
  - `taxRate`
- Optional `metadata` fields (현재 `aappcar` 템플릿 기준):
  - `filePath`
  - `approvalYn`
  - `approvalDate`
- Server-managed fields:
  - `approvalYn`: defaults to `N` when omitted
  - `approvalDate`: saved as `LocalDate.now()` only when effective `approvalYn` is `Y`, otherwise `null`
  - `companyId`: currently forced to `COMPANY01` on server-side controller
  - `customId`: currently forced to `CUSTOM01` on server-side controller
  - `filePath`: assigned by the upload pipeline to the stored temp path + filename before persistence
- Shared `Metadata` contract:
  - `assignFilePath(String filePath)` accepts the server-assigned stored upload path
- Import-level temp storage contract:
  - definitions may override `ExcelImportDefinition.resolveTempSubdirectory(metadata)` when uploads should be partitioned under a temp subdirectory
  - `resolveTempSubdirectory(metadata)` returns a nullable `String`; `null` means the base temp directory, non-null values are sanitized before use
  - current `aappcar` implementation resolves that subdirectory from `customId`
  - the concrete temp filesystem path is resolved by `ExcelUploadFileService`, not by the template hook
- `metadata` is parsed in strict mode:
  - reject unknown fields (`FAIL_ON_UNKNOWN_PROPERTIES`)
  - reject scalar coercion for textual fields (`ALLOW_COERCION_OF_SCALARS` off + textual coercion fail)

### Upload Request Contract (Thymeleaf)

- Endpoint: `POST /upload/aappcar`
- Form fields include:
  - `comeYear`
  - `comeOrder`
  - `uploadSeq`
  - `equipCode`
  - `equipMean`
  - `hsno`
  - `spec`
  - `taxRate`
  - `filePath` (optional)
  - `approvalYn` (optional)
  - `approvalDate` (optional)
  - `file` (`.xlsx` only)
- Form does not expose `companyId` or `customId`.
- Server injects `companyId=COMPANY01`, `customId=CUSTOM01`.

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

### Upload Response (upload metadata conflict precheck)

```json
{
  "success": false,
  "rowsProcessed": 0,
  "errorRows": 0,
  "errorCount": 0,
  "message": "입력한 메타데이터 조합과 일치하는 승인된 장비가 이미 존재합니다.",
  "uploadMetadataConflict": {
    "type": "METADATA_DUPLICATE_APPROVED_EQUIP",
    "description": "아래 메타데이터 값 조합에 대해 이미 승인된 장비가 존재합니다. 엑셀 파일이 아니라 메타데이터 값을 수정하세요.",
    "fields": [
      {"fieldName": "companyId", "label": "회사 ID", "value": "COMPANY01"},
      {"fieldName": "customId", "label": "거래처 ID", "value": "CUSTOM01"},
      {"fieldName": "comeYear", "label": "반입연도", "value": "2026"},
      {"fieldName": "comeOrder", "label": "반입차수", "value": "1"},
      {"fieldName": "uploadSeq", "label": "업로드 순번", "value": "1"},
      {"fieldName": "equipCode", "label": "설비코드", "value": "EQ-01"}
    ]
  }
}
```

## Architecture

```
src/main/java/com/foo/excel/
├── ExcelImportApplication.java
├── annotation/          # @ExcelSheet, @ExcelColumn, @ExcelUnique, @ExcelCompositeUnique, HeaderMatchMode
├── config/
│   └── ExcelImportProperties.java           # Global properties (file size, max rows, temp dir)
├── controller/          # Thin split controllers (REST + Thymeleaf) + shared download endpoint
├── service/
│   ├── contract/
│   │   ├── Metadata.java                        # Shared metadata contract (server-assigned file path)
│   │   ├── ExcelImportDefinition.java          # Type-safe bundle: DTO + metadata + cached sheet spec + handlers
│   │   ├── PersistenceHandler.java                # Strategy interface for saving parsed rows with typed metadata
│   │   └── DatabaseUniquenessChecker.java         # Strategy interface for DB-level duplicate checks
│   ├── file/
│   │   ├── ExcelUploadFileService.java            # Multipart file handling + secure temp storage
│   │   └── TempFileCleanupService.java            # Scheduled cleanup of expired temp/error files
│   └── pipeline/
│       ├── ExcelImportOrchestrator.java           # End-to-end pipeline; contains ImportResult record
│       ├── ExcelImportRequestService.java         # Request-level orchestration (size/metadata strict parsing + validation)
│       ├── parse/
│       │   ├── ExcelParserService.java            # Excel -> List<DTO>; contains ColumnMapping + ParseResult records
│       │   ├── ColumnResolutionException.java     # Column header mismatch error
│       │   └── ColumnResolutionBatchException.java# Aggregated column resolution errors
│       ├── report/
│       │   └── ExcelErrorReportService.java       # Format-preserving error report generation (SXSSFWorkbook)
│       └── validation/
│           └── ExcelValidationService.java        # JSR-380 + within-file uniqueness validation
├── templates/samples/aappcar/             # Example template implementation
│   ├── config/
│   │   ├── AAppcarItemImportConfig.java       # Wires the ExcelImportDefinition bean
│   │   └── AAppcarItemImportDefinition.java   # AAppcar-specific temp subdirectory resolution
│   ├── dto/
│   │   ├── AAppcarItemDto.java                # DTO with @ExcelSheet + @ExcelColumn + JSR-380 annotations
│   │   └── AAppcarItemMetadata.java         # Tariff import-specific metadata DTO
│   ├── mapper/
│   │   └── AAppcarItemMetadataFormMapper.java # Thymeleaf form -> AAppcarItemMetadata mapper
│   ├── persistence/
│   │   ├── entity/
│   │   │   ├── AAppcarItemId.java             # Item composite PK (@Embeddable)
│   │   │   ├── AAppcarEquipId.java      # Equip composite PK (@Embeddable)
│   │   │   ├── AAppcarItem.java               # Item JPA entity (@EmbeddedId)
│   │   │   └── AAppcarEquip.java        # Equip JPA entity (@EmbeddedId, upload aggregate)
│   │   └── repository/
│   │       ├── AAppcarItemRepository.java     # Spring Data JPA repository
│   │       └── AAppcarEquipRepository.java # Equip repository
│   └── service/
│       ├── AAppcarItemService.java            # Implements PersistenceHandler
│       └── AAppcarItemDbUniquenessChecker.java# Optional DatabaseUniquenessChecker implementation
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
6. **Header verification** -- verify actual headers match `@ExcelColumn(label=..., column=...)` expectations at declared positions; templates may opt into multi-row header ranges, merged-header paths, and whitespace-insensitive matching for legacy files; fail-fast with Korean error messages if required columns mismatch
7. **Row parsing** -- read data rows, skip blanks, stop at footer marker (`※`)
8. **Type coercion** -- String (trimmed), Integer, BigDecimal, LocalDate, LocalDateTime, Boolean (`Y`/`true` -> true); parse errors are collected per cell
9. **JSR-380 validation** -- `@NotBlank`, `@Size`, `@Pattern`, `@DecimalMin`/`@DecimalMax`, `@Min`
10. **Within-file uniqueness** -- `@ExcelUnique` (single field), `@ExcelCompositeUnique` (composite key); invalid `@ExcelCompositeUnique(fields=...)` declarations fail fast as developer configuration errors
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

1. **DTO** -- Create a DTO class with `@ExcelColumn(label = ..., column = ...)` and JSR-380 validation annotations on each field
   - If you use `@ExcelCompositeUnique`, every field name in `fields()` must exist on the DTO class hierarchy; invalid declarations are treated as fail-fast configuration errors during within-file uniqueness validation
   - For legacy workbooks with merged or multi-line headers, `@ExcelColumn` can opt into a header row range (`headerRowStart`/`headerRowCount`), logical header labels (`headerLabels`), and whitespace-insensitive comparison (`ignoreHeaderWhitespace`)
   - For horizontally owned cells such as `F:G` or `O:P`, set `columnSpan` on the owning field
   - For shared grouped headers such as `소요량`, place `@ExcelHeaderGroup` on the leftmost field and list the grouped fields in left-to-right order
   - Grouped-header metadata is validated strictly; duplicate membership, non-adjacent fields, reversed order, and inferred merge overlaps fail fast during template metadata resolution
2. **Sheet Contract** -- Add `@ExcelSheet` on the DTO to define sheet index, header row, data start row, footer marker, and error column name
3. **Metadata** -- 템플릿별 DTO를 만들고 `Metadata`를 구현한다 (strict JSON + Bean Validation 대상)
   - `assignFilePath(String filePath)`를 구현해 서버가 저장한 업로드 경로를 받을 수 있어야 한다
4. **Persistence** -- Implement `PersistenceHandler<T, M>` with `saveAll(List<T> rows, List<Integer> sourceRowNumbers, M metadata)` to save parsed rows merged with common fields
5. **DB uniqueness** _(optional)_ -- Implement `DatabaseUniquenessChecker<T, M>` with `check(List<T> rows, Class<T> dtoClass, List<Integer> sourceRowNumbers, M metadata)` if duplicates should be checked against existing data
6. **Wire** -- Create a `@Configuration` class that produces an `ExcelImportDefinition<T, M>` `@Bean` with `metadataClass` (and checker bean if enabled); override `resolveTempSubdirectory(metadata)` only when the import needs temp-path partitioning, returning `null` when no subdirectory should be used

See the `AAppcarItem*` classes for a complete example (`AAppcarItemDto`, `AAppcarItemService`, `AAppcarItemDbUniquenessChecker`, `AAppcarItemImportConfig`).

### Merged Header Example

```java
@ExcelColumn(
    label = "제조용",
    column = "J",
    columnSpan = 2,
    ignoreHeaderWhitespace = true,
    headerRowStart = 4,
    headerRowCount = 3,
    headerLabels = {"소요량", "제조용"})
@ExcelHeaderGroup(label = "소요량", fields = {"prodQty", "repairQty"})
private Integer prodQty;

@ExcelColumn(
    label = "수리용",
    column = "L",
    columnSpan = 2,
    ignoreHeaderWhitespace = true,
    headerRowStart = 4,
    headerRowCount = 3,
    headerLabels = {"소요량", "수리용"})
private Integer repairQty;
```

This shape lets the runtime infer grouped header merges and repeating data-row merges without low-level per-row merge annotations.

## Configuration

Key properties in `application.properties`:

| Property | Default | Description |
|----------|---------|-------------|
| `excel.import.max-file-size-mb` | `10` | Maximum upload file size in MB |
| `excel.import.max-rows` | `10000` | Maximum data rows per file |
| `excel.import.pre-count-buffer` | `100` | Extra rows allowed in SAX pre-count to account for headers/blanks |
| `excel.import.error-row-limit` | `100` | Stop row-level error collection after this many invalid rows and return a partial report (`0` or less disables the cap) |
| `excel.import.retention-days` | `30` | Days to keep temp/error files (shorter = more secure) |
| `excel.import.temp-directory` | `${java.io.tmpdir}/excel-imports` | Temp file storage path |

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
| `WithinFileUniqueConstraintValidatorTest` | Component | Single-field and composite uniqueness, null handling, invalid `@ExcelCompositeUnique` declaration fail-fast behavior |
| `ExcelErrorReportServiceTest` | Component | `_ERRORS` column, red styling, format preservation, multi-sheet copy, disclaimer footer, `.meta` file, valid output |
| `ExcelImportIntegrationTest` | Integration | Full upload/download flow via MockMvc, route removal 404 checks, Thymeleaf split routes, strict `metadata`, `.xlsx` success path, `.xls` rejection, exact `413` oversize handling |
| `TariffImportPlanContractTest` | Contract/Plan | Import domain contract checks for `Metadata`/`ExcelImportDefinition<T, M>` signature and tariff persistence mapping |
| `AAppcarItemEmbeddedIdMappingTest` | Template Unit | Item/Equip embedded ID 매핑 및 엔티티 구성 검증 |
| `AAppcarItemEmbeddedIdPersistenceTest` | Template Integration | Embedded ID 기반 upsert/요약 집계 persistence 동작 검증 |
