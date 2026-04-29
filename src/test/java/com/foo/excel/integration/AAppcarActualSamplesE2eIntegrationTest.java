package com.foo.excel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foo.excel.ExcelImportApplication;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.imports.ImportTypeNames;
import com.foo.excel.imports.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import com.foo.excel.imports.samples.aappcar.persistence.repository.AAppcarItemRepository;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Stream;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = ExcelImportApplication.class)
@AutoConfigureMockMvc
class AAppcarActualSamplesE2eIntegrationTest {

  private static final String API_UPLOAD = "/api/excel/upload/" + ImportTypeNames.AAPPCAR;
  private static final Path ACTUAL_SAMPLES_DIR = Path.of("docs", "actual-samples");
  private static final Path MANUAL_TEST_FILES_DIR =
      Path.of("docs", "aappcar-manual-e2e", "01_test-files");
  private static final Pattern TC_ID_PATTERN = Pattern.compile("^(TC-\\d{2})");

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ExcelImportProperties properties;
  @Autowired private AAppcarItemRepository itemRepository;
  @Autowired private AAppcarEquipRepository equipRepository;

  @BeforeEach
  void resetState() throws IOException {
    itemRepository.deleteAll();
    equipRepository.deleteAll();
    Files.createDirectories(properties.getTempDirectoryPath().resolve("errors"));
  }

  @Test
  void survey_tcFiles_skipsManualSetAlreadyCoveredByGeneratedE2e() throws IOException {
    assertThat(tcIdsIn(MANUAL_TEST_FILES_DIR))
        .containsExactlyInAnyOrder(
            "TC-01", "TC-02", "TC-03", "TC-04", "TC-05", "TC-06",
            "TC-07", "TC-08", "TC-09", "TC-10", "TC-11", "TC-12");
    assertThat(tcIdsIn(ACTUAL_SAMPLES_DIR))
        .contains(
            "TC-13", "TC-14", "TC-15", "TC-16", "TC-17", "TC-18", "TC-19", "TC-20",
            "TC-21");
  }

  @Test
  void tc13_actualSample_goodsDescriptionTooLong_generatesErrorReport() throws Exception {
    UploadResponse response = uploadFixture("TC-13_", "13");

    assertValidationFailureWithErrorReport(response, "100자 이내");
    assertThat(itemRepository.count()).isZero();
  }

  @Test
  void tc14_actualSample_specialCharactersAcceptedByCurrentContract() throws Exception {
    UploadResponse response = uploadFixture("TC-14_", "14");

    assertSuccess(response, 2);
  }

  @Test
  void tc15_actualSample_negativeUnitPrice_generatesErrorReport() throws Exception {
    UploadResponse response = uploadFixture("TC-15_", "15");

    assertValidationFailureWithErrorReport(response, "단가는 0 이상");
    assertThat(itemRepository.count()).isZero();
  }

  @Test
  void tc16_actualSample_optionalApprovalHeaderVariantAccepted() throws Exception {
    UploadResponse response = uploadFixture("TC-16_", "16");

    assertSuccess(response, 2);
  }

  @Test
  void tc17_actualSample_nonIgnoredGoodsHeaderWhitespaceRejected() throws Exception {
    UploadResponse response = uploadFixture("TC-17_", "17");

    assertHeaderFailure(response);
    assertThat(String.valueOf(response.body().get("message"))).contains("C열");
    assertThat(itemRepository.count()).isZero();
  }

  @Test
  void tc18_actualSample_startsWithAndContainsHeaderVariantsAccepted() throws Exception {
    UploadResponse response = uploadFixture("TC-18_", "18");

    assertSuccess(response, 2);
  }

  @Test
  void tc19_actualSample_containsHeaderVariantAccepted() throws Exception {
    UploadResponse response = uploadFixture("TC-19_", "19");

    assertSuccess(response, 2);
  }

  @Test
  void tc20_actualSample_shiftedHeadersRejected() throws Exception {
    UploadResponse response = uploadFixture("TC-20_", "20");

    assertHeaderFailure(response);
    assertThat(String.valueOf(response.body().get("message"))).contains("B열", "C열");
    assertThat(itemRepository.count()).isZero();
  }

  @Test
  void tc21_actualSample_shiftedHeadersRejected() throws Exception {
    UploadResponse response = uploadFixture("TC-21_", "21");

    assertHeaderFailure(response);
    assertThat(String.valueOf(response.body().get("message"))).contains("B열", "C열");
    assertThat(itemRepository.count()).isZero();
  }

