package com.foo.excel.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import com.foo.excel.service.pipeline.ExcelImportOrchestrator.ImportResult;
import com.foo.excel.service.pipeline.ExcelUploadRequestService;
import com.foo.excel.templates.TemplateTypes;
import java.io.IOException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequiredArgsConstructor
public class AAppcarItemUploadApiController {

  private static final String DEFAULT_COMPANY_ID = "COMPANY01";
  private static final String DEFAULT_CUSTOM_ID = "CUSTOM01";

  private final ExcelUploadRequestService uploadRequestService;
  private final ObjectMapper objectMapper;

  @PostMapping("/api/excel/upload/" + TemplateTypes.AAPPCAR)
  public ResponseEntity<Map<String, Object>> upload(
      @RequestPart("file") MultipartFile file,
      @RequestPart(value = "metaData", required = false) String metaDataJson)
      throws IOException {
    String enrichedMetaDataJson = withDefaultCompanyAndCustom(metaDataJson);
    ImportResult result =
        uploadRequestService.upload(file, TemplateTypes.AAPPCAR, enrichedMetaDataJson);
    Map<String, Object> response = uploadRequestService.toApiResponse(result);
    if (result.success()) {
      return ResponseEntity.ok(response);
    }
    return ResponseEntity.badRequest().body(response);
  }

  private String withDefaultCompanyAndCustom(String metaDataJson) {
    if (metaDataJson == null || metaDataJson.isBlank()) {
      return metaDataJson;
    }

    try {
      ObjectNode root = (ObjectNode) objectMapper.readTree(metaDataJson);
      root.put("companyId", DEFAULT_COMPANY_ID);
      root.put("customId", DEFAULT_CUSTOM_ID);
      return objectMapper.writeValueAsString(root);
    } catch (Exception e) {
      // 서비스의 엄격 파싱이 기존 검증 메시지를 반환할 수 있도록 원본 페이로드를 유지한다.
      return metaDataJson;
    }
  }
}
