package com.foo.excel.templates.samples.aappcar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemMetaData;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItem;
import com.foo.excel.templates.samples.aappcar.persistence.entity.AAppcarItemId;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarEquipRepository;
import com.foo.excel.templates.samples.aappcar.persistence.repository.AAppcarItemRepository;
import com.foo.excel.templates.samples.aappcar.service.AAppcarItemDbUniquenessChecker;
import com.foo.excel.validation.RowError;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class AAppcarItemDbUniquenessCheckerTest {

  @Mock private AAppcarItemRepository itemRepository;
  @Mock private AAppcarEquipRepository equipRepository;

  private AAppcarItemDbUniquenessChecker checker;

  @BeforeEach
  void setUp() {
    checker = new AAppcarItemDbUniquenessChecker(itemRepository, equipRepository);
  }

  @Test
  void check_whenNoDuplicates_returnsEmptyErrors() {
    when(equipRepository.existsById(any())).thenReturn(false);
    when(itemRepository.findAllById(any())).thenReturn(List.of());

    List<RowError> result =
        checker.check(List.of(7), createMetaData("2026", "1", "1", "EQ-01"));

    assertThat(result).isEmpty();
  }

  @Test
  void check_whenEquipIdExists_marksAllRowsAsDuplicate() {
    when(equipRepository.existsById(any())).thenReturn(true);
    when(itemRepository.findAllById(any())).thenReturn(List.of());

    List<RowError> result =
        checker.check(List.of(7, 8), createMetaData("2026", "1", "1", "EQ-01"));

    assertThat(result).hasSize(2);
    assertThat(result)
        .allMatch(rowError -> rowError.getFormattedMessage().startsWith("B열 "))
        .allMatch(rowError -> !rowError.getFormattedMessage().contains("B열열"))
        .allMatch(rowError -> rowError.getFormattedMessage().contains("동일 업로드 식별자"));
  }

  @Test
  void check_whenItemIdExists_marksOnlyConflictingRows() {
    when(equipRepository.existsById(any())).thenReturn(false);
    AAppcarItem existing =
        AAppcarItem.builder()
            .id(new AAppcarItemId("COMPANY01", "CUSTOM01", "2026", "1", "1", "EQ-01", 8))
            .build();
    when(itemRepository.findAllById(any())).thenReturn(List.of(existing));

    List<RowError> result =
        checker.check(List.of(7, 8), createMetaData("2026", "1", "1", "EQ-01"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getRowNumber()).isEqualTo(8);
    assertThat(result.get(0).getFormattedMessage())
        .startsWith("B열 ")
        .doesNotContain("B열열")
        .contains("품목 테이블에 이미 존재하는 ID");
  }

  private AAppcarItemMetaData createMetaData(
      String comeYear, String comeOrder, String uploadSeq, String equipCode) {
    AAppcarItemMetaData metaData = new AAppcarItemMetaData();
    metaData.setComeYear(comeYear);
    metaData.setComeOrder(comeOrder);
    metaData.setUploadSeq(uploadSeq);
    metaData.setEquipCode(equipCode);
    metaData.setCompanyId("COMPANY01");
    metaData.setCustomId("CUSTOM01");
    return metaData;
  }
}
