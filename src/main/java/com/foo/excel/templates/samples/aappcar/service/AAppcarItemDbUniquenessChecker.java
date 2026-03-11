package com.foo.excel.templates.samples.aappcar.service;

import com.foo.excel.service.contract.DatabaseUniquenessChecker;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetaData;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarEquipId;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItem;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItemId;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarItemRepository;
import com.foo.excel.validation.CellError;
import com.foo.excel.validation.ExcelColumnRef;
import com.foo.excel.validation.RowError;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

/**
 * Database duplicate checker for the sample {@code aappcar} tariff template.
 *
 * <p>This implementation is intentionally interesting because it does not use the parsed row DTO
 * values even though the {@link DatabaseUniquenessChecker} contract is row-oriented. The business
 * key for this template is composed from:
 *
 * <ul>
 *   <li>upload-level metadata entered outside the Excel file
 *   <li>the original Excel row number, which becomes part of the persisted item ID
 * </ul>
 *
 * <p>That means DB uniqueness for this template is effectively metadata-driven. We still implement
 * the full row-oriented contract because the abstraction should remain understandable to readers
 * and reusable by future templates that may genuinely need DTO field values.
 *
 * <p>The checker validates two persistence targets:
 *
 * <ul>
 *   <li>the upload-level equip aggregate
 *   <li>the per-row item records
 * </ul>
 *
 * <p>Any conflict is converted into {@link RowError} instances pointing at the "순번" column so the
 * upload pipeline can render the problem back to the user in the standard Excel error-report
 * format.
 */
