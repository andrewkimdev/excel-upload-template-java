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
import java.time.LocalDate;
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

    upsertEquipWithRetry(commonData);

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
          updateEntityFromDto(entity, dto, commonData);
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

  private void upsertEquipWithRetry(AAppcarItemCommonData commonData) {
    AAppcarEquipId equipId = buildEquipId(commonData);
    int attempt = 0;
    while (attempt <= UPSERT_RETRY_LIMIT) {
      attempt++;
      try {
        Optional<AAppcarEquip> existing = equipRepository.findById(equipId);

        if (existing.isPresent()) {
          AAppcarEquip entity = existing.get();
          applyEquipFields(entity, commonData);
          equipRepository.saveAndFlush(entity);
          return;
        }

        AAppcarEquip newEntity = AAppcarEquip.builder().id(equipId).build();
        applyEquipFields(newEntity, commonData);
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

  private void updateEntityFromDto(
      AAppcarItem entity, AAppcarItemDto dto, AAppcarItemCommonData commonData) {
    entity.setGoodsDes(dto.getGoodsDes());
    entity.setSpec(dto.getSpec());
    entity.setModelDes(dto.getModelDes());
    entity.setHsno(dto.getHsno());
    entity.setTaxRate(dto.getTaxRate());
    entity.setUnitprice(dto.getUnitprice());
    entity.setProdQty(dto.getProdQty());
    entity.setRepairQty(dto.getRepairQty());
    entity.setImportAmt(dto.getImportAmt());
    entity.setImportQty(dto.getImportQty());
    entity.setApprovalYn(resolveApprovalYn(dto.getApprovalYn(), commonData));
  }

  private AAppcarItem buildEntityFromDto(
      AAppcarItemDto dto, AAppcarItemCommonData commonData) {
    AAppcarItem entity =
        AAppcarItem.builder()
            .goodsDes(dto.getGoodsDes())
            .spec(dto.getSpec())
            .modelDes(dto.getModelDes())
            .hsno(dto.getHsno())
            .taxRate(dto.getTaxRate())
            .unitprice(dto.getUnitprice())
            .prodQty(dto.getProdQty())
            .repairQty(dto.getRepairQty())
            .importAmt(dto.getImportAmt())
            .importQty(dto.getImportQty())
            .approvalYn(resolveApprovalYn(dto.getApprovalYn(), commonData))
            .build();
    return entity;
  }

  private AAppcarItemId buildItemId(AAppcarItemCommonData commonData, Integer rowNumber) {
    return new AAppcarItemId(
        commonData.getCompanyId(),
        commonData.getCustomId(),
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

  private void applyEquipFields(AAppcarEquip entity, AAppcarItemCommonData commonData) {
    entity.setEquipMean(commonData.getEquipMean());
    entity.setHsno(commonData.getHsno());
    entity.setSpec(commonData.getSpec());
    entity.setTaxRate(commonData.getTaxRate());
    entity.setFilePath(commonData.getFilePath());
    entity.setApprovalYn(commonData.getApprovedYn());
    entity.setApprovalDate(commonData.isApprovalYnYes() ? LocalDate.now() : null);
  }

  private String resolveApprovalYn(String dtoApprovalYn, AAppcarItemCommonData commonData) {
    if (dtoApprovalYn == null || dtoApprovalYn.isBlank()) {
      return commonData.getApprovedYn();
    }
    String normalized = dtoApprovalYn.trim();
    if (normalized.equalsIgnoreCase("Y")
        || normalized.equals("승인")
        || normalized.equals("적합")
        || normalized.equals("통과")
        || normalized.equalsIgnoreCase("PASS")
        || normalized.equalsIgnoreCase("OK")) {
      return "Y";
    }
    if (normalized.equalsIgnoreCase("N")
        || normalized.equals("미승인")
        || normalized.equals("부적합")
        || normalized.equals("반려")
        || normalized.equalsIgnoreCase("FAIL")) {
      return "N";
    }
    return commonData.getApprovedYn();
  }
}
