package com.foo.excel.controller;

import com.foo.excel.config.ExcelImportProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.regex.Pattern;

@Slf4j
@Controller
@RequiredArgsConstructor
public class ExcelFileController {

    private static final Pattern UUID_PATTERN =
            Pattern.compile("^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$");

    private final ExcelImportProperties properties;

    @GetMapping("/api/excel/download/{fileId}")
    public ResponseEntity<Resource> downloadErrorFile(@PathVariable String fileId) {
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
            log.warn("메타 파일을 읽는 중 오류가 발생했습니다. fileId={}, message={}", fileId, e.getMessage());
        }
        return "errors_" + fileId + ".xlsx";
    }

    private String buildContentDisposition(String downloadFilename, String fileId) {
        String encodedFilename = URLEncoder.encode(downloadFilename, StandardCharsets.UTF_8)
                .replace("+", "%20");
        return "attachment; filename=\"" + fileId + ".xlsx\"; filename*=UTF-8''" + encodedFilename;
    }
}
