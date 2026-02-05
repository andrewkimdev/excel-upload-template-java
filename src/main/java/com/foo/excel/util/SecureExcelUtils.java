package com.foo.excel.util;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Secure utilities for Excel file handling.
 * Provides protection against XXE (XML External Entity) attacks and Zip Bombs.
 */
public final class SecureExcelUtils {

    // Maximum bytes for a single record (100 MB)
    private static final int MAX_RECORD_LENGTH = 100_000_000;

    // Maximum size for byte array allocation (200 MB)
    private static final int MAX_BYTE_ARRAY_SIZE = 200_000_000;

    // Maximum text size in cells (10 MB)
    private static final int MAX_TEXT_SIZE = 10_000_000;

    // XLSX magic bytes (ZIP format: PK)
    private static final byte[] XLSX_MAGIC = {0x50, 0x4B, 0x03, 0x04};

    // XLS magic bytes (OLE compound document)
    private static final byte[] XLS_MAGIC = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

    static {
        // Configure Apache POI security limits globally
        IOUtils.setByteArrayMaxOverride(MAX_BYTE_ARRAY_SIZE);
    }

    private SecureExcelUtils() {
        // Utility class
    }

    /**
     * Creates a Workbook from a file with security protections enabled.
     * Protects against XXE and Zip Bomb attacks.
     *
     * @param file the Excel file to open
     * @return a Workbook instance
     * @throws IOException if the file cannot be read or is invalid
     * @throws SecurityException if the file fails security validation
     */
    public static Workbook createWorkbook(File file) throws IOException {
        validateFileContent(file.toPath());

        // Use OPCPackage with read-only access for XLSX files
        String fileName = file.getName().toLowerCase();
        if (fileName.endsWith(".xlsx")) {
            try {
                OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ);
                return new XSSFWorkbook(pkg);
            } catch (Exception e) {
                throw new IOException("Failed to open XLSX file securely: " + e.getMessage(), e);
            }
        }

        // For XLS files, use WorkbookFactory with default protections
        return WorkbookFactory.create(file);
    }

    /**
     * Creates a Workbook from a Path with security protections enabled.
     *
     * @param path the path to the Excel file
     * @return a Workbook instance
     * @throws IOException if the file cannot be read or is invalid
     * @throws SecurityException if the file fails security validation
     */
    public static Workbook createWorkbook(Path path) throws IOException {
        return createWorkbook(path.toFile());
    }

    /**
     * Validates that a file's content matches its extension.
     * Checks magic bytes to prevent disguised malicious files.
     *
     * @param path the file to validate
     * @throws IOException if the file cannot be read
     * @throws SecurityException if the file content doesn't match expected format
     */
    public static void validateFileContent(Path path) throws IOException {
        String fileName = path.getFileName().toString().toLowerCase();
        byte[] header = readFileHeader(path, 8);

        if (fileName.endsWith(".xlsx")) {
            if (!matchesMagicBytes(header, XLSX_MAGIC)) {
                throw new SecurityException(
                    "File content does not match XLSX format. " +
                    "The file may be corrupted or disguised.");
            }
        } else if (fileName.endsWith(".xls")) {
            if (!matchesMagicBytes(header, XLS_MAGIC)) {
                throw new SecurityException(
                    "File content does not match XLS format. " +
                    "The file may be corrupted or disguised.");
            }
        }
    }

    /**
     * Validates that an InputStream contains valid Excel content.
     * Note: This consumes bytes from the stream, so use with mark/reset or a fresh stream.
     *
     * @param inputStream the stream to validate
     * @param expectedExtension the expected file extension (xlsx or xls)
     * @throws IOException if the stream cannot be read
     * @throws SecurityException if the content doesn't match expected format
     */
    public static void validateStreamContent(InputStream inputStream, String expectedExtension)
            throws IOException {
        byte[] header = new byte[8];
        int bytesRead = inputStream.read(header);

        if (bytesRead < 4) {
            throw new SecurityException("File is too small to be a valid Excel file");
        }

        if ("xlsx".equalsIgnoreCase(expectedExtension)) {
            if (!matchesMagicBytes(header, XLSX_MAGIC)) {
                throw new SecurityException(
                    "File content does not match XLSX format");
            }
        } else if ("xls".equalsIgnoreCase(expectedExtension)) {
            if (!matchesMagicBytes(header, XLS_MAGIC)) {
                throw new SecurityException(
                    "File content does not match XLS format");
            }
        }
    }

    /**
     * Sanitizes a filename to prevent path traversal attacks.
     * Removes directory components and dangerous characters.
     *
     * @param originalFilename the original filename from user input
     * @return a sanitized filename safe for use in file operations
     * @throws IllegalArgumentException if the filename is null or empty after sanitization
     */
    public static String sanitizeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) {
            throw new IllegalArgumentException("Filename cannot be null or empty");
        }

        // Extract just the filename, removing any path components
        String filename = originalFilename;

        // Handle both Unix and Windows path separators
        int lastSlash = Math.max(filename.lastIndexOf('/'), filename.lastIndexOf('\\'));
        if (lastSlash >= 0) {
            filename = filename.substring(lastSlash + 1);
        }

        // Remove null bytes and other control characters
        filename = filename.replaceAll("[\\x00-\\x1F\\x7F]", "");

        // Remove or replace potentially dangerous characters
        // Allow only alphanumeric, dots, hyphens, underscores, spaces, and Korean characters
        filename = filename.replaceAll("[^a-zA-Z0-9.\\-_\\s\\uAC00-\\uD7AF\\u1100-\\u11FF\\u3130-\\u318F]", "_");

        // Prevent multiple consecutive dots (e.g., ".." for path traversal)
        filename = filename.replaceAll("\\.{2,}", ".");

        // Remove leading/trailing dots and spaces
        filename = filename.replaceAll("^[.\\s]+|[.\\s]+$", "");

        if (filename.isBlank()) {
            throw new IllegalArgumentException("Filename is invalid after sanitization");
        }

        // Ensure the filename has a valid Excel extension
        String lowerFilename = filename.toLowerCase();
        if (!lowerFilename.endsWith(".xlsx") && !lowerFilename.endsWith(".xls")) {
            throw new IllegalArgumentException("Invalid file extension");
        }

        return filename;
    }

    /**
     * Sanitizes a string value for safe inclusion in Excel cells.
     * Prevents formula injection attacks.
     *
     * @param value the value to sanitize
     * @return a sanitized value safe for Excel cells
     */
    public static String sanitizeForExcelCell(String value) {
        if (value == null || value.isEmpty()) {
            return value;
        }

        // Characters that can trigger formula interpretation in Excel
        char firstChar = value.charAt(0);
        if (firstChar == '=' || firstChar == '+' || firstChar == '-' ||
            firstChar == '@' || firstChar == '\t' || firstChar == '\r' || firstChar == '\n') {
            // Prefix with single quote to prevent formula interpretation
            return "'" + value;
        }

        return value;
    }

    private static byte[] readFileHeader(Path path, int length) throws IOException {
        try (InputStream is = Files.newInputStream(path)) {
            byte[] header = new byte[length];
            int bytesRead = is.read(header);
            if (bytesRead < 4) {
                throw new IOException("File is too small to be a valid Excel file");
            }
            return header;
        }
    }

    private static boolean matchesMagicBytes(byte[] header, byte[] magic) {
        if (header.length < magic.length) {
            return false;
        }
        for (int i = 0; i < magic.length; i++) {
            if (header[i] != magic[i]) {
                return false;
            }
        }
        return true;
    }
}
