package com.foo.excel.controller;

import com.foo.excel.service.pipeline.ExcelUploadRequestService;
import com.foo.excel.templates.TemplateTypes;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.multipart.MaxUploadSizeExceededException;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(TariffExemptionUploadApiController.class)
@Import(ExcelApiExceptionHandler.class)
class ExcelApiExceptionHandlerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockBean
    private ExcelUploadRequestService uploadRequestService;

    @Test
    void unexpectedException_hidesInternalDetails() throws Exception {
        when(uploadRequestService.upload(any(), anyString(), anyString()))
                .thenThrow(new RuntimeException("internal /tmp/secret details"));

        mockMvc.perform(multipart("/api/excel/upload/" + TemplateTypes.TARIFF_EXEMPTION)
                        .file(filePart())
                        .file(commonDataPart()))
                .andExpect(status().isInternalServerError())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("파일 처리 중 오류가 발생했습니다. 관리자에게 문의하세요."))
                .andExpect(jsonPath("$.message", not(containsString("internal"))))
                .andExpect(jsonPath("$.message", not(containsString("/tmp"))));
    }

    @Test
    void maxUploadSize_mapsTo413() throws Exception {
        when(uploadRequestService.upload(any(), anyString(), anyString()))
                .thenThrow(new MaxUploadSizeExceededException(1024));

        mockMvc.perform(multipart("/api/excel/upload/" + TemplateTypes.TARIFF_EXEMPTION)
                        .file(filePart())
                        .file(commonDataPart()))
                .andExpect(status().isPayloadTooLarge())
                .andExpect(jsonPath("$.success").value(false))
                .andExpect(jsonPath("$.message").value("업로드 파일 크기가 제한을 초과했습니다."));
    }

    private MockMultipartFile filePart() {
        return new MockMultipartFile(
                "file",
                "a.xlsx",
                "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
                "a".getBytes()
        );
    }

    private MockMultipartFile commonDataPart() {
        return new MockMultipartFile(
                "commonData",
                "commonData",
                MediaType.APPLICATION_JSON_VALUE,
                ("{" +
                        "\"comeYear\":\"2026\"," +
                        "\"comeSequence\":\"001\"," +
                        "\"uploadSequence\":\"U001\"," +
                        "\"equipCode\":\"EQ-01\"" +
                        "}").getBytes()
        );
    }
}
