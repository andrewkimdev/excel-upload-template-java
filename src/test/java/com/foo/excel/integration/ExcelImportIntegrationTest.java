package com.foo.excel.integration;

import com.foo.excel.ExcelUploadApplication;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.templates.TemplateTypes;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
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

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.startsWith;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

@SpringBootTest(classes = ExcelUploadApplication.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ExcelImportIntegrationTest {

    private static final String API_UPLOAD_TARIFF = "/api/excel/upload/" + TemplateTypes.TARIFF_EXEMPTION;
    private static final String PAGE_UPLOAD_TARIFF = "/upload/" + TemplateTypes.TARIFF_EXEMPTION;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ExcelImportProperties properties;

    @BeforeEach
    void setUp() throws IOException {
        Files.createDirectories(properties.getTempDirectoryPath().resolve("errors"));
    }

    @Test
    void upload_validXlsx_returnsSuccess_blackBoxLiteralRoute() throws Exception {
        byte[] xlsxBytes = createValidTariffExemptionXlsx(2);
        MockMultipartFile file = new MockMultipartFile(
                "file", "tariff.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        mockMvc.perform(multipart("/api/excel/upload/tariff-exemption")
                        .file(file)
                        .file(requiredCommonDataPart()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rowsProcessed").value(2))
                .andExpect(jsonPath("$.message").value("데이터 업로드 완료"));
    }

    @Test
    void upload_sameFileAndCommonData_twice_switchesCreatedToUpdated() throws Exception {
        byte[] xlsxBytes = createValidTariffExemptionXlsx(2);
        MockMultipartFile firstFile = new MockMultipartFile(
                "file", "tariff.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);
        MockMultipartFile secondFile = new MockMultipartFile(
                "file", "tariff.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        mockMvc.perform(multipart(API_UPLOAD_TARIFF)
                        .file(firstFile)
                        .file(requiredCommonDataPart()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rowsCreated").value(2))
                .andExpect(jsonPath("$.rowsUpdated").value(0));

        mockMvc.perform(multipart(API_UPLOAD_TARIFF)
                        .file(secondFile)
                        .file(requiredCommonDataPart()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.success").value(true))
                .andExpect(jsonPath("$.rowsCreated").value(0))
                .andExpect(jsonPath("$.rowsUpdated").value(2));
    }

    @Test
    void upload_invalidData_returnsErrorWithDownloadUrl() throws Exception {
        byte[] xlsxBytes = createInvalidTariffExemptionXlsx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "tariff_invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        mockMvc.perform(multipart(API_UPLOAD_TARIFF)
                        .file(file)
                        .file(requiredCommonDataPart()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.errorRows").value(greaterThan(0)))
                .andExpect(jsonPath("$.downloadUrl").value(startsWith("/api/excel/download/")));
    }

    @Test
    void download_existingErrorFile_returnsXlsxContentType() throws Exception {
        byte[] xlsxBytes = createInvalidTariffExemptionXlsx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "tariff_invalid.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        MvcResult uploadResult = mockMvc.perform(
                        multipart(API_UPLOAD_TARIFF)
                                .file(file)
                                .file(requiredCommonDataPart()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.downloadUrl").exists())
                .andReturn();

        String responseBody = uploadResult.getResponse().getContentAsString();
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
    void upload_xlsFile_rejected() throws Exception {
        byte[] xlsBytes = createValidTariffExemptionXls(2);
        MockMultipartFile file = new MockMultipartFile(
                "file", "tariff.xls",
                "application/vnd.ms-excel",
                xlsBytes);

        mockMvc.perform(multipart(API_UPLOAD_TARIFF)
                        .file(file)
                        .file(requiredCommonDataPart()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString(".xlsx")));
    }

    @Test
    void upload_missingCommonData_rejected() throws Exception {
        byte[] xlsxBytes = createValidTariffExemptionXlsx(1);
        MockMultipartFile file = new MockMultipartFile(
                "file", "tariff.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        mockMvc.perform(multipart(API_UPLOAD_TARIFF)
                        .file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("commonData")));
    }

    @Test
    void upload_commonDataUnknownField_rejected() throws Exception {
        byte[] xlsxBytes = createValidTariffExemptionXlsx(1);
        MockMultipartFile file = new MockMultipartFile(
                "file", "tariff.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);
        MockMultipartFile invalidCommonData = new MockMultipartFile(
                "commonData",
                "commonData",
                MediaType.APPLICATION_JSON_VALUE,
                ("{" +
                        "\"comeYear\":\"2026\"," +
                        "\"comeSequence\":\"001\"," +
                        "\"uploadSequence\":\"U001\"," +
                        "\"equipCode\":\"EQ-01\"," +
                        "\"unknown\":\"x\"" +
                        "}").getBytes()
        );

        mockMvc.perform(multipart(API_UPLOAD_TARIFF)
                        .file(file)
                        .file(invalidCommonData))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("commonData")));
    }

    @Test
    void upload_commonDataScalarCoercion_rejected() throws Exception {
        byte[] xlsxBytes = createValidTariffExemptionXlsx(1);
        MockMultipartFile file = new MockMultipartFile(
                "file", "tariff.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);
        MockMultipartFile invalidCommonData = new MockMultipartFile(
                "commonData",
                "commonData",
                MediaType.APPLICATION_JSON_VALUE,
                ("{" +
                        "\"comeYear\":2026," +
                        "\"comeSequence\":\"001\"," +
                        "\"uploadSequence\":\"U001\"," +
                        "\"equipCode\":\"EQ-01\"" +
                        "}").getBytes()
        );

        mockMvc.perform(multipart(API_UPLOAD_TARIFF)
                        .file(file)
                        .file(invalidCommonData))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message", containsString("commonData")));
    }

    @Test
    void removedGenericUploadRoute_returns404() throws Exception {
        byte[] xlsxBytes = createValidTariffExemptionXlsx(1);
        MockMultipartFile file = new MockMultipartFile(
                "file", "test.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        mockMvc.perform(multipart("/api/excel/upload/unknown-type")
                        .file(file)
                        .file(requiredCommonDataPart()))
                .andExpect(status().isNotFound());
    }

    @Test
    void removedTemplateDownloadRoute_returns404() throws Exception {
        mockMvc.perform(get("/api/excel/template/" + TemplateTypes.TARIFF_EXEMPTION))
                .andExpect(status().isNotFound());
    }

    @Test
    void page_route_rendersTemplateSpecificForm() throws Exception {
        mockMvc.perform(get(PAGE_UPLOAD_TARIFF))
                .andExpect(status().isOk())
                .andExpect(view().name("upload-tariff-exemption"))
                .andExpect(content().string(containsString("관세면제 업로드")));
    }

    @Test
    void page_formSubmission_success_rendersResultModel() throws Exception {
        byte[] xlsxBytes = createValidTariffExemptionXlsx(1);
        MockMultipartFile file = new MockMultipartFile(
                "file", "tariff.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        mockMvc.perform(multipart(PAGE_UPLOAD_TARIFF)
                        .file(file)
                        .param("comeYear", "2026")
                        .param("comeSequence", "001")
                        .param("uploadSequence", "U001")
                        .param("equipCode", "EQ-01"))
                .andExpect(status().isOk())
                .andExpect(view().name("result"))
                .andExpect(model().attributeExists("result"));
    }

    @Test
    void upload_tooManyRows_rejectedByPreCount() throws Exception {
        int originalMaxRows = properties.getMaxRows();
        int originalBuffer = properties.getPreCountBuffer();
        try {
            properties.setMaxRows(5);
            properties.setPreCountBuffer(10);
            byte[] xlsxBytes = createValidTariffExemptionXlsx(25);
            MockMultipartFile file = new MockMultipartFile(
                    "file", "too_many_rows.xlsx",
                    "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                    xlsxBytes);

            mockMvc.perform(multipart(API_UPLOAD_TARIFF)
                            .file(file)
                            .file(requiredCommonDataPart()))
                    .andExpect(status().isBadRequest())
                    .andExpect(jsonPath("$.success").value(false))
                    .andExpect(jsonPath("$.message").value(containsString("최대 행 수")));
        } finally {
            properties.setMaxRows(originalMaxRows);
            properties.setPreCountBuffer(originalBuffer);
        }
    }

    @Test
    void upload_fileTooLarge_returns413() throws Exception {
        byte[] largeBytes = new byte[11 * 1024 * 1024];
        MockMultipartFile file = new MockMultipartFile(
                "file", "large.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                largeBytes);

        mockMvc.perform(multipart(API_UPLOAD_TARIFF)
                        .file(file)
                        .file(requiredCommonDataPart()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("크기")));
    }

    @Test
    void upload_multipartParsingFailure_returns400() throws Exception {
        mockMvc.perform(post(API_UPLOAD_TARIFF)
                        .contentType(MediaType.parseMediaType("multipart/form-data; boundary=BrokenBoundary"))
                        .content("--BrokenBoundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"x.xlsx\"\r\n"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("멀티파트 요청 처리 중 오류가 발생했습니다."));
    }

    @Test
    void upload_wrongTemplate_returnsColumnMismatchError() throws Exception {
        byte[] xlsxBytes = createWrongTemplateTariffExemptionXlsx();
        MockMultipartFile file = new MockMultipartFile(
                "file", "wrong_template.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                xlsxBytes);

        mockMvc.perform(multipart(API_UPLOAD_TARIFF)
                        .file(file)
                        .file(requiredCommonDataPart()))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value(containsString("헤더가 일치하지 않습니다")));
    }

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

        for (int i = 0; i < dataRows; i++) {
            Row row = sheet.createRow(6 + i);
            row.createCell(0).setCellValue(i + 1);
            row.createCell(1).setCellValue(i + 1);
            if (makeInvalid) {
                row.createCell(2).setCellValue("");
                row.createCell(5).setCellValue("bad-hs-code");
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

            Row headerRow = sheet.createRow(3);
            headerRow.createCell(0).setCellValue("No");
            headerRow.createCell(1).setCellValue("WRONG_B");
            headerRow.createCell(2).setCellValue("WRONG_C");
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
        int start = jsonResponse.indexOf("\"downloadUrl\":\"") + "\"downloadUrl\":\"".length();
        int end = jsonResponse.indexOf("\"", start);
        return jsonResponse.substring(start, end);
    }

    private MockMultipartFile requiredCommonDataPart() {
        return new MockMultipartFile(
                "commonData",
                "commonData",
                MediaType.APPLICATION_JSON_VALUE,
                ("{" +
                        "\"comeYear\":\"2026\"," +
                        "\"comeSequence\":\"001\"," +
                        "\"uploadSequence\":\"U001\"," +
                        "\"equipCode\":\"EQ-01\"" +
                        "}").getBytes()
        );
    }
}
