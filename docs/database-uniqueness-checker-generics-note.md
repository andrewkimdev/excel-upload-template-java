# DatabaseUniquenessChecker Generics Note

## Summary

We simplified the database uniqueness contract from `DatabaseUniquenessChecker<T, C>` to `DatabaseUniquenessChecker<M extends MetaData>`.

At the same time, we standardized the metadata generic name across the template contract layer from `C` to `M`:

- `PersistenceHandler<T, M extends MetaData>`
- `TemplateDefinition<T, M extends MetaData>`
- `DatabaseUniquenessChecker<M extends MetaData>`

This made the contracts read more clearly:

- `T` means the parsed Excel row DTO type
- `M` means the template-specific `MetaData` type

## What Changed

Before:

```java
public interface DatabaseUniquenessChecker<T, C extends MetaData> {
  List<RowError> check(
      List<T> rows, Class<T> dtoClass, List<Integer> sourceRowNumbers, C metaData);
}
```

After:

```java
public interface DatabaseUniquenessChecker<M extends MetaData> {
  List<RowError> check(List<Integer> sourceRowNumbers, M metaData);
}
```

Related implementation changes:

- `TemplateDefinition.checkDbUniqueness(...)` no longer accepts parsed rows
- `ExcelImportOrchestrator` no longer passes parsed rows into DB uniqueness checking
- `AAppcarItemDbUniquenessChecker` now checks uniqueness from `sourceRowNumbers + metaData`
- tests and documentation were updated to match the new contract

## Why We Did It

The old interface implied that DB uniqueness checking needed access to parsed row DTOs and `Class<T>`. In this repository, that was not true.

The only implementation, `AAppcarItemDbUniquenessChecker`, derives uniqueness from:

- upload-level metadata entered by the user (`metaData`)
- Excel source row numbers used as part of the persisted ID (`sourceRowNumbers`)

It does not inspect row DTO fields, DTO annotations, or `Class<T>`.

That meant the old generic `T` on `DatabaseUniquenessChecker` was speculative. It preserved a possible future use case, but it did not describe the current dependency surface honestly. Since no real implementation used `T`, keeping it added abstraction without present value.

The rename from `C` to `M` was made for the same reason: `M` is a clearer generic name for a `MetaData` subtype than `C`, especially after the codebase moved away from `CommonData`.

## Design Rule Going Forward

- Keep `T` where row DTO typing is actually used: parsing, validation, persistence, and `TemplateDefinition`
- Use `M` for template-specific metadata types
- Keep `DatabaseUniquenessChecker` metadata-driven unless a real template requires row-aware DB uniqueness later
- If a future template truly needs row DTO data for DB uniqueness, reintroduce that capability in a focused refactor instead of preserving speculative complexity in advance
