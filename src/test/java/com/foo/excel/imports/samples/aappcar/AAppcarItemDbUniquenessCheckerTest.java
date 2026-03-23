package com.foo.excel.imports.samples.aappcar;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemMetadata;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemDto;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarItem;
import com.foo.excel.imports.samples.aappcar.persistence.entity.AAppcarItemId;
import com.foo.excel.imports.samples.aappcar.persistence.repository.AAppcarItemRepository;
import com.foo.excel.imports.samples.aappcar.service.AAppcarItemDbUniquenessChecker;
import com.foo.excel.imports.samples.aappcar.service.AAppcarItemKeyFactory;
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

  private AAppcarItemDbUniquenessChecker checker;

  @BeforeEach
  void setUp() {
    checker = new AAppcarItemDbUniquenessChecker(itemRepository, new AAppcarItemKeyFactory());
  }

  @Test
  void check_whenNoDuplicates_returnsEmptyErrors() {
    when(itemRepository.findAllById(any())).thenReturn(List.of());

    List<RowError> result =
        checker.check(
            List.of(new AAppcarItemDto()),
            AAppcarItemDto.class,
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
            List.of(new AAppcarItemDto(), new AAppcarItemDto()),
            AAppcarItemDto.class,
            List.of(7, 8),
            createMetadata("2026", "1", "1", "EQ-01"));

    assertThat(result).hasSize(1);
    assertThat(result.get(0).getRowNumber()).isEqualTo(8);
    assertThat(result.get(0).getFormattedMessage())
        .startsWith("B열 ")
        .doesNotContain("B열열")
        .contains("품목 테이블에 이미 존재하는 ID");
  }

  private AAppcarItemMetadata createMetadata(
      String comeYear, String comeOrder, String uploadSeq, String equipCode) {
    AAppcarItemMetadata metadata = new AAppcarItemMetadata();
    metadata.setComeYear(comeYear);
    metadata.setComeOrder(comeOrder);
    metadata.setUploadSeq(uploadSeq);
    metadata.setEquipCode(equipCode);
    metadata.setCompanyId("COMPANY01");
    metadata.setCustomId("CUSTOM01");
    return metadata;
  }
}
