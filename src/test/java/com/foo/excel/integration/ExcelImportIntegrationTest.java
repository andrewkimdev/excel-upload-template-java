package com.foo.excel.integration;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.greaterThan;
import static org.hamcrest.Matchers.startsWith;
import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.model;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.view;

import com.foo.excel.ExcelImportApplication;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarEquip;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarEquipId;
import com.foo.excel.imports.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import com.foo.excel.imports.samples.aappcar.persistence.repository.AAppcarItemRepository;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.imports.ImportTypeNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.file.Files;
import java.time.LocalDate;
import java.util.Map;
import java.util.Optional;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Cell;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
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

@SpringBootTest(classes = ExcelImportApplication.class)
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_EACH_TEST_METHOD)
class ExcelImportIntegrationTest {

  private static final String API_UPLOAD_TARIFF =
      "/api/excel/upload/" + ImportTypeNames.AAPPCAR;
  private static final String PAGE_UPLOAD_TARIFF = "/upload/" + ImportTypeNames.AAPPCAR;

  @Autowired private MockMvc mockMvc;

  @Autowired private ExcelImportProperties properties;
  @Autowired private AAppcarItemRepository itemRepository;
  @Autowired private AAppcarEquipRepository equipRepository;
  @Autowired private ObjectMapper objectMapper;

  @BeforeEach
  void setUp() throws IOException {
    Files.createDirectories(properties.getTempDirectoryPath().resolve("errors"));
  }

  @Test
  void upload_validXlsx_returnsSuccess_blackBoxLiteralRoute() throws Exception {
    byte[] xlsxBytes = createValidAAppcarItemXlsx(2);
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    mockMvc
        .perform(
            multipart("/api/excel/upload/aappcar")
                .file(file)
                .file(requiredMetadataPart()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.rowsProcessed").value(2))
        .andExpect(jsonPath("$.message").value("데이터 업로드 완료"));

    assertThat(itemRepository.count()).isEqualTo(2);
    Optional<AAppcarEquip> savedEquip = equipRepository.findById(requiredMetadataEquipId());
    assertThat(savedEquip).isPresent();
    assertThat(savedEquip.orElseThrow().getEquipMean()).isEqualTo("설비A");
    assertThat(savedEquip.orElseThrow().getHsno()).isEqualTo("8481802000");
    assertThat(savedEquip.orElseThrow().getSpec()).isEqualTo("규격A");
    assertThat(savedEquip.orElseThrow().getApprovalYn()).isEqualTo("N");
    assertThat(savedEquip.orElseThrow().getApprovalDate()).isNull();
    assertThat(savedEquip.orElseThrow().getFilePath())
        .isEqualTo(
            properties.getTempDirectoryPath().resolve("CUSTOM01").resolve("tariff.xlsx").toString());
  }

  @Test
  void upload_whenApprovedEquipAlreadyExists_returnsMetadataConflictWithoutErrorReport()
      throws Exception {
    byte[] xlsxBytes = createValidAAppcarItemXlsx(2);
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file).file(requiredMetadataPart("Y")))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.rowsCreated").value(2))
        .andExpect(jsonPath("$.rowsUpdated").value(0));

    mockMvc
        .perform(
            multipart(API_UPLOAD_TARIFF)
                .file(
                    new MockMultipartFile(
                        "file",
                        "tariff.xlsx",
                        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                        xlsxBytes))
                .file(requiredMetadataPart()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.rowsProcessed").value(0))
        .andExpect(jsonPath("$.message").value(containsString("승인된 장비")))
        .andExpect(jsonPath("$.downloadUrl").doesNotExist())
        .andExpect(jsonPath("$.metadataConflict.type").value("METADATA_DUPLICATE_APPROVED_EQUIP"))
        .andExpect(jsonPath("$.metadataConflict.fields[0].fieldName").value("companyId"))
        .andExpect(jsonPath("$.metadataConflict.fields[0].value").value("COMPANY01"))
        .andExpect(jsonPath("$.metadataConflict.fields[5].fieldName").value("equipCode"))
        .andExpect(jsonPath("$.metadataConflict.fields[5].value").value("EQ-01"));

    assertThat(itemRepository.count()).isEqualTo(2);
    Optional<AAppcarEquip> savedEquip = equipRepository.findById(requiredMetadataEquipId());
    assertThat(savedEquip).isPresent();
    assertThat(savedEquip.orElseThrow().getEquipMean()).isEqualTo("설비A");
    assertThat(savedEquip.orElseThrow().getApprovalYn()).isEqualTo("Y");
  }

  @Test
  void upload_whenOnlyNonApprovedEquipExists_doesNotBlockMetadataPrecheck() throws Exception {
    equipRepository.save(
        AAppcarEquip.builder().id(requiredMetadataEquipId()).approvalYn("N").build());

    byte[] xlsxBytes = createValidAAppcarItemXlsx(2);
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file).file(requiredMetadataPart()))
        .andExpect(status().isOk())
        .andExpect(jsonPath("$.success").value(true))
        .andExpect(jsonPath("$.message").value("데이터 업로드 완료"));
  }

