package com.foo.excel.controller;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.service.CommonData;
import com.foo.excel.service.ExcelImportOrchestrator;
import com.foo.excel.service.ExcelImportOrchestrator.ImportResult;
import com.foo.excel.templates.samples.tariffexemption.TariffExemptionCommonData;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Pattern;

/**
 * Controller for Excel file upload and error report download.
 *
 * <h2>SECURITY NOTES FOR CONSUMERS</h2>
 * <p>When integrating this controller into your application, implement the following:</p>
 *
 * <h3>1. Authentication &amp; Authorization</h3>
 * <pre>
 * // Add Spring Security dependency and configure:
 * &#64;Configuration
 * &#64;EnableWebSecurity
 * public class SecurityConfig {
 *     &#64;Bean
 *     public SecurityFilterChain filterChain(HttpSecurity http) throws Exception {
 *         http.authorizeHttpRequests(auth -> auth
 *             .requestMatchers("/api/excel/**", "/upload").authenticated()
 *             .anyRequest().permitAll()
 *         );
 *         return http.build();
 *     }
 * }
 * </pre>
 *
 * <h3>2. CSRF Protection</h3>
 * <p>Spring Security enables CSRF by default. Ensure forms include the CSRF token:</p>
 * <pre>
 * &lt;input type="hidden" th:name="${_csrf.parameterName}" th:value="${_csrf.token}"/&gt;
 * </pre>
 *
 * <h3>3. Rate Limiting</h3>
 * <p>Implement rate limiting to prevent abuse. Example using Bucket4j or Spring Cloud Gateway.</p>
 *
 * <h3>4. Input Validation</h3>
 * <p>The templateType path variable should be validated against allowed values.
 * Currently, unknown types throw IllegalArgumentException, but consider using
 * &#64;Pattern validation for stricter control.</p>
 */
@Slf4j
@Controller
@RequiredArgsConstructor
public class ExcelFileController {

    private final ExcelImportOrchestrator orchestrator;
    private final ExcelImportProperties properties;
    private final ObjectMapper objectMapper;
    private final Validator validator;

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    // ========== REST API Endpoints ==========

