package com.foo.excel.integration;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.foo.excel.ExcelImportApplication;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.imports.ImportTypeNames;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarEquip;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarEquipId;
import com.foo.excel.imports.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import com.foo.excel.imports.samples.aappcar.persistence.repository.AAppcarItemRepository;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.ss.util.CellRangeAddress;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.MethodOrderer.OrderAnnotation;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestMethodOrder;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

@SpringBootTest(classes = ExcelImportApplication.class)
@AutoConfigureMockMvc
@TestMethodOrder(OrderAnnotation.class)
class AAppcarAutomatedE2eIntegrationTest {

  private static final String API_UPLOAD = "/api/excel/upload/" + ImportTypeNames.AAPPCAR;
  private static final String PAGE_UPLOAD = "/upload/" + ImportTypeNames.AAPPCAR;

  private static final Path ARTIFACT_ROOT = Path.of("build", "e2e-aappcar");
  private static final Path TEST_FILES_DIR = ARTIFACT_ROOT.resolve("01_test-files");
  private static final Path EXECUTION_RESULTS_DIR = ARTIFACT_ROOT.resolve("02_execution-results");
  private static final Path ERROR_REPORTS_DIR = ARTIFACT_ROOT.resolve("03_error-reports");
  private static final Path SUMMARY_DIR = ARTIFACT_ROOT.resolve("04_summary");
  private static final List<ScenarioExecution> EXECUTIONS = new ArrayList<>();

  @Autowired private MockMvc mockMvc;
  @Autowired private ObjectMapper objectMapper;
  @Autowired private ExcelImportProperties properties;
  @Autowired private AAppcarItemRepository itemRepository;
  @Autowired private AAppcarEquipRepository equipRepository;

  @BeforeAll
  static void prepareArtifactDirectories() throws IOException {
    resetDirectory(ARTIFACT_ROOT);
    Files.createDirectories(TEST_FILES_DIR);
    Files.createDirectories(EXECUTION_RESULTS_DIR);
    Files.createDirectories(ERROR_REPORTS_DIR);
    Files.createDirectories(SUMMARY_DIR);
  }

  @AfterAll
  static void writeSummary() throws IOException {
    int total = 12;
    int completed = EXECUTIONS.size();
    long passCount = EXECUTIONS.stream().filter(ScenarioExecution::passed).count();
    long failCount = EXECUTIONS.stream().filter(execution -> !execution.passed()).count();
    List<String> generatedArtifacts = new ArrayList<>();
    EXECUTIONS.forEach(
        execution -> {
          generatedArtifacts.add("01_test-files/" + execution.inputFilename());
          generatedArtifacts.add("02_execution-results/" + execution.resultFilename());
          if (execution.systemResultFilename() != null) {
            generatedArtifacts.add("03_error-reports/" + execution.systemResultFilename());
          }
        });

    List<String> risks =
        EXECUTIONS.stream()
            .filter(execution -> !execution.passed())
            .map(execution -> execution.scenario().id() + ": " + execution.notes())
            .toList();

    List<String> manualFollowUps = new ArrayList<>();
    manualFollowUps.add("브라우저 실제 동작과 파일 선택 UX는 수동 E2E에서 추가 확인 필요");
    manualFollowUps.add("오류 리포트 파일의 서식/가독성은 사람이 직접 열어보며 최종 확인 권장");
    manualFollowUps.add("승인 장비 중복 차단은 현장 데이터셋 기준으로 재검증 권장");

    String summary =
        """
        # AAPPCAR 자동 E2E 요약

        - 전체 시나리오 수: %d
        - 수행 완료 수: %d
        - PASS 수: %d
        - FAIL 수: %d
        - 미수행 수: %d

        ## 자동으로 확인된 주요 위험요소

        %s

        ## 수동 테스터가 추가로 볼 만한 항목

        %s

        ## 생성 산출물 목록

        %s
        """
            .formatted(
                total,
                completed,
                passCount,
                failCount,
                Math.max(0, total - completed),
                toMarkdownList(risks.isEmpty() ? List.of("없음") : risks),
                toMarkdownList(manualFollowUps),
                toMarkdownList(generatedArtifacts));

    Files.writeString(
        SUMMARY_DIR.resolve("manual-e2e-test-summary.md"), summary, StandardCharsets.UTF_8);
  }

