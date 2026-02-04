package com.foo.excel.validation;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class RowError {
    private int rowNumber;
    @Builder.Default
    private List<CellError> cellErrors = new ArrayList<>();

    public String getFormattedMessage() {
        return cellErrors.stream()
                .map(e -> "[" + e.getColumnLetter() + "] " + e.getMessage())
                .collect(Collectors.joining("; "));
    }
}
