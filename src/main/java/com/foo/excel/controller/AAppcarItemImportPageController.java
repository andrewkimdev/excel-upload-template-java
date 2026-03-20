package com.foo.excel.controller;

import com.foo.excel.service.pipeline.ExcelImportOrchestrator.ImportResult;
import com.foo.excel.service.pipeline.ExcelImportRequestService;
import com.foo.excel.templates.ImportTypes;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetadata;
import com.foo.excel.templates.samples.aappcar.mapper.AAppcarItemMetadataFormMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
@Controller
@RequiredArgsConstructor
public class AAppcarItemImportPageController {

  private static final String DEFAULT_COMPANY_ID = "COMPANY01";
  private static final String DEFAULT_CUSTOM_ID = "CUSTOM01";

  private final ExcelImportRequestService importRequestService;
  private final AAppcarItemMetadataFormMapper metadataFormMapper;

  @GetMapping("/upload/" + ImportTypes.AAPPCAR)
  public String importForm() {
    return "upload-aappcar";
  }

  @PostMapping("/upload/" + ImportTypes.AAPPCAR)
  public String importExcel(
      @RequestParam("comeYear") String comeYear,
      @RequestParam("comeOrder") String comeOrder,
      @RequestParam("uploadSeq") String uploadSeq,
      @RequestParam("equipCode") String equipCode,
      @RequestParam("equipMean") String equipMean,
      @RequestParam("hsno") String hsno,
      @RequestParam("spec") String spec,
      @RequestParam("taxRate") String taxRate,
      @RequestParam(value = "filePath", required = false) String filePath,
      @RequestParam(value = "approvalYn", required = false) String approvalYn,
      @RequestParam(value = "approvalDate", required = false) String approvalDate,
      @RequestParam("file") MultipartFile file,
      Model model) {
    try {
      AAppcarItemMetadata metadata =
          metadataFormMapper.toMetadata(
              comeYear,
              comeOrder,
              uploadSeq,
              equipCode,
              equipMean,
              hsno,
              spec,
              taxRate,
              filePath,
              approvalYn,
              approvalDate);
      metadata.setCompanyId(DEFAULT_COMPANY_ID);
      metadata.setCustomId(DEFAULT_CUSTOM_ID);
      ImportResult result = importRequestService.upload(file, ImportTypes.AAPPCAR, metadata);
      model.addAttribute("result", result);
    } catch (IllegalArgumentException e) {
      log.warn("업로드 요청 오류: {}", e.getMessage());
      model.addAttribute(
          "result", ImportResult.builder().success(false).message(e.getMessage()).build());
    } catch (SecurityException e) {
      log.warn("업로드 보안 검증 실패: {}", e.getMessage());
      model.addAttribute(
          "result", ImportResult.builder().success(false).message("파일 보안 검증에 실패했습니다").build());
    } catch (MaxUploadSizeExceededException e) {
      log.warn("업로드 파일 크기 초과: {}", e.getMessage());
      model.addAttribute(
          "result",
          ImportResult.builder().success(false).message("업로드 파일 크기가 제한을 초과했습니다.").build());
    } catch (Exception e) {
      log.error("import 처리 실패", e);
      model.addAttribute(
          "result",
          ImportResult.builder()
              .success(false)
              .message("파일 처리 중 오류가 발생했습니다. 관리자에게 문의하세요.")
              .build());
    }
    return "result";
  }
}
