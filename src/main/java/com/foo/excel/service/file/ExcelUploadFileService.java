package com.foo.excel.service.file;

import com.foo.excel.util.SecureExcelUtils;
import java.io.IOException;
import java.nio.file.Path;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class ExcelUploadFileService {

  public record StoredUpload(Path path, String sanitizedFilename) {}

  /** .xlsx 파일만 허용한다. */
  public StoredUpload storeAndValidateXlsx(MultipartFile file, Path tempDir) throws IOException {
    String originalName = file.getOriginalFilename();

    if (originalName == null) {
      throw new IllegalArgumentException("파일명이 없습니다");
    }

    String safeName = SecureExcelUtils.sanitizeFilename(originalName);

    Path targetPath = tempDir.resolve(safeName);
    file.transferTo(targetPath);
    SecureExcelUtils.validateFileContent(targetPath);
    return new StoredUpload(targetPath, safeName);
  }
}
