# Phase 1 TDD Plan v2: DTO-Local Merge Semantics for Error Reports

This version replaces the earlier low-level merge annotation direction with a more readable DTO contract. The immediate goal is still the same as v1: make `ExcelErrorReportService` reconstruct template-defined merged cells in generated error reports even when the uploaded workbook does not contain those merges. The difference is how the template contract is expressed.

## Summary

The template contract should stay close to the DTO fields that own the Excel columns. Field-local column metadata remains the primary source of truth, and merge semantics should be expressed at the same level whenever possible.

For AAppcar, this means:

- simple horizontal ownership such as `F:G`, `J:K`, `L:M`, `O:P` should be expressed on the owning field
- shared grouped header structure such as `소요량` should be declared near the anchor field of the group rather than in a distant DTO-level block
- low-level merge mechanics like repeated `@ExcelHeaderMerge(rowOffset=...)` should be replaced by a more semantic contract

This version also proposes a cleanup of the `ExcelColumn` API so the annotation reads more like template structure and less like parser internals.

## Design Direction

1. Keep the DTO as the authoritative template contract.
   - Merge semantics should be derived from DTO annotations.
   - Do not move merge rules into `ExcelImportConfig`.

2. Simplify field-owned width semantics.
   - Add `columnSpan` to `@ExcelColumn`.
   - Use that as the source of truth for horizontal field coverage.
   - Remove the need for a separate field-level annotation whose only job is horizontal span.

3. Replace ambiguous header naming with more readable terms.
   - Rename `ExcelColumn.header` to `label`.
   - Rename `ExcelColumn.headerPath` to `headerLabels`.
   - Replace `headerRowEnd` with `headerRowCount`.
   - Keep `headerRowStart`.

4. Express shared header groups semantically, not mechanically.
   - Introduce a compact anchor-field annotation such as `@ExcelHeaderGroup`.
   - It should be placed on the leftmost field that owns the group.
   - It should reference sibling fields in the group, for example `fields = {"prodQty", "repairQty"}`.
   - The runtime resolver should infer the actual merged regions from the anchor field and referenced fields.

5. Remove low-level header merge annotations from the DTO surface.
   - Avoid forcing readers to interpret `rowOffset`, `rowSpan`, and `columnSpan` repeatedly just to understand a grouped header.
   - The annotation API should describe the sheet concept, not the renderer algorithm.

## Proposed DTO Shape

For AAppcar, the target shape should look like this:

```java
@ExcelColumn(
    label = "제조용",
    column = "J",
    columnSpan = 2,
    ignoreHeaderWhitespace = true,
    headerRowStart = 4,
    headerRowCount = 3,
    headerLabels = {"소요량", "제조용"})
@ExcelHeaderGroup(
    label = "소요량",
    fields = {"prodQty", "repairQty"})
@Min(value = 0, message = "제조용 소요량은 0 이상이어야 합니다")
private Integer prodQty;

@ExcelColumn(
    label = "수리용",
    column = "L",
    columnSpan = 2,
    ignoreHeaderWhitespace = true,
    headerRowStart = 4,
    headerRowCount = 3,
    headerLabels = {"소요량", "수리용"})
@Min(value = 0, message = "수리용 소요량은 0 이상이어야 합니다")
private Integer repairQty;
```

The same principle applies to:

- `hsno`: `column = "F"`, `columnSpan = 2`
- `approvalYn`: `column = "O"`, `columnSpan = 2`

## Implementation Changes

1. Revise `ExcelColumn`.
   - Add `int columnSpan() default 1`.
   - Rename `String header()` to `String label()`.
   - Rename `String[] headerPath()` to `String[] headerLabels()`.
   - Replace `int headerRowEnd()` with `int headerRowCount()`.
   - Update all call sites, parser logic, tests, and error-message usage.

