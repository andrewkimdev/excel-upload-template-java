package com.foo.excel.validation;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public final class RowErrorAccumulator {

  private final List<RowError> rowErrors;
  private final Map<Integer, RowError> rowErrorsByRowNumber;

  public RowErrorAccumulator() {
    this.rowErrors = new ArrayList<>();
    this.rowErrorsByRowNumber = new HashMap<>();
  }

  public RowErrorAccumulator(List<RowError> existingRowErrors) {
    this.rowErrors = new ArrayList<>();
    this.rowErrorsByRowNumber = new HashMap<>();

    for (RowError rowError : existingRowErrors) {
      RowError normalized = normalize(rowError);
      rowErrors.add(normalized);
      rowErrorsByRowNumber.put(normalized.getRowNumber(), normalized);
    }
  }

  public void addRowError(RowError rowError) {
    RowError existing = rowErrorsByRowNumber.get(rowError.getRowNumber());
    if (existing != null) {
      existing.getCellErrors().addAll(rowError.getCellErrors());
      return;
    }

    RowError normalized = normalize(rowError);
    rowErrors.add(normalized);
    rowErrorsByRowNumber.put(normalized.getRowNumber(), normalized);
  }

  public void addCellError(int rowNumber, CellError cellError) {
    RowError existing = rowErrorsByRowNumber.get(rowNumber);
    if (existing != null) {
      existing.getCellErrors().add(cellError);
      return;
    }

    RowError rowError =
        RowError.builder().rowNumber(rowNumber).cellErrors(new ArrayList<>(List.of(cellError))).build();
    rowErrors.add(rowError);
    rowErrorsByRowNumber.put(rowNumber, rowError);
  }

  public void addAll(List<RowError> additionalRowErrors) {
    for (RowError rowError : additionalRowErrors) {
      addRowError(rowError);
    }
  }

  public List<RowError> toList() {
    return rowErrors;
  }

  private RowError normalize(RowError rowError) {
    return RowError.builder()
        .rowNumber(rowError.getRowNumber())
        .cellErrors(new ArrayList<>(rowError.getCellErrors()))
        .build();
  }
}
