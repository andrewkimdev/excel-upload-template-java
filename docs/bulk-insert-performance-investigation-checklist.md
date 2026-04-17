# Bulk Insert Performance Investigation Checklist

## Purpose

This note captures the current understanding of the `aappcar` 10K-row performance regression and
provides a practical checklist for investigating it.

The key point is that the baseline implementation in this repository is not the problem. The
regression appeared after the branch named `excel-upload-with-history` was merged into `develop` in
another project that had copied this codebase.

## Current Context

The situation is:

- This repository's upload implementation performed well before the downstream merge.
- The same implementation, copied into another project, also performed well before merge.
- After `excel-upload-with-history` was merged into `develop` in that downstream project, 10K-row
  upload performance degraded sharply.

That means the main question is not "why is this repository slow?" but:

- what changed in the downstream merged save path
- what extra DB or ORM work now happens during persistence
- why the merged branch changed `saveMs` from a few seconds to over a minute

## Known Timing Evidence

### Fast Version

Observed log:

- `fileMs=340`
- `preCountMs=810`
- `parseMs=6639`
- `validationMs=261`
- `saveMs=6217`
- `totalMs=14317`

What this means:

- 10,000-row save stage completed in about `6.2s`
- average save cost was about `0.62ms/row`
- end-to-end pipeline time was about `14.3s`
- parsing and saving were of similar magnitude

### Slow Version

Observed log:

- `rowsProcessed=10000`
- `fileMs=217`
- `preCountMs=744`
- `parseMs=7073`
- `validationMs=442`
- `saveMs=74978`
- `totalMs=83490`

What this means:

- 10,000-row save stage completed in about `75.0s`
- average save cost was about `7.50ms/row`
- end-to-end pipeline time was about `83.5s`
- the regression is overwhelmingly in the save stage, not in file handling, pre-count, parsing, or
  validation

Observed SQL/log shape near the tail of the same slow run:

- repeated `insert into a_appcar_item (...) values (...)` statements were logged individually
- the sampled item inserts were around `5-6ms` each in `p6spy`
- one `update a_appcar_equip ...` was logged at about `8ms`
- the logged call path reached `KsiaAppcarEquipController.create -> ExcelImportRequestService.upload
  -> ExcelImportOrchestrator.doProcess -> ...ItemPersistenceService.saveAll(...)`

What this adds:

- even in the known slow case, individual row-level SQL statements can still look inexpensive
- the slowdown therefore cannot be diagnosed from per-statement timings alone
- the investigation still needs save-stage sub-timers, SQL counts, and branch-to-branch comparison

## Primary Conclusion From The Logs

