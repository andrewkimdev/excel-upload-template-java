# How to Read This Codebase

The codebase is ~2,000 lines of production code and ~2,000 lines of tests across 38 Java files — very manageable.

---

## Phase 1: Orientation (read once, skim)

1. **SPEC.md** — You wrote this, but re-read it now so the intended design is fresh
2. **CLAUDE.md** — Coding standards and resolved tech debt; establishes what "correct" looks like
3. **README.md** — Architecture overview
4. **build.gradle** — Dependencies, versions, what's actually pulled in

## Phase 2: The annotation DSL (~130 lines)

This is the "API" your users see when defining a template. Read these first because every service references them.

5. **ExcelColumn.java** — The core annotation
6. **HeaderMatchMode.java** — Matching strategy enum
7. **ExcelUnique.java** — Single-field uniqueness
8. **ExcelCompositeUnique.java** + **ExcelCompositeUniques.java** — Multi-field uniqueness

## Phase 3: Configuration & wiring (~100 lines)

9. **ExcelImportConfig.java** — Interface each template implements
10. **ExcelImportProperties.java** — `@ConfigurationProperties` for limits
11. **TemplateDefinition.java** — The bean that ties a template together (auto-discovery mechanism)

## Phase 4: The processing pipeline (core logic, ~770 lines)

Read in execution order — this is how an upload flows through the system:

12. **ExcelUploadController.java** (238 lines) — Entry point, HTTP handling
13. **SecureExcelUtils.java** (236 lines) — Security checks run before anything else
14. **ExcelImportOrchestrator.java** (138 lines) — **Read this carefully** — it's the conductor that calls everything below
15. **ExcelParserService.java** (364 lines) — **Largest file**, POI workbook → DTO conversion. Take your time here
16. **ExcelColumnUtil.java** (72 lines) — Reflection helper for annotations
17. **ExcelConversionService.java** (140 lines) — Cell value → field type conversion
18. **ExcelValidationService.java** (123 lines) — JSR-380 + custom validation
19. **UniqueConstraintValidator.java** (172 lines) — In-file + DB uniqueness enforcement
20. **ExcelErrorReportService.java** (102 lines) — Error Excel file generation

## Phase 5: Validation DTOs (~45 lines, quick)

21. **CellError.java**, **RowError.java**, **ExcelValidationResult.java** — Data carriers for validation results

## Phase 6: The sample template (~380 lines)

This is a complete worked example. Read it as a cohesive unit:

22. **TariffExemptionDto.java** — DTO with annotations (shows how the DSL is used)
23. **TariffExemptionImportConfig.java** — Config implementation
24. **TariffExemption.java** — JPA entity
25. **TariffExemptionRepository.java** — Spring Data repo
26. **TariffExemptionService.java** — Persistence handler
27. **TariffExemptionDbUniquenessChecker.java** — DB-level uniqueness
28. **TariffExemptionTemplateConfig.java** — Wires the `TemplateDefinition` bean

## Phase 7: Remaining services

29. **TempFileCleanupService.java** — Scheduled cleanup
30. **PersistenceHandler.java** + **DatabaseUniquenessChecker.java** — SPI interfaces

## Phase 8: Web layer

31. **application.properties**
32. **upload.html** + **result.html**
33. **style.css**

## Phase 9: Tests (read alongside or after the source)

Read in the same pipeline order. The tests double as documentation of edge cases:

34. **SecureExcelUtilsTest.java** (377 lines)
35. **ExcelParserServiceTest.java** (419 lines) — largest test
36. **ExcelConversionServiceTest.java** (235 lines)
37. **ExcelValidationServiceTest.java** (209 lines)
38. **UniqueConstraintValidatorTest.java** (168 lines)
39. **ExcelErrorReportServiceTest.java** (320 lines)
40. **ExcelColumnUtilTest.java** (93 lines)
41. **ExcelImportIntegrationTest.java** (234 lines) — end-to-end, read last

---

## Before you start reading

Run the app and upload a file so you have a mental model of what the code produces:

```bash
./gradlew bootRun
```

Then visit `http://localhost:8080`, upload a valid file, upload an invalid one, and see the error report. Having that experience makes the code much easier to follow.

---

## Things to watch for as you read

- **The orchestrator is the spine.** If you only read one file deeply, make it `ExcelImportOrchestrator.java`. It shows the full parse → validate → persist → report flow in one place.
- **SPEC.md drift.** Your spec mentions `required()` on `@ExcelColumn` and `getSkipColumnIndices()` on the config interface — the implementation diverged from these. Worth noting whether you want to update the spec or the code.
- **Generic type erasure.** The orchestrator has a `@SuppressWarnings("unchecked")` cast that's inherent to how `TemplateDefinition<T>` works with Spring's bean registry. Understand why it's unavoidable.
- **Security surface.** `SecureExcelUtils` is 236 lines of defense (zip bombs, XXE, macro detection, path traversal). Worth understanding what threats are covered and whether there are gaps for your use case.
- **Test coverage gaps.** There's no dedicated test for `ExcelUploadController`, `TempFileCleanupService`, or the tariff exemption template classes. The integration test covers some controller paths, but mocking-level controller tests are absent.
- **The `ExcelParserService.ParseResult` inner class** — check whether this could be a Java record per your CLAUDE.md rules. Same for any other static inner DTOs.
