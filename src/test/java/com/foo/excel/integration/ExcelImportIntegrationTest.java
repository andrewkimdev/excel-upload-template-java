package com.foo.excel.integration;

import com.foo.excel.ExcelUploadApplication;
import com.foo.excel.config.ExcelImportProperties;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.junit.jupiter.api.MethodOrderer;
import org.junit.jupiter.api.Order;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.hamcrest.Matchers.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringBootTest(classes = ExcelUploadApplication.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ExcelImportIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExcelImportProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        // Ensure temp/errors directory exists
        Files.createDirectories(properties.getTempDirectoryPath().resolve("errors"));
    }

    @Test
    void upload_validXlsx_returnsSuccess() throws Exception {
        byte[] xlsxBytes = createValidTariffExemptionXlsx(2);
        MockMultipartFile file = new MockMultipartFile(
                "file", "tariff.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        mockMvc.perform(multipart("/api/excel/upload/tariff-exemption").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rowsProcessed").value(2))
                .andExpect(jsonPath("$.message").value("데이터 업로드 완료"));
    }

    @Test
    void upload_invalidData_returnsErrorWithDownloadUrl() throws Exception {
        byte[] xlsxBytes = createInvalidTariffExemptionXlsx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "tariff_invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        mockMvc.perform(multipart("/api/excel/upload/tariff-exemption").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorRows").value(greaterThan(0)))
                .andExpect(jsonPath("$.downloadUrl").value(startsWith("/api/excel/download/")));
    }

    @Test
    void download_existingErrorFile_returnsXlsxContentType() throws Exception {
        // First upload an invalid file to generate an error report
        byte[] xlsxBytes = createInvalidTariffExemptionXlsx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "tariff_invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        MvcResult uploadResult = mockMvc.perform(
                multipart("/api/excel/upload/tariff-exemption").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.downloadUrl").exists())
                .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
        // Extract download URL from JSON response
        String downloadUrl = extractDownloadUrl(responseBody);

        mockMvc.perform(get(downloadUrl))
                .andExpect(status().isOk())
                .andExpect(content().contentType(
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
    }

    @Test
    void download_nonexistentFile_returns404() throws Exception {
        mockMvc.perform(get("/api/excel/download/00000000-0000-0000-0000-000000000000"))
                .andExpect(status().isNotFound());
    }

    @Test
    void upload_xlsFile_autoConvertsAndProcesses() throws Exception {
        byte[] xlsBytes = createValidTariffExemptionXls(2);
        MockMultipartFile file = new MockMultipartFile(
                "file", "tariff.xls",
                "application/vnd.ms-excel",
                xlsBytes);

        mockMvc.perform(multipart("/api/excel/upload/tariff-exemption").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rowsProcessed").value(2));
    }

    @Test
    void upload_unknownTemplateType_returnsError() throws Exception {
        byte[] xlsxBytes = createValidTariffExemptionXlsx(1);
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        mockMvc.perform(multipart("/api/excel/upload/unknown-type").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false));
    }

    @Test
    void upload_fileTooLarge_rejected() throws Exception {
        // Create a file > 10MB
        byte[] largeBytes = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                largeBytes);

        // Spring's multipart max-file-size should reject this
        mockMvc.perform(multipart("/api/excel/upload/tariff-exemption").file(file))
                .andExpect(status().is4xxClientError());
    }

    @Test
    void upload_wrongTemplate_returnsColumnMismatchError() throws Exception {
        byte[] xlsxBytes = createWrongTemplateTariffExemptionXlsx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "wrong_template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        mockMvc.perform(multipart("/api/excel/upload/tariff-exemption").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("헤더가 일치하지 않습니다")));
    }

    // ===== Helper methods =====

    private byte[] createValidTariffExemptionXlsx(int dataRows) throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            fillTariffExemptionSheet(sheet, dataRows, false);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private byte[] createInvalidTariffExemptionXlsx() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            fillTariffExemptionSheet(sheet, 1, true);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private byte[] createValidTariffExemptionXls(int dataRows) throws IOException {
        try (HSSFWorkbook wb = new HSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");
            fillTariffExemptionSheet(sheet, dataRows, false);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private void fillTariffExemptionSheet(Sheet sheet, int dataRows, boolean makeInvalid) {
        // Header row at row 4 (0-based: row 3)
        Row headerRow = sheet.createRow(3);
        headerRow.createCell(0).setCellValue("No");
        headerRow.createCell(1).setCellValue("순번");
        headerRow.createCell(2).setCellValue("물품명");
        headerRow.createCell(3).setCellValue("규격1)");
        headerRow.createCell(4).setCellValue("모델명1)");
        headerRow.createCell(5).setCellValue("HSK No");
        headerRow.createCell(6).setCellValue("");
        headerRow.createCell(7).setCellValue("관세율");
        headerRow.createCell(8).setCellValue("단가($)");
        headerRow.createCell(9).setCellValue("제조용");
        headerRow.createCell(10).setCellValue("");
        headerRow.createCell(11).setCellValue("수리용");
        headerRow.createCell(12).setCellValue("");
        headerRow.createCell(13).setCellValue("연간수입예상금액($)");
        headerRow.createCell(14).setCellValue("심의결과");
        headerRow.createCell(15).setCellValue("");
        headerRow.createCell(16).setCellValue("연간 예상소요량");

        // Data rows start at row 7 (0-based: row 6)
        for (int i = 0; i < dataRows; i++) {
            Row row = sheet.createRow(6 + i);
            row.createCell(0).setCellValue(i + 1);
            row.createCell(1).setCellValue(i + 1);
            if (makeInvalid) {
                row.createCell(2).setCellValue("");  // blank itemName - violates @NotBlank
                row.createCell(5).setCellValue("bad-hs-code");  // violates @Pattern
            } else {
                row.createCell(2).setCellValue("Item" + (i + 1));
                row.createCell(5).setCellValue("8481.80-200" + i);
            }
            row.createCell(3).setCellValue("Spec" + (i + 1));
            row.createCell(4).setCellValue("Model" + (i + 1));
            row.createCell(7).setCellValue(8.0);
            row.createCell(8).setCellValue(100.0);
            row.createCell(9).setCellValue(10);
            row.createCell(11).setCellValue(5);
            row.createCell(13).setCellValue(50000.0);
            row.createCell(14).setCellValue("통과");
            row.createCell(16).setCellValue(100);
        }
    }

    private byte[] createWrongTemplateTariffExemptionXlsx() throws IOException {
        try (XSSFWorkbook wb = new XSSFWorkbook()) {
            Sheet sheet = wb.createSheet("Sheet1");

            // Header row at row 4 (0-based: row 3) with WRONG headers
            Row headerRow = sheet.createRow(3);
            headerRow.createCell(0).setCellValue("No");
            headerRow.createCell(1).setCellValue("WRONG_B");   // Expected: 순번
            headerRow.createCell(2).setCellValue("WRONG_C");   // Expected: 물품명
            headerRow.createCell(3).setCellValue("WRONG_D");
            headerRow.createCell(4).setCellValue("WRONG_E");
            headerRow.createCell(5).setCellValue("WRONG_F");
            headerRow.createCell(7).setCellValue("WRONG_H");
            headerRow.createCell(8).setCellValue("WRONG_I");
            headerRow.createCell(9).setCellValue("WRONG_J");
            headerRow.createCell(11).setCellValue("WRONG_L");
            headerRow.createCell(13).setCellValue("WRONG_N");
            headerRow.createCell(14).setCellValue("WRONG_O");
            headerRow.createCell(16).setCellValue("WRONG_Q");

            Row dataRow = sheet.createRow(6);
            dataRow.createCell(1).setCellValue(1);
            dataRow.createCell(2).setCellValue("Item1");

            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            wb.write(bos);
            return bos.toByteArray();
        }
    }

    private String extractDownloadUrl(String jsonResponse) {
        // Simple extraction: find "downloadUrl":"..."
        int start = jsonResponse.indexOf("\"downloadUrl\":\"") + "\"downloadUrl\":\"".length();
        int end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }
}
