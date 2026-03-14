package com.foo.excel.templates.samples.aappcar.service;

import com.foo.excel.service.contract.MetadataConflict;
import com.foo.excel.service.contract.UploadPrecheck;
import com.foo.excel.service.contract.UploadPrecheckFailure;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetaData;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarEquipId;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AAppcarItemUploadPrecheck implements UploadPrecheck<AAppcarItemMetaData> {

  private static final String APPROVED_EQUIP_EXISTS_MESSAGE =
      "입력한 메타데이터 조합과 일치하는 승인된 장비가 이미 존재합니다.";
  private static final String CONFLICT_TYPE = "METADATA_DUPLICATE_APPROVED_EQUIP";
  private static final String CONFLICT_DESCRIPTION =
      "아래 메타데이터 값 조합에 대해 이미 승인된 장비가 존재합니다. 엑셀 파일이 아니라 메타데이터 값을 수정하세요.";

  private final AAppcarEquipRepository equipRepository;
  private final AAppcarItemKeyFactory keyFactory;

  @Override
  public Optional<UploadPrecheckFailure> check(AAppcarItemMetaData metaData) {
    AAppcarEquipId equipId = keyFactory.buildEquipId(metaData);
    if (equipRepository.existsByIdAndApprovalYn(equipId, "Y")) {
      return Optional.of(
          new UploadPrecheckFailure(
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
