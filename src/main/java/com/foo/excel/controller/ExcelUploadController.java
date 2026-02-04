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
        } catch (Exception e) {
            log.error("Upload failed", e);
            Map<String, Object> error = Map.of(
                    "success", false,
                    "message", "파일 처리 중 오류가 발생했습니다: " + e.getMessage()
            );
            return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(error);
        }
    }

    @GetMapping("/api/excel/download/{fileId}")
    public ResponseEntity<Resource> downloadErrorFile(@PathVariable String fileId) {
        // UUID validation to prevent path traversal
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
        } catch (Exception e) {
            log.error("Upload failed", e);
            model.addAttribute("result", ImportResult.builder()
                    .success(false)
                    .message("파일 처리 중 오류가 발생했습니다: " + e.getMessage())
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
