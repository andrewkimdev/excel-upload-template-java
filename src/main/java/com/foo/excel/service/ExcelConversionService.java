package com.foo.excel.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
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
     */
    public Path ensureXlsxFormat(MultipartFile file, Path tempDir) throws IOException {
        String originalName = file.getOriginalFilename();

        if (originalName == null) {
            throw new IllegalArgumentException("파일명이 없습니다");
        }

        String lowerName = originalName.toLowerCase();

        if (lowerName.endsWith(".xlsx")) {
            Path targetPath = tempDir.resolve(originalName);
            file.transferTo(targetPath);
            return targetPath;
        } else if (lowerName.endsWith(".xls")) {
            return convertXlsToXlsx(file, tempDir, originalName);
        } else {
            throw new IllegalArgumentException(
                "지원하지 않는 파일 형식입니다. .xlsx 또는 .xls 파일만 업로드 가능합니다.");
        }
    }

    private Path convertXlsToXlsx(MultipartFile file, Path tempDir, String originalName)
            throws IOException {

        try (InputStream is = file.getInputStream();
             HSSFWorkbook hssfWorkbook = new HSSFWorkbook(is)) {

            XSSFWorkbook xssfWorkbook = convertWorkbook(hssfWorkbook);

            String newName = originalName.substring(0, originalName.lastIndexOf('.')) + ".xlsx";
            Path targetPath = tempDir.resolve(newName);

            try (OutputStream os = Files.newOutputStream(targetPath)) {
                xssfWorkbook.write(os);
            }

            xssfWorkbook.close();
            return targetPath;
        }
    }

    private XSSFWorkbook convertWorkbook(HSSFWorkbook hssfWorkbook) {
        XSSFWorkbook xssfWorkbook = new XSSFWorkbook();

        for (int i = 0; i < hssfWorkbook.getNumberOfSheets(); i++) {
            var hssfSheet = hssfWorkbook.getSheetAt(i);
            var xssfSheet = xssfWorkbook.createSheet(hssfSheet.getSheetName());

            // Copy merged regions
            for (var mergedRegion : hssfSheet.getMergedRegions()) {
                xssfSheet.addMergedRegion(mergedRegion);
            }

            // Copy rows and cells
            for (var hssfRow : hssfSheet) {
                var xssfRow = xssfSheet.createRow(hssfRow.getRowNum());

                for (var hssfCell : hssfRow) {
                    var xssfCell = xssfRow.createCell(hssfCell.getColumnIndex());
                    copyCellValue(hssfCell, xssfCell);
                }
            }
        }

        return xssfWorkbook;
    }

    private void copyCellValue(org.apache.poi.ss.usermodel.Cell source,
                               org.apache.poi.ss.usermodel.Cell target) {
        switch (source.getCellType()) {
            case STRING -> target.setCellValue(source.getStringCellValue());
            case NUMERIC -> target.setCellValue(source.getNumericCellValue());
            case BOOLEAN -> target.setCellValue(source.getBooleanCellValue());
            case FORMULA -> target.setCellFormula(source.getCellFormula());
            case BLANK -> target.setBlank();
            default -> { /* ignore */ }
        }
    }
}
