# AGENTS.md

## Purpose

Minimal operating rules for coding agents in this repository.

## Source Priority

When documents conflict, use this order:

1. Runtime behavior in code under `src/main/java`
2. `docs/SPEC-final.md` (if present, and explicitly "as implemented")
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
- Keep REST `commonData` contract strict:
  - reject unknown JSON fields (`FAIL_ON_UNKNOWN_PROPERTIES`)
  - disable scalar coercion for textual fields (`ALLOW_COERCION_OF_SCALARS` off + textual coercion fail)
- Add new Excel templates under `com.foo.excel.templates...` using the existing pattern:
  - DTO (`@ExcelColumn` + validation)
  - `ExcelImportConfig`
  - template-specific `CommonData` DTO + bean validation
  - `PersistenceHandler<T, C extends CommonData>` (`saveAll(List<T> rows, List<Integer> sourceRowNumbers, C commonData)`)
  - optional `DatabaseUniquenessChecker`
  - `TemplateDefinition<T, C>` bean wiring (`commonDataClass` 포함)
  - include summary entity/repository where the template uses upload-level aggregate persistence

## Guardrails for Changes

- Avoid introducing rules that conflict with current implementation.
- This repository is designed to be cherry-picked into a larger project, not consumed as a stable standalone API.
- Backward compatibility is not a goal here; prefer simplifying refactors even when they introduce breaking changes.
- Proactively remove unused or dead code when identified.
- Do not enforce style mandates not present in `README.md` or `docs/SPEC-final.md`.
- If a broad policy from `CLAUDE.md` is unsupported by code/docs, treat it as optional guidance.
- Keep documentation inventories (architecture/tests) synchronized with implemented classes/tests (e.g., summary repositories and contract tests).
- After edits, run relevant tests before finalizing.
