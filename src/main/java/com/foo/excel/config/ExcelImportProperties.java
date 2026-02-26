package com.foo.excel.config;

import jakarta.annotation.PostConstruct;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

@Data
@Component
@ConfigurationProperties(prefix = "excel.import")
public class ExcelImportProperties {

  private int maxFileSizeMb = 10;
  private int maxRows = 10000;
  private int preCountBuffer = 100;
  private int retentionDays = 30;
  private String tempDirectory = System.getProperty("java.io.tmpdir") + "/excel-imports";

  @PostConstruct
  public void init() throws IOException {
    Path tempDir = Path.of(tempDirectory);
    if (!Files.exists(tempDir)) {
      Files.createDirectories(tempDir);
    }
  }

  public Path getTempDirectoryPath() {
    return Path.of(tempDirectory);
  }
}
