package com.foo.excel.service.file;

import com.foo.excel.config.ExcelImportProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.nio.file.*;
import java.nio.file.attribute.BasicFileAttributes;
import java.time.Instant;
import java.time.temporal.ChronoUnit;

@Slf4j
@Service
@RequiredArgsConstructor
public class TempFileCleanupService {

    private final ExcelImportProperties properties;

    @Scheduled(cron = "0 0 2 * * *")
    public void cleanupOldFiles() {
        Path tempDir = properties.getTempDirectoryPath();
        if (!Files.exists(tempDir)) {
            return;
        }

        Instant cutoff = Instant.now().minus(properties.getRetentionDays(), ChronoUnit.DAYS);

        try {
            Files.walkFileTree(tempDir, new SimpleFileVisitor<>() {
                @Override
                public FileVisitResult visitFile(Path file, BasicFileAttributes attrs)
                        throws IOException {
                    if (attrs.creationTime().toInstant().isBefore(cutoff)) {
                        Files.deleteIfExists(file);
                        log.debug("Deleted expired temp file: {}", file);
                    }
                    return FileVisitResult.CONTINUE;
                }

                @Override
                public FileVisitResult postVisitDirectory(Path dir, IOException exc)
                        throws IOException {
                    if (!dir.equals(tempDir) && isEmptyDirectory(dir)) {
                        Files.deleteIfExists(dir);
                        log.debug("Deleted empty temp directory: {}", dir);
                    }
                    return FileVisitResult.CONTINUE;
                }
            });
        } catch (IOException e) {
            log.error("Error during temp file cleanup", e);
        }
    }

    private boolean isEmptyDirectory(Path dir) throws IOException {
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(dir)) {
            return !stream.iterator().hasNext();
        }
    }
}
