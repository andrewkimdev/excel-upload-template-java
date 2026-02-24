package com.foo.excel.controller;

import com.foo.excel.service.pipeline.ExcelImportOrchestrator.ImportResult;
import com.foo.excel.service.pipeline.ExcelUploadRequestService;
import com.foo.excel.templates.TemplateTypes;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.Map;

@RestController
@RequiredArgsConstructor
public class TariffExemptionUploadApiController {

    private final ExcelUploadRequestService uploadRequestService;

    @PostMapping("/api/excel/upload/" + TemplateTypes.TARIFF_EXEMPTION)
    public ResponseEntity<Map<String, Object>> upload(
            @RequestPart("file") MultipartFile file,
            @RequestPart(value = "commonData", required = false) String commonDataJson) throws IOException {
        ImportResult result = uploadRequestService.upload(file, TemplateTypes.TARIFF_EXEMPTION, commonDataJson);
        Map<String, Object> response = uploadRequestService.toApiResponse(result);
        if (result.success()) {
            return ResponseEntity.ok(response);
        }
        return ResponseEntity.badRequest().body(response);
    }
}
