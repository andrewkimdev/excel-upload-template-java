# Evidence-Vetted Plan: Consolidate Filename Sanitization in `ExcelUploadFileService`

## Summary

Refactor the upload path so raw upload filenames are sanitized exactly once, at the upload boundary in `ExcelUploadFileService`.
`ExcelImportOrchestrator` should stop calling `SecureExcelUtils.sanitizeFilename(...)` directly and should instead use the sanitized filename returned from the file service.
Also remove duplicate extension validation so there is one source of truth for filename acceptance.

## Key Changes

1. Add a small return type to `ExcelUploadFileService`.
   - Introduce a nested record such as `StoredUpload(Path path, String sanitizedFilename)`.
   - Keep it local to the service unless another production class later needs it.

2. Change `storeAndValidateXlsx(...)` to return `StoredUpload` instead of `Path`.
   - This is safe because the only production caller is `ExcelImportOrchestrator`, plus unit tests.
   - The returned `sanitizedFilename` must be the exact value used to build the stored file path.

3. Remove the orchestrator-side sanitization block.
   - Delete the block in `ExcelImportOrchestrator` that reads `file.getOriginalFilename()`, sanitizes it, and catches `IllegalArgumentException`.
   - Replace it by calling `uploadFileService.storeAndValidateXlsx(...)` once and using:
     - `storedUpload.path()` for parsing and row counting
     - `storedUpload.sanitizedFilename()` for error-report metadata
     - `storedUpload.sanitizedFilename()` for `ImportResult.originalFilename`

4. Keep filename-rule ownership in one place.
   - Preferred implementation: make `SecureExcelUtils.sanitizeFilename(...)` the only filename-extension validator and remove the pre-check block in `ExcelUploadFileService`.
   - `ExcelUploadFileService` should then:
     - reject `null` filename with the existing Korean message
     - call `sanitizeFilename(...)`
     - save the file
     - run magic-byte validation

5. Move user-facing extension messages into the utility if it becomes the single validator.
   - `ExcelApiExceptionHandler` returns `IllegalArgumentException.getMessage()` directly, so `sanitizeFilename(...)` must emit Korean messages if it owns the validation.
   - Update `sanitizeFilename(...)` to throw:
     - the current Korean legacy `.xls` rejection message for `.xls`
     - the current Korean invalid-extension message for non-`.xlsx`
   - Do not leave English `Invalid file extension` or generic `extension` behavior in the utility if the service pre-checks are removed.

6. Keep the rest of the pipeline unchanged.
   - `ExcelErrorReportService.generateErrorReport(...)` already accepts the filename as data and writes it to `.meta`; no signature change is needed there.
   - Do not expand this refactor into `customId` sanitization. That is a separate path concern in `ExcelImportOrchestrator`.

## Test Plan

1. Update `ExcelUploadFileServiceTest`.
   - Change assertions to the new return type.
   - Verify `storedUpload.path().getFileName()` matches `storedUpload.sanitizedFilename()`.
   - Keep coverage for path traversal, null filename, wrong magic bytes, `.xls`, and unsupported extension.

2. Update `SecureExcelUtilsTest`.
   - Replace English `extension` assertions with explicit Korean-message assertions if the utility becomes the single extension gate.
   - Keep all existing normalization behavior checks unchanged.

3. Update orchestrator-level tests only if needed.
   - If there are tests asserting filename flow into `ImportResult.originalFilename` or error reports, make them consume the service-returned sanitized name.
   - Existing integration coverage around `.xls` rejection should continue to pass with the same visible API behavior.

4. Run the relevant tests.
   - Minimum:
     - `./gradlew test --tests "*ExcelUploadFileServiceTest"`
     - `./gradlew test --tests "*SecureExcelUtilsTest"`
     - `./gradlew test --tests "*ExcelImportIntegrationTest"`
   - Preferred final verification:
     - `./gradlew test`

## Assumptions

- The intended reduction is "one sanitization call in the upload pipeline," not a larger generic path-sanitization abstraction.
- `ExcelUploadFileService` is the correct public owner because it is the upload boundary and already stores the sanitized file.
- Returning both `Path` and `sanitizedFilename` is preferable to recomputing the name elsewhere.
- Preserving Korean user-visible errors is mandatory because the exception handler forwards `IllegalArgumentException` messages directly to API clients.
