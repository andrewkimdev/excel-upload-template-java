package com.foo.excel.service.file;

import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.util.SecureExcelUtils;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ExcelUploadFileService {

  private final ExcelImportProperties properties;

  public record StoredUpload(Path path, String sanitizedFilename) {}

  /** .xlsx 파일만 허용한다. */
  public StoredUpload storeAndValidateXlsx(MultipartFile file, String tempSubdirectory)
      throws IOException {
    String originalName = file.getOriginalFilename();

    if (originalName == null) {
      throw new IllegalArgumentException("파일명이 없습니다");
    }

    Path tempDir = resolveTempDirectory(tempSubdirectory);
    String safeName = SecureExcelUtils.sanitizeFilename(originalName);

    Path targetPath = tempDir.resolve(safeName);
    file.transferTo(targetPath);
    SecureExcelUtils.validateFileContent(targetPath);
    return new StoredUpload(targetPath, safeName);
  }

  private Path resolveTempDirectory(String tempSubdirectory) throws IOException {
    Path tempDir =
        tempSubdirectory == null
            ? properties.getTempDirectoryPath()
            : properties.getTempDirectoryPath().resolve(tempSubdirectory);
    Files.createDirectories(tempDir);
    return tempDir;
  }
}
