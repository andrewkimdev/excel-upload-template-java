# POI Error Report Cost Note

The long runtime on large failed uploads is not just "POI being slow" in the abstract. It is
mostly a consequence of how this module currently asks POI to build error workbooks.

## Current full-report path

`ExcelErrorReportService` does the following for non-truncated row-level failures:

1. Reopens the original file as a full `XSSFWorkbook`
2. Creates a new workbook
3. Copies every sheet
4. Copies rows, cells, column widths, merged regions, styles, and values
5. Adds the `_ERRORS` column
6. Writes the reconstructed workbook back to disk

This means the service is effectively cloning the workbook, not merely writing an error summary.

## Why this becomes expensive

- `XSSFWorkbook` is relatively heavy for styled `.xlsx` files
- Full workbook copy multiplies the cost of sheet width/style/merged-region handling
- Serialization cost is paid again when the copied workbook is written

## Measured example

Observed on a 6,756-row failed upload:

- `fileMs=133`
- `preCountMs=211`
- `parseMs=145547`
- `validationMs=148`
- `dbUniquenessMs=26`
- `errorReportMs=326223`
- `totalMs=472296`

The dominant costs were parsing and full error workbook generation. Validation and DB uniqueness
were negligible in comparison.

## Conclusion

The cost is not inherent to every POI-based Excel write. It is mostly driven by the current
full-fidelity workbook-copy strategy. POI makes that kind of cloning expensive, but the major win
comes from changing the strategy:

- skip workbook generation entirely for upload-level blockers
- generate compact summary workbooks for truncated row-level failures
- reserve full workbook copy for smaller, non-truncated row-level failures
