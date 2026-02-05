package com.foo.excel.service;

import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.validation.ExcelValidationResult;
import com.foo.excel.validation.RowError;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
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

    private final ExcelConversionService conversionService;
    private final ExcelParserService parserService;
    private final ExcelValidationService validationService;
    private final ExcelErrorReportService errorReportService;
    private final ExcelImportProperties properties;
    private final List<TemplateDefinition<?>> templateDefinitions;

    @Data
    @Builder
    @AllArgsConstructor
    public static class ImportResult {
        private boolean success;
        private int rowsProcessed;
        private int rowsCreated;
        private int rowsUpdated;
        private int errorRows;
        private int errorCount;
        private String errorFileId;
        private String downloadUrl;
        private String message;
    }

    public ImportResult processUpload(MultipartFile file, String templateType) throws IOException {
        TemplateDefinition<?> template = findTemplate(templateType);
        return doProcess(template, file);
    }

    private TemplateDefinition<?> findTemplate(String templateType) {
        return templateDefinitions.stream()
                .filter(t -> t.getTemplateType().equals(templateType))
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException(
                        "Unknown template type: " + templateType));
    }

    @SuppressWarnings("unchecked")
    private <T> ImportResult doProcess(TemplateDefinition<T> template, MultipartFile file)
            throws IOException {

        ExcelImportConfig config = template.getConfig();

        // 1. Create UUID-based temp subdirectory
        String uploadId = UUID.randomUUID().toString();
        Path tempSubDir = properties.getTempDirectoryPath().resolve(uploadId);
        Files.createDirectories(tempSubDir);

        try {
            // 2. Convert to xlsx if needed
            Path xlsxFile = conversionService.ensureXlsxFormat(file, tempSubDir);

            // 3. Parse
            ExcelParserService.ParseResult<T> parseResult =
                    parserService.parse(xlsxFile, template.getDtoClass(), config);

            // 4. Check max rows
            if (parseResult.getRows().size() > properties.getMaxRows()) {
                return ImportResult.builder()
                        .success(false)
                        .rowsProcessed(parseResult.getRows().size())
                        .message("최대 행 수(" + properties.getMaxRows() + ")를 초과했습니다. 현재: "
                                + parseResult.getRows().size() + "행")
                        .build();
            }

            // 5. Validate (JSR-380 + within-file uniqueness)
            ExcelValidationResult validationResult = validationService.validate(
                    parseResult.getRows(), template.getDtoClass(),
                    parseResult.getSourceRowNumbers());

            // 6. DB uniqueness check
            List<RowError> dbErrors = template.checkDbUniqueness(
                    parseResult.getRows(), parseResult.getSourceRowNumbers());
            validationResult.merge(dbErrors);

            // 7. Merge parse errors
            validationResult.merge(parseResult.getParseErrors());

            if (validationResult.isValid()) {
                // 8. Persist
                PersistenceHandler.SaveResult saveResult =
                        template.getPersistenceHandler().saveAll(parseResult.getRows());

                return ImportResult.builder()
                        .success(true)
                        .rowsProcessed(parseResult.getRows().size())
                        .rowsCreated(saveResult.created())
                        .rowsUpdated(saveResult.updated())
                        .message("데이터 업로드 완료")
                        .build();
            } else {
                // 9. Generate error report
                Path errorFile = errorReportService.generateErrorReport(
                        xlsxFile, validationResult, parseResult.getColumnMappings(), config);

                String errorFileId = errorFile.getFileName().toString().replace(".xlsx", "");

                return ImportResult.builder()
                        .success(false)
                        .rowsProcessed(validationResult.getTotalRows())
                        .errorRows(validationResult.getErrorRowCount())
                        .errorCount(validationResult.getTotalErrorCount())
                        .errorFileId(errorFileId)
                        .downloadUrl("/api/excel/download/" + errorFileId)
                        .message(validationResult.getErrorRowCount() + "개 행에서 "
                                + validationResult.getTotalErrorCount() + "개 오류가 발견되었습니다")
                        .build();
            }
        } catch (Exception e) {
            log.error("Error processing upload", e);
            throw e;
        }
    }
}
