# How the Excel Upload Pipeline Works

This document traces the complete lifecycle of an Excel file from the moment a user
uploads it until the data is persisted in the database (or an error report is returned).

---

## Table of Contents

1. [Architecture at a Glance](#architecture-at-a-glance)
2. [Package Structure](#package-structure)
3. [The Pipeline in Detail](#the-pipeline-in-detail)
   - [Entry Points](#entry-points-controller)
   - [Step 1: Template Resolution](#step-1-template-resolution)
   - [Step 2: Format Conversion](#step-2-format-conversion)
   - [Step 2b: Row Count Pre-check](#step-2b-row-count-pre-check-sax)
   - [Step 3: Parsing](#step-3-parsing-excel-to-dtos)
   - [Step 4: Row Count Check](#step-4-row-count-check-authoritative)
   - [Step 5: Validation (JSR-380 + Uniqueness)](#step-5-validation)
   - [Step 6: Database Uniqueness Check](#step-6-database-uniqueness-check)
   - [Step 7: Error Merging](#step-7-error-merging)
   - [Step 8: Persist or Report](#step-8-persist-or-generate-error-report)
4. [Flowchart](#flowchart)
5. [The Template Plugin System](#the-template-plugin-system)
6. [Security Measures](#security-measures)
7. [Key Classes Reference](#key-classes-reference)
8. [Configuration Reference](#configuration-reference)

---

## Architecture at a Glance

The application follows a **linear pipeline** pattern. Every uploaded file passes through
the same sequence of stages, regardless of which template type it belongs to. The pipeline
is orchestrated by a single service (`ExcelImportOrchestrator`) that coordinates
specialized services for each stage:

```
HTTP Request (MultipartFile)
  --> Controller
    --> Orchestrator
      --> Conversion    (.xls to .xlsx if needed)
      --> Parsing       (Excel rows to Java DTOs)
      --> Validation    (JSR-380 + within-file uniqueness)
      --> DB Uniqueness (check against existing records)
      --> Error Merge   (combine all error sources)
      --> Persist       (upsert to database)
         OR
      --> Error Report  (annotated Excel with error column)
    <-- ImportResult
  <-- HTTP Response
```

The template-specific behavior (which columns to read, which validations to apply, how to
persist) is encapsulated in **TemplateDefinition** beans. The core pipeline code never
references any specific template directly -- it operates on generic `<T>` DTOs.

---

## Package Structure

```
com.foo.excel/
|
+-- annotation/                  # Metadata annotations for DTO fields
|   +-- @ExcelColumn             # Maps a DTO field to an Excel column
|   +-- @ExcelUnique             # Marks a field as requiring uniqueness
|   +-- @ExcelCompositeUnique    # Marks a combination of fields as a unique key
|   +-- HeaderMatchMode          # How to match header text (EXACT, CONTAINS, etc.)
|
+-- config/                      # Application-level configuration
|   +-- ExcelImportConfig        # Interface: header row, data start row, footer marker, etc.
|   +-- ExcelImportProperties    # Externalized config (max file size, max rows, temp dir)
|
+-- controller/                  # HTTP layer
|   +-- ExcelUploadController    # REST API + Thymeleaf endpoints
|
+-- service/                     # Core pipeline services
|   +-- ExcelImportOrchestrator  # The central pipeline coordinator
|   +-- ExcelConversionService   # .xls-to-.xlsx conversion
|   +-- ExcelParserService       # Reads Excel into DTOs via reflection
|   +-- ExcelValidationService   # JSR-380 validation + uniqueness delegation
|   +-- ExcelErrorReportService  # Generates annotated error Excel files
|   +-- TempFileCleanupService   # Scheduled cleanup of old temp files
|   +-- TemplateDefinition<T>    # Generic container that bundles a template's components
|   +-- PersistenceHandler<T>    # Interface: how to save DTOs to the database
|   +-- DatabaseUniquenessChecker<T>  # Interface: how to check DB for duplicates
|
+-- util/                        # Stateless utility classes
|   +-- ExcelColumnUtil          # Column letter <-> index conversion ("B" <-> 1)
|   +-- SecureExcelUtils         # Secure file open, magic byte checks, sanitization
|
+-- validation/                  # Validation data structures
|   +-- CellError                # Error for a single cell (column + message)
|   +-- RowError                 # Error for a single row (row number + list of CellErrors)
|   +-- ExcelValidationResult    # Aggregate: valid/invalid + all RowErrors
|   +-- UniqueConstraintValidator # Checks @ExcelUnique and @ExcelCompositeUnique
|
+-- templates/samples/           # Concrete template implementations
    +-- tariffexemption/
        +-- TariffExemptionDto               # DTO with @ExcelColumn + JSR-380 annotations
        +-- TariffExemptionImportConfig      # Header row 4, data row 7, footer "※"
        +-- TariffExemption                  # JPA @Entity
        +-- TariffExemptionRepository        # Spring Data JPA repository
        +-- TariffExemptionService           # PersistenceHandler (upsert logic)
        +-- TariffExemptionDbUniquenessChecker  # Checks DB for duplicate natural keys
        +-- TariffExemptionTemplateConfig    # @Configuration: produces TemplateDefinition bean
```

---

## The Pipeline in Detail

### Entry Points (Controller)

`ExcelUploadController` exposes two parallel interfaces for the same operation:

| Endpoint | Method | Interface | Response |
|---|---|---|---|
| `/` | GET | Thymeleaf | Renders upload form (`upload.html`) |
| `/upload` | POST | Thymeleaf | Renders result page (`result.html`) |
| `/api/excel/upload/{templateType}` | POST | REST API | JSON `ImportResult` |
| `/api/excel/download/{fileId}` | GET | REST API | Error report `.xlsx` download |

Both the Thymeleaf `POST /upload` and REST `POST /api/excel/upload/{type}` follow the
same path:

1. **File size check** -- reject immediately if the file exceeds `maxFileSizeMb` (default 10 MB).
2. **Delegate** to `ExcelImportOrchestrator.processUpload(file, templateType)`.
3. **Exception handling** -- all exceptions are caught and translated to generic Korean
   user-facing messages. Internal details (stack traces, file paths, `e.getMessage()`)
   are never exposed.

### Step 1: Template Resolution

```java
// ExcelImportOrchestrator.java
TemplateDefinition<?> template = findTemplate(templateType);
```

The orchestrator holds an injected `List<TemplateDefinition<?>>` -- Spring automatically
collects every `TemplateDefinition` bean in the application context. It searches this list
for a match on `templateType` (e.g. `"tariff-exemption"`).

A `TemplateDefinition<T>` bundles everything the pipeline needs for a specific template:

| Component | Purpose |
|---|---|
| `templateType` (String) | Identifier for URL routing |
| `dtoClass` (Class\<T\>) | The DTO class to instantiate per row |
| `config` (ExcelImportConfig) | Header row, data start row, footer marker, natural key fields |
| `persistenceHandler` (PersistenceHandler\<T\>) | How to save validated DTOs |
| `dbUniquenessChecker` (DatabaseUniquenessChecker\<T\>) | Optional: how to check DB for duplicates |

### Step 2: Format Conversion

```java
// ExcelImportOrchestrator.java
Path xlsxFile = conversionService.ensureXlsxFormat(file, tempSubDir);
```

`ExcelConversionService` ensures the pipeline always works with `.xlsx` (OOXML) format:

- **`.xlsx` files**: Saved to the temp directory as-is, then validated via magic bytes
  (`0x50 4B 03 04` -- the PK/ZIP header that all `.xlsx` files share).
- **`.xls` files**: Validated via magic bytes (`0xD0 CF 11 E0` -- the OLE2 compound
  document header), then converted sheet-by-sheet to `.xlsx` using Apache POI
  (`HSSFWorkbook` -> `XSSFWorkbook`). Merged regions and cell values are preserved.
- **Other extensions**: Rejected with `IllegalArgumentException`.

The filename is sanitized before use (`SecureExcelUtils.sanitizeFilename`) to prevent
path traversal attacks and strip dangerous characters.

### Step 2b: Row Count Pre-check (SAX)

```java
// ExcelImportOrchestrator.java
int roughRowCount = SecureExcelUtils.countRows(xlsxFile, config.getSheetIndex());
int preCountThreshold = maxRows + (config.getDataStartRow() - 1) + preCountBuffer;
if (roughRowCount > preCountThreshold) { ... }
```

Before the expensive `XSSFWorkbook` DOM load, a lightweight StAX streaming pass counts
`<row>` elements in the xlsx ZIP. This uses `OPCPackage` + `XSSFReader` + `XMLStreamReader`
with constant memory regardless of file size.

The threshold is heuristic: `maxRows + headerRows + buffer` (default buffer = 100). This
accounts for header rows, blank rows, and footer rows that SAX counts but the parser skips.
Files that pass this check proceed to full parsing; the authoritative exact check in Step 4
catches any borderline cases.

### Step 3: Parsing (Excel to DTOs)

```java
// ExcelImportOrchestrator.java
ExcelParserService.ParseResult<T> parseResult =
    parserService.parse(xlsxFile, template.getDtoClass(), config, properties.getMaxRows());
```

This is the most complex step. `ExcelParserService` converts raw Excel rows into typed
Java objects using reflection and the `@ExcelColumn` annotation.

**Phase 3a: Column Resolution**

The parser reads the header row (configurable per template, e.g. row 4 for tariff
exemption) and maps each `@ExcelColumn`-annotated field to a physical column index:

```
Priority 1: Explicit column letter    @ExcelColumn(column = "B")       --> index 1
Priority 2: Explicit index            @ExcelColumn(index = 3)          --> index 3
Priority 3: Auto-detect from header   @ExcelColumn(header = "물품명")  --> scan header row
```

For auto-detection, the `HeaderMatchMode` controls how the header cell text is compared:
- `EXACT` -- case-insensitive exact match
- `CONTAINS` -- header cell text contains the expected string (default)
- `STARTS_WITH` -- header cell text starts with the expected string
- `REGEX` -- header cell text matches the regex pattern

Merged cells in the header row are resolved by looking up the top-left cell of the
merged region.

**Phase 3b: Data Row Iteration**

Starting from `dataStartRow` (e.g. row 7), the parser iterates through rows:
- **Blank rows** are skipped.
- **Footer rows** (containing the footer marker, e.g. `"※"`) stop iteration.
- **Row limit**: If accumulated rows exceed `maxRows`, the loop breaks immediately
  (parser early-exit — avoids CPU cost of mapping remaining rows).
- For each data row, a new DTO instance is created via reflection.

**Phase 3c: Cell Value Conversion**

Each cell is converted to the target Java type based on the DTO field's declared type:

| Java Type | Conversion Strategy |
|---|---|
| `String` | `DataFormatter.formatCellValue()` (thread-safe via `ThreadLocal`) |
| `Integer` | `getNumericCellValue()` or parse string (strips commas/spaces) |
| `BigDecimal` | `BigDecimal.valueOf(numericValue)` or parse string |
| `LocalDate` | POI date detection or parse with configurable `dateFormat` |
| `LocalDateTime` | Same as `LocalDate` but preserves time component |
| `Boolean` | Native boolean cell or `"Y"`/`"true"` string check |

If a conversion fails (e.g. `"abc"` into an `Integer` field), a `CellError` is recorded
in `parseErrors` with the message `"'abc' 값을 Integer 타입으로 변환할 수 없습니다"`.
The DTO field is set to `null` and parsing continues -- errors do not halt the pipeline.

**Output**: `ParseResult<T>` containing:
- `rows` -- list of populated DTOs
- `sourceRowNumbers` -- the original 1-based Excel row number for each DTO (needed for
  error reporting back to the correct row)
- `columnMappings` -- resolved column positions (needed for error report generation)
- `parseErrors` -- type conversion errors from Phase 3c

### Step 4: Row Count Check (Authoritative)

```java
if (parseResult.getRows().size() > properties.getMaxRows()) { ... }
```

The authoritative exact check. By this point, most oversized files were already rejected
by the SAX pre-count (Step 2b) or stopped early by the parser (Step 3). This catches
borderline cases where the heuristic pre-count let the file through but the actual
data row count exceeds `maxRows`.

### Step 5: Validation

```java
ExcelValidationResult validationResult = validationService.validate(
    parseResult.getRows(), template.getDtoClass(), parseResult.getSourceRowNumbers());
```

`ExcelValidationService` runs two passes over the parsed DTOs:

**Pass 1: JSR-380 Bean Validation**

Each DTO is validated using the standard `jakarta.validation.Validator`. Annotations on
DTO fields drive the checks:

```java
@NotBlank(message = "물품명은 필수 입력 항목입니다")
@Size(max = 100, message = "물품명은 100자 이내로 입력하세요")
private String itemName;

@Pattern(regexp = "^\\d{4}\\.\\d{2}-\\d{4}$", message = "HSK 형식이 올바르지 않습니다")
private String hsCode;
```

Each constraint violation is mapped back to its Excel column position using the
`@ExcelColumn` annotation on the same field, producing a `CellError` with the column
letter, header name, rejected value, and Korean error message.

**Pass 2: Within-File Uniqueness**

`UniqueConstraintValidator` checks two kinds of uniqueness constraints:

- **Single-field** (`@ExcelUnique`): Tracks seen values per field across all rows. When a
  duplicate is found, both the original and duplicate rows are identified:
  `"중복된 값입니다 (행 5과(와) 중복)"`.

- **Composite** (`@ExcelCompositeUnique`): Builds a composite key from multiple fields
  (e.g. `[itemName, specification, hsCode]`) and checks for duplicates within the file.

### Step 6: Database Uniqueness Check

```java
List<RowError> dbErrors = template.checkDbUniqueness(
    parseResult.getRows(), parseResult.getSourceRowNumbers());
validationResult.merge(dbErrors);
```

If the template provides a `DatabaseUniquenessChecker`, each row is checked against
existing database records. For tariff exemption, this queries:

```java
repository.existsByItemNameAndSpecificationAndHsCode(itemName, specification, hsCode)
```

Rows that conflict with existing DB records get an error:
`"이미 등록된 데이터입니다 (물품명 + 규격 + HSK 조합)"`.

**Note**: The DB uniqueness check is separate from the persistence handler's upsert logic.
The check flags conflicts as errors to inform the user. The persistence handler's upsert
would update existing records -- but that path only runs if validation passes. Whether DB
duplicates are treated as errors (reject) or as update targets (upsert) depends on whether
the template provides a `DatabaseUniquenessChecker`.

### Step 7: Error Merging

```java
validationResult.merge(parseResult.getParseErrors());
```

Parse-time type conversion errors (from Step 3) are merged into the same
`ExcelValidationResult`. The `merge()` method consolidates errors by row number -- if a
row already has validation errors, the parse errors are appended to the same `RowError`.

At this point, `ExcelValidationResult` contains errors from all three sources:
1. JSR-380 constraint violations
2. Within-file uniqueness violations
3. Type conversion failures from parsing

### Step 8: Persist or Generate Error Report

```java
if (validationResult.isValid()) {
    // PERSIST
    PersistenceHandler.SaveResult saveResult =
        template.getPersistenceHandler().saveAll(parseResult.getRows());
} else {
    // ERROR REPORT
    Path errorFile = errorReportService.generateErrorReport(
        xlsxFile, validationResult, parseResult.getColumnMappings(), config);
}
```

**Success path -- Persist**:

`PersistenceHandler.saveAll()` receives the full list of validated DTOs. The tariff
exemption implementation uses **upsert semantics**:

1. For each DTO, query the DB by natural key (`itemName + specification + hsCode`).
2. If a record exists: update all its fields from the DTO.
3. If no record exists: create a new entity from the DTO.
4. Batch-save via `repository.saveAll()` in a single `@Transactional` boundary.
5. Return `SaveResult(created, updated)` counts.

**Failure path -- Error Report**:

`ExcelErrorReportService` produces an annotated copy of the original Excel file:

1. Opens the original uploaded `.xlsx` file.
2. Appends an `_ERRORS` column header after the last existing column.
3. For each row with errors:
   - Highlights the offending cells with a **rose background**.
   - Writes the concatenated error messages into the `_ERRORS` column.
   - All cell values are sanitized via `SecureExcelUtils.sanitizeForExcelCell()` to
     prevent formula injection (prefixes dangerous characters like `=`, `+`, `-`, `@`
     with a single quote).
4. Saves the annotated workbook as `{UUID}.xlsx` in the `errors/` subdirectory.
5. Returns the file path; the orchestrator constructs a download URL:
   `/api/excel/download/{UUID}`.

---

## Flowchart

```
 USER
  |
  |  Uploads .xls/.xlsx via browser form or REST API
  |
  v
+------------------------------------------------------------------+
|  CONTROLLER  (ExcelUploadController)                             |
|                                                                  |
|  1. File size > 10 MB?  --YES-->  Return error                  |
|     |                                                            |
|     NO                                                           |
|     |                                                            |
|  2. orchestrator.processUpload(file, templateType)               |
|                                                                  |
|  3. Catch exceptions --> generic Korean error messages            |
+---------|--------------------------------------------------------+
          |
          v
+------------------------------------------------------------------+
|  ORCHESTRATOR  (ExcelImportOrchestrator)                         |
|                                                                  |
|  1. Find TemplateDefinition by templateType                      |
|     Not found? --> IllegalArgumentException                      |
|                                                                  |
|  2. Create temp dir: /tmp/excel-imports/{UUID}/                  |
+---------|--------------------------------------------------------+
          |
          v
+------------------------------------------------------------------+
|  STEP 2: CONVERSION  (ExcelConversionService)                    |
|                                                                  |
|  sanitizeFilename() --> validate magic bytes                     |
|                                                                  |
|  .xlsx?  Save as-is, validate content                            |
|  .xls?   Validate content, convert HSSFWorkbook -> XSSFWorkbook |
|  other?  Reject                                                  |
|                                                                  |
|  Output: Path to guaranteed .xlsx file                           |
+---------|--------------------------------------------------------+
          |
          v
+------------------------------------------------------------------+
|  STEP 2b: ROW COUNT PRE-CHECK  (SecureExcelUtils.countRows)      |
|                                                                  |
|  StAX streaming count of <row> elements (constant memory)        |
|  roughRowCount > maxRows + headerRows + buffer?  --YES--> Error  |
+---------|--------------------------------------------------------+
          |
          v
+------------------------------------------------------------------+
|  STEP 3: PARSING  (ExcelParserService)                           |
|                                                                  |
|  a) Open workbook securely (SecureExcelUtils.createWorkbook)     |
|  b) Read header row (e.g. row 4)                                 |
|  c) Resolve @ExcelColumn annotations to physical column indices  |
|     (by letter, by index, or by header text matching)            |
|  d) Iterate data rows (e.g. from row 7):                        |
|       - Skip blank rows                                          |
|       - Stop at footer marker (e.g. "※")                        |
|       - Stop if rows > maxRows (parser early-exit)               |
|       - Instantiate DTO per row                                  |
|       - Convert each cell to the field's Java type               |
|       - On conversion error: record CellError, set field to null |
|  e) Track source row numbers for error reporting                 |
|                                                                  |
|  Output: ParseResult { rows, sourceRowNumbers,                   |
|                         columnMappings, parseErrors }            |
+---------|--------------------------------------------------------+
          |
          v
+------------------------------------------------------------------+
|  STEP 4: ROW COUNT CHECK (authoritative)                         |
|                                                                  |
|  rows.size() > 10,000?  --YES-->  Return error                  |
|  (Most oversized files already caught by 2b or parser exit)      |
+---------|--------------------------------------------------------+
          |
          v
+------------------------------------------------------------------+
|  STEP 5: VALIDATION  (ExcelValidationService)                    |
|                                                                  |
|  Pass 1: JSR-380 Bean Validation                                 |
|    For each DTO: validator.validate(dto)                         |
|    @NotBlank, @Size, @Pattern, @DecimalMin, @Min, etc.           |
|    Violations --> CellError (mapped to Excel column position)    |
|                                                                  |
|  Pass 2: Within-File Uniqueness (UniqueConstraintValidator)      |
|    @ExcelUnique          --> single-field duplicate detection    |
|    @ExcelCompositeUnique --> composite-key duplicate detection   |
|                                                                  |
|  Output: ExcelValidationResult { valid, rowErrors[] }            |
+---------|--------------------------------------------------------+
          |
          v
+------------------------------------------------------------------+
|  STEP 6: DB UNIQUENESS  (DatabaseUniquenessChecker)              |
|                                                                  |
|  For each DTO: query DB by natural key                           |
|    EXISTS --> CellError: "이미 등록된 데이터입니다"              |
|                                                                  |
|  validationResult.merge(dbErrors)                                |
+---------|--------------------------------------------------------+
          |
          v
+------------------------------------------------------------------+
|  STEP 7: MERGE PARSE ERRORS                                      |
|                                                                  |
|  validationResult.merge(parseResult.parseErrors)                 |
|  (Type conversion errors from Step 3 are added)                  |
+---------|--------------------------------------------------------+
          |
          v
     +---------+
     | VALID?  |
     +----+----+
     YES  |    NO
     |    |    |
     v    |    v
+----------------+   +----------------------------------------------+
|  PERSIST       |   |  ERROR REPORT  (ExcelErrorReportService)      |
|                |   |                                               |
|  For each DTO: |   |  1. Open original .xlsx                       |
|   Query by key |   |  2. Append "_ERRORS" header column            |
|    |           |   |  3. For each error row:                       |
|   Found?       |   |     - Highlight cells (rose background)       |
|   Y: UPDATE    |   |     - Write error messages in error column    |
|   N: CREATE    |   |     - Sanitize against formula injection      |
|                |   |  4. Save as errors/{UUID}.xlsx                |
|  Batch save    |   |  5. Return download URL                       |
|  in @Transact. |   |                                               |
+-------+--------+   +----------------------+-----------------------+
        |                                    |
        v                                    v
+------------------------------------------------------------------+
|  RESPONSE  (ImportResult)                                        |
|                                                                  |
|  Success:                        Failure:                        |
|    success: true                   success: false                |
|    rowsProcessed: 150              rowsProcessed: 150            |
|    rowsCreated: 120                errorRows: 12                 |
|    rowsUpdated: 30                 errorCount: 25                |
|    message: "데이터 업로드 완료"   downloadUrl: /api/.../UUID    |
|                                    message: "12개 행에서 ..."    |
+------------------------------------------------------------------+
          |
          v
   Rendered as Thymeleaf HTML (result.html) or JSON response body


                   +----------------------------+
                   |  BACKGROUND: Temp Cleanup   |
                   |  (daily at 2:00 AM)         |
                   |  Deletes files > 30 days    |
                   |  from /tmp/excel-imports/   |
                   +----------------------------+
```

---

## The Template Plugin System

Adding a new Excel template requires **zero changes** to the core pipeline. You create a
subpackage under `com.foo.excel.templates` with these components:

| # | File | Role |
|---|---|---|
| 1 | `*Dto.java` | DTO with `@ExcelColumn` + JSR-380 annotations. Each field maps to a column. |
| 2 | `*ImportConfig.java` | Implements `ExcelImportConfig`. Specifies header row, data start row, footer marker, natural key fields. |
| 3 | `*Entity.java` | JPA `@Entity` (if persistence is needed). Uses `@Getter`/`@Setter`, never `@Data`. |
| 4 | `*Repository.java` | Spring Data JPA repository for the entity. |
| 5 | `*Service.java` | Implements `PersistenceHandler<Dto>`. Contains the upsert logic. |
| 6 | `*DbUniquenessChecker.java` | *(Optional)* Implements `DatabaseUniquenessChecker<Dto>`. Checks DB for pre-existing records. |
| 7 | `*TemplateConfig.java` | `@Configuration` class that produces a `TemplateDefinition<Dto>` bean, wiring all the above together. |

The `TemplateDefinition` bean is automatically discovered by the orchestrator via
Spring's collection injection (`List<TemplateDefinition<?>>`).

**Example** (tariff exemption):

```java
@Configuration
public class TariffExemptionTemplateConfig {
    @Bean
    public TemplateDefinition<TariffExemptionDto> tariffExemptionTemplate(
            TariffExemptionService persistenceHandler,
            TariffExemptionDbUniquenessChecker dbUniquenessChecker) {
        return new TemplateDefinition<>(
                "tariff-exemption",          // URL path segment
                TariffExemptionDto.class,    // DTO class for reflection
                new TariffExemptionImportConfig(),  // Excel layout config
                persistenceHandler,          // How to save
                dbUniquenessChecker          // How to check DB uniqueness
        );
    }
}
```

---

## Security Measures

The application enforces several layers of security:

| Measure | Where | Purpose |
|---|---|---|
| Magic byte validation | `SecureExcelUtils.validateFileContent()` | Prevents disguised files (e.g. a `.exe` renamed to `.xlsx`) |
| Filename sanitization | `SecureExcelUtils.sanitizeFilename()` | Blocks path traversal (`../`), null bytes, and non-whitelisted characters |
| Formula injection prevention | `SecureExcelUtils.sanitizeForExcelCell()` | Prefixes `=`, `+`, `-`, `@` with `'` in error report cells |
| Generic error messages | Controller exception handlers | Never exposes `e.getMessage()`, stack traces, or file paths to users |
| Secure workbook opening | `SecureExcelUtils.createWorkbook()` | Uses `OPCPackage` in read-only mode; sets POI byte array limits |
| ThreadLocal DataFormatter | `ExcelParserService` | Prevents thread-safety bugs with POI's non-thread-safe `DataFormatter` |
| File size limit | Controller + `ExcelImportProperties` | Rejects files over 10 MB before any processing |
| Row count limit | SecureExcelUtils + Parser + Orchestrator | Two-tier: SAX pre-count rejects before DOM load; parser exits early; orchestrator does authoritative check |
| UUID-based temp directories | Orchestrator | Isolates uploads; prevents filename collisions and enumeration |
| Scheduled cleanup | `TempFileCleanupService` | Deletes temp files older than 30 days (runs daily at 2:00 AM) |

---

## Key Classes Reference

| Class | Location | Responsibility |
|---|---|---|
| `ExcelUploadController` | `controller/` | HTTP endpoints (REST + Thymeleaf) |
| `ExcelImportOrchestrator` | `service/` | Central pipeline coordinator |
| `ExcelConversionService` | `service/` | `.xls` to `.xlsx` conversion |
| `ExcelParserService` | `service/` | Reflection-based Excel-to-DTO parsing |
| `ExcelValidationService` | `service/` | JSR-380 validation + uniqueness delegation |
| `ExcelErrorReportService` | `service/` | Generates annotated error Excel files |
| `TempFileCleanupService` | `service/` | Scheduled temp file cleanup |
| `TemplateDefinition<T>` | `service/` | Generic bundle of template components |
| `PersistenceHandler<T>` | `service/` | Interface for saving DTOs to the DB |
| `DatabaseUniquenessChecker<T>` | `service/` | Interface for checking DB duplicates |
| `UniqueConstraintValidator` | `validation/` | Within-file uniqueness checking |
| `ExcelValidationResult` | `validation/` | Aggregate validation result with merge support |
| `RowError` / `CellError` | `validation/` | Error data structures |
| `ExcelColumnUtil` | `util/` | Column letter/index conversion |
| `SecureExcelUtils` | `util/` | Secure file operations and sanitization |
| `ExcelImportConfig` | `config/` | Interface for template-specific Excel layout |
| `ExcelImportProperties` | `config/` | Externalized configuration (`application.properties`) |

---

## Configuration Reference

All properties are prefixed with `excel.import.*` in `application.properties`:

| Property | Default | Purpose |
|---|---|---|
| `excel.import.max-file-size-mb` | `10` | Maximum upload file size in megabytes |
| `excel.import.max-rows` | `10000` | Maximum data rows allowed per upload |
| `excel.import.pre-count-buffer` | `100` | Extra rows allowed in SAX pre-count threshold |
| `excel.import.retention-days` | `30` | Days before temp files are cleaned up |
| `excel.import.temp-directory` | `${java.io.tmpdir}/excel-imports` | Where temp and error files are stored |
| `excel.import.error-column-name` | `_ERRORS` | Header text for the error column in reports |

Spring multipart limits must also align:

| Property | Default |
|---|---|
| `spring.servlet.multipart.max-file-size` | `10MB` |
| `spring.servlet.multipart.max-request-size` | `10MB` |
