package com.foo.excel.service;

import com.foo.excel.util.SecureExcelUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class ExcelConversionService {

    /**
     * Ensure the file is in .xlsx format.
     *
     * <p><b>Security Notes:</b></p>
     * <ul>
     *   <li>Filename is sanitized to prevent path traversal attacks</li>
     *   <li>File content is validated against magic bytes to ensure it matches the extension</li>
     * </ul>
     */
    public Path ensureXlsxFormat(MultipartFile file, Path tempDir) throws IOException {
        String originalName = file.getOriginalFilename();

        if (originalName == null) {
            throw new IllegalArgumentException("파일명이 없습니다");
        }

        // SECURITY: Sanitize filename to prevent path traversal attacks.
        // User-supplied filenames may contain "../" or other malicious sequences
        // that could write files outside the intended directory.
        String safeName = SecureExcelUtils.sanitizeFilename(originalName);
        String lowerName = safeName.toLowerCase();

        if (lowerName.endsWith(".xlsx")) {
            Path targetPath = tempDir.resolve(safeName);
            file.transferTo(targetPath);

            // SECURITY: Validate file content matches the extension.
            // Prevents uploading malicious files disguised with Excel extensions.
            SecureExcelUtils.validateFileContent(targetPath);

            return targetPath;
        } else {
            throw new IllegalArgumentException(
                "지원하지 않는 파일 형식입니다. .xlsx 파일만 업로드 가능합니다.");
        }
    }
}