  @Test
  void upload_invalidData_returnsErrorWithDownloadUrl() throws Exception {
    byte[] xlsxBytes = createInvalidAAppcarItemXlsx();
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff_invalid.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file).file(requiredMetadataPart()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.errorRows").value(greaterThan(0)))
        .andExpect(jsonPath("$.downloadUrl").value(startsWith("/api/excel/download/")));
  }

  @Test
  void upload_invalidDataWithoutHeaderMerges_downloadedErrorReportRebuildsImportMerges()
      throws Exception {
    byte[] xlsxBytes = createInvalidAAppcarItemXlsxWithoutHeaderMerges();
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff_invalid_unmerged.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    MvcResult uploadResult =
        mockMvc
            .perform(multipart(API_UPLOAD_TARIFF).file(file).file(requiredMetadataPart()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.downloadUrl").exists())
            .andReturn();

    String downloadUrl = extractDownloadUrl(uploadResult.getResponse().getContentAsString());
    byte[] reportBytes =
        mockMvc
            .perform(get(downloadUrl))
            .andExpect(status().isOk())
            .andReturn()
            .getResponse()
            .getContentAsByteArray();

    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(reportBytes))) {
      Sheet sheet = workbook.getSheetAt(0);
      assertThat(sheet.getMergedRegions())
          .anySatisfy(region -> assertThat(region.formatAsString()).isEqualTo("J4:M4"))
          .anySatisfy(region -> assertThat(region.formatAsString()).isEqualTo("F7:G7"))
          .anySatisfy(region -> assertThat(region.formatAsString()).isEqualTo("O7:P7"));
      Cell errorHeaderCell = sheet.getRow(3).getCell(17);
      assertThat(errorHeaderCell.getStringCellValue()).isEqualTo("_ERRORS");
    }
  }

  @Test
  void download_existingErrorFile_returnsXlsxContentType() throws Exception {
    byte[] xlsxBytes = createInvalidAAppcarItemXlsx();
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff_invalid.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    MvcResult uploadResult =
        mockMvc
            .perform(multipart(API_UPLOAD_TARIFF).file(file).file(requiredMetadataPart()))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.downloadUrl").exists())
            .andReturn();

    String responseBody = uploadResult.getResponse().getContentAsString();
    String downloadUrl = extractDownloadUrl(responseBody);

    mockMvc
        .perform(get(downloadUrl))
        .andExpect(status().isOk())
        .andExpect(
            content()
                .contentType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet"));
  }

  @Test
  void download_nonexistentFile_returns404() throws Exception {
    mockMvc
        .perform(get("/api/excel/download/00000000-0000-0000-0000-000000000000"))
        .andExpect(status().isNotFound());
  }

  @Test
  void download_malformedFileId_returns400() throws Exception {
    mockMvc.perform(get("/api/excel/download/not-a-uuid")).andExpect(status().isBadRequest());
  }

  @Test
  void upload_xlsFile_rejected() throws Exception {
    byte[] xlsBytes = createValidAAppcarItemXls(2);
    MockMultipartFile file =
        new MockMultipartFile("file", "tariff.xls", "application/vnd.ms-excel", xlsBytes);

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file).file(requiredMetadataPart()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value(containsString(".xlsx")));
  }

  @Test
  void upload_missingMetadata_rejected() throws Exception {
    byte[] xlsxBytes = createValidAAppcarItemXlsx(1);
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message", containsString("metadata")));
  }

