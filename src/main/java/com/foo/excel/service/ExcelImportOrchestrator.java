package com.foo.excel.service;

import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.util.SecureExcelUtils;
import com.foo.excel.validation.ExcelValidationResult;
import com.foo.excel.validation.RowError;
import lombok.Builder;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.UUID;

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
                .orElseThrow(() -> new IllegalArgumentException(
                        "알 수 없는 템플릿 타입입니다: " + templateType));
    }

    @SuppressWarnings("unchecked")
    private <T, C extends CommonData> ImportResult doProcess(TemplateDefinition<?, ?> rawTemplate,
                                                             MultipartFile file,
                                                             CommonData commonData)
            throws IOException {
        TemplateDefinition<T, C> template = (TemplateDefinition<T, C>) rawTemplate;
        if (!template.getCommonDataClass().isInstance(commonData)) {
            throw new IllegalArgumentException("commonData 형식이 템플릿과 일치하지 않습니다.");
        }
        C typedCommonData = template.getCommonDataClass().cast(commonData);

        ExcelImportConfig config = template.getConfig();

        // Extract and sanitize original filename for error report
        String sanitizedFilename = null;
        try {
            String originalName = file.getOriginalFilename();
            if (originalName != null && !originalName.isBlank()) {
                sanitizedFilename = SecureExcelUtils.sanitizeFilename(originalName);
            }
        } catch (IllegalArgumentException e) {
            log.warn("원본 파일명 정규화에 실패했습니다: {}", e.getMessage());
        }

        // 1. Create UUID-based temp subdirectory
        String uploadId = UUID.randomUUID().toString();
        Path tempSubDir = properties.getTempDirectoryPath().resolve(uploadId);
        Files.createDirectories(tempSubDir);

        try {
            // 2. Store and validate xlsx file
            Path xlsxFile = uploadFileService.storeAndValidateXlsx(file, tempSubDir);

            // 2b. Quick row count pre-check (lightweight SAX — avoids full parse for oversized files)
            int roughRowCount = SecureExcelUtils.countRows(xlsxFile, config.getSheetIndex());
            int preCountThreshold = properties.getMaxRows()
                    + (config.getDataStartRow() - 1) + properties.getPreCountBuffer();
            if (roughRowCount > preCountThreshold) {
                log.info("Pre-count rejected file: roughly {} rows, threshold={}", roughRowCount, preCountThreshold);
                return ImportResult.builder()
                        .success(false)
                        .rowsProcessed(roughRowCount)
                        .message("최대 행 수(" + properties.getMaxRows() + ")를 초과했습니다. "
                                + "파일에 약 " + roughRowCount + "행이 포함되어 있습니다")
                        .build();
            }

            // 3. Parse
            ExcelParserService.ParseResult<T> parseResult =
                    parserService.parse(xlsxFile, template.getDtoClass(), config,
                            properties.getMaxRows());

            // 4. Check max rows
            if (parseResult.rows().size() > properties.getMaxRows()) {
                return ImportResult.builder()
                        .success(false)
                        .rowsProcessed(parseResult.rows().size())
                        .message("최대 행 수(" + properties.getMaxRows() + ")를 초과했습니다. 현재: "
                                + parseResult.rows().size() + "행")
                        .build();
            }

            // 5. Validate (JSR-380 + within-file uniqueness)
            ExcelValidationResult validationResult = validationService.validate(
                    parseResult.rows(), template.getDtoClass(),
                    parseResult.sourceRowNumbers());

            // 6. DB uniqueness check
            List<RowError> dbErrors = template.checkDbUniqueness(
                    parseResult.rows(), parseResult.sourceRowNumbers());
            validationResult.merge(dbErrors);

            // 7. Merge parse errors
            validationResult.merge(parseResult.parseErrors());

            if (validationResult.isValid()) {
                // 8. Persist
                PersistenceHandler.SaveResult saveResult =
                        template.getPersistenceHandler().saveAll(
                                parseResult.rows(),
                                parseResult.sourceRowNumbers(),
                                typedCommonData);

                return ImportResult.builder()
                        .success(true)
                        .rowsProcessed(parseResult.rows().size())
                        .rowsCreated(saveResult.created())
                        .rowsUpdated(saveResult.updated())
                        .message("데이터 업로드 완료")
                        .build();
            } else {
                // 9. Generate error report
                Path errorFile = errorReportService.generateErrorReport(
                        xlsxFile, validationResult, parseResult.columnMappings(), config,
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
                        .message(validationResult.getErrorRowCount() + "개 행에서 "
                                + validationResult.getTotalErrorCount() + "개 오류가 발견되었습니다")
                        .build();
            }
        } catch (ColumnResolutionBatchException e) {
            log.warn("컬럼 해석에 실패했습니다: {}", e.getMessage());
            return ImportResult.builder()
                    .success(false)
                    .message(e.toKoreanMessage())
                    .build();
        } catch (Exception e) {
            log.error("업로드 처리 중 오류가 발생했습니다", e);
            throw e;
        }
    }
}
