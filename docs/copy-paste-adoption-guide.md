# Copy/Paste Adoption Guide

This guide is intentionally strict and short. It only includes claims that are verified by runtime code in this repository.

## 1. Baseline

- Java 17
- Spring Boot 3.3.x
- Gradle 8+

Required dependencies for this module:
- `spring-boot-starter-web`
- `spring-boot-starter-validation`
- `org.apache.poi:poi-ooxml:5.4.1`
- Lombok (`compileOnly` + `annotationProcessor`)

Add when needed:
- `spring-boot-starter-data-jpa` (if your template persistence uses JPA, like the tariff sample)
- `spring-boot-starter-thymeleaf` (only for server-side pages)

Not required by current runtime code:
- `org.apache.commons:commons-lang3`

## 2. Copy Order

### Step A: Core engine (required)

Copy packages:
- `com.foo.excel.annotation`
- `com.foo.excel.config`
- `com.foo.excel.service.contract`
- `com.foo.excel.service.file`
- `com.foo.excel.service.pipeline`
- `com.foo.excel.util`
- `com.foo.excel.validation`

And copy:
- `com.foo.excel.templates.TemplateTypes`

### Step B: One working template (required)

Copy:
- `com.foo.excel.templates.samples.tariffexemption`

This gives the full pattern:
- row DTO (`@ExcelColumn` + validation)
- `ExcelImportConfig`
- template `CommonData`
- `PersistenceHandler<T, C extends CommonData>`
- optional `DatabaseUniquenessChecker<T>`
- `TemplateDefinition<T, C>` bean with `commonDataClass`
- upload-level summary entity/repository

### Step C: Controller layer (choose one)

API only:
- `TariffExemptionUploadApiController`
- `ExcelFileController`
- `ExcelApiExceptionHandler`
- `ExcelUploadRequestService`

API + Thymeleaf pages:
- add `TariffExemptionUploadPageController`
- add `UploadIndexController`
- add `templates/*.html`, `static/style.css`
- add `TariffExemptionCommonDataFormMapper`

## 3. Security and Contract Rules You Must Keep

- filename sanitization
- magic-byte validation
- `.xlsx` only (`.xls` reject)
- secure workbook open
- row pre-count + max-row checks
- strict `commonData` JSON (unknown fields reject + textual scalar coercion off)
- hide internal exception details from API responses
- external/user-facing errors in Korean
- use `th:text` for user content in Thymeleaf

## 4. Required Wiring Notes (Easy to Miss)

- Enable scheduling in your app (`@EnableScheduling`) if you copy `TempFileCleanupService` and want cleanup job execution.
- `ExcelApiExceptionHandler` is scoped to `TariffExemptionUploadApiController`. If you replace that controller, update advice scope accordingly.
- If you do not use Lombok in your target project, you must replace Lombok annotations with explicit Java code.

## 5. Properties to Carry

`excel.import.*`
- `max-file-size-mb`
- `max-rows`
- `pre-count-buffer`
- `retention-days`
- `temp-directory`
- `error-column-name`

Multipart limits:
- `spring.servlet.multipart.max-file-size`
- `spring.servlet.multipart.max-request-size`

## 6. Validation Checklist After Paste

1. `./gradlew test` passes.
2. Valid `.xlsx` upload succeeds.
3. Invalid rows return error report download URL.
4. `.xls` is rejected.
5. Oversized upload returns HTTP `413`.
6. Unknown `commonData` fields are rejected.
7. Unexpected exceptions do not leak internals.

## 7. Evidence (Repository)

- Dependency reality: `build.gradle`
- Strict `commonData` parsing: `ExcelUploadRequestService`
- Upload security gates: `ExcelUploadFileService`, `SecureExcelUtils`, `ExcelImportOrchestrator`
- Hidden internal errors in API responses: `ExcelApiExceptionHandler`
- Cleanup scheduling requirement: `TempFileCleanupService`, `ExcelUploadApplication`
- Exception handler scope coupling: `ExcelApiExceptionHandler`
