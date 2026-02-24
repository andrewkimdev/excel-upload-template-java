# Style Guide

## Scope

- Keep behavior and external contracts unchanged when applying style-only revisions.
- Prefer small, mechanical commits for formatting/import/package-only changes.

## Java Imports

- Wildcard imports are not allowed (`*` and static `*` 모두 금지).
- Keep imports grouped and ordered deterministically (Spotless + Checkstyle 기준).
- Remove unused and duplicate imports.

## Formatting Baseline

- Java formatting is enforced by Spotless (`google-java-format`).
- Basic file hygiene is enforced by Checkstyle:
  - newline at end of file
  - tab character prohibition

## Test Package Layout

- Test package paths should mirror production architecture where practical.
- Current service test mapping:
  - `service/file` -> file service tests
  - `service/pipeline/parse` -> parser/column resolution tests
  - `service/pipeline/validation` -> validation tests
  - `service/pipeline/report` -> error report tests

## Documentation Sync

- Keep `README.md` architecture paths aligned with actual source tree.
- Keep `HOW_TO_UNDERSTAND_THE_UPLOAD_PIPELINE.md` route/contract descriptions aligned with controller/runtime behavior.
- Use `TemplateDefinition<T, C extends CommonData>` terminology and template-specific `commonDataClass`.

## Language Policy

- External/user-facing messages and externally returned errors must remain Korean.
- Internal technical logs/comments may remain mixed language based on existing codebase conventions.
