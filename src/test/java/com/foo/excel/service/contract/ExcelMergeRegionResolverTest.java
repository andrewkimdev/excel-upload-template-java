package com.foo.excel.service.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.ExcelHeaderGroup;
import com.foo.excel.imports.samples.aappcar.dto.AAppcarItemRow;
import java.util.List;
import org.junit.jupiter.api.Test;

class ExcelMergeRegionResolverTest {

  @Test
  void resolve_aAppcarExpandsReadableFieldMetadataIntoExpectedMergeRegions() {
    List<ExcelMergeRegion> regions = ExcelMergeRegionResolver.resolve(AAppcarItemRow.class);

    assertThat(regions)
        .contains(
            new ExcelMergeRegion(ExcelMergeScope.DATA, 0, 1, 5, 2, true),
            new ExcelMergeRegion(ExcelMergeScope.DATA, 0, 1, 9, 2, true),
            new ExcelMergeRegion(ExcelMergeScope.DATA, 0, 1, 11, 2, true),
            new ExcelMergeRegion(ExcelMergeScope.DATA, 0, 1, 14, 2, true),
            new ExcelMergeRegion(ExcelMergeScope.HEADER, 0, 1, 9, 4, false),
            new ExcelMergeRegion(ExcelMergeScope.HEADER, 1, 1, 9, 2, false),
            new ExcelMergeRegion(ExcelMergeScope.HEADER, 2, 1, 9, 2, false),
            new ExcelMergeRegion(ExcelMergeScope.HEADER, 1, 1, 11, 2, false),
            new ExcelMergeRegion(ExcelMergeScope.HEADER, 2, 1, 11, 2, false));
  }

  @Test
  void resolve_failsFastWhenHeaderGroupReusesAFieldAcrossGroups() {
    assertThatThrownBy(() -> ExcelMergeRegionResolver.resolve(DuplicateGroupMembershipDto.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("reuses field 'second'");
  }

  @Test
  void resolve_failsFastWhenHeaderGroupFieldsAreNotAdjacent() {
    assertThatThrownBy(() -> ExcelMergeRegionResolver.resolve(NonAdjacentHeaderGroupDto.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-adjacent or out-of-order field 'third'");
  }

  @Test
  void resolve_failsFastWhenInferredRegionsOverlap() {
    assertThatThrownBy(() -> ExcelMergeRegionResolver.resolve(OverlappingDataSpanDto.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("Overlapping inferred merge regions");
  }

  static class DuplicateGroupMembershipDto {

    @ExcelColumn(column = "A", label = "A", columnSpan = 2, headerRowStart = 1, headerRowCount = 2)
    @ExcelHeaderGroup(label = "group1", fields = {"first", "second"})
    private String first;

    @ExcelColumn(column = "C", label = "B", columnSpan = 2, headerRowStart = 1, headerRowCount = 2)
    private String second;

    @ExcelColumn(column = "E", label = "C", columnSpan = 2, headerRowStart = 1, headerRowCount = 2)
    @ExcelHeaderGroup(label = "group2", fields = {"third", "second"})
    private String third;
  }

  static class NonAdjacentHeaderGroupDto {

    @ExcelColumn(column = "A", label = "A", columnSpan = 2, headerRowStart = 1, headerRowCount = 2)
    @ExcelHeaderGroup(label = "group", fields = {"first", "third"})
    private String first;

    @ExcelColumn(column = "C", label = "B", columnSpan = 2, headerRowStart = 1, headerRowCount = 2)
    private String second;

    @ExcelColumn(column = "E", label = "C", columnSpan = 2, headerRowStart = 1, headerRowCount = 2)
    private String third;
  }

  static class OverlappingDataSpanDto {

    @ExcelColumn(column = "A", label = "A", columnSpan = 2)
    private String first;

    @ExcelColumn(column = "B", label = "B", columnSpan = 2)
    private String second;
  }
}
