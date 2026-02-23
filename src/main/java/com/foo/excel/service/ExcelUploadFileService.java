package com.foo.excel.service;

import com.foo.excel.util.SecureExcelUtils;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.nio.file.Path;

@Service
public class ExcelUploadFileService {

    /**
     * .xlsx 파일만 허용한다.
     */
    public Path storeAndValidateXlsx(MultipartFile file, Path tempDir) throws IOException {
        String originalName = file.getOriginalFilename();

        if (originalName == null) {
            throw new IllegalArgumentException("파일명이 없습니다");
        }

        String lowerOriginalName = originalName.trim().toLowerCase();
        if (lowerOriginalName.endsWith(".xls")) {
            throw new IllegalArgumentException("지원하지 않는 파일 형식입니다. .xlsx 파일만 업로드 가능합니다.");
        }
        if (!lowerOriginalName.endsWith(".xlsx")) {
            throw new IllegalArgumentException("유효하지 않은 파일 확장자입니다.");
        }

        String safeName = SecureExcelUtils.sanitizeFilename(originalName);

        Path targetPath = tempDir.resolve(safeName);
        file.transferTo(targetPath);
        SecureExcelUtils.validateFileContent(targetPath);
        return targetPath;
    }
}
