package com.foo.excel.templates.samples.aappcar.service;

import com.foo.excel.service.contract.PersistenceHandler;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemCommonData;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItem;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItemId;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarEquip;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarEquipId;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarItemRepository;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AAppcarItemService
    implements PersistenceHandler<AAppcarItemDto, AAppcarItemCommonData> {

  private static final int UPSERT_RETRY_LIMIT = 2;

  private final AAppcarItemRepository itemRepository;
  private final AAppcarEquipRepository equipRepository;

  @Override
  @Transactional
  public SaveResult saveAll(
      List<AAppcarItemDto> dtos,
      List<Integer> sourceRowNumbers,
      AAppcarItemCommonData commonData) {
    int created = 0;
    int updated = 0;

    for (int i = 0; i < dtos.size(); i++) {
      boolean createdNow = upsertItemWithRetry(dtos.get(i), sourceRowNumbers.get(i), commonData);
      if (createdNow) {
        created++;
      } else {
        updated++;
      }
    }

    upsertEquipWithRetry(dtos.size(), commonData);

    return new SaveResult(created, updated);
  }

  private boolean upsertItemWithRetry(
      AAppcarItemDto dto, Integer rowNumber, AAppcarItemCommonData commonData) {
    AAppcarItemId itemId = buildItemId(commonData, rowNumber);
    int attempt = 0;
    while (attempt <= UPSERT_RETRY_LIMIT) {
      attempt++;
      try {
        Optional<AAppcarItem> existing = itemRepository.findById(itemId);

        if (existing.isPresent()) {
          AAppcarItem entity = existing.get();
          updateEntityFromDto(entity, dto);
          itemRepository.saveAndFlush(entity);
          return false;
        }

        AAppcarItem newEntity = buildEntityFromDto(dto, commonData);
        newEntity.setId(itemId);
        itemRepository.saveAndFlush(newEntity);
        return true;
      } catch (DataIntegrityViolationException e) {
        if (attempt > UPSERT_RETRY_LIMIT) {
          throw new IllegalStateException("동시 저장 충돌로 품목 데이터 처리에 실패했습니다.");
        }
      }
    }

    throw new IllegalStateException("동시 저장 충돌로 품목 데이터 처리에 실패했습니다.");
  }

  private void upsertEquipWithRetry(int rowCount, AAppcarItemCommonData commonData) {
    AAppcarEquipId equipId = buildEquipId(commonData);
    int attempt = 0;
    while (attempt <= UPSERT_RETRY_LIMIT) {
      attempt++;
      try {
        Optional<AAppcarEquip> existing = equipRepository.findById(equipId);

        if (existing.isPresent()) {
          AAppcarEquip entity = existing.get();
          entity.setUploadedRows(rowCount);
          applyAuditDefaults(entity, commonData);
          equipRepository.saveAndFlush(entity);
          return;
        }

        AAppcarEquip newEntity = AAppcarEquip.builder().id(equipId).uploadedRows(rowCount).build();
        applyAuditDefaults(newEntity, commonData);
        equipRepository.saveAndFlush(newEntity);
        return;
      } catch (DataIntegrityViolationException e) {
        if (attempt > UPSERT_RETRY_LIMIT) {
          throw new IllegalStateException("동시 저장 충돌로 설비 데이터 처리에 실패했습니다.");
        }
      }
    }

    throw new IllegalStateException("동시 저장 충돌로 설비 데이터 처리에 실패했습니다.");
  }

  private void updateEntityFromDto(AAppcarItem entity, AAppcarItemDto dto) {
    entity.setSequenceNo(dto.getSequenceNo());
    entity.setItemName(dto.getItemName());
    entity.setSpecification(dto.getSpecification());
    entity.setModelName(dto.getModelName());
    entity.setHsCode(dto.getHsCode());
    entity.setTariffRate(dto.getTariffRate());
    entity.setUnitPrice(dto.getUnitPrice());
    entity.setQtyForManufacturing(dto.getQtyForManufacturing());
    entity.setQtyForRepair(dto.getQtyForRepair());
    entity.setAnnualImportEstimate(dto.getAnnualImportEstimate());
    entity.setReviewResult(dto.getReviewResult());
    entity.setAnnualExpectedQty(dto.getAnnualExpectedQty());
  }

  private AAppcarItem buildEntityFromDto(
      AAppcarItemDto dto, AAppcarItemCommonData commonData) {
    AAppcarItem entity =
        AAppcarItem.builder()
            .sequenceNo(dto.getSequenceNo())
            .itemName(dto.getItemName())
            .specification(dto.getSpecification())
            .modelName(dto.getModelName())
            .hsCode(dto.getHsCode())
            .tariffRate(dto.getTariffRate())
            .unitPrice(dto.getUnitPrice())
            .qtyForManufacturing(dto.getQtyForManufacturing())
            .qtyForRepair(dto.getQtyForRepair())
            .annualImportEstimate(dto.getAnnualImportEstimate())
            .reviewResult(dto.getReviewResult())
            .annualExpectedQty(dto.getAnnualExpectedQty())
            .build();
    applyAuditDefaults(entity, commonData);
    return entity;
  }

  private AAppcarItemId buildItemId(AAppcarItemCommonData commonData, Integer rowNumber) {
    return new AAppcarItemId(
        commonData.getComeYear(),
        commonData.getComeOrder(),
        commonData.getUploadSeq(),
        commonData.getEquipCode(),
        rowNumber);
  }

  private AAppcarEquipId buildEquipId(AAppcarItemCommonData commonData) {
    return new AAppcarEquipId(
        commonData.getCompanyId(),
        commonData.getCustomId(),
        commonData.getComeYear(),
        Integer.valueOf(commonData.getComeOrder()),
        Integer.valueOf(commonData.getUploadSeq()),
        commonData.getEquipCode());
  }

  private void applyAuditDefaults(AAppcarItem entity, AAppcarItemCommonData commonData) {
    entity.setApprovedYn(commonData.getApprovedYn());
    entity.setCreatedBy(commonData.getCreatedBy());
    entity.setCreatedAt(LocalDateTime.now());
  }

  private void applyAuditDefaults(
      AAppcarEquip entity, AAppcarItemCommonData commonData) {
    entity.setApprovedYn(commonData.getApprovedYn());
    entity.setCreatedBy(commonData.getCreatedBy());
    entity.setCreatedAt(LocalDateTime.now());
  }
}
