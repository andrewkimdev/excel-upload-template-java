# Refactor Plan: Thin, Constant-Driven Controllers (REST + Thymeleaf Split)

## Summary
Refactor `ExcelFileController` into:
1. Template-specific thin controllers with fixed template constants.
2. A shared upload application service handling parsing/validation/orchestration.
3. A centralized REST exception handler for Korean-safe error responses.
4. Template-specific Thymeleaf form handlers/builders (split now).

Decisions locked:
- API route strategy: `Switch immediately` (remove generic `{templateType}` upload route).
- Thymeleaf strategy: `Split now`.
- Template identifier style: `String constants`.
- Old `/api/excel/upload/{templateType}` is removed entirely (no fallback/deprecation).
- Old `/api/excel/template/{templateType}` is removed entirely.
- Multipart oversize must return exact `413`.
- No migration/deprecation window is required (demo/cherrypick context).

## Confirmed Risks and Handling

1. Breaking route removal is intentional:
   - `POST /api/excel/upload/{templateType}` and `GET /api/excel/template/{templateType}` are intentionally removed.
   - Required handling: explicit README/API breaking-change documentation + 404 contract tests.
2. Oversize behavior is now strict contract:
   - Existing test currently accepts generic 4xx.
   - Required handling: enforce and test exact `413` with Korean-safe message.
3. Route constants in tests can hide contract drift:
   - If all tests use shared constants only, external route regressions may be missed.
   - Required handling: keep at least one black-box test using literal public URL.

## Target Design

### 1. Template constants
- Add `com.foo.excel.templates.TemplateTypes`:
  - `public static final String TARIFF_EXEMPTION = "tariff-exemption";`
- Replace raw literals in controller/config/test code with constants.
- Hard requirement: `TemplateDefinition` wiring must use the same constant (not string literal) to prevent route/wiring drift.
- Preserve at least one literal-route contract test for public endpoint stability.

### 2. REST: template-specific thin controllers
- Create `TariffExemptionUploadApiController`:
  - `POST /api/excel/upload/tariff-exemption`
  - Delegates directly to shared upload service with fixed constant.
- Remove generic REST upload mapping `/api/excel/upload/{templateType}`.
- Remove placeholder template download mapping `/api/excel/template/{templateType}`.
- Ensure removed routes intentionally return framework `404`.

### 3. Thymeleaf: split by template
- Replace centralized `/upload` handler logic with template-specific controller:
  - `TariffExemptionUploadPageController`:
    - `GET /upload/tariff-exemption` renders template-specific form page.
    - `POST /upload/tariff-exemption` delegates to shared form upload service.
- Keep root page (`GET /`) as a selector/index that links to template pages.
- Move template-specific `commonData` creation from controller into template-specific mapper/builder component.

### 4. Shared upload service layer
Create service (e.g., `ExcelUploadRequestService`) to own shared non-HTTP logic:
- File size check.
- Strict `commonData` JSON parsing and validation.
- Orchestrator call: `processUpload(file, templateType, commonData)`.
- Upload result mapping for API and view models.

Create companion form service/mapper:
- Template-specific form DTO -> template `CommonData` conversion.
- Bean validation.
- No template `if/else` in controller.

### 5. Error handling
- Add `@RestControllerAdvice` for REST response mapping:
  - `IllegalArgumentException` -> 400 with Korean message.
  - `SecurityException` -> 400 fixed Korean message.
  - `MaxUploadSizeExceededException` -> 413 with Korean-safe message.
  - Spring multipart parsing failures -> 400 with Korean-safe message.
  - unexpected `Exception` -> 500 fixed Korean message.
- Thymeleaf controllers handle exceptions by adding `ImportResult` to model with safe Korean messages.
- Hard requirement: preserve existing no-disclosure posture (do not expose exception detail strings in 500/system failures).

### 6. Keep core contracts stable
- Do not change:
  - `ExcelImportOrchestrator.processUpload(MultipartFile, String, CommonData)`
  - `TemplateDefinition<T, C extends CommonData>`

## Public API / Route Changes
- REST upload:
  - Removed: `/api/excel/upload/{templateType}`
  - Added/finalized: `/api/excel/upload/tariff-exemption`
- REST template download:
  - Removed: `/api/excel/template/{templateType}`
- Thymeleaf:
  - Removed: `/upload` post flow
  - Added: `/upload/tariff-exemption` (GET page + POST submit)
  - Root `/` remains and links to template-specific page(s)
- Unchanged:
  - `/api/excel/download/{fileId}` remains centralized

## File-Level Implementation Plan
1. Add `TemplateTypes`.
2. Add shared upload request service.
3. Add REST exception advice.
4. Add `TariffExemptionUploadApiController`.
5. Add Thymeleaf template-specific controller + form mapper/builder for tariff.
6. Split HTML templates:
   - Keep index/selector page.
   - Add tariff-specific upload page template.
   - Keep shared result page.
7. Trim/delete upload/template responsibilities from `ExcelFileController` (retain shared download endpoint).
8. Update config/wiring (`TariffExemptionTemplateConfig`) and tests to constants/routes.
9. Update README/API docs in same change set.

## Testing Plan
- Update integration tests to call:
  - REST: `/api/excel/upload/tariff-exemption`
  - Thymeleaf form: `/upload/tariff-exemption`
- Add/adjust tests for:
  - Removed generic REST route returns 404 (`/api/excel/upload/{templateType}` no longer mapped).
  - Removed template download route returns 404 (`/api/excel/template/{templateType}` no longer mapped).
  - Unknown route behavior is framework 404 after generic route removal.
  - Template-specific page render.
  - Form submission success/failure model rendering.
  - Strict `commonData` validation still enforced in REST.
  - Multipart oversize behavior mapped to exact 413 with Korean-safe error response.
  - Multipart parsing failure mapped to 400 with Korean-safe error response.
  - `download` endpoint behavior unchanged (`/api/excel/download/{fileId}` UUID guard and existing content type).
  - Korean message and no-internal-detail checks for unexpected/system failure path.
  - At least one black-box literal-route test to detect public API drift.
- Run `./gradlew test`.

## Acceptance Criteria
- Controllers are thin delegates with fixed template constants.
- No template branching logic (`if templateType`) remains in controllers.
- REST and Thymeleaf flows are both split by template.
- Old generic upload and template-download endpoints are fully removed and return 404.
- Strict `commonData` rules and upload security behaviors are unchanged.
- Oversize multipart uploads return exact 413.
- Korean user-facing/log messages remain.
- Unexpected failures still hide internal details.
- README/API docs are updated in the same change set to reflect final route contract.

## Assumptions and Defaults
- Start with tariff template split; pattern scales by adding one API controller + one page controller + one mapper per template.
- Immediate hard-switch is intentional; no migration/deprecation compatibility is provided.
- Broad `IllegalArgumentException` REST advice scope is acceptable for this demo repository and intended cherrypick workflow.
- Shared download endpoint remains centralized unless explicitly template-specific later.
