# AGENTS.md

## Purpose

Minimal operating rules for coding agents in this repository.

## Source Priority

When documents conflict, use this order:

1. Runtime behavior in code under `src/main/java`
2. `docs/SPEC-final.md` (explicitly "as implemented")
3. `README.md`
4. `CLAUDE.md` (advisory, not canonical)

## Verified Baseline

- Java `17`, Spring Boot `3.3.4`, Gradle `8+` (`build.gradle`)
- Main commands:
  - `./gradlew bootRun`
  - `./gradlew test`
  - `./gradlew build`

## Implementation Constraints (Evidence-backed)

- Keep upload security path aligned with existing flow in README:
  - filename sanitization
  - magic-byte validation
  - reject legacy `.xls` and accept `.xlsx` only
  - secure workbook opening
  - row pre-count / row-limit checks
- Hide internal exception details for unexpected/system failures in controller responses.
- Keep user-facing messages and logs in Korean.
- Use Thymeleaf escaping (`th:text`) for user content.
- Keep REST `commonData` contract strict: reject unknown JSON fields (`FAIL_ON_UNKNOWN_PROPERTIES`).
- Add new Excel templates under `com.foo.excel.templates...` using the existing pattern:
  - DTO (`@ExcelColumn` + validation)
  - `ExcelImportConfig`
  - `PersistenceHandler` (`saveAll(List<T> rows, List<Integer> sourceRowNumbers, UploadCommonData commonData)`)
  - optional `DatabaseUniquenessChecker`
  - `TemplateDefinition` bean wiring
  - include summary entity/repository where the template uses upload-level aggregate persistence

## Guardrails for Changes

- Avoid introducing rules that conflict with current implementation.
- Do not enforce style mandates not present in `README.md` or `docs/SPEC-final.md`.
- If a broad policy from `CLAUDE.md` is unsupported by code/docs, treat it as optional guidance.
- Keep documentation inventories (architecture/tests) synchronized with implemented classes/tests (e.g., summary repositories and contract tests).
- After edits, run relevant tests before finalizing.