  private UploadResponse uploadFixture(String filenamePrefix, String uploadSeq) throws Exception {
    Path fixture = fixtureStartingWith(filenamePrefix);
    byte[] fileBytes = Files.readAllBytes(fixture);
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            fixture.getFileName().toString(),
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            fileBytes);

    MvcResult result =
        mockMvc
            .perform(multipart(API_UPLOAD).file(file).file(metadataJsonPart(uploadSeq)))
            .andReturn();
    Map<String, Object> body =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<LinkedHashMap<String, Object>>() {});
    return new UploadResponse(result.getResponse().getStatus(), body);
  }

  private void assertSuccess(UploadResponse response, int expectedRows) {
    assertThat(response.status()).isEqualTo(200);
    assertThat(response.body().get("success")).isEqualTo(true);
    assertThat(response.body().get("rowsProcessed")).isEqualTo(expectedRows);
    assertThat(response.body().get("message")).isEqualTo("데이터 업로드 완료");
    assertThat(response.body()).doesNotContainKey("downloadUrl");
    assertThat(itemRepository.count()).isEqualTo(expectedRows);
    assertThat(equipRepository.count()).isEqualTo(1);
  }

  private void assertHeaderFailure(UploadResponse response) {
    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body().get("success")).isEqualTo(false);
    assertThat(String.valueOf(response.body().get("message"))).contains("헤더가 일치하지 않습니다");
    assertThat(response.body()).doesNotContainKey("downloadUrl");
  }

  private void assertValidationFailureWithErrorReport(UploadResponse response, String messagePart)
      throws Exception {
    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body().get("success")).isEqualTo(false);
    assertThat(String.valueOf(response.body().get("message"))).contains("오류가 발견되었습니다");
    String downloadUrl = String.valueOf(response.body().get("downloadUrl"));
    assertErrorWorkbookContains(downloadUrl, messagePart);
  }

  private void assertErrorWorkbookContains(String downloadUrl, String expectedText) throws Exception {
    assertThat(downloadUrl).startsWith("/api/excel/download/");
    MvcResult result = mockMvc.perform(get(downloadUrl)).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    try (Workbook workbook =
        WorkbookFactory.create(new ByteArrayInputStream(result.getResponse().getContentAsByteArray()))) {
      Sheet sheet = workbook.getSheetAt(0);
      assertThat(sheet.getRow(3).getCell(17).getStringCellValue()).isEqualTo("_ERRORS");
      assertThat(workbookText(workbook)).contains(expectedText);
    }
  }

  private String workbookText(Workbook workbook) {
    StringBuilder text = new StringBuilder();
    workbook.forEach(
        sheet ->
            sheet.forEach(
                row ->
                    row.forEach(cell -> text.append(cell.toString()).append('\n'))));
    return text.toString();
  }

  private MockMultipartFile metadataJsonPart(String uploadSeq) throws Exception {
    Map<String, Object> metadata = new LinkedHashMap<>();
    metadata.put("comeYear", "2026");
    metadata.put("comeOrder", "001");
    metadata.put("uploadSeq", uploadSeq);
    metadata.put("equipCode", "EQ-" + uploadSeq);
    metadata.put("equipMean", "설비" + uploadSeq);
    metadata.put("hsno", "8481802000");
    metadata.put("spec", "규격" + uploadSeq);
    metadata.put("taxRate", new BigDecimal("8.50"));
    return new MockMultipartFile(
        "metadata",
        "metadata",
        MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsBytes(metadata));
  }

  private Path fixtureStartingWith(String prefix) throws IOException {
    try (Stream<Path> files = Files.list(ACTUAL_SAMPLES_DIR)) {
      return files
          .filter(path -> path.getFileName().toString().startsWith(prefix))
          .findFirst()
          .orElseThrow(() -> new IllegalStateException("Missing fixture prefix: " + prefix));
    }
  }

  private Set<String> tcIdsIn(Path directory) throws IOException {
    try (Stream<Path> files = Files.list(directory)) {
      return files
          .map(path -> path.getFileName().toString())
          .map(TC_ID_PATTERN::matcher)
          .filter(Matcher::find)
          .map(matcher -> matcher.group(1))
          .collect(java.util.stream.Collectors.toSet());
    }
  }

  private record UploadResponse(int status, Map<String, Object> body) {}
}
