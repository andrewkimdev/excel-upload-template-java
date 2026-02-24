# How the Excel Upload Pipeline Works

This document describes the upload pipeline as currently implemented.

## 1) End-to-end flow

```
HTTP (REST/Thymeleaf)
  -> ExcelFileController
    -> ExcelImportOrchestrator
      -> ExcelUploadFileService (.xlsx-only gate)
      -> SecureExcelUtils.countRows (pre-check)
      -> ExcelParserService
      -> ExcelValidationService (+ WithinFileUniqueConstraintValidator)
      -> optional DatabaseUniquenessChecker (template-dependent)
      -> merge parse/validation/db errors
      -> PersistenceHandler.saveAll(...) OR ExcelErrorReportService.generateErrorReport(...)
```

## 2) Entry points and contracts

### REST

- `POST /api/excel/upload/{templateType}`
- Parts:
  - `file` (`multipart/form-data`)
  - `commonData` (`application/json`, required)

`commonData` required fields:
- `comeYear`
- `comeSequence`
- `uploadSequence`
- `equipCode`

Behavior:
- strict JSON parsing (`FAIL_ON_UNKNOWN_PROPERTIES`, scalar coercion disabled)
- bean validation errors return bad request
- server-managed values are fixed by `UploadCommonData`:
  - `createdBy=user01`
  - `approvedYn=N`

### Thymeleaf

- `GET /` -> `upload.html`
- `POST /upload` -> `result.html`

Form fields map to `UploadCommonData` fields and file.

### Download

- `GET /api/excel/download/{fileId}`
- `fileId` must match UUID regex in controller
- downloads `errors/{fileId}.xlsx`
- if `{fileId}.meta` exists, download name uses original filename with `오류_` prefix

### Template download placeholder

- `GET /api/excel/template/{templateType}` currently returns `501 Not Implemented`.

## 3) Detailed pipeline steps

### Step 0: request-level size gate (controller)

`ExcelFileController` checks file size against `excel.import.max-file-size-mb` before orchestrator call.

### Step 1: template resolution (orchestrator)

`ExcelImportOrchestrator.findTemplate(templateType)` resolves from injected `List<TemplateDefinition<?>>`.
Unknown template -> `IllegalArgumentException`.

### Step 2: secure filename + `.xlsx`-only format gate

`ExcelUploadFileService.storeAndValidateXlsx(...)`:
- sanitizes filename (`SecureExcelUtils.sanitizeFilename`)
- rejects non-`.xlsx` with Korean error
- saves multipart file into temp upload directory
- validates magic bytes (`SecureExcelUtils.validateFileContent`)

Important: there is no `.xls` to `.xlsx` conversion path in the current code.

### Step 2b: lightweight row pre-count

`SecureExcelUtils.countRows(...)` uses OPCPackage + XSSFReader + StAX (`<row>` count).

Pre-check threshold:
- `maxRows + (dataStartRow - 1) + preCountBuffer`

If rough row count exceeds threshold, upload is rejected before full parse.

### Step 3: secure parse to DTO rows

`ExcelParserService.parse(...)`:
- opens workbook via `SecureExcelUtils.createWorkbook`
- resolves `@ExcelColumn` mappings (header matching modes supported)
- iterates from `dataStartRow`
- skips blank rows
- stops at footer marker (`getFooterMarker()`, default `※`)
- converts to field types (`String`, `Integer`, `BigDecimal`, `LocalDate`, `LocalDateTime`, `Boolean`)
- collects conversion errors as `RowError`/`CellError`
- tracks original 1-based source row numbers
- exits early if parsed rows exceed `maxRows`

### Step 4: authoritative max-row check

Orchestrator rejects if parsed row count is greater than `maxRows`.

### Step 5: validation

`ExcelValidationService.validate(...)`:
- JSR-380 bean validation
- within-file uniqueness via `WithinFileUniqueConstraintValidator`
  - `@ExcelUnique`
  - `@ExcelCompositeUnique`

### Step 6: optional DB uniqueness

Orchestrator calls `template.checkDbUniqueness(...)`.
If template has no checker (`null`), this returns empty errors.

Current tariff template wiring (`TariffExemptionTemplateConfig`) passes `null`, so orchestrator-level DB uniqueness is disabled for active wiring.

### Step 7: merge parse errors

Orchestrator merges parser conversion errors into `ExcelValidationResult`.

### Step 8a: success path (persist)

If valid, orchestrator calls:
- `PersistenceHandler.saveAll(List<T> rows, List<Integer> sourceRowNumbers, UploadCommonData commonData)`

Current tariff implementation (`TariffExemptionService`):
- upserts detail rows by `(comeYear, comeSequence, uploadSequence, equipCode, rowNumber)`
- upserts summary row by `(comeYear, comeSequence, uploadSequence, equipCode)`
- applies audit defaults (`createdBy`, `approvedYn`, `createdAt`)
- retries on `DataIntegrityViolationException` up to retry limit

### Step 8b: failure path (error report)

`ExcelErrorReportService.generateErrorReport(...)`:
- opens source workbook securely
- rebuilds workbook while preserving formatting (`WorkbookCopyUtils`)
- highlights errored cells with error style
- appends error column (`config.getErrorColumnName()`, default `_ERRORS`)
- writes sanitized error messages (`SecureExcelUtils.sanitizeForExcelCell`)
- adds disclaimer footer line
- writes `errors/{uuid}.xlsx`
- writes `errors/{uuid}.meta` with original sanitized filename when available

Response includes `downloadUrl=/api/excel/download/{uuid}`.

## 4) Security model (implemented)

- Filename sanitization: path traversal and unsafe characters blocked.
- Magic-byte validation: extension/content mismatch blocked.
- `.xlsx` only: legacy `.xls` rejected.
- Secure workbook open: POI limits + protected XML parsing settings.
- Row-exhaustion protection: pre-count + parser early-exit + authoritative max-row check.
- Formula injection mitigation: error message cells sanitized.
- Download hardening: UUID-only `fileId` accepted.
- Error handling: internal details hidden for security/system failures.

## 5) Key classes map

- Controller: `src/main/java/com/foo/excel/controller/ExcelFileController.java`
- Orchestrator: `src/main/java/com/foo/excel/service/pipeline/ExcelImportOrchestrator.java`
- Conversion: `src/main/java/com/foo/excel/service/file/ExcelUploadFileService.java`
- Parser: `src/main/java/com/foo/excel/service/pipeline/parse/ExcelParserService.java`
- Validation: `src/main/java/com/foo/excel/service/pipeline/validation/ExcelValidationService.java`
- Uniqueness validator: `src/main/java/com/foo/excel/validation/WithinFileUniqueConstraintValidator.java`
- Error report: `src/main/java/com/foo/excel/service/pipeline/report/ExcelErrorReportService.java`
- Security utilities: `src/main/java/com/foo/excel/util/SecureExcelUtils.java`
- Template wiring example: `src/main/java/com/foo/excel/templates/samples/tariffexemption/config/TariffExemptionTemplateConfig.java`

## 6) Configuration reference

Application properties:
- `excel.import.max-file-size-mb` (default `10`)
- `excel.import.max-rows` (default `10000`)
- `excel.import.pre-count-buffer` (default `100`)
- `excel.import.retention-days` (default `30`)
- `excel.import.temp-directory` (default `${java.io.tmpdir}/excel-imports`)
- `excel.import.error-column-name` (default `_ERRORS`)

Multipart limits should align:
- `spring.servlet.multipart.max-file-size=10MB`
- `spring.servlet.multipart.max-request-size=10MB`
