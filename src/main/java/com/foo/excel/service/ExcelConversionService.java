package com.foo.excel.service;

import com.foo.excel.util.SecureExcelUtils;
import com.foo.excel.util.WorkbookCopyUtils;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.CellStyle;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ExcelConversionService {

    /**
     * Ensure the file is in .xlsx format.
     * If .xls is uploaded, convert to .xlsx.
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
        } else if (lowerName.endsWith(".xls")) {
            return convertXlsToXlsx(file, tempDir, safeName);
        } else {
            throw new IllegalArgumentException(
                "지원하지 않는 파일 형식입니다. .xlsx 또는 .xls 파일만 업로드 가능합니다.");
        }
    }

    private Path convertXlsToXlsx(MultipartFile file, Path tempDir, String safeName)
            throws IOException {

        // SECURITY: Validate XLS content before processing.
        // We need to validate the stream content, so we read the header first.
        byte[] fileBytes = file.getBytes();
        validateXlsContent(fileBytes);

        try (InputStream is = new java.io.ByteArrayInputStream(fileBytes);
             HSSFWorkbook hssfWorkbook = new HSSFWorkbook(is)) {

            XSSFWorkbook xssfWorkbook = convertWorkbook(hssfWorkbook);

            String newName = safeName.substring(0, safeName.lastIndexOf('.')) + ".xlsx";
            Path targetPath = tempDir.resolve(newName);

            try (OutputStream os = Files.newOutputStream(targetPath)) {
                xssfWorkbook.write(os);
            }

            xssfWorkbook.close();
            return targetPath;
        }
    }

    /**
     * Validates that the byte array contains valid XLS content by checking magic bytes.
     */
    private void validateXlsContent(byte[] fileBytes) {
        // XLS magic bytes (OLE compound document): D0 CF 11 E0
        byte[] xlsMagic = {(byte) 0xD0, (byte) 0xCF, 0x11, (byte) 0xE0};

        if (fileBytes.length < xlsMagic.length) {
            throw new SecurityException("File is too small to be a valid XLS file");
        }

        for (int i = 0; i < xlsMagic.length; i++) {
            if (fileBytes[i] != xlsMagic[i]) {
                throw new SecurityException(
                    "File content does not match XLS format. " +
                    "The file may be corrupted or disguised.");
            }
        }
    }

    private XSSFWorkbook convertWorkbook(HSSFWorkbook hssfWorkbook) {
        var xssfWorkbook = new XSSFWorkbook();

        // Build style mapping (HSSF→XSSF cross-format copy)
        var styleMap = WorkbookCopyUtils.buildStyleMapping(hssfWorkbook, xssfWorkbook);

        for (int i = 0; i < hssfWorkbook.getNumberOfSheets(); i++) {
            var hssfSheet = hssfWorkbook.getSheetAt(i);
            var xssfSheet = xssfWorkbook.createSheet(hssfSheet.getSheetName());

            // Copy sheet-level metadata
            int maxCol = 0;
            for (var row : hssfSheet) {
                maxCol = Math.max(maxCol, row.getLastCellNum());
            }
            WorkbookCopyUtils.copyColumnWidths(hssfSheet, xssfSheet, maxCol);
            WorkbookCopyUtils.copyMergedRegions(hssfSheet, xssfSheet);

            // Copy rows, cells, styles, and row heights
            for (var hssfRow : hssfSheet) {
                var xssfRow = xssfSheet.createRow(hssfRow.getRowNum());
                xssfRow.setHeight(hssfRow.getHeight());

                for (var hssfCell : hssfRow) {
                    var xssfCell = xssfRow.createCell(hssfCell.getColumnIndex());
                    WorkbookCopyUtils.copyCellValue(hssfCell, xssfCell);

                    CellStyle mappedStyle = styleMap.get((int) hssfCell.getCellStyle().getIndex());
                    if (mappedStyle != null) {
                        xssfCell.setCellStyle(mappedStyle);
                    }
                }
            }
        }

        return xssfWorkbook;
    }
}
