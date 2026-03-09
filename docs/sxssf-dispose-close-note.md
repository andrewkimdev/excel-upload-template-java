# SXSSFWorkbook `dispose()` and `close()`

## Conclusion

In this repository, explicit `SXSSFWorkbook.dispose()` is not required in
[`ExcelErrorReportService`](../src/main/java/com/foo/excel/service/pipeline/report/ExcelErrorReportService.java)
because the project uses Apache POI `5.4.1`, and in that line of POI changes
`SXSSFWorkbook.close()` disposes the temp files internally.

The current code uses try-with-resources:

```java
try (XSSFWorkbook targetXssf = new XSSFWorkbook();
    SXSSFWorkbook sxssfWb = new SXSSFWorkbook(targetXssf, 100)) {
  // ...
}
```

That means `close()` is invoked automatically, which is sufficient for temp file
cleanup in this version.

## Why this is the answer

Older POI guidance often told callers to invoke `dispose()` explicitly for
`SXSSFWorkbook` temp file cleanup. That advice became outdated after POI changed
`close()` to call `dispose()` internally.

For this project:

- `build.gradle` declares `org.apache.poi:poi-ooxml:5.4.1`
- local verification of POI `5.4.1` bytecode shows
  `SXSSFWorkbook.close()` invokes `dispose()`
- therefore explicit `dispose()` before `close()` would be redundant

## Public references

- POI dev list thread proposing the behavior change:
  https://marc.info/?l=poi-dev&m=170741287425712&w=2
- POI dev list archive noting the merged change, added test, and deprecation of
  `SXSSFWorkbook#dispose`:
  https://www.mail-archive.com/dev%40poi.apache.org/msg40475.html
- Later POI dev list note confirming tests moved to `close()` and `dispose()`
  deprecation:
  https://www.mail-archive.com/dev%40poi.apache.org/msg43084.html
- Current SXSSFWorkbook Javadoc:
  https://poi.apache.org/apidocs/dev/org/apache/poi/xssf/streaming/SXSSFWorkbook.html

## Recommended review response

`dispose()` used to need explicit handling in older Apache POI guidance, but in
newer POI `SXSSFWorkbook.close()` disposes the temp files internally. This
project uses POI `5.4.1`, and the workbook is managed by try-with-resources, so
an additional explicit `dispose()` call is not necessary.