  @Test
  void upload_metadataUnknownField_rejected() throws Exception {
    byte[] xlsxBytes = createValidAAppcarItemXlsx(1);
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);
    MockMultipartFile invalidMetadata =
        new MockMultipartFile(
            "metadata",
            "metadata",
            MediaType.APPLICATION_JSON_VALUE,
            ("{"
                    + "\"comeYear\":\"2026\","
                    + "\"comeOrder\":\"001\","
                    + "\"uploadSeq\":\"U001\","
                    + "\"equipCode\":\"EQ-01\","
                    + "\"unknown\":\"x\""
                    + "}")
                .getBytes());

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file).file(invalidMetadata))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message", containsString("metadata")));
  }

  @Test
  void upload_metadataScalarCoercion_rejected() throws Exception {
    byte[] xlsxBytes = createValidAAppcarItemXlsx(1);
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);
    MockMultipartFile invalidMetadata =
        new MockMultipartFile(
            "metadata",
            "metadata",
            MediaType.APPLICATION_JSON_VALUE,
            ("{"
                    + "\"comeYear\":2026,"
                    + "\"comeOrder\":\"001\","
                    + "\"uploadSeq\":\"U001\","
                    + "\"equipCode\":\"EQ-01\""
                    + "}")
                .getBytes());

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file).file(invalidMetadata))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message", containsString("metadata")));
  }

  @Test
  void upload_metadataBooleanCoercion_rejected() throws Exception {
    byte[] xlsxBytes = createValidAAppcarItemXlsx(1);
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);
    MockMultipartFile invalidMetadata =
        new MockMultipartFile(
            "metadata",
            "metadata",
            MediaType.APPLICATION_JSON_VALUE,
            ("{"
                    + "\"comeYear\":\"2026\","
                    + "\"comeOrder\":true,"
                    + "\"uploadSeq\":\"U001\","
                    + "\"equipCode\":\"EQ-01\""
                    + "}")
                .getBytes());

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file).file(invalidMetadata))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message", containsString("metadata")));
  }

  @Test
  void removedGenericUploadRoute_returns404() throws Exception {
    byte[] xlsxBytes = createValidAAppcarItemXlsx(1);
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "test.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    mockMvc
        .perform(
            multipart("/api/excel/upload/unknown-type").file(file).file(requiredMetadataPart()))
        .andExpect(status().isNotFound());
  }

  @Test
  void removedLegacyDownloadRoute_returns404() throws Exception {
    mockMvc
        .perform(get("/api/excel/template/" + ImportTypeNames.AAPPCAR))
        .andExpect(status().isNotFound());
  }

  @Test
  void page_route_rendersImportSpecificForm() throws Exception {
    mockMvc
        .perform(get(PAGE_UPLOAD_TARIFF))
        .andExpect(status().isOk())
        .andExpect(view().name("upload-aappcar"))
        .andExpect(content().string(containsString("관세면제 업로드")));
  }

  @Test
  void page_formSubmission_success_rendersResultModel() throws Exception {
    byte[] xlsxBytes = createValidAAppcarItemXlsx(1);
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    mockMvc
        .perform(
            multipart(PAGE_UPLOAD_TARIFF)
                .file(file)
                .param("comeYear", "2026")
                .param("comeOrder", "001")
                .param("uploadSeq", "U001")
                .param("equipCode", "EQ-01")
                .param("equipMean", "설비A")
                .param("hsno", "8481802000")
                .param("spec", "규격A")
                .param("taxRate", "8.50")
                .param("approvalYn", "Y")
                .param("approvalDate", LocalDate.now().toString()))
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
      byte[] xlsxBytes = createValidAAppcarItemXlsx(25);
      MockMultipartFile file =
          new MockMultipartFile(
              "file",
              "too_many_rows.xlsx",
              "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
              xlsxBytes);

      mockMvc
          .perform(multipart(API_UPLOAD_TARIFF).file(file).file(requiredMetadataPart()))
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
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "large.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            largeBytes);

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file).file(requiredMetadataPart()))
        .andExpect(status().isPayloadTooLarge())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value(containsString("크기")));
  }

  @Test
  void upload_multipartParsingFailure_returns400() throws Exception {
    mockMvc
        .perform(
            post(API_UPLOAD_TARIFF)
                .contentType(
                    MediaType.parseMediaType("multipart/form-data; boundary=BrokenBoundary"))
                .content(
                    "--BrokenBoundary\r\nContent-Disposition: form-data; name=\"file\"; filename=\"x.xlsx\"\r\n"))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value("멀티파트 요청 처리 중 오류가 발생했습니다."));
  }

  @Test
  void upload_wrongImportLayout_returnsColumnMismatchError() throws Exception {
    byte[] xlsxBytes = createWrongImportLayoutAAppcarItemXlsx();
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "wrong_import_layout.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            xlsxBytes);

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file).file(requiredMetadataPart()))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.success").value(false))
        .andExpect(jsonPath("$.message").value(containsString("헤더가 일치하지 않습니다")));
  }

  private byte[] createValidAAppcarItemXlsx(int dataRows) throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");
      fillAAppcarItemSheet(sheet, dataRows, false);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      wb.write(bos);
      return bos.toByteArray();
    }
  }

  private byte[] createInvalidAAppcarItemXlsx() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");
      fillAAppcarItemSheet(sheet, 1, true);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      wb.write(bos);
      return bos.toByteArray();
    }
  }

  private byte[] createInvalidAAppcarItemXlsxWithoutHeaderMerges() throws IOException {
    try (XSSFWorkbook wb = new XSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");
      fillAAppcarItemSheet(sheet, 1, true, false);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      wb.write(bos);
      return bos.toByteArray();
    }
  }

  private byte[] createValidAAppcarItemXls(int dataRows) throws IOException {
    try (HSSFWorkbook wb = new HSSFWorkbook()) {
      Sheet sheet = wb.createSheet("Sheet1");
      fillAAppcarItemSheet(sheet, dataRows, false);
      ByteArrayOutputStream bos = new ByteArrayOutputStream();
      wb.write(bos);
      return bos.toByteArray();
    }
  }

  private void fillAAppcarItemSheet(Sheet sheet, int dataRows, boolean makeInvalid) {
    fillAAppcarItemSheet(sheet, dataRows, makeInvalid, true);
  }

  private void fillAAppcarItemSheet(
      Sheet sheet, int dataRows, boolean makeInvalid, boolean includeHeaderMerges) {
    createTariffHeaderRows(sheet, includeHeaderMerges);

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

  private void createTariffHeaderRows(Sheet sheet) {
    createTariffHeaderRows(sheet, true);
  }

  private void createTariffHeaderRows(Sheet sheet, boolean includeHeaderMerges) {
    Row row4 = sheet.createRow(3);
    row4.createCell(0).setCellValue("No");
    row4.createCell(1).setCellValue("순번");
    row4.createCell(2).setCellValue("물품명");
    row4.createCell(3).setCellValue("규격1)");
    row4.createCell(4).setCellValue("모델명1)");
    row4.createCell(5).setCellValue("HSK No");
    row4.createCell(6).setCellValue("");
    row4.createCell(7).setCellValue("관세율");
    row4.createCell(8).setCellValue("단가($)");
    row4.createCell(9).setCellValue("소요량");
    row4.createCell(10).setCellValue(includeHeaderMerges ? "" : "소요량");
    row4.createCell(11).setCellValue(includeHeaderMerges ? "" : "소요량");
    row4.createCell(12).setCellValue(includeHeaderMerges ? "" : "소요량");
    row4.createCell(13).setCellValue("연간수입예상금액($)");
    row4.createCell(14).setCellValue("심의결과");
    row4.createCell(15).setCellValue("");
    row4.createCell(16).setCellValue("연간 예상소요량");

    Row row5 = sheet.createRow(4);
    row5.createCell(9).setCellValue("제조용");
    row5.createCell(10).setCellValue(includeHeaderMerges ? "" : "제조용");
    row5.createCell(11).setCellValue("수리용");
    row5.createCell(12).setCellValue(includeHeaderMerges ? "" : "수리용");

    Row row6 = sheet.createRow(5);
    row6.createCell(9).setCellValue("");
    row6.createCell(10).setCellValue("");
    row6.createCell(11).setCellValue("");
    row6.createCell(12).setCellValue("");

    if (includeHeaderMerges) {
      sheet.addMergedRegion(new CellRangeAddress(3, 3, 9, 12));
      sheet.addMergedRegion(new CellRangeAddress(4, 4, 9, 10));
      sheet.addMergedRegion(new CellRangeAddress(4, 4, 11, 12));
      sheet.addMergedRegion(new CellRangeAddress(5, 5, 9, 10));
      sheet.addMergedRegion(new CellRangeAddress(5, 5, 11, 12));
    }
  }

  private byte[] createWrongImportLayoutAAppcarItemXlsx() throws IOException {
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

  private String extractDownloadUrl(String jsonResponse) throws Exception {
    Map<?, ?> response = objectMapper.readValue(jsonResponse, Map.class);
    return String.valueOf(response.get("downloadUrl"));
  }

  private AAppcarEquipId requiredMetadataEquipId() {
    return new AAppcarEquipId("COMPANY01", "CUSTOM01", "2026", 1, 1, "EQ-01");
  }

  private MockMultipartFile requiredMetadataPart() {
    return requiredMetadataPart(null);
  }

  private MockMultipartFile requiredMetadataPart(String approvalYn) {
    String approvalField =
        approvalYn == null ? "" : ",\"approvalYn\":\"" + approvalYn + "\"";
    return new MockMultipartFile(
        "metadata",
        "metadata",
        MediaType.APPLICATION_JSON_VALUE,
        ("{"
                + "\"comeYear\":\"2026\","
                + "\"comeOrder\":\"001\","
                + "\"uploadSeq\":\"1\","
                + "\"equipCode\":\"EQ-01\","
                + "\"equipMean\":\"설비A\","
                + "\"hsno\":\"8481802000\","
                + "\"spec\":\"규격A\","
                + "\"taxRate\":8.50"
                + approvalField
                + "}")
            .getBytes());
  }
}
