# Phase 1 TDD Plan: Template-Driven Cell Merges in Error Reports

This phase adds merge behavior without removing the current config abstraction yet. The design constraint is that merge metadata should live explicitly with the DTO/template contract so Phase 2 can remove config cleanly instead of redesigning merge metadata again.

## Summary

The goal is to make `ExcelErrorReportService` generate error report workbooks that apply template-defined merges even when the uploaded workbook does not contain them. This includes both header merges and data-row merges for AAppcar. The current `ExcelImportConfig` flow remains in place for sheet-level settings in this phase, but merge metadata is introduced in a DTO-centered way.

## Implementation Changes

1. Define explicit merge metadata owned by the DTO/template contract.
   - Introduce structured merge metadata, not comments.
   - The metadata must support:
     - merge scope: `HEADER` and `DATA`
     - row span for header merges
     - column span for both header and data merges
     - whether a data merge repeats on every data row
   - The metadata should be attached to or derived from `AAppcarItemDto`, not invented only in config.

2. Keep existing `ExcelImportConfig` for non-merge sheet settings in this phase.
   - `AAppcarItemImportConfig` continues to provide `headerRow`, `dataStartRow`, and `footerMarker`.
   - Do not move those settings yet.
   - If necessary, add a narrow bridge so runtime code can resolve merge metadata from the DTO while still using config for the rest.

3. Make AAppcar merge spec explicit.
   - Header merges must be represented as template metadata and actively applied.
   - Data-row merges must also be explicit.
   - For AAppcar, Phase 1 should cover:
     - header merges currently implied by the sample sheet structure, including the `소요량` grouped headers
     - data-row merges: `F:G`, `J:K`, `L:M`, `O:P`

4. Update error report generation to apply template merges proactively.
   - The report generator must not rely only on `WorkbookCopyUtils.copyMergedRegions(...)`.
   - It should still copy existing source merges, but then also apply template-defined merges.
   - If a merge already exists from the source workbook, skip re-adding it.
   - If a merge would overlap illegally with an existing region, handle it deterministically by skipping the duplicate template application rather than throwing.
   - `_ERRORS` must remain outside all template-driven merged regions.

5. Limit merge application to the intended sheet and rows.
   - Apply only on the configured data sheet.
   - Apply header merges to the template’s header area.
   - Apply repeating data merges only from `dataStartRow` through the copied data rows.
   - Keep the compact report path unchanged unless it copies sheet data requiring the same merge behavior.

## TDD Workflow

1. Test agent writes the failing tests first.
   - Add unit tests in `ExcelErrorReportServiceTest`.
   - Add only the smallest necessary additional integration coverage in `ExcelImportIntegrationTest`.

2. Required failing tests for Phase 1:
   - when the source workbook already contains header merges, the generated error report preserves them
   - when the source workbook omits header merges that are required by template metadata, the generated error report reconstructs them
   - generated error reports apply AAppcar data-row merges `F:G`, `J:K`, `L:M`, `O:P`
   - `_ERRORS` column is appended outside merged regions
   - duplicate merge application does not throw when source and template define the same merge
   - error cell highlighting and error message output still work after merge application

3. Implementation agent makes production changes until the tests pass.
   - The agent should not redesign `ExcelImportConfig` in this phase.
   - The agent should not leave merge semantics split between comments and runtime rules.
   - The agent should treat the DTO metadata as the authoritative merge definition.

4. Validation commands after implementation:
   - `./gradlew test --tests com.foo.excel.service.pipeline.report.ExcelErrorReportServiceTest`
   - `./gradlew test --tests com.foo.excel.integration.ExcelImportIntegrationTest`

## Acceptance Criteria

- Error reports reconstruct required AAppcar header merges from metadata even if the uploaded workbook lacks them.
- Error reports apply required AAppcar repeating data-row merges.
- Existing source merges are still preserved.
- No overlap or duplicate merge exception is thrown.
- `_ERRORS` stays unmerged and usable.
- Phase 1 does not remove `ExcelImportConfig`.

## Assumptions

- In this phase, only merge metadata is promoted to the DTO/template contract.
- Header row, data start row, footer marker, and error column name remain in `ExcelImportConfig` until Phase 2.
- AAppcar is the first template to use the new merge metadata model.
