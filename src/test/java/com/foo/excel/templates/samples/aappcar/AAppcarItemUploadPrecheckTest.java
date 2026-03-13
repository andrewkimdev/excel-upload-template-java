package com.foo.excel.templates.samples.aappcar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetaData;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import com.foo.excel.templates.samples.aappcar.service.AAppcarItemKeyFactory;
import com.foo.excel.templates.samples.aappcar.service.AAppcarItemUploadPrecheck;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AAppcarItemUploadPrecheckTest {

  @Mock private AAppcarEquipRepository equipRepository;

  private AAppcarItemUploadPrecheck precheck;

  @BeforeEach
  void setUp() {
    precheck = new AAppcarItemUploadPrecheck(equipRepository, new AAppcarItemKeyFactory());
  }

  @Test
  void check_whenApprovedEquipExists_returnsBlockingMessage() {
    when(equipRepository.existsById(any())).thenReturn(true);

    Optional<String> result = precheck.check(createMetaData());

    assertThat(result).contains("이미 승인된 장비가 존재합니다.");
  }

  @Test
  void check_whenNoApprovedEquipExists_returnsEmpty() {
    when(equipRepository.existsById(any())).thenReturn(false);

    Optional<String> result = precheck.check(createMetaData());

    assertThat(result).isEmpty();
  }

  private AAppcarItemMetaData createMetaData() {
    AAppcarItemMetaData metaData = new AAppcarItemMetaData();
    metaData.setComeYear("2026");
    metaData.setComeOrder("1");
    metaData.setUploadSeq("1");
    metaData.setEquipCode("EQ-01");
    metaData.setCompanyId("COMPANY01");
    metaData.setCustomId("CUSTOM01");
    return metaData;
  }
}
