# Package Refactor Plan (Hardened): `service` + `tariffexemption` Subfoldering

## Decision
- Keep the package split direction.
- Harden execution because the current plan under-specifies verification and has one invalid static check.

## Evidence-Based Challenges to Previous Plan
1. Static check in the prior plan is not valid for detecting stale imports.
- Prior command: `rg "com\\.foo\\.excel\\.service\\.|com\\.foo\\.excel\\.templates\\.samples\\.tariffexemption\\." ...`
- Problem: new target packages still start with those roots, so this check will continue matching even after successful refactor.

2. Blast radius is larger than a single "move + fix imports" pass implies.
- Current tests and controllers import old root packages directly (for example: `TariffUploadPlanContractTest`, `TariffExemptionUploadPageController`).
- This requires staged compile checkpoints, not only an end-of-work full test run.

3. Documentation sync scope is wider than README.
- `README.md`, `HOW_TO_UNDERSTAND_THE_UPLOAD_PIPELINE.md`, and `src/HOW_TO_READ_CODE.md` include concrete class paths under current package roots.
- Limiting updates to README violates repo guardrail to keep inventories synchronized.

4. Behavior-sensitive security/contract paths need explicit acceptance gates.
- Upload security and strict `commonData` parsing are guaranteed by behavior tests and must be explicitly re-verified after refactor.
- Exception-response safety (internal details hidden) must also be re-verified.

5. Tariff DB uniqueness checker move can create confusion without wiring statement.
- Current template wiring intentionally passes `null` checker in `TariffExemptionTemplateConfig`.
- Refactor must keep this unchanged unless a separate behavior change is approved.

## Target Package Structure
1. `com.foo.excel.service.contract`
- Move: `CommonData`, `TemplateDefinition`, `PersistenceHandler`, `DatabaseUniquenessChecker`

2. `com.foo.excel.service.pipeline`
- Move: `ExcelImportOrchestrator`, `ExcelUploadRequestService`

3. `com.foo.excel.service.pipeline.parse`
- Move: `ExcelParserService`, `ColumnResolutionException`, `ColumnResolutionBatchException`

4. `com.foo.excel.service.pipeline.validation`
- Move: `ExcelValidationService`

5. `com.foo.excel.service.pipeline.report`
- Move: `ExcelErrorReportService`

6. `com.foo.excel.service.file`
- Move: `ExcelUploadFileService`, `TempFileCleanupService`

7. `com.foo.excel.templates.samples.tariffexemption.dto`
- Move: `TariffExemptionDto`, `TariffExemptionCommonData`

8. `com.foo.excel.templates.samples.tariffexemption.mapper`
- Move: `TariffExemptionCommonDataFormMapper`

9. `com.foo.excel.templates.samples.tariffexemption.config`
- Move: `TariffExemptionImportConfig`, `TariffExemptionTemplateConfig`

10. `com.foo.excel.templates.samples.tariffexemption.persistence.entity`
- Move: `TariffExemption`, `TariffExemptionId`, `TariffExemptionSummary`, `TariffExemptionSummaryId`

11. `com.foo.excel.templates.samples.tariffexemption.persistence.repository`
- Move: `TariffExemptionRepository`, `TariffExemptionSummaryRepository`

12. `com.foo.excel.templates.samples.tariffexemption.service`
- Move: `TariffExemptionService`, `TariffExemptionDbUniquenessChecker`

## Hardened Execution Steps
1. Prepare package directories and move service contracts first (`service.contract`) with compile checkpoint.
2. Move pipeline/file/report/validation/parse services in small batches, compile after each batch.
3. Move tariff template classes by concern (dto/mapper/config/entity/repository/service), compile after each batch.
4. Update all imports in `src/main/java` and `src/test/java` immediately per batch.
5. Preserve signatures and runtime behavior; this refactor is package-path-only.
6. Preserve tariff checker behavior: keep `TemplateDefinition(..., null)` unless explicitly changing behavior in a separate PR.
7. Update documentation inventories in the same commit set:
- `README.md`
- `HOW_TO_UNDERSTAND_THE_UPLOAD_PIPELINE.md`
- `src/HOW_TO_READ_CODE.md`
- Any active plan docs that enumerate concrete class paths
8. Run full tests and fix fallout.

## Verification Gates
1. Compile checkpoints (required after each phase):
- `./gradlew compileJava`
- `./gradlew testClasses`

2. Focused behavior gates (required before full suite):
- `./gradlew test --tests "com.foo.excel.controller.ExcelApiExceptionHandlerTest"`
- `./gradlew test --tests "com.foo.excel.service.ExcelUploadFileServiceTest"`
- `./gradlew test --tests "com.foo.excel.integration.ExcelImportIntegrationTest"`
- `./gradlew test --tests "com.foo.excel.plan.TariffUploadPlanContractTest"`

3. Full regression:
- `./gradlew test`

4. Correct stale-import checks (replace prior invalid grep):
- `rg -n "^import com\\.foo\\.excel\\.service\\.[A-Z]" src/main/java src/test/java`
- `rg -n "^import com\\.foo\\.excel\\.templates\\.samples\\.tariffexemption\\.[A-Z]" src/main/java src/test/java`
- Expectation: no remaining imports from old flat roots.

5. Documentation path checks:
- `rg -n "src/main/java/com/foo/excel/service/|src/main/java/com/foo/excel/templates/samples/tariffexemption/" README.md HOW_TO_UNDERSTAND_THE_UPLOAD_PIPELINE.md src/HOW_TO_READ_CODE.md docs`
- Expectation: any surviving references are intentional and accurate.

## Interface Impact
- Breaking package-name changes remain expected for moved classes.
- Method signatures remain unchanged.
- This is acceptable under repo guardrail (backward compatibility is not a goal).

## Out of Scope
- No behavioral changes to validation/security/persistence semantics.
- No activation of tariff DB uniqueness checker in this refactor.