  @BeforeEach
  void resetState() throws IOException {
    itemRepository.deleteAll();
    equipRepository.deleteAll();
    Files.createDirectories(properties.getTempDirectoryPath());
    Files.createDirectories(properties.getTempDirectoryPath().resolve("errors"));
  }

  @Test
  @Order(1)
  void tc01_validUpload_successAndUiFlow() throws Exception {
    ScenarioCase scenario =
        new ScenarioCase("TC-01", "valid-upload", "정상 업로드 신규 생성", "성공");
    Map<String, String> metadata = metadataValues("1");
    byte[] inputBytes = createValidAAppcarItemXlsx(2);
    Path inputFile = writeInputFile(scenario, "xlsx", inputBytes);

    MvcResult pageResult = mockMvc.perform(get(PAGE_UPLOAD)).andReturn();
    assertThat(pageResult.getResponse().getStatus()).isEqualTo(200);
    assertThat(pageResult.getResponse().getContentAsString()).contains("관세면제 업로드");

    UploadResponse apiResponse =
        uploadViaApi(inputFile.getFileName().toString(), inputBytes, metadataJsonPart(metadata));

    assertThat(apiResponse.status()).isEqualTo(200);
    assertThat(apiResponse.body().get("success")).isEqualTo(true);
    assertThat(apiResponse.body().get("rowsProcessed")).isEqualTo(2);
    assertThat(apiResponse.body().get("message")).isEqualTo("데이터 업로드 완료");
    assertThat(apiResponse.body()).doesNotContainKey("downloadUrl");
    assertThat(itemRepository.count()).isEqualTo(2);
    AAppcarEquipId equipId = equipId(metadata);
    AAppcarEquip savedEquip = equipRepository.findById(equipId).orElseThrow();
    assertThat(savedEquip.getEquipMean()).isEqualTo(metadata.get("equipMean"));

    Map<String, String> pageMetadata = metadataValues("101");
    PageResponse successPage =
        uploadViaPage(
            "TC-01_valid-upload-page.xlsx",
            createValidAAppcarItemXlsx(1),
            pageMetadata);
    assertThat(successPage.status()).isEqualTo(200);
    assertThat(successPage.html()).contains("업로드 결과", "데이터 업로드 완료", "처리 행 수");

    recordScenario(
        scenario,
        inputFile.getFileName().toString(),
        metadata,
        apiResponse.status(),
        String.valueOf(apiResponse.body().get("message")),
        false,
        null,
        "품목 2건 저장, 설비 메타데이터 1건 신규 반영",
        "API 성공 및 GET/POST UI 렌더링 흐름 확인",
        true);
  }

  @Test
  @Order(2)
  void tc02_xlsUpload_rejected() throws Exception {
    ScenarioCase scenario =
        new ScenarioCase("TC-02", "invalid-extension", "파일 형식 오류 .xls", "파일 보안 검증 단계");
    Map<String, String> metadata = metadataValues("2");
    byte[] inputBytes = createValidAAppcarItemXls(2);
    Path inputFile = writeInputFile(scenario, "xls", inputBytes);

    UploadResponse response =
        uploadViaApi(
            inputFile.getFileName().toString(),
            inputBytes,
            "application/vnd.ms-excel",
            metadataJsonPart(metadata));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body().get("success")).isEqualTo(false);
    assertThat(String.valueOf(response.body().get("message"))).contains(".xlsx");
    assertThat(itemRepository.count()).isZero();

