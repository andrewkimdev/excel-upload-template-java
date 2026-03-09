# `DataFormatter` / `ThreadLocal` Review Note

## Question

In `ExcelParserService`, `DATA_FORMATTER` is a `static final ThreadLocal<DataFormatter>`.

The review comment was roughly:

> `ThreadLocal` is static final, but `remove()` is never called.  
> If the same Tomcat worker thread handles Excel upload multiple times, the instance may keep growing because previous cache is retained.

## Short Answer

The cleanup concern is valid.

The wording that "`@Service` size grows every upload" is not quite accurate.

What actually happens is:

- `ExcelParserService` is a singleton Spring `@Service`
- `ThreadLocal` values are stored on the current thread, not inside the service object itself
- so the retained object is not "the service growing"
- the retained object is "one `DataFormatter` per worker thread"

That distinction matters.

## What `ThreadLocal.remove()` Actually Does

`ThreadLocal.remove()` does **not** just clear some internal cache.

It removes the entire value associated with that `ThreadLocal` for the current thread.

For this code:

```java
private static final ThreadLocal<DataFormatter> DATA_FORMATTER =
    ThreadLocal.withInitial(DataFormatter::new);
```

that means:

1. `DATA_FORMATTER.get()` creates a `DataFormatter` for the current thread if one does not exist
2. that thread keeps the `DataFormatter` unless removed
3. `DATA_FORMATTER.remove()` deletes the thread's stored `DataFormatter`
4. the next `DATA_FORMATTER.get()` creates a fresh `DataFormatter`

So `remove()` does not make `parse()` one-time-use. It just clears the current thread's retained formatter after the call finishes.

## Why the Review Has Real Basis

Apache POI `DataFormatter` is not just a stateless helper.

In local inspection of POI `5.4.1`, `DataFormatter` has an internal `formats` map and caches format instances using `Map.get(...)` / `Map.put(...)`.

That means if a worker thread keeps reusing the same `DataFormatter`, it can retain formatting state across uploads handled by that thread.

So this concern is valid:

- one worker thread may keep one formatter for a long time
- that formatter may keep cached formats from previous uploads

## What Was Overstated in the Review

This part is overstated:

> the singleton `@Service` keeps growing every upload

That is not the precise mechanism.

The service does not itself grow because of `ThreadLocal`.
The per-thread value is retained by the worker thread.

So the accurate phrasing is:

- not "service instance keeps growing"
- but "worker threads may retain a `DataFormatter` and its cache across requests"

## Safer Fix if Keeping `ThreadLocal`

If you keep `ThreadLocal`, the review-friendly pattern is:

```java
try {
  // parse work
} finally {
  DATA_FORMATTER.remove();
}
```

That makes the lifecycle explicit and avoids retaining the formatter between parse calls on the same thread.

## Better Design for This Class

For `ExcelParserService`, a plain local `DataFormatter` is usually cleaner than `ThreadLocal`.

Reason:

- the formatter is only needed during one `parse(...)` call
- it does not need to survive after parsing finishes
- a local variable is naturally thread-safe because it is not shared
- no `ThreadLocal` cleanup policy is needed

So instead of shared thread-bound state:

```java
private static final ThreadLocal<DataFormatter> DATA_FORMATTER =
    ThreadLocal.withInitial(DataFormatter::new);
```

prefer:

```java
public <T> ParseResult<T> parse(...) throws IOException {
  DataFormatter dataFormatter = new DataFormatter();
  // use or pass down dataFormatter
}
```

## "But `getStringValue()` and `getCellStringValue()` Also Use It"

That is not a problem.

Those helper methods can receive `DataFormatter` as a parameter.

Example:

```java
public <T> ParseResult<T> parse(...) throws IOException {
  DataFormatter dataFormatter = new DataFormatter();
  // ...
  parseDataRows(..., dataFormatter);
}

private String getStringValue(Cell cell, DataFormatter dataFormatter) {
  String value = dataFormatter.formatCellValue(cell);
  return value != null ? value.trim() : null;
}

private String getCellStringValue(Cell cell, DataFormatter dataFormatter) {
  if (cell == null) {
    return null;
  }
  return dataFormatter.formatCellValue(cell).trim();
}
```

This keeps:

- one formatter per parse call
- no cross-request shared state
- explicit dependencies

## Recommendation

Best option:

- remove `ThreadLocal`
- create one `DataFormatter` inside `parse(...)`
- pass it to helper methods

Acceptable option:

- keep `ThreadLocal`
- always call `remove()` in `finally`

## Practical Bottom Line

If the goal is "give the reviewer peace of mind" and keep the code easy to defend:

- `ThreadLocal + finally { remove(); }` is safe
- local `new DataFormatter()` per `parse(...)` call is simpler and probably the better design
