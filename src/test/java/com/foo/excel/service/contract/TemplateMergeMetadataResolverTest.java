package com.foo.excel.service.contract;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.ExcelHeaderGroup;
import com.foo.excel.templates.samples.aappcar.dto.AAppcarItemDto;
import java.util.List;
import org.junit.jupiter.api.Test;

class TemplateMergeMetadataResolverTest {

  @Test
  void resolve_aAppcarExpandsReadableFieldMetadataIntoExpectedMergeRegions() {
    List<TemplateMergeRegion> regions = TemplateMergeMetadataResolver.resolve(AAppcarItemDto.class);

    assertThat(regions)
        .contains(
            new TemplateMergeRegion(TemplateMergeScope.DATA, 0, 1, 5, 2, true),
            new TemplateMergeRegion(TemplateMergeScope.DATA, 0, 1, 9, 2, true),
            new TemplateMergeRegion(TemplateMergeScope.DATA, 0, 1, 11, 2, true),
            new TemplateMergeRegion(TemplateMergeScope.DATA, 0, 1, 14, 2, true),
            new TemplateMergeRegion(TemplateMergeScope.HEADER, 0, 1, 9, 4, false),
            new TemplateMergeRegion(TemplateMergeScope.HEADER, 1, 1, 9, 2, false),
            new TemplateMergeRegion(TemplateMergeScope.HEADER, 2, 1, 9, 2, false),
            new TemplateMergeRegion(TemplateMergeScope.HEADER, 1, 1, 11, 2, false),
            new TemplateMergeRegion(TemplateMergeScope.HEADER, 2, 1, 11, 2, false));
  }

  @Test
  void resolve_failsFastWhenHeaderGroupReusesAFieldAcrossGroups() {
    assertThatThrownBy(() -> TemplateMergeMetadataResolver.resolve(DuplicateGroupMembershipDto.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("reuses field 'second'");
  }

  @Test
  void resolve_failsFastWhenHeaderGroupFieldsAreNotAdjacent() {
    assertThatThrownBy(() -> TemplateMergeMetadataResolver.resolve(NonAdjacentHeaderGroupDto.class))
        .isInstanceOf(IllegalStateException.class)
        .hasMessageContaining("non-adjacent or out-of-order field 'third'");
  }

  @Test
  void resolve_failsFastWhenInferredRegionsOverlap() {
    assertThatThrownBy(() -> TemplateMergeMetadataResolver.resolve(OverlappingDataSpanDto.class))
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
