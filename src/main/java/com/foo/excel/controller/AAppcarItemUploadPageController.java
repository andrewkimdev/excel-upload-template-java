package com.foo.excel.controller;

import com.foo.excel.service.pipeline.ExcelImportOrchestrator.ImportResult;
import com.foo.excel.service.pipeline.ExcelUploadRequestService;
import com.foo.excel.templates.TemplateTypes;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemCommonData;
import com.foo.excel.templates.samples.aappcar.mapper.AAppcarItemCommonDataFormMapper;
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
public class AAppcarItemUploadPageController {

  private static final String DEFAULT_COMPANY_ID = "COMPANY01";
  private static final String DEFAULT_CUSTOM_ID = "CUSTOM01";

  private final ExcelUploadRequestService uploadRequestService;
  private final AAppcarItemCommonDataFormMapper commonDataFormMapper;

  @GetMapping("/upload/" + TemplateTypes.AAPPCAR)
  public String uploadForm() {
    return "upload-aappcar";
  }

  @PostMapping("/upload/" + TemplateTypes.AAPPCAR)
  public String upload(
      @RequestParam("comeYear") String comeYear,
      @RequestParam("comeOrder") String comeOrder,
      @RequestParam("uploadSeq") String uploadSeq,
      @RequestParam("equipCode") String equipCode,
      @RequestParam("file") MultipartFile file,
      Model model) {
    try {
      AAppcarItemCommonData commonData =
          commonDataFormMapper.toCommonData(comeYear, comeOrder, uploadSeq, equipCode);
      commonData.setCompanyId(DEFAULT_COMPANY_ID);
      commonData.setCustomId(DEFAULT_CUSTOM_ID);
      ImportResult result =
          uploadRequestService.upload(file, TemplateTypes.AAPPCAR, commonData);
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
      log.error("업로드 처리 실패", e);
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
