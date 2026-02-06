package com.foo.excel.controller;

import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.service.ExcelImportOrchestrator;
import com.foo.excel.service.ExcelImportOrchestrator.ImportResult;
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
public class ExcelUploadController {

    private final ExcelImportOrchestrator orchestrator;
    private final ExcelImportProperties properties;

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    // ========== REST API Endpoints ==========

    @PostMapping("/api/excel/upload/{templateType}")
    @ResponseBody
    public ResponseEntity<Map<String, Object>> apiUpload(
            @PathVariable String templateType,
            @RequestParam("file") MultipartFile file) {

        try {
            ImportResult fileSizeError = checkFileSize(file);
            if (fileSizeError != null) {
                Map<String, Object> error = Map.of(
                        "success", false,
                        "message", fileSizeError.message()
                );
                return ResponseEntity.badRequest().body(error);
            }

            ImportResult result = orchestrator.processUpload(file, templateType);

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
            log.warn("Invalid request during upload: {}", e.getMessage());
            Map<String, Object> error = Map.of(
                    "success", false,
                    "message", "잘못된 요청입니다. 파일 형식과 템플릿 유형을 확인하세요."
            );
            return ResponseEntity.badRequest().body(error);
        } catch (SecurityException e) {
            // SECURITY: Security exceptions indicate potential attacks.
            // Log the details but return a generic message to avoid information disclosure.
            log.warn("Security violation during upload: {}", e.getMessage());
            Map<String, Object> error = Map.of(
                    "success", false,
                    "message", "파일 보안 검증에 실패했습니다"
            );
            return ResponseEntity.badRequest().body(error);
        } catch (Exception e) {
            // SECURITY: Do not expose internal error details to users.
            // Exception messages may contain file paths, stack traces, or other sensitive info.
            // Log the full error for debugging but return a generic message.
            log.error("Upload failed", e);
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
                    // Normalize extension: .xls → .xlsx (source was converted)
                    if (originalName.toLowerCase().endsWith(".xls")) {
                        originalName = originalName.substring(0, originalName.length() - 4) + ".xlsx";
                    }
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
            @RequestParam("file") MultipartFile file,
            Model model) {

        try {
            ImportResult fileSizeError = checkFileSize(file);
            if (fileSizeError != null) {
                model.addAttribute("result", fileSizeError);
                return "result";
            }

            ImportResult result = orchestrator.processUpload(file, templateType);
            model.addAttribute("result", result);
        } catch (IllegalArgumentException e) {
            log.warn("Invalid request during upload: {}", e.getMessage());
            model.addAttribute("result", ImportResult.builder()
                    .success(false)
                    .message("잘못된 요청입니다. 파일 형식과 템플릿 유형을 확인하세요.")
                    .build());
        } catch (SecurityException e) {
            // SECURITY: Security exceptions indicate potential attacks.
            // Log the details but return a generic message to avoid information disclosure.
            log.warn("Security violation during upload: {}", e.getMessage());
            model.addAttribute("result", ImportResult.builder()
                    .success(false)
                    .message("파일 보안 검증에 실패했습니다")
                    .build());
        } catch (Exception e) {
            // SECURITY: Do not expose internal error details to users.
            // Exception messages may contain file paths, stack traces, or other sensitive info.
            log.error("Upload failed", e);
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
}
