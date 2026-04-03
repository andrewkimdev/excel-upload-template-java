# Excel Upload Feature

Spring Boot application for uploading, parsing, validating, and importing Excel files. The current sample import is `aappcar`, and the upload path accepts `.xlsx` only.

## Requirements

- JDK 17+
- Gradle 8+

## Quick Start

```bash
./gradlew bootRun
```

Open `http://localhost:8080`.

## Endpoints

| Method | Path | Description |
|--------|------|-------------|
| `GET` | `/` | Import selector page |
| `GET` | `/upload/aappcar` | `aappcar` upload form |
| `POST` | `/upload/aappcar` | `aappcar` form submission |
| `POST` | `/api/excel/upload/aappcar` | `aappcar` REST upload |
| `GET` | `/api/excel/download/{fileId}` | Error report download |

## Upload Contract

### REST

- Endpoint: `POST /api/excel/upload/aappcar`
- Content type: `multipart/form-data`
- Parts:
  - `file`: Excel file (`.xlsx` only)
  - `metadata`: JSON (`application/json`)
- `metadata` is parsed in strict mode:
  - reject unknown fields
  - reject scalar coercion for textual fields

Current `aappcar` required metadata fields:

- `comeYear`
- `comeOrder`
- `uploadSeq`
- `equipCode`
- `equipMean`
- `hsno`
- `spec`
- `taxRate`

Current optional metadata fields:

- `filePath`
- `approvalYn`
- `approvalDate`
- `companyId`
- `customId`

Server-managed behavior:

- API and page controllers inject `companyId=COMPANY01` and `customId=CUSTOM01`
- `approvalYn` defaults to `N` when omitted
- the stored upload path is assigned through `ImportMetadata.assignFilePath(String)`
- `AAppcarItemImportDefinition.resolveTempSubdirectory(...)` partitions temp storage by `customId`

### Thymeleaf

- Endpoint: `POST /upload/aappcar`
- Form fields cover the same business metadata plus `file`
- `companyId` and `customId` are not exposed in the form; the controller injects them

## Responses

Success response shape:

```json
{
  "success": true,
  "rowsProcessed": 150,
  "rowsCreated": 150,
  "rowsUpdated": 0,
  "message": "데이터 업로드 완료"
}
```

Validation-failure response shape:

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

Metadata-conflict precheck response shape:

```json
{
  "success": false,
  "rowsProcessed": 0,
  "errorRows": 0,
  "errorCount": 0,
  "message": "입력한 메타데이터 조합과 일치하는 승인된 장비가 이미 존재합니다.",
  "metadataConflict": {
    "type": "METADATA_DUPLICATE_APPROVED_EQUIP"
  }
}
```

## Runtime Flow

1. `ExcelImportRequestService` checks upload size and parses `metadata` strictly.
2. `ExcelUploadFileService` sanitizes the filename, stores the file, validates magic bytes, and rejects non-`.xlsx` uploads.
3. `ExcelImportOrchestrator` runs import-level prechecks before parsing.
4. `SecureExcelUtils.countRows(...)` performs a lightweight pre-count.
5. `ExcelParserService` opens the workbook securely, resolves headers, parses rows, skips blanks, and stops at the footer marker `※`.
6. `ExcelValidationService` applies Bean Validation and within-file uniqueness rules.
7. On failure, `ExcelErrorReportService` generates a format-preserving error workbook with `_ERRORS` and a downloadable `.meta` filename hint.
8. On success, the import-specific `PersistenceHandler` saves the parsed rows.

## Package Layout

```text
src/main/java/com/foo/excel/
├── annotation/
├── config/
├── controller/
├── imports/
│   └── samples/aappcar/
│       ├── config/
│       ├── dto/
│       ├── mapper/
│       ├── persistence/
│       └── service/
├── service/
│   ├── contract/
│   ├── file/
│   └── pipeline/
├── util/
└── validation/
```

Key sample classes:

- `AAppcarItemImportRow`
- `AAppcarItemImportMetadata`
- `AAppcarItemImportConfig`
- `AAppcarItemImportDefinition`
- `AAppcarItemImportPrecheck`
- `AAppcarItemPersistenceService`

## Security

Built-in protections:

- filename sanitization
- magic-byte validation
- legacy `.xls` rejection and `.xlsx`-only acceptance
- secure workbook opening
- row pre-count and parser row-limit enforcement
- formula-injection sanitization in generated error reports
- safe Korean messages for unexpected/system failures

Consumer responsibilities remain in `src/main/resources/application.properties`:

- authentication and authorization
- CSRF protection
- rate limiting
- security headers
- HTTPS-only deployment
- disabling the H2 console outside development

## Adding a New Import

1. Create a row DTO with `@ExcelSheet`, `@ExcelColumn`, and Bean Validation annotations.
2. Add import-specific metadata implementing `ImportMetadata`.
3. Implement `PersistenceHandler<T, M extends ImportMetadata>`.
4. Optionally implement `ImportPrecheck<M>` and `DatabaseUniquenessChecker<T, M extends ImportMetadata>`.
5. Wire everything in an `ExcelImportDefinition<T, M>` bean.

Merged-header and merged-cell support is DTO-driven through `@ExcelColumn(columnSpan=...)` and `@ExcelHeaderGroup`.

## Configuration

Important properties from `application.properties`:

| Property | Default |
|----------|---------|
| `excel.import.max-file-size-mb` | `10` |
| `excel.import.max-rows` | `10000` |
| `excel.import.pre-count-buffer` | `100` |
| `excel.import.error-row-limit` | `100` |
| `excel.import.retention-days` | `30` |
| `excel.import.temp-directory` | `${java.io.tmpdir}/excel-imports` |

## Testing

```bash
./gradlew test
```

Representative test coverage:

- `ExcelImportIntegrationTest`
- `ExcelApiExceptionHandlerTest`
- `ExcelUploadFileServiceTest`
- `ExcelParserServiceTest`
- `ExcelValidationServiceTest`
- `ExcelErrorReportServiceTest`
- `WithinFileUniqueConstraintValidatorTest`
- `ExcelMergeRegionResolverTest`
- `ExcelSheetSpecResolverTest`
- `TariffImportPlanContractTest`
- `AAppcarItemEmbeddedIdMappingTest`
- `AAppcarItemEmbeddedIdPersistenceTest`
