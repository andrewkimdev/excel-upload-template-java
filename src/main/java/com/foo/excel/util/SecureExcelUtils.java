package com.foo.excel.util;

import org.apache.poi.openxml4j.opc.OPCPackage;
import org.apache.poi.openxml4j.opc.PackageAccess;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.util.IOUtils;
import org.apache.poi.xssf.eventusermodel.XSSFReader;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;

import javax.xml.stream.XMLInputFactory;
import javax.xml.stream.XMLStreamConstants;
import javax.xml.stream.XMLStreamReader;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Iterator;

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

        try {
            OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ);
            return new XSSFWorkbook(pkg);
        } catch (Exception e) {
            throw new IOException("Failed to open XLSX file securely: " + e.getMessage(), e);
        }
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
        } else {
            throw new SecurityException("Only .xlsx files are supported.");
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
        } else {
            throw new SecurityException("Only xlsx validation is supported");
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
        if (!lowerFilename.endsWith(".xlsx")) {
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

    /**
     * Counts the number of rows in an xlsx sheet using lightweight StAX streaming.
     * Does NOT load the full workbook DOM — uses constant memory regardless of file size.
     *
     * @param xlsxFile the xlsx file to count rows in
     * @param sheetIndex 0-based sheet index
     * @return the number of row elements in the sheet XML
     * @throws IOException if the file cannot be read or the sheet is not found
     */
    public static int countRows(Path xlsxFile, int sheetIndex) throws IOException {
        try (OPCPackage pkg = OPCPackage.open(xlsxFile.toFile(), PackageAccess.READ)) {
            var reader = new XSSFReader(pkg);
            Iterator<InputStream> sheets = reader.getSheetsData();

            int currentSheet = 0;
            while (sheets.hasNext()) {
                try (InputStream sheetStream = sheets.next()) {
                    if (currentSheet == sheetIndex) {
                        return countRowElements(sheetStream);
                    }
                }
                currentSheet++;
            }

            throw new IOException("Sheet index " + sheetIndex + " not found in workbook");
        } catch (IOException e) {
            throw e;
        } catch (Exception e) {
            throw new IOException("Failed to count rows in xlsx file: " + e.getMessage(), e);
        }
    }

    private static int countRowElements(InputStream sheetStream) throws IOException {
        try {
            XMLInputFactory factory = XMLInputFactory.newInstance();
            factory.setProperty(XMLInputFactory.IS_SUPPORTING_EXTERNAL_ENTITIES, false);
            factory.setProperty(XMLInputFactory.SUPPORT_DTD, false);

            XMLStreamReader xmlReader = factory.createXMLStreamReader(sheetStream);
            int rowCount = 0;
            while (xmlReader.hasNext()) {
                if (xmlReader.next() == XMLStreamConstants.START_ELEMENT
                        && "row".equals(xmlReader.getLocalName())) {
                    rowCount++;
                }
            }
            xmlReader.close();
            return rowCount;
        } catch (Exception e) {
            throw new IOException("Failed to parse sheet XML: " + e.getMessage(), e);
        }
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
