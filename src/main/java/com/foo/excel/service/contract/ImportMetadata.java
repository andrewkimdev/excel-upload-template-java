package com.foo.excel.service.contract;

/** 템플릿별 공통 데이터(metadata) 계약 인터페이스. */
public interface ImportMetadata {

  /** 서버가 저장된 업로드 파일 경로를 주입한다. */
  void assignFilePath(String filePath);
}
