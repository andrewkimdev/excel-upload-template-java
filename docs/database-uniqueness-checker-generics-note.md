# DatabaseUniquenessChecker Generics Note

## Summary

We keep the database uniqueness contract row-oriented as `DatabaseUniquenessChecker<T, M extends MetaData>`.

At the same time, we standardized the metadata generic name across the template contract layer from `C` to `M`:

- `PersistenceHandler<T, M extends MetaData>`
- `ExcelImportDefinition<T, M extends MetaData>`
- `DatabaseUniquenessChecker<T, M extends MetaData>`

This made the contracts read more clearly:

- `T` means the parsed Excel row DTO type
- `M` means the import-specific `MetaData` type

## What Changed

Before:

```java
public interface DatabaseUniquenessChecker<T, C extends MetaData> {
  List<RowError> check(
      List<T> rows, Class<T> rowClass, List<Integer> sourceRowNumbers, C metaData);
}
```

After:

```java
public interface DatabaseUniquenessChecker<T, M extends MetaData> {
  List<RowError> check(
      List<T> rows, Class<T> rowClass, List<Integer> sourceRowNumbers, M metaData);
}
```

Related implementation changes:

- `ExcelImportDefinition.checkDbUniqueness(...)` keeps parsed rows in its signature
- `ExcelImportOrchestrator` continues passing parsed rows into DB uniqueness checking
- `AAppcarItemDatabaseUniquenessChecker` remains a metadata-driven special case inside a row-oriented contract
- tests and documentation were updated to match the new contract

## Why We Did It

From a code reader's perspective, a type named `DatabaseUniquenessChecker` naturally suggests row-oriented duplicate checking against persisted data. Keeping `T` in the contract preserves that expectation.

The only implementation, `AAppcarItemDatabaseUniquenessChecker`, derives uniqueness from:

- upload-level metadata entered by the user (`metaData`)
- Excel source row numbers used as part of the persisted ID (`sourceRowNumbers`)

It does not inspect row DTO fields, DTO annotations, or `Class<T>`.

Even so, we keep the row DTO type in the contract because the abstraction is intended to describe the general approach of DB duplicate checking across templates, not just the needs of the current sample implementation. `AAppcarItem` is treated as a business-specific exception inside that broader shape.

The rename from `C` to `M` was still kept because `M` is a clearer generic name for a `MetaData` subtype than `C`, especially after the codebase moved away from `CommonData`.

## Design Rule Going Forward

- Keep `T` on `DatabaseUniquenessChecker` to preserve the reader-facing row-oriented design of the abstraction
- Keep `T` where row DTO typing is actually used: parsing, validation, persistence, and `ExcelImportDefinition`
- Use `M` for import-specific metadata types
- Allow individual checker implementations to ignore row DTO values when business logic is driven by upload-level metadata
- Revisit the contract only if future template patterns show that another abstraction shape is clearly better across multiple implementations
