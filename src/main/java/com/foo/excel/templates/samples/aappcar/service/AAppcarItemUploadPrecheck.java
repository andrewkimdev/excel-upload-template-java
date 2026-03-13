package com.foo.excel.templates.samples.aappcar.service;

import com.foo.excel.service.contract.UploadPrecheck;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetaData;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AAppcarItemUploadPrecheck implements UploadPrecheck<AAppcarItemMetaData> {

  private static final String APPROVED_EQUIP_EXISTS_MESSAGE = "이미 승인된 장비가 존재합니다.";

  private final AAppcarEquipRepository equipRepository;
  private final AAppcarItemKeyFactory keyFactory;

  @Override
  public Optional<String> check(AAppcarItemMetaData metaData) {
    if (equipRepository.existsById(keyFactory.buildEquipId(metaData))) {
      return Optional.of(APPROVED_EQUIP_EXISTS_MESSAGE);
    }
    return Optional.empty();
  }
}
