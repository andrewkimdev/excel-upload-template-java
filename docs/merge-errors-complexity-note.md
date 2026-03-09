# Row-Error Aggregation Complexity Note

## Summary

This note records the row-error aggregation issue that was identified in the validation pipeline
and the implemented refactor.

The original quadratic pattern existed in three production sites:

- `src/main/java/com/foo/excel/service/pipeline/validation/ExcelValidationService.java`
  - `mergeErrors(List<RowError> target, List<RowError> source)`
- `src/main/java/com/foo/excel/validation/ExcelValidationResult.java`
  - `merge(List<RowError> additionalErrors)`
- `src/main/java/com/foo/excel/validation/WithinFileUniqueConstraintValidator.java`
  - `addErrorToRow(...)`

All three sites grouped `RowError` objects by `rowNumber` using repeated linear scans over a
growing `List<RowError>`.

## Original problem

The common shape was:

```java
for (RowError srcError : source) {
  RowError existing =
      rowErrors.stream()
          .filter(r -> r.getRowNumber() == srcError.getRowNumber())
          .findFirst()
          .orElse(null);

  if (existing != null) {
    existing.getCellErrors().addAll(srcError.getCellErrors());
  } else {
    rowErrors.add(srcError);
  }
}
```

For `WithinFileUniqueConstraintValidator`, the same issue appeared while adding one `CellError`
at a time to a row bucket.

### Complexity

Let:

- `r = existing row bucket count`
- `a = incoming error count`

Then the merge or add step was worst-case:

- `O(r * a)` for list-to-list merging
- `O(n^2)` when the lists are of similar size

For the uniqueness validator, duplicate detection itself already used hash maps
(`seenValues`, `seenKeys`), but row grouping still used repeated list scans. That meant the
duplicate lookup path was efficient while the error-bucketing path was not.

## Why the fix was scoped narrowly

The codebase only had three production matches of this exact issue.

Evidence:

- `ExcelValidationService.mergeErrors()` merged bean-validation errors with within-file uniqueness errors
- `ExcelValidationResult.merge()` merged DB uniqueness errors and parse errors in
  `ExcelImportOrchestrator`
- `WithinFileUniqueConstraintValidator.addErrorToRow()` grouped repeated duplicate hits for the same row

Counterexamples checked and intentionally left unchanged:

- `ExcelErrorReportService` already uses `HashMap<Integer, RowError>` and `HashSet<Integer>` for `O(1)` lookup
- `AAppcarItemDbUniquenessChecker` uses `Set.contains(...)`, not repeated row grouping
- `ExcelParserService` emits parse errors per row during a single row pass and does not repeatedly regroup rows

Because the issue was concentrated in one concept, the chosen fix was:

- introduce one small helper dedicated to row-number-based error aggregation
- refactor only the three known hotspots
- avoid changing `RowError`, `CellError`, parser contracts, report generation, or database checks

This avoided underengineering:

- all known production sites were fixed together

This avoided overengineering:

- no generic collection framework
- no broader performance or profiling infrastructure
- no changes to user-visible error shape

## Implemented design

A helper was added:

- `src/main/java/com/foo/excel/validation/RowErrorAccumulator.java`

Its role is intentionally small:

- keep `List<RowError>` output for compatibility
- maintain a `Map<Integer, RowError>` index for average `O(1)` row lookup
- preserve row order by first appearance
- preserve `CellError` append order within each row
- merge by `rowNumber` without deduplicating cell errors

The helper supports:

- initialize from an empty list or existing `List<RowError>`
- add a full `RowError`
- add a single `CellError` to a row number
- add all errors from another `List<RowError>`

## Why the helper keeps both a `List` and a `Map`

The helper uses two structures for different jobs:

- `List<RowError>` preserves stable output order
- `Map<Integer, RowError>` provides average `O(1)` lookup by `rowNumber`

This is a standard space-for-time tradeoff:

- before: very little extra space, but repeated linear scans
- after: one additional index structure, but near-linear merge cost

The map does not exist as a second full copy of the error payload. In the normal case, the map
stores references to the same `RowError` bucket objects that are already present in the list.

So:

- lookups use the map
- ordered output uses the list
- inserting a new row updates both
- appending to an existing row finds the bucket through the map and mutates that shared bucket

The additional memory cost is therefore mostly:

- one map entry per distinct row number
- not a second full copy of every `RowError`

## Why `normalize(...)` exists

The accumulator mutates row buckets by appending `CellError` values to `existing.getCellErrors()`.
That requires the internal `cellErrors` lists to be mutable.

However, incoming `RowError` values are not guaranteed to be backed by mutable lists. This repo
already creates `RowError` values from immutable inputs in tests and helper paths, for example
using `List.of(...)`.

Without normalization, this kind of merge can fail:

```java
RowError rowError =
    RowError.builder()
        .rowNumber(7)
        .cellErrors(List.of(cellError))
        .build();
```

If the accumulator later does:

```java
existing.getCellErrors().add(otherCellError);
```

that can throw `UnsupportedOperationException`.

`normalize(...)` prevents that by creating the accumulator's own mutable working bucket:

- create a fresh `RowError`
- copy `cellErrors` into a new `ArrayList`

This means there can be one boundary copy when external `RowError` values enter the accumulator,
but that is not the same as duplicating the whole dataset for each lookup.

The internal model is:

- external `RowError` objects: caller-owned input, possibly immutable
- accumulator buckets: internal mutable state
- map: index pointing to those internal buckets
- list: ordered view of those same internal buckets

So the refactor is still primarily an indexing optimization. The normalization step exists for
mutability safety, not because the algorithm depends on keeping duplicate full copies of the data.

## Current behavior after refactor

### `ExcelValidationService`

`mergeErrors(...)` now delegates to `RowErrorAccumulator` instead of repeatedly scanning `target`.

Result:

- same row-level output semantics
- average-case merge cost reduced to `O(target + source)` rather than worst-case quadratic

### `ExcelValidationResult`

`merge(...)` now uses the same accumulator and then recomputes:

- `valid`
- `errorRowCount`
- `totalErrorCount`

The summary-field contract did not change.

### `WithinFileUniqueConstraintValidator`

`checkWithinFileUniqueness(...)` now creates one accumulator for the whole check and passes it
through both uniqueness phases.

Result:

- duplicate detection remains map-based
- row grouping is now also map-based
- multiple uniqueness hits for the same row are merged without repeated scans

## Behavioral guarantees preserved

The refactor intentionally preserves:

- `RowError` as the public error unit
- stable row order based on first appearance
- stable `CellError` append order
- Korean user-facing error messages
- existing error report rendering behavior

No sorting was introduced.
No cell-error deduplication was introduced.
No public validation contract was changed.

## Test coverage added

The following regression coverage was added:

- `ExcelValidationResultTest`
  - merging into a success result marks it invalid and updates counts
  - merging into an existing row appends errors without creating a duplicate row
  - mixed merges preserve row order and summary counts
- `ExcelValidationServiceTest`
  - bean-validation and within-file uniqueness errors on the same row merge into one `RowError`
- `WithinFileUniqueConstraintValidatorTest`
  - duplicate hits across two unique fields on the same row merge into one `RowError`

## Remaining limits

This refactor only addresses row-error aggregation complexity.

It does not attempt to optimize:

- Bean Validation execution cost
- repeated reflection such as `findExcelColumn(...)`
- workbook parsing and I/O
- database uniqueness query cost

If future profiling shows those are material, they should be handled separately rather than folded
into this refactor.