    @PostMapping("/api/excel/upload/{templateType}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiUpload(
            @PathVariable String templateType,
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "commonData", required = false) String commonDataJson) {

        try {
            ImportResult fileSizeError = checkFileSize(file);
            if (fileSizeError != null) {
                Map<String, Object> error = Map.of(
                        "success", false,
                        "message", fileSizeError.message()
                );
                return ResponseEntity.badRequest().body(error);
            }

            Class<? extends CommonData> commonDataClass = orchestrator.getCommonDataClass(templateType);
            CommonData commonData = parseAndValidateCommonData(commonDataJson, commonDataClass);

            ImportResult result = orchestrator.processUpload(file, templateType, commonData);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", result.success());
            response.put("rowsProcessed", result.rowsProcessed());
            response.put("message", result.message());

            if (result.success()) {
                response.put("rowsCreated", result.rowsCreated());
                response.put("rowsUpdated", result.rowsUpdated());
                return ResponseEntity.ok(response);
            } else {
                response.put("errorRows", result.errorRows());
                response.put("errorCount", result.errorCount());
                if (result.downloadUrl() != null) {
                    response.put("downloadUrl", result.downloadUrl());
                }
                return ResponseEntity.badRequest().body(response);
            }
        } catch (IllegalArgumentException e) {
            log.warn("업로드 요청 오류: {}", e.getMessage());
            Map<String, Object> error = Map.of(
                    "success", false,
                    "message", e.getMessage()
            );
            return ResponseEntity.badRequest().body(error);
        } catch (SecurityException e) {
            // SECURITY: Security exceptions indicate potential attacks.
            // Log the details but return a generic message to avoid information disclosure.
            log.warn("업로드 보안 검증 실패: {}", e.getMessage());
            Map<String, Object> error = Map.of(
                    "success", false,
                    "message", "파일 보안 검증에 실패했습니다"
            );
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            // SECURITY: Do not expose internal error details to users.
            // Exception messages may contain file paths, stack traces, or other sensitive info.
            // Log the full error for debugging but return a generic message.
            log.error("업로드 처리 실패", e);
            Map<String, Object> error = Map.of(
                    "success", false,
                    "message", "파일 처리 중 오류가 발생했습니다. 관리자에게 문의하세요."
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    /**
     * Downloads an error report file by its UUID.
     *
     * <p><b>Security measures:</b></p>
     * <ul>
     *   <li>UUID format validation prevents path traversal attacks</li>
     *   <li>Files are stored in a controlled temp directory</li>
     *   <li>Only .xlsx files with valid UUID names can be accessed</li>
     * </ul>
     */
    @GetMapping("/api/excel/download/{fileId}")
    public ResponseEntity<Resource> downloadErrorFile(@PathVariable String fileId) {
        // SECURITY: Strict UUID validation prevents path traversal attacks.
        // Only files with valid UUID names (generated by the system) can be downloaded.
        if (!UUID_PATTERN.matcher(fileId).matches()) {
            return ResponseEntity.badRequest().build();
        }

        Path errorsDir = properties.getTempDirectoryPath().resolve("errors");
        Path errorFile = errorsDir.resolve(fileId + ".xlsx");

        if (!Files.exists(errorFile)) {
            return ResponseEntity.notFound().build();
        }

        String downloadFilename = buildDownloadFilename(fileId, errorsDir);
        String contentDisposition = buildContentDisposition(downloadFilename, fileId);

        Resource resource = new FileSystemResource(errorFile);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition)
                .body(resource);
    }

    /**
     * Reads original filename from .meta file, or falls back to UUID-based naming.
     */
    private String buildDownloadFilename(String fileId, Path errorsDir) {
        try {
            Path metaFile = errorsDir.resolve(fileId + ".meta");
            if (Files.exists(metaFile)) {
                String originalName = Files.readString(metaFile, StandardCharsets.UTF_8).trim();
                if (!originalName.isBlank()) {
                    return "오류_" + originalName;
                }
            }
        } catch (IOException e) {
            log.warn("Failed to read meta file for {}: {}", fileId, e.getMessage());
        }
        return "errors_" + fileId + ".xlsx";
    }

    /**
     * Builds Content-Disposition with RFC 5987 encoding for Korean filenames.
     * Uses both filename= (ASCII fallback) and filename*= (UTF-8 for modern browsers).
     */
    private String buildContentDisposition(String downloadFilename, String fileId) {
        String encodedFilename = URLEncoder.encode(downloadFilename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + fileId + ".xlsx\"; filename*=UTF-8''" + encodedFilename;
    }

    @GetMapping("/api/excel/template/{templateType}")
    public ResponseEntity<Void> downloadTemplate(@PathVariable String templateType) {
        // Placeholder - template download not yet implemented
        return ResponseEntity.status(HttpStatus.NOT_IMPLEMENTED).build();
    }

    // ========== Thymeleaf Endpoints ==========

    @GetMapping("/")
    public String uploadForm(Model model) {
        return "upload";
    }

    @PostMapping("/upload")
    public String handleUpload(
            @RequestParam("templateType") String templateType,
            @RequestParam("comeYear") String comeYear,
            @RequestParam("comeSequence") String comeSequence,
            @RequestParam("uploadSequence") String uploadSequence,
            @RequestParam("equipCode") String equipCode,
            @RequestParam("file") MultipartFile file,
            Model model) {

        try {
            ImportResult fileSizeError = checkFileSize(file);
            if (fileSizeError != null) {
                model.addAttribute("result", fileSizeError);
                return "result";
            }

            CommonData commonData = buildCommonDataFromForm(
                    templateType, comeYear, comeSequence, uploadSequence, equipCode);
            validateCommonData(commonData);

            ImportResult result = orchestrator.processUpload(file, templateType, commonData);
            model.addAttribute("result", result);
        } catch (IllegalArgumentException e) {
            log.warn("업로드 요청 오류: {}", e.getMessage());
            model.addAttribute("result", ImportResult.builder()
                    .success(false)
                    .message(e.getMessage())
                    .build());
        } catch (SecurityException e) {
            // SECURITY: Security exceptions indicate potential attacks.
            // Log the details but return a generic message to avoid information disclosure.
            log.warn("업로드 보안 검증 실패: {}", e.getMessage());
            model.addAttribute("result", ImportResult.builder()
                    .success(false)
                    .message("파일 보안 검증에 실패했습니다")
                    .build());
        } catch (Exception e) {
            // SECURITY: Do not expose internal error details to users.
            // Exception messages may contain file paths, stack traces, or other sensitive info.
            log.error("업로드 처리 실패", e);
            model.addAttribute("result", ImportResult.builder()
                    .success(false)
                    .message("파일 처리 중 오류가 발생했습니다. 관리자에게 문의하세요.")
                    .build());
        }

        return "result";
    }

    private ImportResult checkFileSize(MultipartFile file) {
        long maxBytes = (long) properties.getMaxFileSizeMb() * 1024 * 1024;
        if (file.getSize() > maxBytes) {
            return ImportResult.builder()
                    .success(false)
                    .message("파일 크기가 " + properties.getMaxFileSizeMb() + "MB를 초과했습니다")
                    .build();
        }
        return null;
    }

    private CommonData parseAndValidateCommonData(String commonDataJson,
                                                  Class<? extends CommonData> commonDataClass) {
        if (commonDataJson == null || commonDataJson.isBlank()) {
            throw new IllegalArgumentException("commonData 파트는 필수입니다.");
        }

        try {
            ObjectMapper strictMapper = objectMapper.copy()
                    .enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES)
                    .disable(MapperFeature.ALLOW_COERCION_OF_SCALARS);
            strictMapper.coercionConfigFor(LogicalType.Textual)
                    .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
                    .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);

            CommonData commonData = strictMapper.readValue(commonDataJson, commonDataClass);
            validateCommonData(commonData);
            return commonData;
        } catch (JsonProcessingException e) {
            throw new IllegalArgumentException("commonData 형식이 올바르지 않습니다.");
        }
    }

    private CommonData buildCommonDataFromForm(
            String templateType,
            String comeYear,
            String comeSequence,
            String uploadSequence,
            String equipCode) {
        if (!"tariff-exemption".equals(templateType)) {
            throw new IllegalArgumentException("알 수 없는 템플릿 타입입니다: " + templateType);
        }

        TariffExemptionCommonData commonData = new TariffExemptionCommonData();
        commonData.setComeYear(comeYear);
        commonData.setComeSequence(comeSequence);
        commonData.setUploadSequence(uploadSequence);
        commonData.setEquipCode(equipCode);
        return commonData;
    }

    private void validateCommonData(CommonData commonData) {
        var violations = validator.validate(commonData);
        if (!violations.isEmpty()) {
            ConstraintViolation<?> violation = violations.iterator().next();
            throw new IllegalArgumentException(violation.getMessage());
        }
    }
}
