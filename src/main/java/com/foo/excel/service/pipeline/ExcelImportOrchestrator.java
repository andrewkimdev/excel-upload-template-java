package com.foo.excel.service.pipeline;

import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.service.contract.ExcelImportDefinition;
import com.foo.excel.service.contract.ExcelSheetSpec;
import com.foo.excel.service.contract.ImportMetadata;
import com.foo.excel.service.contract.MetadataConflict;
import com.foo.excel.service.contract.PersistenceHandler;
import com.foo.excel.service.file.ExcelUploadFileService;
import com.foo.excel.service.file.ExcelUploadFileService.StoredUpload;
import com.foo.excel.service.pipeline.parse.ColumnResolutionBatchException;
import com.foo.excel.service.pipeline.parse.ExcelParserService;
import com.foo.excel.service.pipeline.report.ExcelErrorReportService;
import com.foo.excel.service.pipeline.validation.ExcelValidationService;
import com.foo.excel.util.SecureExcelUtils;
import com.foo.excel.validation.ExcelValidationResult;
import com.foo.excel.validation.RowError;
import java.io.IOException;
import java.nio.file.Path;
import java.util.List;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Service
@RequiredArgsConstructor
public class ExcelImportOrchestrator {

  private final ExcelUploadFileService uploadFileService;
  private final ExcelParserService parserService;
  private final ExcelValidationService validationService;
  private final ExcelErrorReportService errorReportService;
  private final ExcelImportProperties properties;
  private final List<ExcelImportDefinition<?, ?>> importDefinitions;

  /**
   * 엑셀 업로드 처리 결과를 외부로 전달하기 위한 응답 모델이다.
   *
   * @param success 처리 성공 여부
   * @param rowsProcessed 처리한 행 수
   * @param rowsCreated 생성한 행 수
   * @param rowsUpdated 수정한 행 수
   * @param errorRows 오류가 발생한 행 수
   * @param errorCount 전체 오류 개수
   * @param errorFileId 오류 파일 식별자
   * @param downloadUrl 오류 파일 다운로드 URL
   * @param originalFilename 원본 파일명
   * @param metadataConflict 메타데이터 충돌 정보
   * @param message 사용자 메시지
   */
  @Builder
  public record ImportResult(
      boolean success,
      int rowsProcessed,
      int rowsCreated,
      int rowsUpdated,
      int errorRows,
      int errorCount,
      String errorFileId,
      String downloadUrl,
      String originalFilename,
      MetadataConflict metadataConflict,
      String message) {}

  public ImportResult processImport(MultipartFile file, String importType, ImportMetadata metadata)
      throws IOException {
    ExcelImportDefinition<?, ?> importDefinition = findDefinition(importType);
    return doProcess(importDefinition, file, metadata);
  }

  public Class<? extends ImportMetadata> getMetadataClass(String importType) {
    return findDefinition(importType).getMetadataClass();
  }

