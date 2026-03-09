# OPCPackage Workbook Open Decision

## Decision

`SecureExcelUtils.createWorkbook(File)` must not use `try-with-resources` around `OPCPackage`
when returning `new XSSFWorkbook(pkg)`.

Instead, it opens the package, returns the workbook on success, and closes the package only if
`XSSFWorkbook` construction fails.

## Why

`XSSFWorkbook` takes ownership of the `OPCPackage` passed to its constructor.
After a successful return, the caller closes the workbook, and POI closes or reverts the package
from `Workbook.close()`.

This means the following pattern is incorrect:

```java
try (OPCPackage pkg = OPCPackage.open(file, PackageAccess.READ)) {
  return new XSSFWorkbook(pkg);
}
```

Although it looks safe, Java closes `pkg` when leaving the `try` block, including on `return`.
That returns a workbook backed by an already-closed package.

## Failure Mode We Are Fixing

There is still a real cleanup problem to solve:

1. `OPCPackage.open(...)` succeeds
2. `new XSSFWorkbook(pkg)` throws
3. no object has taken ownership of `pkg`
4. the factory method must close `pkg` itself

The implementation therefore performs explicit cleanup only in the constructor-failure path.

## Consequences

- Success path: workbook remains usable and is closed by the caller
- Failure path: opened package does not leak
- Returned behavior stays aligned with existing callers such as the parsing and report flows
