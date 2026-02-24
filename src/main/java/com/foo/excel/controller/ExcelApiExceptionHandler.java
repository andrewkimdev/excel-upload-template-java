package com.foo.excel.controller;

import java.util.Map;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.multipart.MaxUploadSizeExceededException;
import org.springframework.web.multipart.MultipartException;
import org.springframework.web.multipart.support.MissingServletRequestPartException;

@Slf4j
@RestControllerAdvice(basePackageClasses = TariffExemptionUploadApiController.class)
public class ExcelApiExceptionHandler {

  @ExceptionHandler(IllegalArgumentException.class)
  public ResponseEntity<Map<String, Object>> handleIllegalArgument(IllegalArgumentException e) {
    log.warn("업로드 요청 오류: {}", e.getMessage());
    return ResponseEntity.badRequest().body(errorBody(e.getMessage()));
  }

  @ExceptionHandler(SecurityException.class)
  public ResponseEntity<Map<String, Object>> handleSecurity(SecurityException e) {
    log.warn("업로드 보안 검증 실패: {}", e.getMessage());
    return ResponseEntity.badRequest().body(errorBody("파일 보안 검증에 실패했습니다"));
  }

  @ExceptionHandler(MaxUploadSizeExceededException.class)
  public ResponseEntity<Map<String, Object>> handleMaxUploadSize(MaxUploadSizeExceededException e) {
    log.warn("업로드 파일 크기 초과: {}", e.getMessage());
    return ResponseEntity.status(HttpStatus.PAYLOAD_TOO_LARGE)
        .body(errorBody("업로드 파일 크기가 제한을 초과했습니다."));
  }

  @ExceptionHandler({MultipartException.class, MissingServletRequestPartException.class})
  public ResponseEntity<Map<String, Object>> handleMultipartException(Exception e) {
    log.warn("멀티파트 요청 오류: {}", e.getMessage());
    return ResponseEntity.badRequest().body(errorBody("멀티파트 요청 처리 중 오류가 발생했습니다."));
  }

  @ExceptionHandler(Exception.class)
  public ResponseEntity<Map<String, Object>> handleUnexpected(Exception e) {
    log.error("업로드 처리 실패", e);
    return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
        .body(errorBody("파일 처리 중 오류가 발생했습니다. 관리자에게 문의하세요."));
  }

  private Map<String, Object> errorBody(String message) {
    return Map.of("success", false, "message", message);
  }
}
