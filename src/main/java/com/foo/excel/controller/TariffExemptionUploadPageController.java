package com.foo.excel.controller;

import com.foo.excel.service.pipeline.ExcelImportOrchestrator.ImportResult;
import com.foo.excel.service.pipeline.ExcelUploadRequestService;
import com.foo.excel.templates.TemplateTypes;
import com.foo.excel.templates.samples.tariffexemption.dto.TariffExemptionCommonData;
import com.foo.excel.templates.samples.tariffexemption.mapper.TariffExemptionCommonDataFormMapper;
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
public class TariffExemptionUploadPageController {

  private final ExcelUploadRequestService uploadRequestService;
  private final TariffExemptionCommonDataFormMapper commonDataFormMapper;

  @GetMapping("/upload/" + TemplateTypes.TARIFF_EXEMPTION)
  public String uploadForm() {
    return "upload-tariff-exemption";
  }

  @PostMapping("/upload/" + TemplateTypes.TARIFF_EXEMPTION)
  public String upload(
      @RequestParam("comeYear") String comeYear,
      @RequestParam("comeSequence") String comeSequence,
      @RequestParam("uploadSequence") String uploadSequence,
      @RequestParam("equipCode") String equipCode,
      @RequestParam("file") MultipartFile file,
      Model model) {
    try {
      TariffExemptionCommonData commonData =
          commonDataFormMapper.toCommonData(comeYear, comeSequence, uploadSequence, equipCode);
      ImportResult result =
          uploadRequestService.upload(file, TemplateTypes.TARIFF_EXEMPTION, commonData);
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
