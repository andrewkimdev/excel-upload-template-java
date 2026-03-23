# Excel Import Rename Plan

## Constraints

- Feature-level terminology uses `Import`.
- Upload terminology remains only for multipart ingress and temp-file storage.
- DB-mapped entity attributes must not change.
- Breaking renames are preferred over compatibility aliases.

## Rename Matrix

| Old | New |
|---|---|
| `ExcelUploadApplication` | `ExcelImportApplication` |
| `ExcelUploadRequestService` | `ExcelImportRequestService` |
| `ExcelDefinition` | `ExcelImportDefinition` |
| `templateType` | `importType` |
| `TemplateTypes` | `ImportTypeNames` |
| `UploadPrecheck` | `ImportPrecheck` |
| `UploadPrecheckFailure` | `ImportPrecheckFailure` |
| `AAppcarItemTemplateConfig` | `AAppcarItemImportConfig` |
| `AAppcarItemExcelDefinition` | `AAppcarItemImportDefinition` |
| `AAppcarItemUploadApiController` | `AAppcarItemImportApiController` |
| `AAppcarItemUploadPageController` | `AAppcarItemImportPageController` |
| `UploadIndexController` | `ImportIndexController` |
| `AAppcarItemUploadPrecheck` | `AAppcarItemImportPrecheck` |

## Preserved Names

- HTTP upload routes may remain unchanged.
- `ExcelUploadFileService` remains upload-scoped.
- Business and persistence names such as `uploadSeq` stay unchanged where they are part of the domain or DB mapping.

## Acceptance Criteria

- Import-facing code, docs, and tests use the new names consistently.
- No compatibility wrappers or duplicate old/new types remain.
- Persistence entities and their DB-mapped attributes are unchanged.
- The test suite passes.
