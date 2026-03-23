package com.foo.excel.service.contract;

/**
 * 본격적인 파싱 전에 업로드를 중단해야 하는 사유를 표현한다.
 *
 * @param message 사용자에게 반환할 실패 메시지
 * @param metadataConflict 메타데이터 충돌 정보
 */
public record ImportPrecheckFailure(String message, MetadataConflict metadataConflict) {}
