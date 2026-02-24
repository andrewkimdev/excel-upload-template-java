package com.foo.excel.service.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.service.contract.CommonData;
import com.foo.excel.service.pipeline.ExcelImportOrchestrator.ImportResult;
import jakarta.validation.ConstraintViolation;
import jakarta.validation.Validator;
import java.io.IOException;
import java.util.LinkedHashMap;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
public class ExcelUploadRequestService {

  private final ExcelImportOrchestrator orchestrator;
  private final ExcelImportProperties properties;
  private final ObjectMapper objectMapper;
  private final Validator validator;

  public ImportResult upload(MultipartFile file, String templateType, String commonDataJson)
      throws IOException {
    checkFileSize(file);
    Class<? extends CommonData> commonDataClass = orchestrator.getCommonDataClass(templateType);
    CommonData commonData = parseAndValidateCommonData(commonDataJson, commonDataClass);
    return orchestrator.processUpload(file, templateType, commonData);
  }

  public ImportResult upload(MultipartFile file, String templateType, CommonData commonData)
      throws IOException {
    checkFileSize(file);
    validateCommonData(commonData);
    return orchestrator.processUpload(file, templateType, commonData);
  }

  public Map<String, Object> toApiResponse(ImportResult result) {
    Map<String, Object> response = new LinkedHashMap<>();
    response.put("success", result.success());
    response.put("rowsProcessed", result.rowsProcessed());
    response.put("message", result.message());

    if (result.success()) {
      response.put("rowsCreated", result.rowsCreated());
      response.put("rowsUpdated", result.rowsUpdated());
      return response;
    }

    response.put("errorRows", result.errorRows());
    response.put("errorCount", result.errorCount());
    if (result.downloadUrl() != null) {
      response.put("downloadUrl", result.downloadUrl());
    }
    return response;
  }

  private void checkFileSize(MultipartFile file) {
    long maxBytes = (long) properties.getMaxFileSizeMb() * 1024 * 1024;
    if (file.getSize() > maxBytes) {
      throw new MaxUploadSizeExceededException(maxBytes);
    }
  }

  private CommonData parseAndValidateCommonData(
      String commonDataJson, Class<? extends CommonData> commonDataClass) {
    if (commonDataJson == null || commonDataJson.isBlank()) {
      throw new IllegalArgumentException("commonData 파트는 필수입니다.");
    }

    try {
      ObjectMapper strictMapper =
          objectMapper.copy().enable(DeserializationFeature.FAIL_ON_UNKNOWN_PROPERTIES);
      strictMapper.setConfig(
          strictMapper.getDeserializationConfig().without(MapperFeature.ALLOW_COERCION_OF_SCALARS));
      strictMapper
          .coercionConfigFor(LogicalType.Textual)
          .setCoercion(CoercionInputShape.Integer, CoercionAction.Fail)
          .setCoercion(CoercionInputShape.Float, CoercionAction.Fail)
          .setCoercion(CoercionInputShape.Boolean, CoercionAction.Fail);

      CommonData commonData = strictMapper.readValue(commonDataJson, commonDataClass);
      validateCommonData(commonData);
      return commonData;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("commonData 형식이 올바르지 않습니다.");
    }
  }

  private void validateCommonData(CommonData commonData) {
    var violations = validator.validate(commonData);
    if (!violations.isEmpty()) {
      ConstraintViolation<?> violation = violations.iterator().next();
      throw new IllegalArgumentException(violation.getMessage());
    }
  }
}
