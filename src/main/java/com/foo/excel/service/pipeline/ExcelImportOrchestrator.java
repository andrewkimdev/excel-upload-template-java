package com.foo.excel.service.pipeline;

import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.service.contract.CommonData;
import com.foo.excel.service.contract.PersistenceHandler;
import com.foo.excel.service.contract.TemplateDefinition;
import com.foo.excel.service.file.ExcelUploadFileService;
import com.foo.excel.service.pipeline.parse.ColumnResolutionBatchException;
import com.foo.excel.service.pipeline.parse.ExcelParserService;
import com.foo.excel.service.pipeline.report.ExcelErrorReportService;
import com.foo.excel.service.pipeline.validation.ExcelValidationService;
import com.foo.excel.util.SecureExcelUtils;
import com.foo.excel.validation.ExcelValidationResult;
import com.foo.excel.validation.RowError;
import java.io.IOException;
import java.nio.file.Files;
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
  private final List<TemplateDefinition<?, ?>> templateDefinitions;

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
      String message) {}

  public ImportResult processUpload(MultipartFile file, String templateType, CommonData commonData)
      throws IOException {
    TemplateDefinition<?, ?> template = findTemplate(templateType);
    return doProcess(template, file, commonData);
  }

  public Class<? extends CommonData> getCommonDataClass(String templateType) {
    return findTemplate(templateType).getCommonDataClass();
  }

  private TemplateDefinition<?, ?> findTemplate(String templateType) {
    return templateDefinitions.stream()
        .filter(t -> t.getTemplateType().equals(templateType))
        .findFirst()
        .orElseThrow(() -> new IllegalArgumentException("알 수 없는 템플릿 타입입니다: " + templateType));
  }

  @SuppressWarnings("unchecked")
  private <T, C extends CommonData> ImportResult doProcess(
      TemplateDefinition<?, ?> rawTemplate, MultipartFile file, CommonData commonData)
      throws IOException {
    TemplateDefinition<T, C> template = (TemplateDefinition<T, C>) rawTemplate;
    if (!template.getCommonDataClass().isInstance(commonData)) {
      throw new IllegalArgumentException("commonData 형식이 템플릿과 일치하지 않습니다.");
    }
    C typedCommonData = template.getCommonDataClass().cast(commonData);

    ExcelImportConfig config = template.getConfig();

    // 오류 리포트용 원본 파일명을 추출하고 정규화
    String sanitizedFilename = null;
    try {
      String originalName = file.getOriginalFilename();
      if (originalName != null && !originalName.isBlank()) {
        sanitizedFilename = SecureExcelUtils.sanitizeFilename(originalName);
      }
    } catch (IllegalArgumentException e) {
      log.warn("원본 파일명 정규화에 실패했습니다: {}", e.getMessage());
    }

    // 1. customId 기반 임시 하위 디렉터리 생성
    String customIdPath = resolveCustomIdPath(typedCommonData);
    Path tempSubDir = properties.getTempDirectoryPath().resolve(customIdPath);
    Files.createDirectories(tempSubDir);

    try {
      // 2. xlsx 파일 저장 및 검증
      Path xlsxFile = uploadFileService.storeAndValidateXlsx(file, tempSubDir);

      // 2b. 빠른 행 수 사전 점검(경량 SAX, 대용량 파일의 전체 파싱 회피)
      int roughRowCount = SecureExcelUtils.countRows(xlsxFile, config.getSheetIndex());
      int preCountThreshold =
          properties.getMaxRows() + (config.getDataStartRow() - 1) + properties.getPreCountBuffer();
      if (roughRowCount > preCountThreshold) {
        log.info(
            "Pre-count rejected file: roughly {} rows, threshold={}",
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
      ExcelParserService.ParseResult<T> parseResult =
          parserService.parse(xlsxFile, template.getDtoClass(), config, properties.getMaxRows());

      // 4. 최대 행 수 확인
      if (parseResult.rows().size() > properties.getMaxRows()) {
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

      // 5. 검증(JSR-380 + 파일 내 유일성)
      ExcelValidationResult validationResult =
          validationService.validate(
              parseResult.rows(), template.getDtoClass(), parseResult.sourceRowNumbers());

      // 6. DB 유일성 검사
      List<RowError> dbErrors =
          template.checkDbUniqueness(
              parseResult.rows(), parseResult.sourceRowNumbers(), typedCommonData);
      validationResult.merge(dbErrors);

      // 7. 파싱 오류 병합
      validationResult.merge(parseResult.parseErrors());

      if (validationResult.isValid()) {
        // 8. 저장
        PersistenceHandler.SaveResult saveResult =
            template
                .getPersistenceHandler()
                .saveAll(parseResult.rows(), parseResult.sourceRowNumbers(), typedCommonData);

        return ImportResult.builder()
            .success(true)
            .rowsProcessed(parseResult.rows().size())
            .rowsCreated(saveResult.created())
            .rowsUpdated(saveResult.updated())
            .message("데이터 업로드 완료")
            .build();
      } else {
        // 9. 오류 리포트 생성
        Path errorFile =
            errorReportService.generateErrorReport(
                xlsxFile,
                validationResult,
                parseResult.columnMappings(),
                config,
                sanitizedFilename);

        String errorFileId = errorFile.getFileName().toString().replace(".xlsx", "");

        return ImportResult.builder()
            .success(false)
            .rowsProcessed(validationResult.getTotalRows())
            .errorRows(validationResult.getErrorRowCount())
            .errorCount(validationResult.getTotalErrorCount())
            .errorFileId(errorFileId)
            .downloadUrl("/api/excel/download/" + errorFileId)
            .originalFilename(sanitizedFilename)
            .message(
                validationResult.getErrorRowCount()
                    + "개 행에서 "
                    + validationResult.getTotalErrorCount()
                    + "개 오류가 발견되었습니다")
            .build();
      }
    } catch (ColumnResolutionBatchException e) {
      log.warn("컬럼 해석에 실패했습니다: {}", e.getMessage());
      return ImportResult.builder().success(false).message(e.toKoreanMessage()).build();
    } catch (Exception e) {
      log.error("업로드 처리 중 오류가 발생했습니다", e);
      throw e;
    }
  }

  private String resolveCustomIdPath(CommonData commonData) {
    String customId = commonData.getCustomId();
    if (customId == null || customId.isBlank()) {
      throw new IllegalArgumentException("customId는 필수입니다.");
    }

    String sanitized =
        customId
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
}