    recordScenario(
        scenario,
        inputFile.getFileName().toString(),
        metadata,
        response.status(),
        String.valueOf(response.body().get("message")),
        false,
        null,
        "DB 반영 없음",
        "파일명/확장자 검증에서 즉시 차단됨",
        true);
  }

  @Test
  @Order(3)
  void tc03_fakeXlsx_rejected() throws Exception {
    ScenarioCase scenario =
        new ScenarioCase("TC-03", "fake-xlsx", "파일 내용 위장", "파일 보안 검증 단계");
    Map<String, String> metadata = metadataValues("3");
    byte[] inputBytes = "not-a-real-xlsx".getBytes(StandardCharsets.UTF_8);
    Path inputFile = writeInputFile(scenario, "xlsx", inputBytes);

    UploadResponse response =
        uploadViaApi(
            inputFile.getFileName().toString(),
            inputBytes,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            metadataJsonPart(metadata));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body().get("success")).isEqualTo(false);
    assertThat(response.body().get("message")).isEqualTo("파일 보안 검증에 실패했습니다");
    assertThat(itemRepository.count()).isZero();

    recordScenario(
        scenario,
        inputFile.getFileName().toString(),
        metadata,
        response.status(),
        String.valueOf(response.body().get("message")),
        false,
        null,
        "DB 반영 없음",
        "매직 바이트 검증에서 차단됨",
        true);
  }

  @Test
  @Order(4)
  void tc04_missingMetadata_rejected() throws Exception {
    ScenarioCase scenario =
        new ScenarioCase("TC-04", "valid-file_missing-metadata", "필수 metadata 누락", "요청/metadata 검증 단계");
    byte[] inputBytes = createValidAAppcarItemXlsx(1);
    Path inputFile = writeInputFile(scenario, "xlsx", inputBytes);

    UploadResponse response = uploadViaApi(inputFile.getFileName().toString(), inputBytes, null);

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body().get("success")).isEqualTo(false);
    assertThat(String.valueOf(response.body().get("message"))).contains("metadata");
    assertThat(itemRepository.count()).isZero();

    recordScenario(
        scenario,
        inputFile.getFileName().toString(),
        Map.of(),
        response.status(),
        String.valueOf(response.body().get("message")),
        false,
        null,
        "DB 반영 없음",
        "metadata 파트 필수 검증에서 차단됨",
        true);
  }

  @Test
  @Order(5)
  void tc05_duplicateApprovedMetadata_blockedByPrecheckAndRenderedOnPage() throws Exception {
    ScenarioCase scenario =
        new ScenarioCase(
            "TC-05", "valid-file_duplicate-metadata", "승인 장비 메타데이터 중복", "import precheck 단계");
    Map<String, String> metadata = metadataValues("5");
    equipRepository.save(
        AAppcarEquip.builder()
            .id(equipId(metadata))
            .equipMean(metadata.get("equipMean"))
            .hsno(metadata.get("hsno"))
            .spec(metadata.get("spec"))
            .approvalYn("Y")
            .build());

    byte[] inputBytes = createValidAAppcarItemXlsx(2);
    Path inputFile = writeInputFile(scenario, "xlsx", inputBytes);

    UploadResponse apiResponse =
        uploadViaApi(inputFile.getFileName().toString(), inputBytes, metadataJsonPart(metadata));

    assertThat(apiResponse.status()).isEqualTo(400);
    assertThat(apiResponse.body().get("success")).isEqualTo(false);
    assertThat(String.valueOf(apiResponse.body().get("message"))).contains("승인된 장비");
    assertThat(apiResponse.body()).doesNotContainKey("downloadUrl");
    assertThat(itemRepository.count()).isZero();

    PageResponse pageResponse =
        uploadViaPage("TC-05_duplicate-page.xlsx", createValidAAppcarItemXlsx(1), metadata);
    assertThat(pageResponse.status()).isEqualTo(200);
    assertThat(pageResponse.html()).contains("오류 발생", "승인된 장비", "회사 ID");

    recordScenario(
        scenario,
        inputFile.getFileName().toString(),
        metadata,
        apiResponse.status(),
        String.valueOf(apiResponse.body().get("message")),
        false,
        null,
        "기존 승인 장비 유지, 신규 품목/설비 반영 없음",
        "파싱 전에 import precheck에서 차단되고 페이지 결과 렌더링도 확인됨",
        true);
  }

  @Test
  @Order(6)
  void tc06_wrongHeader_rejected() throws Exception {
    ScenarioCase scenario =
        new ScenarioCase("TC-06", "wrong-header", "헤더 불일치", "헤더/컬럼 해석 단계");
    Map<String, String> metadata = metadataValues("6");
    byte[] inputBytes =
        createWorkbook(
            workbook -> workbook.getSheetAt(0).getRow(3).getCell(2).setCellValue("WRONG_C"));
    Path inputFile = writeInputFile(scenario, "xlsx", inputBytes);

    UploadResponse response =
        uploadViaApi(inputFile.getFileName().toString(), inputBytes, metadataJsonPart(metadata));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body().get("success")).isEqualTo(false);
    assertThat(String.valueOf(response.body().get("message"))).contains("헤더가 일치하지 않습니다");
    assertThat(itemRepository.count()).isZero();

    recordScenario(
        scenario,
        inputFile.getFileName().toString(),
        metadata,
        response.status(),
        String.valueOf(response.body().get("message")),
        false,
        null,
        "DB 반영 없음",
        "헤더명 훼손으로 컬럼 해석 단계에서 실패",
        true);
  }

  @Test
  @Order(7)
  void tc07_missingRequiredColumn_rejected() throws Exception {
    ScenarioCase scenario =
        new ScenarioCase(
            "TC-07", "missing-required-column", "필수 컬럼 누락", "헤더/컬럼 해석 단계");
    Map<String, String> metadata = metadataValues("7");
    byte[] inputBytes =
        createWorkbook(
            workbook -> {
              Sheet sheet = workbook.getSheetAt(0);
              sheet.getRow(3).getCell(5).setCellValue("");
              sheet.getRow(3).getCell(6).setCellValue("");
            });
    Path inputFile = writeInputFile(scenario, "xlsx", inputBytes);

    UploadResponse response =
        uploadViaApi(inputFile.getFileName().toString(), inputBytes, metadataJsonPart(metadata));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body().get("success")).isEqualTo(false);
    assertThat(String.valueOf(response.body().get("message"))).contains("헤더");
    assertThat(itemRepository.count()).isZero();

    recordScenario(
        scenario,
        inputFile.getFileName().toString(),
        metadata,
        response.status(),
        String.valueOf(response.body().get("message")),
        false,
        null,
        "DB 반영 없음",
        "필수 HSK 컬럼 제거로 컬럼 해석 단계에서 실패",
        true);
  }

  @Test
  @Order(8)
  void tc08_missingRequiredCell_generatesErrorReportAndPageDownloadLink() throws Exception {
    ScenarioCase scenario =
        new ScenarioCase(
            "TC-08", "missing-required-cell", "행 필수값 누락", "행 검증 단계");
    Map<String, String> metadata = metadataValues("8");
    byte[] inputBytes =
        createWorkbook(workbook -> workbook.getSheetAt(0).getRow(6).getCell(2).setCellValue(""));
    Path inputFile = writeInputFile(scenario, "xlsx", inputBytes);

    UploadResponse apiResponse =
        uploadViaApi(inputFile.getFileName().toString(), inputBytes, metadataJsonPart(metadata));

    assertThat(apiResponse.status()).isEqualTo(400);
    assertThat(apiResponse.body().get("success")).isEqualTo(false);
    assertThat(String.valueOf(apiResponse.body().get("message"))).contains("오류가 발견되었습니다");
    String downloadUrl = String.valueOf(apiResponse.body().get("downloadUrl"));
    Path errorReport = downloadErrorReport(scenario, downloadUrl);
    assertErrorWorkbookContainsErrors(errorReport);

    PageResponse pageResponse =
        uploadViaPage("TC-08_missing-required-cell-page.xlsx", inputBytes, metadata);
    assertThat(pageResponse.status()).isEqualTo(200);
    assertThat(pageResponse.html()).contains("오류 리포트 다운로드");

    recordScenario(
        scenario,
        inputFile.getFileName().toString(),
        metadata,
        apiResponse.status(),
        String.valueOf(apiResponse.body().get("message")),
        true,
        errorReport.getFileName().toString(),
        "DB 반영 없음",
        "행 검증 실패 후 오류 리포트 생성 및 페이지 다운로드 링크 렌더링 확인",
        true);
  }

  @Test
  @Order(9)
  void tc09_invalidFormat_generatesErrorReport() throws Exception {
    ScenarioCase scenario =
        new ScenarioCase("TC-09", "invalid-format", "데이터 형식 오류", "행 검증 단계");
    Map<String, String> metadata = metadataValues("9");
    byte[] inputBytes =
        createWorkbook(
            workbook -> workbook.getSheetAt(0).getRow(6).getCell(5).setCellValue("bad-hs-code"));
    Path inputFile = writeInputFile(scenario, "xlsx", inputBytes);

    UploadResponse response =
        uploadViaApi(inputFile.getFileName().toString(), inputBytes, metadataJsonPart(metadata));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body().get("success")).isEqualTo(false);
    Path errorReport = downloadErrorReport(scenario, String.valueOf(response.body().get("downloadUrl")));
    assertErrorWorkbookContainsErrors(errorReport);

    recordScenario(
        scenario,
        inputFile.getFileName().toString(),
        metadata,
        response.status(),
        String.valueOf(response.body().get("message")),
        true,
        errorReport.getFileName().toString(),
        "DB 반영 없음",
        "HSK 형식 오류가 행 검증 단계에서 오류 리포트로 수집됨",
        true);
  }

  @Test
  @Order(10)
  void tc10_duplicateCompositeKey_generatesErrorReport() throws Exception {
    ScenarioCase scenario =
        new ScenarioCase(
            "TC-10", "duplicate-composite-key", "파일 내 중복 데이터", "행 검증 단계");
    Map<String, String> metadata = metadataValues("10");
    byte[] inputBytes =
        createWorkbook(
            workbook -> {
              Sheet sheet = workbook.getSheetAt(0);
              Row row7 = sheet.getRow(6);
              Row row8 = sheet.getRow(7);
              row8.getCell(2).setCellValue(row7.getCell(2).getStringCellValue());
              row8.getCell(3).setCellValue(row7.getCell(3).getStringCellValue());
              row8.getCell(5).setCellValue(row7.getCell(5).getStringCellValue());
            },
            2);
    Path inputFile = writeInputFile(scenario, "xlsx", inputBytes);

    UploadResponse response =
        uploadViaApi(inputFile.getFileName().toString(), inputBytes, metadataJsonPart(metadata));

    assertThat(response.status()).isEqualTo(400);
    assertThat(response.body().get("success")).isEqualTo(false);
    Path errorReport = downloadErrorReport(scenario, String.valueOf(response.body().get("downloadUrl")));
    assertErrorWorkbookContainsErrors(errorReport);

    recordScenario(
        scenario,
        inputFile.getFileName().toString(),
        metadata,
        response.status(),
        String.valueOf(response.body().get("message")),
        true,
        errorReport.getFileName().toString(),
        "DB 반영 없음",
        "복합 유일성 검증 실패가 오류 리포트로 반환됨",
        true);
  }

  @Test
  @Order(11)
  void tc11_errorReportDownload_succeedsAndContainsErrorsColumn() throws Exception {
    ScenarioCase scenario =
        new ScenarioCase(
            "TC-11", "reuse-error-report-source", "오류 리포트 다운로드 검증", "오류 리포트 다운로드");
    Map<String, String> metadata = metadataValues("11");
    byte[] inputBytes =
        createWorkbook(workbook -> workbook.getSheetAt(0).getRow(6).getCell(2).setCellValue(""));
    Path inputFile = writeInputFile(scenario, "xlsx", inputBytes);

    UploadResponse response =
        uploadViaApi(inputFile.getFileName().toString(), inputBytes, metadataJsonPart(metadata));

    assertThat(response.status()).isEqualTo(400);
    String downloadUrl = String.valueOf(response.body().get("downloadUrl"));
    Path errorReport = downloadErrorReport(scenario, downloadUrl);
    assertErrorWorkbookContainsErrors(errorReport);

    recordScenario(
        scenario,
        inputFile.getFileName().toString(),
        metadata,
        200,
        "오류 리포트 다운로드 성공",
        true,
        errorReport.getFileName().toString(),
        "오류 리포트 `.xlsx` 다운로드 및 `_ERRORS` 컬럼 확인",
        "업로드 실패 이후 다운로드 API가 정상 동작함",
        true);
  }

  @Test
  @Order(12)
  void tc12_tooManyRows_rejectedByPreCount() throws Exception {
    ScenarioCase scenario =
        new ScenarioCase("TC-12", "too-many-rows", "최대 행 수 초과", "최대 행 수 제한 단계");
    Map<String, String> metadata = metadataValues("12");
    byte[] inputBytes = createValidAAppcarItemXlsx(25);
    Path inputFile = writeInputFile(scenario, "xlsx", inputBytes);

    int originalMaxRows = properties.getMaxRows();
    int originalPreCountBuffer = properties.getPreCountBuffer();
    try {
      properties.setMaxRows(5);
      properties.setPreCountBuffer(10);

      UploadResponse response =
          uploadViaApi(inputFile.getFileName().toString(), inputBytes, metadataJsonPart(metadata));

      assertThat(response.status()).isEqualTo(400);
      assertThat(response.body().get("success")).isEqualTo(false);
      assertThat(String.valueOf(response.body().get("message"))).contains("최대 행 수");
      assertThat(itemRepository.count()).isZero();

      recordScenario(
          scenario,
          inputFile.getFileName().toString(),
          metadata,
          response.status(),
          String.valueOf(response.body().get("message")),
          false,
          null,
          "DB 반영 없음",
          "경량 행 수 사전 점검에서 차단됨",
          true);
    } finally {
      properties.setMaxRows(originalMaxRows);
      properties.setPreCountBuffer(originalPreCountBuffer);
    }
  }

  private void recordScenario(
      ScenarioCase scenario,
      String inputFilename,
      Map<String, String> metadata,
      int httpStatus,
      String actualMessage,
      boolean errorReportGenerated,
      String systemResultFilename,
      String dbResult,
      String notes,
      boolean passed)
      throws IOException {
    ScenarioExecution execution =
        new ScenarioExecution(
            scenario,
            inputFilename,
            metadata,
            httpStatus,
            actualMessage,
            errorReportGenerated,
            systemResultFilename,
            dbResult,
            notes,
            passed,
            scenario.id() + "_" + scenario.slug() + "_result.md",
            Instant.now());
    EXECUTIONS.add(execution);
    writeExecutionResult(execution);
  }

  private void writeExecutionResult(ScenarioExecution execution) throws IOException {
    String content =
        """
        시나리오 ID: %s
        시나리오명: %s
        실행일시: %s
        실행 환경: SpringBootTest + MockMvc + Apache POI
        입력 파일명: %s
        입력 metadata 값: %s
        실제 HTTP 상태: %d
        실제 화면 또는 API 메시지: %s
        오류 리포트 생성 여부: %s
        시스템 생성 결과 파일명: %s
        DB/데이터 확인 결과: %s
        기대 대비 판정: %s
        특이사항: %s
        """
            .formatted(
                execution.scenario().id(),
                execution.scenario().name(),
                DateTimeFormatter.ISO_INSTANT.format(execution.executedAt()),
                execution.inputFilename(),
                formatMetadata(execution.metadata()),
                execution.httpStatus(),
                execution.actualMessage(),
                execution.errorReportGenerated() ? "Y" : "N",
                execution.systemResultFilename() == null ? "-" : execution.systemResultFilename(),
                execution.dbResult(),
                execution.passed() ? "PASS" : "FAIL",
                execution.notes());
    Files.writeString(
        EXECUTION_RESULTS_DIR.resolve(execution.resultFilename()), content, StandardCharsets.UTF_8);
  }

  private UploadResponse uploadViaApi(
      String filename, byte[] fileBytes, MockMultipartFile metadataPart) throws Exception {
    return uploadViaApi(
        filename,
        fileBytes,
        "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
        metadataPart);
  }

  private UploadResponse uploadViaApi(
      String filename, byte[] fileBytes, String contentType, MockMultipartFile metadataPart)
      throws Exception {
    MockMultipartFile file = new MockMultipartFile("file", filename, contentType, fileBytes);
    var requestBuilder = multipart(API_UPLOAD).file(file);
    if (metadataPart != null) {
      requestBuilder.file(metadataPart);
    }
    MvcResult result = mockMvc.perform(requestBuilder).andReturn();
    Map<String, Object> body =
        objectMapper.readValue(
            result.getResponse().getContentAsByteArray(),
            new TypeReference<LinkedHashMap<String, Object>>() {});
    return new UploadResponse(result.getResponse().getStatus(), body);
  }

  private PageResponse uploadViaPage(String filename, byte[] fileBytes, Map<String, String> metadata)
      throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            filename,
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            fileBytes);
    MvcResult result =
        mockMvc
            .perform(
                multipart(PAGE_UPLOAD)
                    .file(file)
                    .param("comeYear", metadata.get("comeYear"))
                    .param("comeOrder", metadata.get("comeOrder"))
                    .param("uploadSeq", metadata.get("uploadSeq"))
                    .param("equipCode", metadata.get("equipCode"))
                    .param("equipMean", metadata.get("equipMean"))
                    .param("hsno", metadata.get("hsno"))
                    .param("spec", metadata.get("spec"))
                    .param("taxRate", metadata.get("taxRate"))
                    .param("filePath", metadata.getOrDefault("filePath", ""))
                    .param("approvalYn", metadata.getOrDefault("approvalYn", ""))
                    .param("approvalDate", metadata.getOrDefault("approvalDate", "")))
            .andReturn();
    return new PageResponse(result.getResponse().getStatus(), result.getResponse().getContentAsString());
  }

  private MockMultipartFile metadataJsonPart(Map<String, String> metadata) throws Exception {
    Map<String, Object> payload = new LinkedHashMap<>();
    metadata.forEach(
        (key, value) -> {
          if ("taxRate".equals(key)) {
            payload.put(key, Double.valueOf(value));
          } else {
            payload.put(key, value);
          }
        });
    return new MockMultipartFile(
        "metadata",
        "metadata",
        MediaType.APPLICATION_JSON_VALUE,
        objectMapper.writeValueAsBytes(payload));
  }

  private Path downloadErrorReport(ScenarioCase scenario, String downloadUrl) throws Exception {
    assertThat(downloadUrl).startsWith("/api/excel/download/");
    MvcResult result = mockMvc.perform(get(downloadUrl)).andReturn();
    assertThat(result.getResponse().getStatus()).isEqualTo(200);
    assertThat(result.getResponse().getContentType())
        .isEqualTo("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    Path output =
        ERROR_REPORTS_DIR.resolve(scenario.id() + "_" + scenario.slug() + "_error-report.xlsx");
    Files.write(output, result.getResponse().getContentAsByteArray());
    return output;
  }

  private void assertErrorWorkbookContainsErrors(Path errorReport) throws Exception {
    try (Workbook workbook = WorkbookFactory.create(new ByteArrayInputStream(Files.readAllBytes(errorReport)))) {
      Sheet sheet = workbook.getSheetAt(0);
      assertThat(sheet.getRow(3).getCell(17).getStringCellValue()).isEqualTo("_ERRORS");
    }
  }

  private Path writeInputFile(ScenarioCase scenario, String extension, byte[] bytes) throws IOException {
    String filename = scenario.id() + "_" + scenario.slug() + "_input." + extension;
    Path path = TEST_FILES_DIR.resolve(filename);
    Files.write(path, bytes);
    return path;
  }

  private Map<String, String> metadataValues(String uploadSeq) {
    Map<String, String> values = new LinkedHashMap<>();
    values.put("comeYear", "2026");
    values.put("comeOrder", "001");
    values.put("uploadSeq", uploadSeq);
    values.put("equipCode", "EQ-" + uploadSeq);
    values.put("equipMean", "설비" + uploadSeq);
    values.put("hsno", "8481802000");
    values.put("spec", "규격" + uploadSeq);
    values.put("taxRate", "8.50");
    return values;
  }

  private AAppcarEquipId equipId(Map<String, String> metadata) {
    return new AAppcarEquipId(
        "COMPANY01",
        "CUSTOM01",
        metadata.get("comeYear"),
        Integer.valueOf(metadata.get("comeOrder")),
        Integer.valueOf(metadata.get("uploadSeq")),
        metadata.get("equipCode"));
  }

  private byte[] createValidAAppcarItemXlsx(int dataRows) throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Sheet1");
      fillAAppcarItemSheet(sheet, dataRows);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private byte[] createValidAAppcarItemXls(int dataRows) throws IOException {
    try (HSSFWorkbook workbook = new HSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Sheet1");
      fillAAppcarItemSheet(sheet, dataRows);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private byte[] createWorkbook(WorkbookMutation mutation) throws IOException {
    return createWorkbook(mutation, 1);
  }

  private byte[] createWorkbook(WorkbookMutation mutation, int dataRows) throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Sheet1");
      fillAAppcarItemSheet(sheet, dataRows);
      mutation.apply(workbook);
      ByteArrayOutputStream output = new ByteArrayOutputStream();
      workbook.write(output);
      return output.toByteArray();
    }
  }

  private void fillAAppcarItemSheet(Sheet sheet, int dataRows) {
    createHeaderRows(sheet);
    for (int i = 0; i < dataRows; i++) {
      Row row = sheet.createRow(6 + i);
      row.createCell(0).setCellValue(i + 1);
      row.createCell(1).setCellValue(i + 1);
      row.createCell(2).setCellValue("Item" + (i + 1));
      row.createCell(3).setCellValue("Spec" + (i + 1));
      row.createCell(4).setCellValue("Model" + (i + 1));
      row.createCell(5).setCellValue("8481.80-200" + i);
      row.createCell(7).setCellValue(8.0);
      row.createCell(8).setCellValue(100.0);
      row.createCell(9).setCellValue(10);
      row.createCell(11).setCellValue(5);
      row.createCell(13).setCellValue(50000.0);
      row.createCell(14).setCellValue("통과");
      row.createCell(16).setCellValue(100);
    }
  }

  private void createHeaderRows(Sheet sheet) {
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
    row4.createCell(10).setCellValue("");
    row4.createCell(11).setCellValue("");
    row4.createCell(12).setCellValue("");
    row4.createCell(13).setCellValue("연간수입예상금액($)");
    row4.createCell(14).setCellValue("심의결과");
    row4.createCell(15).setCellValue("");
    row4.createCell(16).setCellValue("연간 예상소요량");

    Row row5 = sheet.createRow(4);
    row5.createCell(9).setCellValue("제조용");
    row5.createCell(10).setCellValue("");
    row5.createCell(11).setCellValue("수리용");
    row5.createCell(12).setCellValue("");

    Row row6 = sheet.createRow(5);
    row6.createCell(9).setCellValue("");
    row6.createCell(10).setCellValue("");
    row6.createCell(11).setCellValue("");
    row6.createCell(12).setCellValue("");

    sheet.addMergedRegion(new CellRangeAddress(3, 3, 9, 12));
    sheet.addMergedRegion(new CellRangeAddress(4, 4, 9, 10));
    sheet.addMergedRegion(new CellRangeAddress(4, 4, 11, 12));
    sheet.addMergedRegion(new CellRangeAddress(5, 5, 9, 10));
    sheet.addMergedRegion(new CellRangeAddress(5, 5, 11, 12));
  }

  private static String formatMetadata(Map<String, String> metadata) {
    if (metadata.isEmpty()) {
      return "-";
    }
    return metadata.entrySet().stream()
        .map(entry -> entry.getKey() + "=" + entry.getValue())
        .reduce((left, right) -> left + ", " + right)
        .orElse("-");
  }

  private static String toMarkdownList(List<String> values) {
    return values.stream()
        .map(value -> "- " + value)
        .reduce((left, right) -> left + "\n" + right)
        .orElse("- 없음");
  }

  private static void resetDirectory(Path directory) throws IOException {
    if (Files.exists(directory)) {
      try (var walk = Files.walk(directory)) {
        walk.sorted(Comparator.reverseOrder()).forEach(AAppcarAutomatedE2eIntegrationTest::deleteQuietly);
      }
    }
  }

  private static void deleteQuietly(Path path) {
    try {
      Files.deleteIfExists(path);
    } catch (IOException e) {
      throw new IllegalStateException("Failed to reset artifact directory: " + path, e);
    }
  }

  @FunctionalInterface
  private interface WorkbookMutation {
    void apply(Workbook workbook) throws IOException;
  }

  private record ScenarioCase(String id, String slug, String name, String expectedStage) {}

  private record ScenarioExecution(
      ScenarioCase scenario,
      String inputFilename,
      Map<String, String> metadata,
      int httpStatus,
      String actualMessage,
      boolean errorReportGenerated,
      String systemResultFilename,
      String dbResult,
      String notes,
      boolean passed,
      String resultFilename,
      Instant executedAt) {}

  private record UploadResponse(int status, Map<String, Object> body) {}

  private record PageResponse(int status, String html) {}
}
