# Phase 2 TDD Plan: Remove `ExcelImportConfig` and Move Remaining Template ImportMetadata to DTOs

This phase starts only after Phase 1 is complete and green. Its job is to remove the separate config abstraction and consolidate the remaining sheet-level metadata onto DTO-owned metadata, reusing the merge metadata model introduced in Phase 1.

## Summary

The goal is to eliminate `ExcelImportConfig` and per-template config classes such as `AAppcarItemImportConfig`. After this phase, a template’s Excel contract should be defined by DTO metadata plus existing field-level annotations in `AAppcarItemRow`.

## Implementation Changes

1. Introduce DTO-level metadata for the remaining sheet settings.
   - Add class-level metadata for:
     - `sheetIndex`
     - `headerRow`
     - `dataStartRow`
     - `footerMarker`
     - `errorColumnName`
   - Reuse the Phase 1 DTO-centered merge metadata model rather than replacing it.

2. Annotate AAppcar DTO with the full template contract.
   - `AAppcarItemRow` becomes the home for:
     - sheet-level metadata
     - merge metadata
     - existing field-level `@ExcelColumn` mapping and validation
   - No behavior should continue to depend on a separate AAppcar config class after this phase.

3. Remove config plumbing from the runtime model.
   - Remove the `config` field from `ExcelImportDefinition`.
   - Replace `template.getConfig()` usage in parser, orchestrator, and error-report code with resolved DTO metadata.
   - Normalize DTO metadata once per template rather than reflecting repeatedly in deep loops.

4. Delete old config types after runtime conversion is complete.
   - Delete `ExcelImportConfig`.
   - Delete `AAppcarItemImportConfig`.
   - Update `AAppcarItemImportConfig` so `ExcelImportDefinition` is built without config.

5. Preserve all existing semantics.
   - Parsing behavior must remain the same:
     - header matching still uses row `4`
     - data starts at row `7`
     - footer marker `※` still stops parsing
   - Error reports must preserve the Phase 1 merge behavior exactly.
   - Korean-facing behavior and existing error messages remain unchanged.

## TDD Workflow

1. Test agent writes failing tests first for DTO-level metadata resolution.
   - Add a focused metadata-resolution test class if needed.
   - Update existing tests so the contract is asserted through DTO metadata rather than config objects.

2. Required failing tests for Phase 2:
   - DTO metadata resolves AAppcar sheet index correctly
   - DTO metadata resolves header row `4`
   - DTO metadata resolves data start row `7`
   - DTO metadata resolves footer marker `※`
   - DTO metadata resolves `_ERRORS` column name
   - parser behavior remains unchanged after config removal
   - error report behavior from Phase 1 still passes unchanged after config removal
   - AAppcar integration import path still works end-to-end after config removal

3. Implementation agent removes config-based behavior until all tests pass.
   - The agent should not change the observable semantics of parsing or reporting.
   - The agent should not reintroduce a second parallel config abstraction under another name.

4. Validation commands after implementation:
   - `./gradlew test --tests com.foo.excel.service.pipeline.parse.ExcelParserServiceTest`
   - `./gradlew test --tests com.foo.excel.service.pipeline.report.ExcelErrorReportServiceTest`
   - `./gradlew test --tests com.foo.excel.integration.ExcelImportIntegrationTest`
   - `./gradlew test`

## Acceptance Criteria

- `ExcelImportConfig` and AAppcar’s config class are removed.
- AAppcar’s sheet-level Excel contract is defined on the DTO side.
- Parser behavior is unchanged.
- Error-report merge behavior from Phase 1 is unchanged.
- Full test suite passes.

## Assumptions

- Phase 2 depends on Phase 1 and reuses its merge metadata model.
- DTO-level metadata is the single source of truth for template structure after this phase.
- `ExcelImportDefinition` still retains `rowClass`, `metaDataClass`, persistence handler, upload precheck, and DB uniqueness checker; only config is removed.
