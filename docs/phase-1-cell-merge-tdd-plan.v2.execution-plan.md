# Phase 1 Cell Merge v2 Execution Plan

This note records the agreed execution order for implementing [phase-1-cell-merge-tdd-plan.v2.md](/workspaces/excel-upload-template-java/docs/phase-1-cell-merge-tdd-plan.v2.md).

## Status

- Step 1 merge-model transition: completed
- Step 2 `ExcelColumn` naming cleanup: completed
- README/template-authoring update: completed

## Goal

Keep the readable v2 merge-contract direction, but avoid combining it with the broader `ExcelColumn` API rename cleanup in the same implementation step.

## Execution Order

### Step 1: Replace the noisy v1 merge annotations with the readable v2 merge model

Implement the merge-model transition first.

- Keep the existing error-report merge application pipeline:
  - `TemplateDefinition` caches template merge metadata
  - `ExcelImportOrchestrator` passes template merge metadata into the error report service
  - `ExcelErrorReportService` copies source merges, applies template merges, skips duplicate source/template reapplication, skips illegal overlap at report-generation time, and keeps `_ERRORS` outside merged regions
- Replace low-level merge annotations with the v2 contract:
  - move simple horizontal span ownership into `ExcelColumn.columnSpan`
  - introduce `@ExcelHeaderGroup` for grouped header ownership
  - derive template merge regions from `ExcelColumn` + `@ExcelHeaderGroup`
- Keep the `columnSpan` name.
  - The intended audience is already familiar with HTML/CSS table concepts.
  - `columnSpan` is therefore considered readable enough and does not need an additional naming change.
- Remove the v1 low-level DTO merge annotations after the new resolver is in place:
  - `@ExcelHeaderMerge`
  - `@ExcelHeaderMerges`
  - `@ExcelDataMerge`
  - `@ExcelDataMerges`
- Make grouped-header metadata fail fast during metadata resolution when any of the following are detected:
  - duplicate field membership
  - non-adjacent grouped fields
  - reversed field order
  - inferred merge overlap
  - duplicate inferred regions
- Add resolver-only tests before or alongside the implementation change.
  - Lock down adjacent-group expansion
  - Lock down duplicate membership failure
  - Lock down non-adjacent field failure
  - Lock down inferred overlap failure
  - Lock down the intended grouped-header expansion for AAppcar
- Centralize annotation-shape validation in the merge metadata resolver.
  - Fail fast there rather than leaving invalid metadata to be discovered indirectly during report generation.
- Treat the intended AAppcar grouped header contract as `J4:M4`, not `J4:L4`
- Update report/integration tests and workbook fixtures to match the new grouped-header span
- Consolidate workbook fixture builders where practical so merged and unmerged AAppcar header variants share one canonical setup path

### Step 2: Do the `ExcelColumn` readability rename cleanup separately

After the merge-model transition was completed and passing, the annotation naming cleanup was performed as a dedicated follow-up refactor.

Planned rename set:

- `header` -> `label`
- `headerPath` -> `headerLabels`
- replace `headerRowStart` + `headerRowEnd` with `headerRowStart` + `headerRowCount`

Completed cleanup scope:

- `ExcelColumn`
- `ExcelParserService`
- parser tests
- affected DTOs
- docs such as `README.md`

Note:

- `headerRowEnd` and `headerRowCount` are not rename-equivalent
- `headerRowEnd` is an inclusive ending row number
- `headerRowCount` is a span/count from `headerRowStart`
- parser logic and tests must be updated as a semantic change, not treated as a simple member rename

### Step 3: Update template-authoring documentation after the model settles

After the merge-model transition and the later `ExcelColumn` cleanup were complete, a short template-authoring note was added to `README.md`.

Recommended content:

- how `columnSpan` is used for horizontally owned fields
- how `@ExcelHeaderGroup` expresses grouped headers
- what metadata combinations fail fast
- a minimal AAppcar-style example

## Why This Order

Separating the work keeps Phase 1 focused on the behavioral change that matters now: readable DTO-local merge semantics for error reports.

It also avoids mixing two categories of change in one pass:

- merge metadata model replacement
- parser annotation API rename churn

That split should make implementation, review, and regression diagnosis simpler.