2. Introduce a compact anchor-field grouped-header annotation.
   - Candidate shape:
     - `label`
     - `fields`
   - The resolver should interpret this as one shared parent header spanning the referenced fields.
   - The resolver should assume the annotated field is the anchor or leftmost field of the group.

3. Update merge metadata resolution.
   - `TemplateMergeMetadataResolver` should derive merge regions from:
     - `ExcelColumn.column`
     - `ExcelColumn.columnSpan`
     - grouped-header annotations on anchor fields
   - It should no longer require low-level per-header merge declarations.
   - It should deduplicate regions deterministically.

4. Preserve the current error-report runtime behavior.
   - `ExcelErrorReportService` should keep:
     - copying source merged regions
     - proactively applying template merge regions
     - skipping duplicate merges
     - skipping illegal overlaps
     - keeping `_ERRORS` outside merged regions

5. Keep `ExcelImportConfig` unchanged in this phase.
   - `headerRow`, `dataStartRow`, `sheetIndex`, `footerMarker`, and error column naming remain where they are.
   - This phase is about the DTO merge contract and annotation readability, not config removal.

6. Remove temporary or noisy merge annotations after the new contract is in place.
   - Remove the current low-level header merge annotation usage from DTOs.
   - Remove redundant field-level merge annotations if `columnSpan` on `ExcelColumn` fully replaces them.

## TDD Workflow

1. Add or revise parser tests first.
   - Verify `headerRowStart + headerRowCount` resolves the same header range as the current `start + end` model.
   - Verify renamed annotation members still resolve multi-row headers correctly.

2. Add or revise merge resolver tests.
   - anchor-field grouped-header annotation expands to the expected shared header merge
   - `columnSpan` on `ExcelColumn` expands to expected horizontal merge coverage
   - duplicate grouped-header declarations are either forbidden or ignored deterministically

3. Keep error-report tests focused on behavior.
   - when source merges exist, preserve them
   - when source merges are absent, reconstruct required AAppcar merges
   - apply AAppcar repeating data-row merges based on field span metadata
   - keep `_ERRORS` outside merged regions
   - avoid duplicate/overlap exceptions
   - preserve error highlighting and error messages

4. Keep only minimal integration coverage.
   - invalid AAppcar upload without source header merges still produces an error report with reconstructed template merges

## Required Test Updates

1. `ExcelParserServiceTest`
   - replace usages of `headerRowEnd` with `headerRowCount`
   - confirm multi-row header matching still works for AAppcar

2. `ExcelErrorReportServiceTest`
   - preserve current merge behavior assertions
   - adjust any test fixtures or helper DTOs to the revised annotation model

3. `ExcelImportIntegrationTest`
   - preserve the current end-to-end error-report merge reconstruction coverage

4. Add resolver-level tests if needed.
   - If `TemplateMergeMetadataResolver` becomes more semantic, direct unit coverage is justified.

## Acceptance Criteria

- The DTO annotation model is simpler to read than the current low-level merge annotation approach.
- AAppcar horizontal field coverage is expressed via `ExcelColumn.columnSpan`.
- Shared grouped header `소요량` is expressed once near the anchor field, not as several low-level merge instructions.
- Error reports still reconstruct required AAppcar header and data merges.
- Existing source merges are still preserved.
- No duplicate or overlap exception is thrown during merge application.
- `_ERRORS` remains outside merged regions.
- `ExcelImportConfig` is not removed in this phase.

## Migration Notes

- This version intentionally changes annotation names for readability.
- Backward compatibility is optional in this repository. A direct replacement is acceptable if it simplifies the design.
- If both old and new annotation members are briefly supported during transition, fail fast when conflicting combinations are supplied.

## Validation Commands

- `./gradlew test --tests com.foo.excel.service.pipeline.parse.ExcelParserServiceTest`
- `./gradlew test --tests com.foo.excel.service.pipeline.report.ExcelErrorReportServiceTest`
- `./gradlew test --tests com.foo.excel.integration.ExcelImportIntegrationTest`
