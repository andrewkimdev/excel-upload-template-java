# How to Read This Codebase

This guide is a practical reading order for the current implementation.

## 1) Start with runtime truth

1. `src/main/java/com/foo/excel/controller/ExcelFileController.java`
2. `src/main/java/com/foo/excel/controller/AAppcarItemImportApiController.java`
3. `src/main/java/com/foo/excel/service/pipeline/ExcelImportRequestService.java`
4. `src/main/java/com/foo/excel/service/pipeline/ExcelImportOrchestrator.java`
5. `src/main/java/com/foo/excel/service/file/ExcelUploadFileService.java`
6. `src/main/java/com/foo/excel/service/pipeline/parse/ExcelParserService.java`
7. `src/main/java/com/foo/excel/service/pipeline/validation/ExcelValidationService.java`
8. `src/main/java/com/foo/excel/validation/WithinFileUniqueConstraintValidator.java`
9. `src/main/java/com/foo/excel/service/pipeline/report/ExcelErrorReportService.java`

These files show the full import flow end-to-end.

## 2) Keep these current behavior facts in mind

- Upload accepts `.xlsx` only. `.xls` is rejected in `ExcelUploadFileService`.
- `metaData` is required for REST upload (`/api/excel/upload/aappcar`).
- `metaData` is parsed strictly (`FAIL_ON_UNKNOWN_PROPERTIES`, coercion disabled).
- Server-managed fields are fixed by import metadata DTOs (for tariff: `AAppcarItemImportMetadata`): `companyId`, `customId`, stored `filePath`, and approval defaults.
- `Metadata`는 `assignFilePath(String)` 계약을 제공하며, orchestrator가 저장된 업로드 경로를 주입합니다.
- Security path uses filename sanitization, magic-byte validation, secure workbook open, row pre-count, parser row-limit early-exit.
- Error reports preserve formatting, add `_ERRORS`, sanitize formula-like values, and store original filename in a `.meta` file.

## 3) Read the annotation/config DSL

- `src/main/java/com/foo/excel/annotation/ExcelColumn.java`
- `src/main/java/com/foo/excel/annotation/HeaderMatchMode.java`
- `src/main/java/com/foo/excel/annotation/ExcelUnique.java`
- `src/main/java/com/foo/excel/annotation/ExcelCompositeUnique.java`
- `src/main/java/com/foo/excel/annotation/ExcelCompositeUniques.java`
- `src/main/java/com/foo/excel/config/ExcelImportProperties.java`
- `src/main/java/com/foo/excel/service/contract/ExcelImportDefinition.java`

## 4) Read utilities and error model

- `src/main/java/com/foo/excel/util/SecureExcelUtils.java`
- `src/main/java/com/foo/excel/util/WorkbookCopyUtils.java`
- `src/main/java/com/foo/excel/util/ExcelColumnUtil.java`
- `src/main/java/com/foo/excel/validation/CellError.java`
- `src/main/java/com/foo/excel/validation/RowError.java`
- `src/main/java/com/foo/excel/validation/ExcelValidationResult.java`

## 5) Read the sample import implementation

- `src/main/java/com/foo/excel/imports/samples/aappcar/dto/AAppcarItemImportRow.java`
- `src/main/java/com/foo/excel/templates/samples/aappcar/config/AAppcarItemImportConfig.java`
- `src/main/java/com/foo/excel/templates/samples/aappcar/service/AAppcarItemPersistenceService.java`
- `src/main/java/com/foo/excel/templates/samples/aappcar/persistence/entity/AAppcarItem.java`
- `src/main/java/com/foo/excel/templates/samples/aappcar/persistence/entity/AAppcarEquip.java`
- `src/main/java/com/foo/excel/templates/samples/aappcar/persistence/repository/AAppcarItemRepository.java`
- `src/main/java/com/foo/excel/templates/samples/aappcar/persistence/repository/AAppcarEquipRepository.java`
- `src/main/java/com/foo/excel/templates/samples/aappcar/service/AAppcarItemDatabaseUniquenessChecker.java`

Note: `AAppcarItemImportConfig` is the import wiring entry point for the sample module.

## 6) Read the web layer

- `src/main/resources/templates/upload-aappcar.html`
- `src/main/resources/templates/result.html`
- `src/main/resources/static/style.css`
- `src/main/resources/application.properties`

## 7) Read tests in pipeline order

- `src/test/java/com/foo/excel/util/SecureExcelUtilsTest.java`
- `src/test/java/com/foo/excel/service/file/ExcelUploadFileServiceTest.java`
- `src/test/java/com/foo/excel/service/pipeline/parse/ExcelParserServiceTest.java`
- `src/test/java/com/foo/excel/service/pipeline/validation/ExcelValidationServiceTest.java`
- `src/test/java/com/foo/excel/validation/WithinFileUniqueConstraintValidatorTest.java`
- `src/test/java/com/foo/excel/service/pipeline/report/ExcelErrorReportServiceTest.java`
- `src/test/java/com/foo/excel/integration/ExcelImportIntegrationTest.java`
- `src/test/java/com/foo/excel/util/WorkbookCopyUtilsTest.java`
- `src/test/java/com/foo/excel/util/ExcelColumnUtilTest.java`
- `src/test/java/com/foo/excel/service/pipeline/parse/ColumnResolutionExceptionTest.java`
- `src/test/java/com/foo/excel/plan/TariffImportPlanContractTest.java`

## 8) Quick manual check before deep reading

```bash
./gradlew bootRun
```

Then test:
- upload a valid `.xlsx`
- upload invalid data to trigger error report
- verify `/api/excel/download/{fileId}` download behavior