  private ExcelImportDefinition<?, ?> findDefinition(String importType) {
    return importDefinitions.stream()
        .filter(t -> t.getImportType().equals(importType))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("알 수 없는 import 타입입니다: " + importType));
  }

  @SuppressWarnings("unchecked")
  private <T, M extends ImportMetadata> ImportResult doProcess(
      ExcelImportDefinition<?, ?> rawDefinition, MultipartFile file, ImportMetadata metadata)
      throws IOException {
    long requestStartedAt = System.nanoTime();
    ExcelImportDefinition<T, M> importDefinition = (ExcelImportDefinition<T, M>) rawDefinition;
    if (!importDefinition.getMetadataClass().isInstance(metadata)) {
      throw new IllegalArgumentException("metadata 형식이 import와 일치하지 않습니다.");
    }
    M typedMetadata = importDefinition.getMetadataClass().cast(metadata);

    ExcelSheetSpec sheetSpec = importDefinition.getSheetSpec();

    // 1. import별 임시 하위 디렉터리 키 결정
    String tempSubdirectory = resolveTempSubdirectory(importDefinition, typedMetadata);

    try {
      // 2. xlsx 파일 저장 및 검증
      long fileStageStartedAt = System.nanoTime();
      StoredUpload storedUpload = uploadFileService.storeAndValidateXlsx(file, tempSubdirectory);
      Path xlsxFile = storedUpload.path();
      typedMetadata.assignFilePath(xlsxFile.toString());
      String sanitizedFilename = storedUpload.sanitizedFilename();
      long fileStageElapsedMs = elapsedMillis(fileStageStartedAt);
      int maxErrorRows = resolveMaxErrorRows();

      // 2a. import-level 선행 차단 조건은 파싱 전에 확인한다.
      var importPrecheckFailure = importDefinition.runImportPrecheck(typedMetadata);
      if (importPrecheckFailure.isPresent()) {
        var failure = importPrecheckFailure.orElseThrow();
        log.info(
            "Import stage timing [importType={}, file={}, fileMs={}, totalMs={}, reportMode=skipped]: import-level blocked",
            importDefinition.getImportType(),
            sanitizedFilename,
            fileStageElapsedMs,
            elapsedMillis(requestStartedAt));
        return ImportResult.builder()
            .success(false)
            .rowsProcessed(0)
            .errorRows(0)
            .errorCount(0)
            .metadataConflict(failure.metadataConflict())
            .message(failure.message())
            .build();
      }

      // 2b. 빠른 행 수 사전 점검(경량 SAX, 대용량 파일의 전체 파싱 회피)
      long preCountStageStartedAt = System.nanoTime();
      // ExcelSheetSpec already stores the resolver-converted 0-based sheet index.
      int roughRowCount = SecureExcelUtils.countRows(xlsxFile, sheetSpec.resolvedSheetIndex());
      int preCountThreshold =
          properties.getMaxRows()
              + (sheetSpec.dataStartRow() - 1)
              + properties.getPreCountBuffer();
      long preCountStageElapsedMs = elapsedMillis(preCountStageStartedAt);
      if (roughRowCount > preCountThreshold) {
        log.info(
            "Import stage timing [importType={}, file={}, fileMs={}, preCountMs={}, totalMs={}]: pre-count rejected, roughRows={}, threshold={}",
            importDefinition.getImportType(),
            sanitizedFilename,
            fileStageElapsedMs,
            preCountStageElapsedMs,
            elapsedMillis(requestStartedAt),
            roughRowCount,
            preCountThreshold);
        return ImportResult.builder()
            .success(false)
            .rowsProcessed(roughRowCount)
            .message(
                "최대 행 수("
                    + properties.getMaxRows()
                    + ")를 초과했습니다. "
                    + "파일에 약 "
                    + roughRowCount
                    + "행이 포함되어 있습니다")
            .build();
      }

      // 3. 파싱
      long parseStageStartedAt = System.nanoTime();
      ExcelParserService.ParseResult<T> parseResult =
          parserService.parse(
              xlsxFile,
              importDefinition.getRowClass(),
              sheetSpec,
              properties.getMaxRows(),
              maxErrorRows);
      long parseStageElapsedMs = elapsedMillis(parseStageStartedAt);

      // 4. 최대 행 수 확인
      if (parseResult.rows().size() > properties.getMaxRows()) {
        log.info(
            "Import stage timing [importType={}, file={}, fileMs={}, preCountMs={}, parseMs={}, totalMs={}]: row limit exceeded after parse, rowsProcessed={}, maxRows={}",
            importDefinition.getImportType(),
            sanitizedFilename,
            fileStageElapsedMs,
            preCountStageElapsedMs,
            parseStageElapsedMs,
            elapsedMillis(requestStartedAt),
            parseResult.rows().size(),
            properties.getMaxRows());
        return ImportResult.builder()
            .success(false)
            .rowsProcessed(parseResult.rows().size())
            .message(
                "최대 행 수("
                    + properties.getMaxRows()
                    + ")를 초과했습니다. 현재: "
                    + parseResult.rows().size()
                    + "행")
            .build();
      }

      // 5. 파싱 오류가 있으면 이후 고비용 검증/DB 조회를 생략하고 즉시 실패 처리
      if (!parseResult.parseErrors().isEmpty()) {
        ExcelValidationResult validationResult =
            capRowErrors(parseResult.rows().size(), parseResult.parseErrors(), maxErrorRows);
        return buildFailureResult(
            importDefinition,
            sanitizedFilename,
            xlsxFile,
            sheetSpec,
            parseResult,
            validationResult,
            fileStageElapsedMs,
            preCountStageElapsedMs,
            parseStageElapsedMs,
            0,
            0,
            requestStartedAt,
            "parse failed; skipped validation and db uniqueness");
      }

      // 6. 검증(JSR-380 + 파일 내 유일성)
      long validationStageStartedAt = System.nanoTime();
      ExcelValidationResult validationResult =
          validationService.validate(
              parseResult.rows(),
              importDefinition.getRowClass(),
              parseResult.sourceRowNumbers(),
              remainingErrorRowBudget(maxErrorRows, 0));
      long validationStageElapsedMs = elapsedMillis(validationStageStartedAt);

      // 7. 이미 유효하지 않으면 DB 유일성 검사를 생략한다.
      if (!validationResult.isValid()) {
        return buildFailureResult(
            importDefinition,
            sanitizedFilename,
            xlsxFile,
            sheetSpec,
            parseResult,
            validationResult,
            fileStageElapsedMs,
            preCountStageElapsedMs,
            parseStageElapsedMs,
            validationStageElapsedMs,
            0,
            requestStartedAt,
            failureOutcome("validation failed; skipped db uniqueness", validationResult));
      }

      // 8. DB 유일성 검사
      long dbUniquenessStageStartedAt = System.nanoTime();
      List<RowError> dbErrors =
          importDefinition.checkDbUniqueness(
              parseResult.rows(), parseResult.sourceRowNumbers(), typedMetadata);
      long dbUniquenessStageElapsedMs = elapsedMillis(dbUniquenessStageStartedAt);
      if (!dbErrors.isEmpty()) {
        List<RowError> cappedDbErrors =
            capAdditionalErrors(
                dbErrors, remainingErrorRowBudget(maxErrorRows, validationResult.getErrorRowCount()));
        validationResult.merge(cappedDbErrors);
        if (cappedDbErrors.size() < dbErrors.size()) {
          validationResult.markTruncated(ExcelValidationResult.DEFAULT_TRUNCATION_MESSAGE);
        }
      }

      if (validationResult.isValid()) {
        // 9. 저장
        long saveStageStartedAt = System.nanoTime();
        PersistenceHandler.SaveResult saveResult =
            importDefinition.getPersistenceHandler()
                .saveAll(parseResult.rows(), parseResult.sourceRowNumbers(), typedMetadata);
        long saveStageElapsedMs = elapsedMillis(saveStageStartedAt);

        log.info(
            "Import stage timing [importType={}, file={}, rowsProcessed={}, fileMs={}, preCountMs={}, parseMs={}, validationMs={}, dbUniquenessMs={}, saveMs={}, totalMs={}]: success",
            importDefinition.getImportType(),
            sanitizedFilename,
            parseResult.rows().size(),
            fileStageElapsedMs,
            preCountStageElapsedMs,
            parseStageElapsedMs,
            validationStageElapsedMs,
            dbUniquenessStageElapsedMs,
            saveStageElapsedMs,
            elapsedMillis(requestStartedAt));

        return ImportResult.builder()
            .success(true)
            .rowsProcessed(parseResult.rows().size())
            .rowsCreated(saveResult.created())
            .rowsUpdated(saveResult.updated())
            .message("데이터 업로드 완료")
            .build();
      } else {
        return buildFailureResult(
            importDefinition,
            sanitizedFilename,
            xlsxFile,
            sheetSpec,
            parseResult,
            validationResult,
            fileStageElapsedMs,
            preCountStageElapsedMs,
            parseStageElapsedMs,
            validationStageElapsedMs,
            dbUniquenessStageElapsedMs,
            requestStartedAt,
            failureOutcome("validation failed after db uniqueness", validationResult));
      }
    } catch (ColumnResolutionBatchException e) {
      log.warn("컬럼 해석에 실패했습니다: {}", e.getMessage());
      return ImportResult.builder().success(false).message(e.toKoreanMessage()).build();
    } catch (Exception e) {
      log.error("import 처리 중 오류가 발생했습니다", e);
      throw e;
    }
  }

  private <M extends ImportMetadata> String resolveTempSubdirectory(
      ExcelImportDefinition<?, M> importDefinition, M metadata) {
    String tempSubdirectory = importDefinition.resolveTempSubdirectory(metadata);
    return tempSubdirectory == null ? null : sanitizeTempSubdirectory(tempSubdirectory);
  }

  private String sanitizeTempSubdirectory(String tempSubdirectory) {
    if (tempSubdirectory == null || tempSubdirectory.isBlank()) {
      throw new IllegalArgumentException("customId는 필수입니다.");
    }

    String sanitized =
        tempSubdirectory
            .trim()
            .replaceAll("[\\x00-\\x1F\\x7F]", "")
            .replaceAll(
                "[^a-zA-Z0-9.\\-_\\s\\uAC00-\\uD7AF\\u1100-\\u11FF\\u3130-\\u318F]",
                "_")
            .replaceAll("\\.{2,}", ".")
            .replaceAll("^[.\\s]+|[.\\s]+$", "");

    if (sanitized.isBlank()) {
      throw new IllegalArgumentException("customId 형식이 올바르지 않습니다.");
    }
    return sanitized;
  }

  private long elapsedMillis(long startedAtNanos) {
    return (System.nanoTime() - startedAtNanos) / 1_000_000;
  }

  private int resolveMaxErrorRows() {
    int configured = properties.getErrorRowLimit();
    return configured > 0 ? configured : Integer.MAX_VALUE;
  }

  private int remainingErrorRowBudget(int maxErrorRows, int currentErrorRows) {
    if (maxErrorRows == Integer.MAX_VALUE) {
      return Integer.MAX_VALUE;
    }
    return Math.max(0, maxErrorRows - currentErrorRows);
  }

  private ExcelValidationResult capRowErrors(
      int totalRows, List<RowError> rowErrors, int maxErrorRows) {
    if (rowErrors.isEmpty()) {
      return ExcelValidationResult.success(totalRows);
    }
    if (maxErrorRows != Integer.MAX_VALUE && rowErrors.size() >= maxErrorRows) {
      return ExcelValidationResult.truncatedFailure(
          totalRows, rowErrors.subList(0, Math.min(rowErrors.size(), maxErrorRows)));
    }
    if (maxErrorRows == Integer.MAX_VALUE || rowErrors.size() <= maxErrorRows) {
      return ExcelValidationResult.failure(totalRows, rowErrors);
    }
    return ExcelValidationResult.truncatedFailure(totalRows, rowErrors.subList(0, maxErrorRows));
  }

  private List<RowError> capAdditionalErrors(List<RowError> rowErrors, int budget) {
    if (budget == Integer.MAX_VALUE || rowErrors.size() <= budget) {
      return rowErrors;
    }
    return rowErrors.subList(0, budget);
  }

  private String failureOutcome(String baseOutcome, ExcelValidationResult validationResult) {
    return validationResult.isTruncated() ? baseOutcome + "; errors truncated" : baseOutcome;
  }

  private <T> ImportResult buildFailureResult(
      ExcelImportDefinition<T, ?> importDefinition,
      String sanitizedFilename,
      Path xlsxFile,
      ExcelSheetSpec sheetSpec,
      ExcelParserService.ParseResult<T> parseResult,
      ExcelValidationResult validationResult,
      long fileStageElapsedMs,
      long preCountStageElapsedMs,
      long parseStageElapsedMs,
      long validationStageElapsedMs,
      long dbUniquenessStageElapsedMs,
      long requestStartedAt,
      String outcome)
      throws IOException {
    long errorReportStageStartedAt = System.nanoTime();
    Path errorFile =
        errorReportService.generateErrorReport(
            xlsxFile,
            validationResult,
            parseResult.columnMappings(),
            sheetSpec,
            sanitizedFilename,
            importDefinition.getMergeRegions());
    long errorReportStageElapsedMs = elapsedMillis(errorReportStageStartedAt);

    String errorFileId = errorFile.getFileName().toString().replace(".xlsx", "");
    String message =
        validationResult.getErrorRowCount()
            + "개 행에서 "
            + validationResult.getTotalErrorCount()
            + "개 오류가 발견되었습니다";
    if (validationResult.isTruncated() && validationResult.getTruncationMessage() != null) {
      message += " " + validationResult.getTruncationMessage();
    }

    log.info(
        "Import stage timing [importType={}, file={}, rowsProcessed={}, errorRows={}, errorCount={}, fileMs={}, preCountMs={}, parseMs={}, validationMs={}, dbUniquenessMs={}, errorReportMs={}, totalMs={}, reportMode={}]: {}",
        importDefinition.getImportType(),
        sanitizedFilename,
        validationResult.getTotalRows(),
        validationResult.getErrorRowCount(),
        validationResult.getTotalErrorCount(),
        fileStageElapsedMs,
        preCountStageElapsedMs,
        parseStageElapsedMs,
        validationStageElapsedMs,
        dbUniquenessStageElapsedMs,
        errorReportStageElapsedMs,
        elapsedMillis(requestStartedAt),
        validationResult.isTruncated() ? "compact" : "full",
        outcome);

    return ImportResult.builder()
        .success(false)
        .rowsProcessed(validationResult.getTotalRows())
        .errorRows(validationResult.getErrorRowCount())
        .errorCount(validationResult.getTotalErrorCount())
        .errorFileId(errorFileId)
        .downloadUrl("/api/excel/download/" + errorFileId)
        .originalFilename(sanitizedFilename)
        .message(message)
        .build();
  }
}
