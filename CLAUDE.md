# CLAUDE.md — Advisory Engineering Notes

## Positioning

This file is advisory. If it conflicts with runtime behavior or `README.md`, follow runtime code first.

## Project Snapshot

- Stack: Java 17, Spring Boot 3.3.4, Gradle 8+, Apache POI 5.4.1, H2 (prototype), Thymeleaf
- Main commands:
  - `./gradlew bootRun`
  - `./gradlew test`
  - `./gradlew build`

## Runtime Contracts To Preserve

### Upload contract (`POST /api/excel/upload/aappcar`)

- Multipart parts:
  - `file` (`.xlsx` only; `.xls` rejected)
  - `commonData` (`application/json`)
- Current runtime wiring uses explicit template routes (not a generic `{templateType}` catch-all).
- `commonData` is template-specific via `TemplateDefinition<T, C extends CommonData>.commonDataClass`
- For current tariff template, required fields are:
  - `comeYear`, `comeOrder`, `uploadSeq`, `equipCode`
  - `customId`는 런타임 계약(`CommonData#getCustomId`)상 필수이며, 현재 컨트롤러에서 `CUSTOM01`로 주입됨
- Strict parsing is enabled:
  - `FAIL_ON_UNKNOWN_PROPERTIES`
  - scalar coercion disabled for textual fields
- Internal/system exception details are not exposed to API clients.

### Security path

- Keep upload checks in the existing order:
  - filename sanitization
  - magic-byte validation
  - OOXML `.xlsx` enforcement
  - secure workbook opening
  - pre-count row limit + parser row limit

### Web behavior

- Keep user-facing messages and externally returned errors in Korean.
- Internal technical logs may remain English where currently implemented.
- Keep Thymeleaf escaping (`th:text`) for user content.

## Current Architectural Shape

- Core orchestration:
  - Upload: `AAppcarItemUploadApiController` -> `ExcelUploadRequestService` -> `ExcelImportOrchestrator`
  - Download: `ExcelFileController` serves `/api/excel/download/{fileId}`
- Template wiring:
  - `TemplateDefinition<T, C extends CommonData>`
  - `PersistenceHandler<T, C extends CommonData>`
  - optional `DatabaseUniquenessChecker<T, C extends CommonData>`
- Current sample template:
  - `templates/samples/aappcar/*`
  - `AAppcarItemTemplateConfig` wires `AAppcarItemDbUniquenessChecker` bean.

## Guidance For New Template Work

Create a subpackage under `com.foo.excel.templates...` with:

1. `*Dto` (`@ExcelColumn` + JSR-380 validation)
2. `*ImportConfig` (`ExcelImportConfig`)
3. `*CommonData` (implements `CommonData`, validated by Bean Validation)
4. Persistence entity/repository classes as needed
5. `*Service` implementing `PersistenceHandler<Dto, CommonDataType>`
6. Optional `*DbUniquenessChecker` implementing `DatabaseUniquenessChecker<Dto, CommonDataType>`
7. `*TemplateConfig` producing `TemplateDefinition<Dto, CommonDataType>` with `commonDataClass`

## Testing Guidance

- Prefer focused unit/component tests for parser, validation, and security utilities.
- Use integration tests (`MockMvc`) for upload/download contract behavior.
- After edits, run relevant tests; for broad changes run `./gradlew test`.
