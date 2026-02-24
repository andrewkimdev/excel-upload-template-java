package com.foo.excel.plan;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.foo.excel.ExcelUploadApplication;
import com.foo.excel.service.contract.CommonData;
import com.foo.excel.service.contract.PersistenceHandler;
import com.foo.excel.service.contract.TemplateDefinition;
import com.foo.excel.service.pipeline.ExcelImportOrchestrator;
import com.foo.excel.templates.TemplateTypes;
import com.foo.excel.templates.samples.tariffexemption.persistence.entity.TariffExemption;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Arrays;
import java.util.List;
import java.util.stream.Collectors;
import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Row;
import org.apache.poi.ss.usermodel.Sheet;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.ApplicationContext;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest(classes = ExcelUploadApplication.class)
@AutoConfigureMockMvc
class TariffUploadPlanContractTest {

  private static final String API_UPLOAD_TARIFF =
      "/api/excel/upload/" + TemplateTypes.TARIFF_EXEMPTION;

  @Autowired private MockMvc mockMvc;

  @Autowired private ApplicationContext applicationContext;

  @Test
  void tariffApiUpload_requiresCommonDataPart() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            createValidTariffExemptionXlsx());

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("commonData")));
  }

  @Test
  void tariffApiUpload_rejectsUnknownCommonDataField() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file",
            "tariff.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            createValidTariffExemptionXlsx());

    MockMultipartFile commonData =
        new MockMultipartFile(
            "commonData",
            "commonData",
            MediaType.APPLICATION_JSON_VALUE,
            ("{"
                    + "\"comeYear\":\"2026\","
                    + "\"comeSequence\":\"001\","
                    + "\"uploadSequence\":\"U001\","
                    + "\"equipCode\":\"EQ-01\","
                    + "\"unknownField\":\"X\""
                    + "}")
                .getBytes());

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file).file(commonData))
        .andExpect(status().isBadRequest());
  }

  @Test
  void tariffApiUpload_rejectsXlsFile() throws Exception {
    MockMultipartFile file =
        new MockMultipartFile(
            "file", "tariff.xls", "application/vnd.ms-excel", createValidTariffExemptionXls());

    MockMultipartFile commonData =
        new MockMultipartFile(
            "commonData",
            "commonData",
            MediaType.APPLICATION_JSON_VALUE,
            requiredCommonDataJson().getBytes());

    mockMvc
        .perform(multipart(API_UPLOAD_TARIFF).file(file).file(commonData))
        .andExpect(status().isBadRequest())
        .andExpect(jsonPath("$.message", containsString("xlsx")));
  }

  @Test
  void orchestratorContract_includesCommonDataParameter() {
    boolean hasNewSignature =
        Arrays.stream(ExcelImportOrchestrator.class.getDeclaredMethods())
            .filter(method -> method.getName().equals("processUpload"))
            .anyMatch(
                method ->
                    method.getParameterCount() == 3
                        && method.getParameterTypes()[2].equals(CommonData.class));

    assertTrue(
        hasNewSignature,
        "ExcelImportOrchestrator.processUpload(file, templateType, commonData) 시그니처가 필요합니다.");
  }

  @Test
  void persistenceHandlerContract_requiresSourceRowsAndCommonData() {
    Method[] methods = PersistenceHandler.class.getDeclaredMethods();
    boolean hasNewSaveAllSignature =
        Arrays.stream(methods)
            .filter(method -> method.getName().equals("saveAll"))
            .anyMatch(
                method ->
                    method.getParameterCount() == 3
                        && List.class.isAssignableFrom(method.getParameterTypes()[0])
                        && List.class.isAssignableFrom(method.getParameterTypes()[1])
                        && CommonData.class.isAssignableFrom(method.getParameterTypes()[2]));

    assertTrue(
        hasNewSaveAllSignature,
        "PersistenceHandler.saveAll(rows, sourceRowNumbers, commonData) 시그니처가 필요합니다.");
  }

  @Test
  void templateDefinitionContract_includesCommonDataClassField() {
    List<String> fieldNames =
        Arrays.stream(TemplateDefinition.class.getDeclaredFields())
            .map(Field::getName)
            .collect(Collectors.toList());

    assertTrue(
        fieldNames.contains("commonDataClass"),
        "TemplateDefinition에는 commonDataClass 필드가 포함되어야 합니다.");
  }

  @Test
  void tariffModule_hasTwoRepositoryBeans_forEntityAAndEntityBTransactionalPersistence() {
    long tariffRepositoryBeanCount =
        Arrays.stream(applicationContext.getBeanDefinitionNames())
            .filter(name -> name.toLowerCase().contains("tariff"))
            .filter(name -> name.toLowerCase().contains("repository"))
            .count();

    assertTrue(
        tariffRepositoryBeanCount >= 2,
        "Expected at least two tariff repository beans for A/B transactional save");
  }

  @Test
  void tariffEntity_includesAuditColumns_withServiceDefaults() {
    List<String> expectedFields = List.of("approvedYn", "createdAt", "createdBy");
    List<String> actualFields =
        Arrays.stream(TariffExemption.class.getDeclaredFields()).map(Field::getName).toList();

    assertTrue(
        actualFields.containsAll(expectedFields),
        "TariffExemption must include approvedYn, createdAt, createdBy");
  }

  private String requiredCommonDataJson() {
    return "{"
        + "\"comeYear\":\"2026\","
        + "\"comeSequence\":\"001\","
        + "\"uploadSequence\":\"U001\","
        + "\"equipCode\":\"EQ-01\""
        + "}";
  }

  private byte[] createValidTariffExemptionXlsx() throws IOException {
    try (XSSFWorkbook workbook = new XSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Sheet1");

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

      Row row = sheet.createRow(6);
      row.createCell(0).setCellValue(1);
      row.createCell(1).setCellValue(1);
      row.createCell(2).setCellValue("Item1");
      row.createCell(3).setCellValue("Spec1");
      row.createCell(4).setCellValue("Model1");
      row.createCell(5).setCellValue("8481.80-2000");
      row.createCell(7).setCellValue(8.0);
      row.createCell(8).setCellValue(100.0);
      row.createCell(9).setCellValue(10);
      row.createCell(11).setCellValue(5);
      row.createCell(13).setCellValue(1000.0);
      row.createCell(14).setCellValue("적합");
      row.createCell(16).setCellValue(100);

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      return outputStream.toByteArray();
    }
  }

  private byte[] createValidTariffExemptionXls() throws IOException {
    try (HSSFWorkbook workbook = new HSSFWorkbook()) {
      Sheet sheet = workbook.createSheet("Sheet1");

      Row headerRow = sheet.createRow(3);
      headerRow.createCell(0).setCellValue("No");
      headerRow.createCell(1).setCellValue("순번");
      headerRow.createCell(2).setCellValue("물품명");
      headerRow.createCell(3).setCellValue("규격1)");
      headerRow.createCell(4).setCellValue("모델명1)");
      headerRow.createCell(5).setCellValue("HSK No");
      headerRow.createCell(7).setCellValue("관세율");
      headerRow.createCell(8).setCellValue("단가($)");
      headerRow.createCell(9).setCellValue("제조용");
      headerRow.createCell(11).setCellValue("수리용");
      headerRow.createCell(13).setCellValue("연간수입예상금액($)");
      headerRow.createCell(14).setCellValue("심의결과");
      headerRow.createCell(16).setCellValue("연간 예상소요량");

      Row row = sheet.createRow(6);
      row.createCell(0).setCellValue(1);
      row.createCell(1).setCellValue(1);
      row.createCell(2).setCellValue("Item1");
      row.createCell(3).setCellValue("Spec1");
      row.createCell(4).setCellValue("Model1");
      row.createCell(5).setCellValue("8481.80-2000");
      row.createCell(7).setCellValue(8.0);
      row.createCell(8).setCellValue(100.0);
      row.createCell(9).setCellValue(10);
      row.createCell(11).setCellValue(5);
      row.createCell(13).setCellValue(1000.0);
      row.createCell(14).setCellValue("적합");
      row.createCell(16).setCellValue(100);

      ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
      workbook.write(outputStream);
      return outputStream.toByteArray();
    }
  }
}
