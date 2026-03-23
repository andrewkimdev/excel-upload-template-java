package com.foo.excel.service.pipeline;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.DeserializationFeature;
import com.fasterxml.jackson.databind.MapperFeature;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.cfg.CoercionAction;
import com.fasterxml.jackson.databind.cfg.CoercionInputShape;
import com.fasterxml.jackson.databind.type.LogicalType;
import com.foo.excel.config.ExcelImportProperties;
import com.foo.excel.service.contract.ImportMetadata;
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
public class ExcelImportRequestService {

  private final ExcelImportOrchestrator orchestrator;
  private final ExcelImportProperties properties;
  private final ObjectMapper objectMapper;
  private final Validator validator;

  public ImportResult upload(MultipartFile file, String importType, String metadataJson)
      throws IOException {
    checkFileSize(file);
    Class<? extends ImportMetadata> metadataClass = orchestrator.getMetadataClass(importType);
    ImportMetadata metadata = parseAndValidateMetadata(metadataJson, metadataClass);
    return orchestrator.processImport(file, importType, metadata);
  }

  public ImportResult upload(MultipartFile file, String importType, ImportMetadata metadata)
      throws IOException {
    checkFileSize(file);
    validateMetadata(metadata);
    return orchestrator.processImport(file, importType, metadata);
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
    if (result.metadataConflict() != null) {
      response.put("metadataConflict", result.metadataConflict());
    }
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

  private ImportMetadata parseAndValidateMetadata(
      String metadataJson, Class<? extends ImportMetadata> metadataClass) {
    if (metadataJson == null || metadataJson.isBlank()) {
      throw new IllegalArgumentException("metadata 파트는 필수입니다.");
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

      ImportMetadata metadata = strictMapper.readValue(metadataJson, metadataClass);
      validateMetadata(metadata);
      return metadata;
    } catch (JsonProcessingException e) {
      throw new IllegalArgumentException("metadata 형식이 올바르지 않습니다.");
    }
  }

  private void validateMetadata(ImportMetadata metadata) {
    var violations = validator.validate(metadata);
    if (!violations.isEmpty()) {
      ConstraintViolation<?> violation = violations.iterator().next();
      throw new IllegalArgumentException(violation.getMessage());
    }
  }
}
