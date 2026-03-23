package com.foo.excel.imports.samples.aappcar.service;

import com.foo.excel.service.contract.ImportPrecheck;
import com.foo.excel.service.contract.ImportPrecheckFailure;
import com.foo.excel.service.contract.MetadataConflict;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemImportMetadata;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarEquipId;
import com.foo.excel.imports.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AAppcarItemImportPrecheck implements ImportPrecheck<AAppcarItemImportMetadata> {

  private static final String APPROVED_EQUIP_EXISTS_MESSAGE =
      "입력한 메타데이터 조합과 일치하는 승인된 장비가 이미 존재합니다.";
  private static final String CONFLICT_TYPE = "METADATA_DUPLICATE_APPROVED_EQUIP";
  private static final String CONFLICT_DESCRIPTION =
      "아래 메타데이터 값 조합에 대해 이미 승인된 장비가 존재합니다. 엑셀 파일이 아니라 메타데이터 값을 수정하세요.";

  private final AAppcarEquipRepository equipRepository;
  private final AAppcarItemKeyFactory keyFactory;

  @Override
  public Optional<ImportPrecheckFailure> check(AAppcarItemImportMetadata metadata) {
    AAppcarEquipId equipId = keyFactory.buildEquipId(metadata);
    if (equipRepository.existsByIdAndApprovalYn(equipId, "Y")) {
      return Optional.of(
          new ImportPrecheckFailure(
              APPROVED_EQUIP_EXISTS_MESSAGE, buildMetadataConflict(equipId)));
    }
    return Optional.empty();
  }

  private MetadataConflict buildMetadataConflict(AAppcarEquipId equipId) {
    return new MetadataConflict(
        CONFLICT_TYPE,
        CONFLICT_DESCRIPTION,
        List.of(
            new MetadataConflict.FieldValue("companyId", "회사 ID", equipId.getCompanyId()),
            new MetadataConflict.FieldValue("customId", "거래처 ID", equipId.getCustomId()),
            new MetadataConflict.FieldValue("comeYear", "반입연도", equipId.getComeYear()),
            new MetadataConflict.FieldValue(
                "comeOrder", "반입차수", String.valueOf(equipId.getComeOrder())),
            new MetadataConflict.FieldValue(
                "uploadSeq", "업로드 순번", String.valueOf(equipId.getUploadSeq())),
            new MetadataConflict.FieldValue("equipCode", "설비코드", equipId.getEquipCode())));
  }
}
