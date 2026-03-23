package com.foo.excel.service.contract;

import com.foo.excel.annotation.ExcelColumn;
import com.foo.excel.annotation.ExcelHeaderGroup;
import com.foo.excel.util.ExcelColumnUtil;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class ExcelMergeRegionResolver {

  private ExcelMergeRegionResolver() {}

  public static List<ExcelMergeRegion> resolve(Class<?> rowClass) {
    Map<String, ColumnMetadata> columnsByField = collectColumns(rowClass);
    List<ExcelMergeRegion> regions = new ArrayList<>();

    for (ColumnMetadata column : columnsByField.values()) {
      if (column.annotation().columnSpan() > 1) {
        addRegion(
            regions,
            new ExcelMergeRegion(
                ExcelMergeScope.DATA, 0, 1, column.startColumnIndex(), column.annotation().columnSpan(), true),
            rowClass);
      }
    }

    Map<String, String> groupMembership = new HashMap<>();
    for (ColumnMetadata anchor : columnsByField.values()) {
      ExcelHeaderGroup headerGroup = anchor.field().getAnnotation(ExcelHeaderGroup.class);
      if (headerGroup == null) {
        continue;
      }
      resolveHeaderGroup(rowClass, anchor, headerGroup, columnsByField, groupMembership, regions);
    }

    return regions;
  }

  private static Map<String, ColumnMetadata> collectColumns(Class<?> rowClass) {
    Map<String, ColumnMetadata> columnsByField = new LinkedHashMap<>();
    for (Field field : rowClass.getDeclaredFields()) {
      ExcelColumn annotation = field.getAnnotation(ExcelColumn.class);
      if (annotation == null) {
        continue;
      }
      if (annotation.column().isBlank()) {
        throw new IllegalStateException(
            "Blank column for field '%s'".formatted(field.getName()));
      }
      if (annotation.columnSpan() < 1) {
        throw new IllegalStateException(
            "Invalid columnSpan for field '%s': %d".formatted(field.getName(), annotation.columnSpan()));
      }
      columnsByField.put(
          field.getName(),
          new ColumnMetadata(field, annotation, ExcelColumnUtil.letterToIndex(annotation.column())));
    }
    return columnsByField;
  }

  private static void resolveHeaderGroup(
      Class<?> rowClass,
      ColumnMetadata anchor,
      ExcelHeaderGroup headerGroup,
      Map<String, ColumnMetadata> columnsByField,
      Map<String, String> groupMembership,
      List<ExcelMergeRegion> regions) {
    String[] groupedFields = headerGroup.fields();
    if (groupedFields.length == 0) {
      throw new IllegalStateException(
          "Header group '%s' on field '%s' must declare at least one field"
              .formatted(headerGroup.label(), anchor.field().getName()));
    }
    if (!groupedFields[0].equals(anchor.field().getName())) {
      throw new IllegalStateException(
          "Header group '%s' on field '%s' must list the anchor field first"
              .formatted(headerGroup.label(), anchor.field().getName()));
    }

    int headerRowStart = anchor.annotation().headerRowStart();
    int headerRowCount = anchor.annotation().headerRowCount();
    if (headerRowStart < 1 || headerRowCount < 1) {
      throw new IllegalStateException(
          "Header group '%s' on field '%s' requires a valid headerRowStart/headerRowCount range"
              .formatted(headerGroup.label(), anchor.field().getName()));
    }

    int expectedStartColumnIndex = anchor.startColumnIndex();
    int totalColumnSpan = 0;
    List<ColumnMetadata> groupedColumns = new ArrayList<>();

    for (String fieldName : groupedFields) {
      ColumnMetadata column = columnsByField.get(fieldName);
      if (column == null) {
        throw new IllegalStateException(
            "Header group '%s' references unknown field '%s'"
                .formatted(headerGroup.label(), fieldName));
      }
      validateGroupMembership(groupMembership, fieldName, anchor.field().getName(), headerGroup.label());
      validateCompatibleHeaderRange(anchor, column, headerGroup.label());
      if (column.startColumnIndex() != expectedStartColumnIndex) {
        throw new IllegalStateException(
            "Header group '%s' contains non-adjacent or out-of-order field '%s'"
                .formatted(headerGroup.label(), fieldName));
      }
      groupedColumns.add(column);
      totalColumnSpan += column.annotation().columnSpan();
      expectedStartColumnIndex += column.annotation().columnSpan();
    }

    addRegion(
        regions,
        new ExcelMergeRegion(
            ExcelMergeScope.HEADER, 0, 1, anchor.startColumnIndex(), totalColumnSpan, false),
        rowClass);

    int repeatedHeaderRows = headerRowCount - 1;
    for (ColumnMetadata column : groupedColumns) {
      if (column.annotation().columnSpan() <= 1) {
        continue;
      }
      for (int rowOffset = 1; rowOffset <= repeatedHeaderRows; rowOffset++) {
        addRegion(
            regions,
            new ExcelMergeRegion(
                ExcelMergeScope.HEADER,
                rowOffset,
                1,
                column.startColumnIndex(),
                column.annotation().columnSpan(),
                false),
            rowClass);
      }
    }
  }

  private static void validateGroupMembership(
      Map<String, String> groupMembership, String fieldName, String anchorFieldName, String label) {
    String existingGroup = groupMembership.putIfAbsent(fieldName, anchorFieldName);
    if (existingGroup != null) {
      throw new IllegalStateException(
          "Header group '%s' reuses field '%s' already owned by group anchored at '%s'"
              .formatted(label, fieldName, existingGroup));
    }
  }

  private static void validateCompatibleHeaderRange(
      ColumnMetadata anchor, ColumnMetadata candidate, String label) {
    if (candidate.annotation().headerRowStart() != anchor.annotation().headerRowStart()
        || candidate.annotation().headerRowCount() != anchor.annotation().headerRowCount()) {
      throw new IllegalStateException(
          "Header group '%s' requires matching header row ranges for fields '%s' and '%s'"
              .formatted(label, anchor.field().getName(), candidate.field().getName()));
    }
  }

  private static void addRegion(
      List<ExcelMergeRegion> regions, ExcelMergeRegion candidate, Class<?> rowClass) {
    if (candidate.columnSpan() <= 1 && candidate.rowSpan() <= 1) {
      return;
    }

    for (ExcelMergeRegion existing : regions) {
      if (sameRegion(existing, candidate)) {
        throw new IllegalStateException(
            "Duplicate inferred merge region for %s: %s"
                .formatted(rowClass.getSimpleName(), describeRegion(candidate)));
      }
      if (overlaps(existing, candidate)) {
        throw new IllegalStateException(
            "Overlapping inferred merge regions for %s: %s vs %s"
                .formatted(
                    rowClass.getSimpleName(), describeRegion(existing), describeRegion(candidate)));
      }
    }

    regions.add(candidate);
  }

  private static boolean sameRegion(ExcelMergeRegion left, ExcelMergeRegion right) {
    return left.scope() == right.scope()
        && left.rowOffset() == right.rowOffset()
        && left.rowSpan() == right.rowSpan()
        && left.startColumnIndex() == right.startColumnIndex()
        && left.columnSpan() == right.columnSpan()
        && left.repeatOnEveryDataRow() == right.repeatOnEveryDataRow();
  }

  private static boolean overlaps(ExcelMergeRegion left, ExcelMergeRegion right) {
    if (left.scope() != right.scope()) {
      return false;
    }

    boolean rowPatternsOverlap =
        switch (left.scope()) {
          case HEADER ->
              rangesOverlap(
                  left.rowOffset(),
                  left.rowOffset() + left.rowSpan() - 1,
                  right.rowOffset(),
                  right.rowOffset() + right.rowSpan() - 1);
          case DATA -> dataRowsOverlap(left, right);
        };

    return rowPatternsOverlap
        && rangesOverlap(
            left.startColumnIndex(),
            left.startColumnIndex() + left.columnSpan() - 1,
            right.startColumnIndex(),
            right.startColumnIndex() + right.columnSpan() - 1);
  }

  private static boolean dataRowsOverlap(ExcelMergeRegion left, ExcelMergeRegion right) {
    if (left.repeatOnEveryDataRow() && right.repeatOnEveryDataRow()) {
      return true;
    }
    if (left.repeatOnEveryDataRow()) {
      return right.rowOffset() >= left.rowOffset();
    }
    if (right.repeatOnEveryDataRow()) {
      return left.rowOffset() >= right.rowOffset();
    }
    return left.rowOffset() == right.rowOffset();
  }

  private static boolean rangesOverlap(int leftStart, int leftEnd, int rightStart, int rightEnd) {
    return leftStart <= rightEnd && rightStart <= leftEnd;
  }

  private static String describeRegion(ExcelMergeRegion region) {
    return "%s[rowOffset=%d,rowSpan=%d,startColumnIndex=%d,columnSpan=%d,repeat=%s]"
        .formatted(
            region.scope(),
            region.rowOffset(),
            region.rowSpan(),
            region.startColumnIndex(),
            region.columnSpan(),
            region.repeatOnEveryDataRow());
  }

  private record ColumnMetadata(Field field, ExcelColumn annotation, int startColumnIndex) {}
}