@Component
@RequiredArgsConstructor
public class AAppcarItemDbUniquenessChecker
    implements DatabaseUniquenessChecker<AAppcarItemDto, AAppcarItemMetaData> {

  /** Column index for the row identifier field used in the generated validation error. */
  private static final int ID_COLUMN_INDEX = 1;

  /** Excel-style column reference for the row identifier field. */
  private static final ExcelColumnRef ID_COLUMN_REF = ExcelColumnRef.ofLetter("B");

  /** DTO field name associated with the row identifier column. */
  private static final String ID_FIELD_NAME = "goodsSeqNo";

  /** Human-readable header name shown in upload error messages. */
  private static final String ID_HEADER_NAME = "순번";

  /** Message used when the upload-level equip aggregate already exists in the database. */
  private static final String EQUIP_DUPLICATE_MESSAGE =
      "동일 업로드 식별자(회사/세관/반입연도/차수/업로드순번/설비코드)가 이미 존재합니다.";

  /** Message used when an item row would collide with an existing persisted item ID. */
  private static final String ITEM_DUPLICATE_MESSAGE = "품목 테이블에 이미 존재하는 ID입니다.";

  /** Repository for per-row item entities. */
  private final AAppcarItemRepository itemRepository;

  /** Repository for the upload-level equip aggregate entity. */
  private final AAppcarEquipRepository equipRepository;

  /**
   * Performs DB duplicate checking for the current upload.
   *
   * <p>The {@code rows} and {@code dtoClass} arguments are not used by this implementation. That is
   * deliberate, not accidental. In this template, persisted IDs are derived from metadata and the
   * original source row numbers, so DTO field values do not participate in DB uniqueness.
   *
   * <p>The check happens in two parts:
   *
   * <ol>
   *   <li>Check whether the upload-level equip ID already exists
   *   <li>Check whether any row-derived item IDs already exist
   * </ol>
   *
   * <p>Both conflict types are reported against the same "순번" column because that column maps most
   * directly to the persisted item identity seen by users.
   *
   * @param rows parsed DTO rows; unused for this template's current business key
   * @param dtoClass DTO class; unused for this template's current business key
   * @param sourceRowNumbers original Excel row numbers that become part of the item identity
   * @param metaData upload-level metadata used to build equip and item IDs
   * @return row errors representing equip-level or item-level DB conflicts
   */
  @Override
  public List<RowError> check(
      List<AAppcarItemDto> rows,
      Class<AAppcarItemDto> dtoClass,
      List<Integer> sourceRowNumbers,
      AAppcarItemMetaData metaData) {
    List<RowError> errors = new ArrayList<>();

    // No source rows means there is nothing row-addressable to report back to the user.
    if (sourceRowNumbers.isEmpty()) {
      return errors;
    }

    // The equip table is modeled as an upload-level aggregate. If it already exists, every row in
    // this upload is invalid because they all belong to the same duplicated aggregate context.
    if (hasDuplicateEquipId(metaData)) {
      for (int rowNumber : sourceRowNumbers) {
        errors.add(buildRowError(rowNumber, EQUIP_DUPLICATE_MESSAGE));
      }
    }

    // Item IDs are row-specific because the original Excel row number is part of the persisted ID.
    // We prefetch existing IDs once and then match in-memory for stable per-row error reporting.
    Set<AAppcarItemId> existingItemIds = findExistingItemIds(sourceRowNumbers, metaData);
    for (int rowNumber : sourceRowNumbers) {
      AAppcarItemId itemId = buildItemId(metaData, rowNumber);
      if (existingItemIds.contains(itemId)) {
        errors.add(buildRowError(rowNumber, ITEM_DUPLICATE_MESSAGE));
      }
    }

    return errors;
  }

  /** Checks whether the upload-level equip aggregate already exists. */
  private boolean hasDuplicateEquipId(AAppcarItemMetaData metaData) {
    return equipRepository.existsById(buildEquipId(metaData));
  }

  /**
   * Loads existing item IDs for the current upload candidate set.
   *
   * <p>The query is assembled from metadata plus each source row number because that combination is
   * the actual persisted item key for this template.
   */
  private Set<AAppcarItemId> findExistingItemIds(
      List<Integer> sourceRowNumbers, AAppcarItemMetaData metaData) {
    List<AAppcarItemId> itemIds =
        sourceRowNumbers.stream().map(rowNumber -> buildItemId(metaData, rowNumber)).toList();
    List<AAppcarItem> existingItems = itemRepository.findAllById(itemIds);
    Set<AAppcarItemId> existingIds = new HashSet<>();
    for (AAppcarItem existingItem : existingItems) {
      existingIds.add(existingItem.getId());
    }
    return existingIds;
  }

  /**
   * Builds a standardized row error pointing at the identifier column.
   *
   * <p>Even equip-level conflicts are attached to a row/cell location because the overall upload
   * pipeline expects DB conflicts to be merged back into row-based validation results.
   */
  private RowError buildRowError(int rowNumber, String message) {
    CellError cellError =
        CellError.builder()
            .columnIndex(ID_COLUMN_INDEX)
            .columnRef(ID_COLUMN_REF)
            .fieldName(ID_FIELD_NAME)
            .headerName(ID_HEADER_NAME)
            .message(message)
            .build();

    List<CellError> cellErrors = new ArrayList<>();
    cellErrors.add(cellError);
    return RowError.builder().rowNumber(rowNumber).cellErrors(cellErrors).build();
  }

  /**
   * Builds the per-row item primary key.
   *
   * <p>Notice that the persisted identity is metadata-heavy and uses {@code rowNumber} instead of a
   * DTO field value. That is why this checker can remain correct without inspecting {@code rows}.
   */
  private AAppcarItemId buildItemId(AAppcarItemMetaData metaData, int rowNumber) {
    return new AAppcarItemId(
        metaData.getCompanyId(),
        metaData.getCustomId(),
        metaData.getComeYear(),
        metaData.getComeOrder(),
        metaData.getUploadSeq(),
        metaData.getEquipCode(),
        rowNumber);
  }

  /**
   * Builds the upload-level equip primary key.
   *
   * <p>This aggregate key is shared by every row in the upload, which is why an equip collision is
   * reported back against every source row number.
   */
  private AAppcarEquipId buildEquipId(AAppcarItemMetaData metaData) {
    return new AAppcarEquipId(
        metaData.getCompanyId(),
        metaData.getCustomId(),
        metaData.getComeYear(),
        Integer.valueOf(metaData.getComeOrder()),
        Integer.valueOf(metaData.getUploadSeq()),
        metaData.getEquipCode());
  }
}
