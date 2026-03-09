package com.foo.excel.validation;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;

class ExcelValidationResultTest {

  @Test
  void merge_intoSuccessResult_marksInvalidAndUpdatesCounts() {
    ExcelValidationResult result = ExcelValidationResult.success(2);

    result.merge(List.of(rowError(7, cellError("goodsDes", "물품명 오류"))));

    assertThat(result.isValid()).isFalse();
    assertThat(result.getErrorRowCount()).isEqualTo(1);
    assertThat(result.getTotalErrorCount()).isEqualTo(1);
    assertThat(result.getRowErrors()).hasSize(1);
  }

  @Test
  void merge_existingRow_appendsCellErrorWithoutAddingNewRow() {
    ExcelValidationResult result =
        ExcelValidationResult.failure(2, List.of(rowError(7, cellError("goodsDes", "물품명 오류"))));

    result.merge(List.of(rowError(7, cellError("hsno", "HSK 오류"))));

    assertThat(result.getRowErrors()).hasSize(1);
    assertThat(result.getRowErrors().get(0).getCellErrors())
        .hasSize(2)
        .anySatisfy(error -> assertThat(error.fieldName()).isEqualTo("goodsDes"))
        .anySatisfy(error -> assertThat(error.fieldName()).isEqualTo("hsno"));
    assertThat(result.getErrorRowCount()).isEqualTo(1);
    assertThat(result.getTotalErrorCount()).isEqualTo(2);
  }

  @Test
  void merge_mixedExistingAndNewRows_preservesOrderAndCounts() {
    ExcelValidationResult result =
        ExcelValidationResult.failure(3, List.of(rowError(7, cellError("goodsDes", "물품명 오류"))));

    result.merge(
        List.of(
            rowError(7, cellError("hsno", "HSK 오류")),
            rowError(8, cellError("taxRate", "관세율 오류"))));

    assertThat(result.getRowErrors()).hasSize(2);
    assertThat(result.getRowErrors().get(0).getRowNumber()).isEqualTo(7);
    assertThat(result.getRowErrors().get(1).getRowNumber()).isEqualTo(8);
    assertThat(result.getErrorRowCount()).isEqualTo(2);
    assertThat(result.getTotalErrorCount()).isEqualTo(3);
  }

  private RowError rowError(int rowNumber, CellError cellError) {
    return RowError.builder().rowNumber(rowNumber).cellErrors(List.of(cellError)).build();
  }

  private CellError cellError(String fieldName, String message) {
    return CellError.builder()
        .columnIndex(-1)
        .columnLetter("?")
        .fieldName(fieldName)
        .headerName(fieldName)
        .message(message)
        .build();
  }
}
