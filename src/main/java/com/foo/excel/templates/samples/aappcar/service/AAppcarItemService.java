package com.foo.excel.templates.samples.aappcar.service;

import com.foo.excel.service.contract.PersistenceHandler;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetaData;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItem;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarEquip;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItemId;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarItemRepository;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import java.util.ArrayList;
import java.util.HashMap;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class AAppcarItemService
    implements PersistenceHandler<AAppcarItemDto, AAppcarItemMetaData> {

  private static final int ITEM_BATCH_SIZE = 100;
  private static final int UPSERT_RETRY_LIMIT = 2;

  private final AAppcarItemRepository itemRepository;
  private final AAppcarEquipRepository equipRepository;
  private final AAppcarItemKeyFactory keyFactory;

  @Override
  @Transactional
  public SaveResult saveAll(
      List<AAppcarItemDto> dtos,
      List<Integer> sourceRowNumbers,
      AAppcarItemMetaData metaData) {
    List<AAppcarItemId> itemIds = buildItemIds(sourceRowNumbers, metaData);
    Map<AAppcarItemId, AAppcarItem> existingItems = findExistingItems(itemIds);
    List<AAppcarItem> entitiesToSave = new ArrayList<>(dtos.size());
    int created = 0;

    for (int i = 0; i < dtos.size(); i++) {
      AAppcarItemId itemId = itemIds.get(i);
      AAppcarItem entity = existingItems.get(itemId);
      if (entity == null) {
        entity = buildEntityFromDto(dtos.get(i), metaData);
        entity.setId(itemId);
        created++;
      } else {
        updateEntityFromDto(entity, dtos.get(i), metaData);
      }
      entitiesToSave.add(entity);
    }

    saveItemsInBatches(entitiesToSave);

    upsertEquipWithRetry(metaData);

    return new SaveResult(created, dtos.size() - created);
  }

  private void upsertEquipWithRetry(AAppcarItemMetaData metaData) {
    var equipId = keyFactory.buildEquipId(metaData);
    int attempt = 0;
    while (attempt <= UPSERT_RETRY_LIMIT) {
      attempt++;
      try {
        Optional<AAppcarEquip> existing = equipRepository.findById(equipId);

        if (existing.isPresent()) {
          AAppcarEquip entity = existing.get();
          applyEquipFields(entity, metaData);
          equipRepository.save(entity);
          return;
        }

        AAppcarEquip newEntity = AAppcarEquip.builder().id(equipId).build();
        applyEquipFields(newEntity, metaData);
        equipRepository.save(newEntity);
        return;
      } catch (DataIntegrityViolationException e) {
        if (attempt > UPSERT_RETRY_LIMIT) {
          throw new IllegalStateException("동시 저장 충돌로 설비 데이터 처리에 실패했습니다.");
        }
      }
    }

    throw new IllegalStateException("동시 저장 충돌로 설비 데이터 처리에 실패했습니다.");
  }

  private List<AAppcarItemId> buildItemIds(
      List<Integer> sourceRowNumbers, AAppcarItemMetaData metaData) {
    return sourceRowNumbers.stream()
        .map(rowNumber -> keyFactory.buildItemId(metaData, rowNumber))
        .toList();
  }

  private Map<AAppcarItemId, AAppcarItem> findExistingItems(List<AAppcarItemId> itemIds) {
    Map<AAppcarItemId, AAppcarItem> existingItems = new HashMap<>();
    for (AAppcarItem existingItem : itemRepository.findAllById(itemIds)) {
      existingItems.put(existingItem.getId(), existingItem);
    }
    return existingItems;
  }

  private void saveItemsInBatches(List<AAppcarItem> entitiesToSave) {
    int total = entitiesToSave.size();
    for (int start = 0; start < total; start += ITEM_BATCH_SIZE) {
      int end = Math.min(start + ITEM_BATCH_SIZE, total);
      itemRepository.saveAll(entitiesToSave.subList(start, end));
      itemRepository.flush();
    }
  }

  private void updateEntityFromDto(FF
      AAppcarItem entity, AAppcarItemDto dto, AAppcarItemMetaData metaData) {
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
    entity.setApprovalYn(resolveApprovalYn(dto.getApprovalYn(), metaData));
  }

  private AAppcarItem buildEntityFromDto(
      AAppcarItemDto dto, AAppcarItemMetaData metaData) {
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
            .approvalYn(resolveApprovalYn(dto.getApprovalYn(), metaData))
            .build();
    return entity;
  }

  private void applyEquipFields(AAppcarEquip entity, AAppcarItemMetaData metaData) {
    entity.setEquipMean(metaData.getEquipMean());
    entity.setHsno(metaData.getHsno());
    entity.setSpec(metaData.getSpec());
    entity.setTaxRate(metaData.getTaxRate());
    entity.setFilePath(metaData.getFilePath());
    entity.setApprovalYn(metaData.getApprovedYn());
    entity.setApprovalDate(metaData.isApprovalYnYes() ? LocalDate.now() : null);
  }

  private String resolveApprovalYn(String dtoApprovalYn, AAppcarItemMetaData metaData) {
    if (dtoApprovalYn == null || dtoApprovalYn.isBlank()) {
      return metaData.getApprovedYn();
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
    return metaData.getApprovedYn();
  }
}
