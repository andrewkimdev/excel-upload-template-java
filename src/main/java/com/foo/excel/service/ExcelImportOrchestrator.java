package com.foo.excel.service;

import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.dto.TariffExemptionDto;
import com.foo.excel.validation.ExcelValidationResult;
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
import java.util.Map;
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

    private static final Map<String, Class<? extends ExcelImportConfig>> TEMPLATE_REGISTRY = Map.of(
            "tariff-exemption", TariffExemptionDto.class
    );

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

    @SuppressWarnings("unchecked")
    public ImportResult processUpload(MultipartFile file, String templateType) throws IOException {
        // 1. Resolve DTO class
        Class<? extends ExcelImportConfig> dtoClass = TEMPLATE_REGISTRY.get(templateType);
        if (dtoClass == null) {
            throw new IllegalArgumentException("Unknown template type: " + templateType);
        }

        // 2. Create UUID-based temp subdirectory
        String uploadId = UUID.randomUUID().toString();
        Path tempSubDir = properties.getTempDirectoryPath().resolve(uploadId);
        Files.createDirectories(tempSubDir);

        try {
            // 3. Convert to xlsx if needed
            Path xlsxFile = conversionService.ensureXlsxFormat(file, tempSubDir);

            // 4. Parse
            ExcelParserService.ParseResult<?> parseResult = parserService.parse(xlsxFile, dtoClass);

            // 5. Check max rows
            if (parseResult.getRows().size() > properties.getMaxRows()) {
                return ImportResult.builder()
                        .success(false)
                        .rowsProcessed(parseResult.getRows().size())
                        .message("최대 행 수(" + properties.getMaxRows() + ")를 초과했습니다. 현재: "
                                + parseResult.getRows().size() + "행")
                        .build();
            }

            // 6. Validate
            ExcelImportConfig config;
            try {
                config = dtoClass.getDeclaredConstructor().newInstance();
            } catch (Exception e) {
                throw new RuntimeException("Cannot instantiate config", e);
            }

            ExcelValidationResult validationResult = validationService.validate(
                    (java.util.List) parseResult.getRows(),
                    (Class) dtoClass,
                    config.getDataStartRow());

            if (validationResult.isValid()) {
                // 7. Upsert (placeholder)
                int rowCount = parseResult.getRows().size();
                log.info("Upsert placeholder: {} rows would be processed for template '{}'",
                        rowCount, templateType);

                return ImportResult.builder()
                        .success(true)
                        .rowsProcessed(rowCount)
                        .rowsCreated(rowCount)
                        .rowsUpdated(0)
                        .message("데이터 업로드 완료")
                        .build();
            } else {
                // 8. Generate error report
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
