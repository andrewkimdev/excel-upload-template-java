# How to Read This Codebase

This guide is a practical reading order for the current implementation.

## 1) Start with runtime truth

1. `src/main/java/com/foo/excel/controller/ExcelFileController.java`
2. `src/main/java/com/foo/excel/service/pipeline/ExcelImportOrchestrator.java`
3. `src/main/java/com/foo/excel/service/file/ExcelUploadFileService.java`
4. `src/main/java/com/foo/excel/service/pipeline/parse/ExcelParserService.java`
5. `src/main/java/com/foo/excel/service/pipeline/validation/ExcelValidationService.java`
6. `src/main/java/com/foo/excel/validation/WithinFileUniqueConstraintValidator.java`
7. `src/main/java/com/foo/excel/service/pipeline/report/ExcelErrorReportService.java`

These files show the full upload flow end-to-end.

## 2) Keep these current behavior facts in mind

- Upload accepts `.xlsx` only. `.xls` is rejected in `ExcelUploadFileService`.
- `commonData` is required for REST upload (`/api/excel/upload/{templateType}`).
- `commonData` is parsed strictly (`FAIL_ON_UNKNOWN_PROPERTIES`, coercion disabled).
- Server-managed fields are fixed by template commonData DTOs (for tariff: `TariffExemptionCommonData`): `createdBy=user01`, `approvedYn=N`.
- Security path uses filename sanitization, magic-byte validation, secure workbook open, row pre-count, parser row-limit early-exit.
- Error reports preserve formatting, add `_ERRORS`, sanitize formula-like values, and store original filename in a `.meta` file.

## 3) Read the annotation/config DSL

- `src/main/java/com/foo/excel/annotation/ExcelColumn.java`
- `src/main/java/com/foo/excel/annotation/HeaderMatchMode.java`
- `src/main/java/com/foo/excel/annotation/ExcelUnique.java`
- `src/main/java/com/foo/excel/annotation/ExcelCompositeUnique.java`
- `src/main/java/com/foo/excel/annotation/ExcelCompositeUniques.java`
- `src/main/java/com/foo/excel/config/ExcelImportConfig.java`
- `src/main/java/com/foo/excel/config/ExcelImportProperties.java`
- `src/main/java/com/foo/excel/service/contract/TemplateDefinition.java`

## 4) Read utilities and error model

- `src/main/java/com/foo/excel/util/SecureExcelUtils.java`
- `src/main/java/com/foo/excel/util/WorkbookCopyUtils.java`
- `src/main/java/com/foo/excel/util/ExcelColumnUtil.java`
- `src/main/java/com/foo/excel/validation/CellError.java`
- `src/main/java/com/foo/excel/validation/RowError.java`
- `src/main/java/com/foo/excel/validation/ExcelValidationResult.java`

## 5) Read the sample template implementation

- `src/main/java/com/foo/excel/templates/samples/tariffexemption/dto/TariffExemptionDto.java`
- `src/main/java/com/foo/excel/templates/samples/tariffexemption/config/TariffExemptionImportConfig.java`
- `src/main/java/com/foo/excel/templates/samples/tariffexemption/config/TariffExemptionTemplateConfig.java`
- `src/main/java/com/foo/excel/templates/samples/tariffexemption/service/TariffExemptionService.java`
- `src/main/java/com/foo/excel/templates/samples/tariffexemption/persistence/entity/TariffExemption.java`
- `src/main/java/com/foo/excel/templates/samples/tariffexemption/persistence/entity/TariffExemptionSummary.java`
- `src/main/java/com/foo/excel/templates/samples/tariffexemption/persistence/repository/TariffExemptionRepository.java`
- `src/main/java/com/foo/excel/templates/samples/tariffexemption/persistence/repository/TariffExemptionSummaryRepository.java`
- `src/main/java/com/foo/excel/templates/samples/tariffexemption/service/TariffExemptionDbUniquenessChecker.java`

Note: `TariffExemptionTemplateConfig` currently wires `dbUniquenessChecker` as `null`, so DB uniqueness in the orchestrator is optional and disabled for the active template wiring.

## 6) Read the web layer

- `src/main/resources/templates/upload-tariff-exemption.html`
- `src/main/resources/templates/result.html`
- `src/main/resources/static/style.css`
- `src/main/resources/application.properties`

## 7) Read tests in pipeline order

- `src/test/java/com/foo/excel/util/SecureExcelUtilsTest.java`
- `src/test/java/com/foo/excel/service/ExcelUploadFileServiceTest.java`
- `src/test/java/com/foo/excel/service/ExcelParserServiceTest.java`
- `src/test/java/com/foo/excel/service/ExcelValidationServiceTest.java`
- `src/test/java/com/foo/excel/validation/WithinFileUniqueConstraintValidatorTest.java`
- `src/test/java/com/foo/excel/service/ExcelErrorReportServiceTest.java`
- `src/test/java/com/foo/excel/integration/ExcelImportIntegrationTest.java`
- `src/test/java/com/foo/excel/util/WorkbookCopyUtilsTest.java`
- `src/test/java/com/foo/excel/util/ExcelColumnUtilTest.java`
- `src/test/java/com/foo/excel/service/ColumnResolutionExceptionTest.java`
- `src/test/java/com/foo/excel/plan/TariffUploadPlanContractTest.java`

## 8) Quick manual check before deep reading

```bash
./gradlew bootRun
```

Then test:
- upload a valid `.xlsx`
- upload invalid data to trigger error report
- verify `/api/excel/download/{fileId}` download behavior
