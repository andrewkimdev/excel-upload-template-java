package com.foo.excel.config;

import java.util.Set;

public interface ExcelImportConfig {

    /**
     * Row number where primary column headers are located (1-indexed).
     * This is the row used for header matching.
     */
    default int getHeaderRow() { return 1; }

    /**
     * Row number where data starts (1-indexed).
     */
    default int getDataStartRow() { return 2; }

    /**
     * Sheet index to read (0-indexed).
     */
    default int getSheetIndex() { return 0; }

    /**
     * Columns to skip using letter notation (e.g., "A", "G").
     * Default skips column A as it's typically decorative.
     */
    default Set<String> getSkipColumns() {
        return Set.of("A");
    }

    /**
     * Marker string that indicates start of footer/notes section.
     * When found in any cell of a row, stop reading data.
     */
    default String getFooterMarker() { return "※"; }

    /**
     * Field names that form the natural key for upsert operations.
     */
    String[] getNaturalKeyFields();

    /**
     * Name of the error column added to error report Excel.
     */
    default String getErrorColumnName() { return "_ERRORS"; }
}
