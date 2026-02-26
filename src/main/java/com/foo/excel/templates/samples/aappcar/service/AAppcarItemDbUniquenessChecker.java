package com.foo.excel.templates.samples.aappcar.service;

import com.foo.excel.service.contract.DatabaseUniquenessChecker;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemCommonData;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarEquipId;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItem;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItemId;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarItemRepository;
import com.foo.excel.validation.CellError;
import com.foo.excel.validation.RowError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AAppcarItemDbUniquenessChecker
    implements DatabaseUniquenessChecker<AAppcarItemDto, AAppcarItemCommonData> {

  private static final int ID_COLUMN_INDEX = 1;
  private static final String ID_COLUMN_LETTER = "B";
  private static final String ID_FIELD_NAME = "sequenceNo";
  private static final String ID_HEADER_NAME = "순번";
  private static final String EQUIP_DUPLICATE_MESSAGE =
      "동일 업로드 식별자(회사/세관/반입연도/차수/업로드순번/설비코드)가 이미 존재합니다.";
  private static final String ITEM_DUPLICATE_MESSAGE = "품목 테이블에 이미 존재하는 ID입니다.";

  private final AAppcarItemRepository itemRepository;
  private final AAppcarEquipRepository equipRepository;

  @Override
  public List<RowError> check(
      List<AAppcarItemDto> rows,
      Class<AAppcarItemDto> dtoClass,
      List<Integer> sourceRowNumbers,
      AAppcarItemCommonData commonData) {
    List<RowError> errors = new ArrayList<>();
    if (rows.isEmpty() || sourceRowNumbers.isEmpty()) {
      return errors;
    }

    if (hasDuplicateEquipId(commonData)) {
      for (int rowNumber : sourceRowNumbers) {
        errors.add(buildRowError(rowNumber, EQUIP_DUPLICATE_MESSAGE));
      }
    }

    Set<AAppcarItemId> existingItemIds = findExistingItemIds(sourceRowNumbers, commonData);
    for (int rowNumber : sourceRowNumbers) {
      AAppcarItemId itemId = buildItemId(commonData, rowNumber);
      if (existingItemIds.contains(itemId)) {
        errors.add(buildRowError(rowNumber, ITEM_DUPLICATE_MESSAGE));
      }
    }

    return errors;
  }

  private boolean hasDuplicateEquipId(AAppcarItemCommonData commonData) {
    return equipRepository.existsById(buildEquipId(commonData));
  }

  private Set<AAppcarItemId> findExistingItemIds(
      List<Integer> sourceRowNumbers, AAppcarItemCommonData commonData) {
    List<AAppcarItemId> itemIds =
        sourceRowNumbers.stream().map(rowNumber -> buildItemId(commonData, rowNumber)).toList();
    List<AAppcarItem> existingItems = itemRepository.findAllById(itemIds);
    Set<AAppcarItemId> existingIds = new HashSet<>();
    for (AAppcarItem existingItem : existingItems) {
      existingIds.add(existingItem.getId());
    }
    return existingIds;
  }

  private RowError buildRowError(int rowNumber, String message) {
    CellError cellError =
        CellError.builder()
            .columnIndex(ID_COLUMN_INDEX)
            .columnLetter(ID_COLUMN_LETTER)
            .fieldName(ID_FIELD_NAME)
            .headerName(ID_HEADER_NAME)
            .message(message)
            .build();

    List<CellError> cellErrors = new ArrayList<>();
    cellErrors.add(cellError);
    return RowError.builder().rowNumber(rowNumber).cellErrors(cellErrors).build();
  }

  private AAppcarItemId buildItemId(AAppcarItemCommonData commonData, int rowNumber) {
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
}
