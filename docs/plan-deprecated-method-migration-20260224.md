# Plan: Deprecated Method Migration (Jackson + Apache POI)

## Goal
Remove current deprecation usage while preserving implemented behavior and contracts.

## Scope
1. `ExcelUploadRequestService` Jackson mapper configuration for strict `commonData` parsing.
2. `ExcelErrorReportService` SXSSF workbook cleanup path.

## Evidence Baseline (Current State)
1. `ExcelUploadRequestService` currently uses deprecated `ObjectMapper.disable(MapperFeature...)`.
2. `ExcelErrorReportService` currently calls deprecated `SXSSFWorkbook.dispose()` in `finally`.
3. `commonData` strictness is already contract-tested for:
   - unknown field rejection
   - numeric scalar-to-textual rejection
4. No current test explicitly verifies boolean scalar-to-textual rejection.
5. No current test explicitly verifies temp cleanup equivalence after replacing `dispose()` with `close()`.

## Migration Design

### 1) Jackson (`ExcelUploadRequestService`)
- File: `src/main/java/com/foo/excel/service/ExcelUploadRequestService.java`
- Current:
  - `strictMapper.disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);`
- Revised:
  - Replace with explicit non-deprecated config mutation that disables the same mapper feature on the copied mapper.
  - Keep:
    - `objectMapper.copy()`
    - `FAIL_ON_UNKNOWN_PROPERTIES`
    - `coercionConfigFor(LogicalType.Textual)` fail rules for Integer/Float/Boolean
- Constraint:
  - Keep behavior limited to parse-time strict deserialization path; no controller/API contract changes.

### 2) Apache POI (`ExcelErrorReportService`)
- File: `src/main/java/com/foo/excel/service/ExcelErrorReportService.java`
- Current:
  - explicit `sxssfWb.dispose();`
- Revised:
  - Use `try-with-resources` for `SXSSFWorkbook` and rely on `close()`.
- Rationale:
  - In POI 5.4.1, `close()` invokes `dispose()` internally and then closes backing `XSSFWorkbook`.
- Constraint:
  - Preserve current error report content/format and temp cleanup behavior.

## Implementation Steps
1. Update `parseAndValidateCommonData(...)` mapper setup to remove deprecated `disable(MapperFeature...)` call while preserving strict parse behavior.
2. Refactor `generateErrorReport(...)` SXSSF lifecycle to try-with-resources and remove direct `dispose()` call.
3. Keep all user-facing messages/logs unchanged (Korean).

## Verification Plan
1. Build and run tests:
   - `./gradlew clean test`
2. Contract behavior checks for `commonData`:
   - unknown field rejected (existing)
   - numeric scalar to textual rejected (existing)
   - boolean scalar to textual rejected (add test)
3. Error report checks:
   - existing format/content tests remain green
   - add a cleanup-focused test that exercises `generateErrorReport(...)` repeatedly to detect temp-file leakage regression
4. Deprecation checks:
   - confirm no direct usage remains in project sources via search:
     - `rg "disable\\(MapperFeature|\\.dispose\\(" src/main/java`
   - optionally compile with deprecation diagnostics if build config enables symbol-level output.

## Risks and Mitigations
1. Risk: mapper strictness accidentally loosens during API replacement.
   - Mitigation: enforce boolean scalar rejection test in addition to existing unknown/numeric tests.
2. Risk: SXSSF cleanup changes leak temporary files.
   - Mitigation: add focused cleanup regression test and run repeated generation in one test case.

## Out of Scope
1. Broad ObjectMapper refactor beyond deprecated-call replacement.
2. Any schema/API/template contract redesign.
