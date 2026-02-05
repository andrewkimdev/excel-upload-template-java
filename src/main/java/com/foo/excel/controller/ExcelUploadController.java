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
                        "message", fileSizeError.getMessage()
                );
                return ResponseEntity.badRequest().body(error);
            }

            ImportResult result = orchestrator.processUpload(file, templateType);

            Map<String, Object> response = new LinkedHashMap<>();
            response.put("success", result.isSuccess());
            response.put("rowsProcessed", result.getRowsProcessed());
            response.put("message", result.getMessage());

            if (result.isSuccess()) {
                response.put("rowsCreated", result.getRowsCreated());
                response.put("rowsUpdated", result.getRowsUpdated());
                return ResponseEntity.ok(response);
            } else {
                response.put("errorRows", result.getErrorRows());
                response.put("errorCount", result.getErrorCount());
                if (result.getDownloadUrl() != null) {
                    response.put("downloadUrl", result.getDownloadUrl());
                }
                return ResponseEntity.badRequest().body(response);
            }
        } catch (IllegalArgumentException e) {
            Map<String, Object> error = Map.of(
                    "success", false,
                    "message", e.getMessage()
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

        Path errorFile = properties.getTempDirectoryPath()
                .resolve("errors")
                .resolve(fileId + ".xlsx");

        if (!Files.exists(errorFile)) {
            return ResponseEntity.notFound().build();
        }

        Resource resource = new FileSystemResource(errorFile);

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"))
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"errors_" + fileId + ".xlsx\"")
                .body(resource);
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
            model.addAttribute("result", ImportResult.builder()
                    .success(false)
                    .message(e.getMessage())
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
