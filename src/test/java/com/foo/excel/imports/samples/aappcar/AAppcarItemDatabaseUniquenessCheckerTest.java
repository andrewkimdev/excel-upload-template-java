package com.foo.excel.imports.samples.aappcar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemImportMetadata;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemImportRow;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarItem;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarItemId;
import com.foo.excel.imports.samples.aappcar.persistence.repository.AAppcarItemRepository;
import com.foo.excel.imports.samples.aappcar.service.AAppcarItemDatabaseUniquenessChecker;
import com.foo.excel.imports.samples.aappcar.service.AAppcarItemKeyFactory;
import com.foo.excel.validation.RowError;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AAppcarItemDatabaseUniquenessCheckerTest {

  @Mock private AAppcarItemRepository itemRepository;

  private AAppcarItemDatabaseUniquenessChecker checker;

  @BeforeEach
  void setUp() {
    checker = new AAppcarItemDatabaseUniquenessChecker(itemRepository, new AAppcarItemKeyFactory());
  }

  @Test
  void check_whenNoDuplicates_returnsEmptyErrors() {
    when(itemRepository.findAllById(any())).thenReturn(List.of());

    List<RowError> result =
        checker.check(
            List.of(new AAppcarItemImportRow()),
            AAppcarItemImportRow.class,
            List.of(7),
            createMetadata("2026", "1", "1", "EQ-01"));

    assertThat(result).isEmpty();
  }

  @Test
  void check_whenItemIdExists_marksOnlyConflictingRows() {
    AAppcarItem existing =
        AAppcarItem.builder()
            .id(new AAppcarItemId("COMPANY01", "CUSTOM01", "2026", "1", "1", "EQ-01", 8))
            .build();
    when(itemRepository.findAllById(any())).thenReturn(List.of(existing));

    List<RowError> result =
        checker.check(
            List.of(new AAppcarItemImportRow(), new AAppcarItemImportRow()),
            AAppcarItemImportRow.class,
            List.of(7, 8),
            createMetadata("2026", "1", "1", "EQ-01"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getRowNumber()).isEqualTo(8);
    assertThat(result.get(0).getFormattedMessage())
        .startsWith("B열 ")
        .doesNotContain("B열열")
        .contains("품목 테이블에 이미 존재하는 ID");
  }

  private AAppcarItemImportMetadata createMetadata(
      String comeYear, String comeOrder, String uploadSeq, String equipCode) {
    AAppcarItemImportMetadata metadata = new AAppcarItemImportMetadata();
    metadata.setComeYear(comeYear);
    metadata.setComeOrder(comeOrder);
    metadata.setUploadSeq(uploadSeq);
    metadata.setEquipCode(equipCode);
    metadata.setCompanyId("COMPANY01");
    metadata.setCustomId("CUSTOM01");
    return metadata;
  }
}
