# Error Report Link Investigation

The first thing to verify in the other project is which response path you are actually using.

In this repo, the Thymeleaf result page only shows the link when `result.errorFileId` is present in the model at [result.html](/workspaces/excel-upload-template-java/src/main/resources/templates/result.html#L27) and specifically [result.html](/workspaces/excel-upload-template-java/src/main/resources/templates/result.html#L56). The backend sets that field only in the validation-failure path inside [ExcelImportOrchestrator.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/service/pipeline/ExcelImportOrchestrator.java#L380), where it builds `ImportResult` with both `errorFileId` and `downloadUrl` at [ExcelImportOrchestrator.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/service/pipeline/ExcelImportOrchestrator.java#L419).

## What To Diff In The Other Project

1. Compare the `ImportResult` shape in the copied orchestrator.
   Check that it still has both `errorFileId` and `downloadUrl` like [ExcelImportOrchestrator.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/service/pipeline/ExcelImportOrchestrator.java#L40).

2. Compare the validation-failure builder path.
   The copied code must still call `errorReportService.generateErrorReport(...)` and then set both fields. If that block was simplified, the page will show counts/messages but no link.

3. Compare the controller/view contract.
   The page flow passes the raw `ImportResult` to the model in [AAppcarItemImportPageController.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/controller/AAppcarItemImportPageController.java#L63). The API flow strips that down to a `Map` and only exposes `downloadUrl`, not `errorFileId`, in [ExcelImportRequestService.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/service/pipeline/ExcelImportRequestService.java#L47).
   If your other project switched from server-side page rendering to API JSON + custom JS, and the HTML still checks `errorFileId`, that is enough to explain the missing link.

4. Compare the download endpoint.
   The link target must still exist at [ExcelFileController.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/controller/ExcelFileController.java#L31). If this controller was not copied or the route changed, the backend may avoid surfacing the link or the link may be dead.

5. Check logs for the error-report generation stage.
   If `generateErrorReport` fails, the page controller falls into generic exception handling and renders a plain failure without `errorFileId` at [AAppcarItemImportPageController.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/controller/AAppcarItemImportPageController.java#L78). Search the other project logs for:
   - `Error report generated:`
   - `Compact error report generated:`
   - `업로드 처리 실패`
   - `Upload stage timing`

## Important Clue

In this repo, `goodsSeqNo` is not annotated for within-file uniqueness in [AAppcarItemDto.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/templates/samples/aappcar/dto/AAppcarItemDto.java#L24). So a duplicate `goodsSeqNo` by itself should not be the canonical trigger here. If your copied project reports errors for that exact case, then the copied code is already behaviorally different from this baseline. That makes a straight diff against these files even more useful.

## Fastest Investigation In The Other Project

- `rg -n "errorFileId|downloadUrl|generateErrorReport|/api/excel/download|업로드 처리 실패|Error report generated"`
- Diff the copied orchestrator, page controller, API controller, result template, and download controller against:
  - [ExcelImportOrchestrator.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/service/pipeline/ExcelImportOrchestrator.java)
  - [ExcelImportRequestService.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/service/pipeline/ExcelImportRequestService.java)
  - [AAppcarItemImportPageController.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/controller/AAppcarItemImportPageController.java)
  - [AAppcarItemImportApiController.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/controller/AAppcarItemImportApiController.java)
  - [ExcelFileController.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/controller/ExcelFileController.java)
  - [result.html](/workspaces/excel-upload-template-java/src/main/resources/templates/result.html)

If needed later, this can be turned into a copy/paste audit checklist for the other repo.