The regression is centered in the persistence step measured by
[ExcelImportOrchestrator.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/service/pipeline/ExcelImportOrchestrator.java#L264).

The surrounding stages stayed relatively stable:

- `fileMs`: similar
- `preCountMs`: similar
- `parseMs`: similar
- `validationMs`: similar

Only `saveMs` exploded:

- fast: `6217ms`
- slow: `74978ms`

That is roughly a `12x` regression in the save stage.

## What The Baseline Save Path Looks Like In This Repository

The current baseline implementation in
[AAppcarItemPersistenceService.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/imports/samples/aappcar/service/AAppcarItemPersistenceService.java#L36)
is structurally lean:

- build all item IDs once
- load existing items with one `findAllById(...)`
- build or update entities in memory
- persist in chunks of 100 via `saveAll(...)`
- `flush()` once per chunk
- upsert equip metadata once at the end

Relevant code points:

- one prefetch of existing items at
  [AAppcarItemPersistenceService.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/imports/samples/aappcar/service/AAppcarItemPersistenceService.java#L101)
- chunked save and flush at
  [AAppcarItemPersistenceService.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/imports/samples/aappcar/service/AAppcarItemPersistenceService.java#L109)
- single equip upsert at
  [AAppcarItemPersistenceService.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/imports/samples/aappcar/service/AAppcarItemPersistenceService.java#L65)

The repository is also configured for batching in
[application.properties](/workspaces/excel-upload-template-java/src/main/resources/application.properties#L45):

- `hibernate.jdbc.batch_size=100`
- `hibernate.order_inserts=true`
- `hibernate.order_updates=true`

So the baseline here already matches the "fast" shape.

## Most Likely Investigation Targets

### 1. New History Persistence Added Per Row

Because the downstream regression appeared after merging `excel-upload-with-history`, the first
suspect is that the merged code now writes history rows for each imported item.

Investigate whether the merged save path now does any of the following per row:

- inserts a history entity
- inserts multiple history entities
- queries history state before insert
- updates audit/history metadata inside the same flush cycle

This is the highest-probability explanation.

### 2. New Row-Level Queries Inside The Save Loop

Check whether `develop` introduced repository calls inside the 10K-row persistence loop.

Examples:

- `findById(...)`
- `existsBy...(...)`
- row-level uniqueness checks
- row-level history lookups
- row-level parent/child existence checks

One extra DB round-trip per row is enough to create a regression of this size.

### 3. JPA Lifecycle Semantics Changed

This code uses assigned composite keys and standard `JpaRepository` repositories:

- [AAppcarItem.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/imports/samples/aappcar/persistence/entity/AAppcarItem.java#L27)
- [AAppcarEquip.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/imports/samples/aappcar/persistence/entity/AAppcarEquip.java#L28)
- [AAppcarItemRepository.java](/workspaces/excel-upload-template-java/src/main/java/com/foo/excel/imports/samples/aappcar/persistence/repository/AAppcarItemRepository.java#L7)

That combination can already be sensitive to entity state detection. If the merged branch added any
extra associations, listeners, cascades, or state transitions, Hibernate may now be doing much more
work during `saveAll(...)` and `flush()`.

Check for:

- entity listeners
- audit hooks
- cascade changes
- bidirectional associations
- additional dirty-checking scope
- entity graph growth during persistence

### 4. Flush Became Expensive

The baseline code flushes once per 100 rows, not once per row. If the slow branch still flushes at
the same frequency but `saveMs` is much worse, the flush itself may have become heavier.

Likely reasons:

- more entities are managed per chunk
- history rows are flushed together with item rows
- flush triggers extra SQL not visible from insert timing alone
- commit-time work grew due to constraints, triggers, or listener logic

### 5. Schema Or DB-Side Cost Changed

Even if Java-side code looks similar, DB-side work may now be heavier in `develop`.

Check whether the merged branch introduced:

- extra indexes on the hot tables
- foreign keys involving history tables
- triggers
- audit tables
- expensive default values or generated columns

Individual `INSERT` statements can still look fast in SQL logs while total flush/commit time grows
substantially.

## What Not To Conclude Too Early

Do not conclude any of the following from "individual insert logs are fast":

- that the save stage is healthy
- that batching is equally effective across branches
- that batching is disabled or enabled with certainty just from SQL log shape
- that there are no extra queries
- that there is no ORM overhead
- that logging or proxy overhead is irrelevant

In the fast version, sub-1ms insert logs and `saveMs=6217` are fully compatible.
In the slow version, sampled `INSERT` logs around `5-6ms` and one `UPDATE` around `8ms` still
coexist with `saveMs=74978`, so the total persistence stage is clearly not healthy.

## Investigation Checklist

### 1. Reproduce Both Branches Under Matching Conditions

Use the same:

- input file
- metadata
- DB engine and version
- JDBC URL options
- logging profile
- JVM options
- machine

Record:

- branch name
- commit SHA
- `rowsProcessed`
- `fileMs`
- `preCountMs`
- `parseMs`
- `validationMs`
- `dbUniquenessMs`
- `saveMs`
- `totalMs`
- external wall-clock upload time

### 2. Add Substage Timers Inside `saveAll(...)`

This is the fastest way to localize the slowdown.

Split the save stage into timings such as:

- `buildItemIdsMs`
- `findExistingItemsMs`
- `mapRowsMs`
- `saveItemsMs`
- `upsertEquipMs`
- `saveHistoryMs` if history exists

If history exists in the merged branch, measure it separately rather than hiding it inside one
large `saveMs`.

### 3. Count SQL By Type During The Save Stage

For the slow branch, count:

- total `SELECT`
- total `INSERT`
- total `UPDATE`
- total `DELETE`
- total SQL statements

Key questions:

- did the merged branch add extra row-level `SELECT`s
- did it add one or more history `INSERT`s per row
- did it introduce `UPDATE`s that were not present before
- how many item `INSERT`s and equip `UPDATE`s occur during one 10K-row upload
- whether the observed SQL mix differs from the pre-merge fast branch

### 4. Inspect The Save Loop For Row-Level Repository Access

Search the merged branch save path for:

- `findById(...)`
- `existsBy...(...)`
- `saveAndFlush(...)`
- `flush()` inside a row loop
- history writes inside a row loop
- audit writes inside a row loop

Any of these can explain a `6s -> 75s` shift.

### 5. Compare The Persistence Code Path Directly

Diff the downstream fast branch against `develop` for:

- the persistence service
- any history service
- any audit service
- any entity listeners
- any transaction synchronization hooks
- any custom repositories used by the upload path

Trust the code diff more than impressionistic SQL log reading.

### 6. Compare Entity Mapping Changes

Check whether the merged branch changed:

- cascades
- fetch strategy
- listeners
- associations
- equals/hashCode behavior on entities or IDs
- auditing annotations

The goal is to find anything that would increase flush-time ORM work.

### 7. Compare Schema Differences On The Target DB

Check the actual deployed schema used by the slow branch.

Look for changes on:

- item table
- equip table
- history table
- related foreign keys
- indexes
- triggers

If the merge introduced a history table with heavy indexing or FK checks, that can explain the
regression even when application code looks reasonable.

### 8. Treat Logging As A Secondary Variable, Not The Default Explanation

Logging can hurt performance, but it should not be your first explanation here unless the merged
branch also changed SQL tracing or statement proxying.

The slow-run evidence confirms that this environment is using `p6spy` with a custom formatter that
captures a Java call stack per statement (`P6SpySqlFormatter.formatMessage(...)` and
`P6SpySqlFormatter.createStack(...)`). That does not prove logging is the root cause, but it does
mean stack capture cost is a real comparison point rather than a theoretical one.

Compare:

- `p6spy`
- `datasource-proxy`
- `log4jdbc`
- Hibernate SQL logging
- bind parameter logging
- stack trace capture per statement

If logging setup is identical across branches, keep the focus on persistence behavior and DB work.

## Working Hypothesis

Based on the branch history and the timing pattern, the most likely explanation is:

- the downstream merge introduced extra persistence work tied to history or auditing
- that extra work happens inside the same save transaction as the 10K-row import
- the extra work is not visible from file/parse/validation timings because it is concentrated in
  `saveMs`

The most likely concrete forms are:

- one or more extra history inserts per imported row
- one or more extra row-level queries per imported row
- heavier flush behavior caused by history entities, cascades, listeners, or auditing

## Recommended Order Of Investigation

1. Diff the downstream fast branch against `develop` around the upload persistence path.
2. Add substage timers inside `saveAll(...)` on the slow branch.
3. Count SQL by type during the slow save stage.
4. Inspect history and audit persistence for row-level work.
5. Inspect entity mapping and listener changes.
6. Inspect schema differences on the real target database.

## Bottom Line

The current evidence does not point to parsing, validation, or file handling.

It points to the save stage.

More specifically, it points to something introduced by the downstream merge into `develop`, with
history-related persistence work as the leading suspect.
