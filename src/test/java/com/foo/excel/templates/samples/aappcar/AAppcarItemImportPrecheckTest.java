package com.foo.excel.templates.samples.aappcar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

import com.foo.excel.service.contract.ImportPrecheckFailure;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetadata;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import com.foo.excel.templates.samples.aappcar.service.AAppcarItemKeyFactory;
import com.foo.excel.templates.samples.aappcar.service.AAppcarItemImportPrecheck;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AAppcarItemImportPrecheckTest {

  @Mock private AAppcarEquipRepository equipRepository;

  private AAppcarItemImportPrecheck precheck;

  @BeforeEach
  void setUp() {
    precheck = new AAppcarItemImportPrecheck(equipRepository, new AAppcarItemKeyFactory());
  }

  @Test
  void check_whenApprovedEquipExists_returnsStructuredBlockingFailure() {
    when(equipRepository.existsByIdAndApprovalYn(any(), eq("Y"))).thenReturn(true);

    Optional<ImportPrecheckFailure> result = precheck.check(createMetadata());

    assertThat(result).isPresent();
    assertThat(result.orElseThrow().message()).contains("승인된 장비");
    assertThat(result.orElseThrow().uploadMetadataConflict()).isNotNull();
    assertThat(result.orElseThrow().uploadMetadataConflict().fields())
        .extracting(field -> field.fieldName() + "=" + field.value())
        .contains(
            "companyId=COMPANY01",
            "customId=CUSTOM01",
            "comeYear=2026",
            "comeOrder=1",
            "uploadSeq=1",
            "equipCode=EQ-01");
  }

  @Test
  void check_whenNoApprovedEquipExists_returnsEmpty() {
    when(equipRepository.existsByIdAndApprovalYn(any(), eq("Y"))).thenReturn(false);

    Optional<ImportPrecheckFailure> result = precheck.check(createMetadata());

    assertThat(result).isEmpty();
  }

  private AAppcarItemMetadata createMetadata() {
    AAppcarItemMetadata metadata = new AAppcarItemMetadata();
    metadata.setComeYear("2026");
    metadata.setComeOrder("1");
    metadata.setUploadSeq("1");
    metadata.setEquipCode("EQ-01");
    metadata.setCompanyId("COMPANY01");
    metadata.setCustomId("CUSTOM01");
    return metadata;
  }
}
