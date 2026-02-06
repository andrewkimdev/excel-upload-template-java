# Excel Upload Feature - Final Specification v2

## Change Log
| Version | Changes |
|---------|---------|
| v1 | Initial specification |
| v2 | Added .xls auto-conversion, column letter notation, confirmed structure patterns |

---

## Core Decisions (Finalized)

### Data Operations
| Decision | Value |
|----------|-------|
| Row identity strategy | Upsert on natural key |
| Validation failure scope | Reject entire file (all-or-nothing) |
| Primary format | `.xlsx` |
| Legacy format | `.xls` (auto-convert to `.xlsx` on upload) |

### Excel Template Structure (Confirmed Pattern)
| Decision | Value |
|----------|-------|
| Structure pattern | Title → Grouped Headers → Primary Header → Data → Footer Notes |
| Column A | Always decorative (skip by default) |
| Header row | Configurable per DTO (primary column names row) |
| Data start row | Configurable per DTO |
| Footer detection | Marker-based (default: "※") |
| Header footnotes | Always present (e.g., 규격1), 모델명1)) — use CONTAINS matching |

### Data Types
| Type | Format | Notes |
|------|--------|-------|
| Date | `yyyy-MM-dd` | Strict |
| DateTime | `yyyy-MM-dd HH:mm:ss` | Strict |
| Boolean | `true`/`false` | Blank → false, `Y`/`y` → true |
| String | As-is | Trimmed |
| Numeric | Java BigDecimal | For precision |

### File Handling
| Decision | Value |
|----------|-------|
| Processing storage | Temp file on disk |
| Retention period | 30 days (configurable via `application.properties`) |
| Max file size | 10 MB (configurable) |
| Max rows | 10,000 (configurable) |
| Auto-conversion | `.xls` → `.xlsx` on upload |

---

## Column Reference System

### Dual Notation Support

Both **numeric index** (0-based) and **Excel letter notation** (A, B, ..., Z, AA, AB, ...) are supported.

```java
// These are equivalent:
@ExcelColumn(header = "물품명", index = 2)      // Numeric: 0-indexed
@ExcelColumn(header = "물품명", column = "C")   // Letter: Excel notation

// Letter notation is preferred for readability
@ExcelColumn(header = "HSK No", column = "F")   // Clear: Column F
@ExcelColumn(header = "HSK No", index = 5)      // Less clear: what column is 5?
```

### Column Letter Utility Class

```java
package com.foo.excel.util;

public final class ExcelColumnUtil {
    
    private ExcelColumnUtil() {}
    
    /**
     * Convert Excel column letter to 0-based index.
     * A=0, B=1, ..., Z=25, AA=26, AB=27, ...
     */
    public static int letterToIndex(String column) {
        if (column == null || column.isBlank()) {
            return -1;
        }
        
        String col = column.toUpperCase().trim();
        int index = 0;
        
        for (int i = 0; i < col.length(); i++) {
            char c = col.charAt(i);
            if (c < 'A' || c > 'Z') {
                throw new IllegalArgumentException(
                    "Invalid column letter: " + column);
            }
            index = index * 26 + (c - 'A' + 1);
        }
        
        return index - 1;  // Convert to 0-based
    }
    
    /**
     * Convert 0-based index to Excel column letter.
     * 0=A, 1=B, ..., 25=Z, 26=AA, 27=AB, ...
     */
    public static String indexToLetter(int index) {
        if (index < 0) {
            throw new IllegalArgumentException(
                "Column index must be non-negative: " + index);
        }
        
        StringBuilder sb = new StringBuilder();
        int col = index + 1;  // Convert to 1-based for calculation
        
        while (col > 0) {
            int remainder = (col - 1) % 26;
            sb.insert(0, (char) ('A' + remainder));
            col = (col - 1) / 26;
        }
        
        return sb.toString();
    }
    
    /**
     * Parse column reference - accepts both letter and numeric string.
     * "C" → 2, "3" → 3, "AA" → 26
     */
    public static int parseColumnReference(String ref) {
        if (ref == null || ref.isBlank()) {
            return -1;
        }
        
        String trimmed = ref.trim();
        
        // Check if purely numeric
        if (trimmed.matches("\\d+")) {
            return Integer.parseInt(trimmed);
        }
        
        // Otherwise treat as letter notation
        return letterToIndex(trimmed);
    }
}
```

### Quick Reference Table

| Letter | Index | Letter | Index | Letter | Index |
|--------|-------|--------|-------|--------|-------|
| A | 0 | J | 9 | S | 18 |
| B | 1 | K | 10 | T | 19 |
| C | 2 | L | 11 | U | 20 |
| D | 3 | M | 12 | V | 21 |
| E | 4 | N | 13 | W | 22 |
| F | 5 | O | 14 | X | 23 |
| G | 6 | P | 15 | Y | 24 |
| H | 7 | Q | 16 | Z | 25 |
| I | 8 | R | 17 | AA | 26 |

---

## Annotations

### @ExcelColumn

```java
package com.foo.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelColumn {
    
    /**
     * Expected header text.
     * Matched against the header row using the specified matchMode.
     */
    String header();
    
    /**
     * Column letter (Excel notation): "A", "B", ..., "Z", "AA", "AB", ...
     * Takes precedence over index() if both are specified.
     * Empty string means "auto-detect from header".
     */
    String column() default "";
    
    /**
     * Column index (0-based): 0=A, 1=B, 2=C, ...
     * Used if column() is not specified.
     * -1 means "auto-detect from header".
     */
    int index() default -1;
    
    /**
     * Whether this field is required (non-null, non-blank for strings).
     */
    boolean required() default false;
    
    /**
     * Date/DateTime format pattern.
     */
    String dateFormat() default "yyyy-MM-dd";
    
    /**
     * Header matching mode.
     */
    HeaderMatchMode matchMode() default HeaderMatchMode.CONTAINS;
    
    /**
     * Custom error message prefix for validation errors on this column.
     */
    String errorPrefix() default "";
}
```

### @HeaderMatchMode

```java
package com.foo.excel.annotation;

public enum HeaderMatchMode {
    
    /**
     * Header must match exactly (case-insensitive, trimmed).
     */
    EXACT,
    
    /**
     * Header contains this text (useful for "규격1)" matching "규격").
     */
    CONTAINS,
    
    /**
     * Header starts with this text.
     */
    STARTS_WITH,
    
    /**
     * Header matches this regex pattern.
     */
    REGEX
}
```

### @ExcelUnique

```java
package com.foo.excel.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

@Target(ElementType.FIELD)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelUnique {
    
    /**
     * Error message when uniqueness is violated.
     */
    String message() default "중복된 값입니다";
    
    /**
     * Whether to check against existing database records.
     */
    boolean checkDatabase() default true;
    
    /**
     * Whether to check for duplicates within the uploaded file.
     */
    boolean checkWithinFile() default true;
}
```

### @ExcelCompositeUnique

```java
package com.foo.excel.annotation;

import java.lang.annotation.*;

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
@Repeatable(ExcelCompositeUniques.class)
public @interface ExcelCompositeUnique {
    
    /**
     * Field names that together form a unique constraint.
     */
    String[] fields();
    
    /**
     * Error message when composite uniqueness is violated.
     */
    String message() default "중복된 조합입니다";
}

@Target(ElementType.TYPE)
@Retention(RetentionPolicy.RUNTIME)
public @interface ExcelCompositeUniques {
    ExcelCompositeUnique[] value();
}
```

---

## ExcelImportConfig Interface

```java
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
     * Columns to skip during parsing (0-indexed).
     * Default skips column A (index 0) as it's typically decorative.
     */
    default Set<Integer> getSkipColumnIndices() {
        return Set.of(0);
    }
    
    /**
     * Alternative: columns to skip using letter notation.
     * Override this for better readability.
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
```

---

## Example DTO (Based on SAMPLE.xls)

```java
package com.foo.excel.dto;

import com.foo.excel.annotation.*;
import com.foo.excel.config.ExcelImportConfig;
import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.util.Set;

@Data
@ExcelCompositeUnique(
    fields = {"itemName", "specification", "hsCode"},
    message = "물품명 + 규격 + HSK 조합이 중복됩니다"
)
public class TariffExemptionDto implements ExcelImportConfig {
    
    // Column B: 순번
    @ExcelColumn(header = "순번", column = "B")
    private Integer sequenceNo;
    
    // Column C: 물품명
    @ExcelColumn(header = "물품명", column = "C", required = true)
    @NotBlank(message = "물품명은 필수 입력 항목입니다")
    @Size(max = 100, message = "물품명은 100자 이내로 입력하세요")
    private String itemName;
    
    // Column D: 규격1)
    @ExcelColumn(header = "규격", column = "D", matchMode = HeaderMatchMode.STARTS_WITH)
    @Size(max = 200, message = "규격은 200자 이내로 입력하세요")
    private String specification;
    
    // Column E: 모델명1)
    @ExcelColumn(header = "모델명", column = "E", matchMode = HeaderMatchMode.STARTS_WITH)
    @Size(max = 100, message = "모델명은 100자 이내로 입력하세요")
    private String modelName;
    
    // Column F: HSK No (merged F-G, read from F)
    @ExcelColumn(header = "HSK", column = "F", matchMode = HeaderMatchMode.CONTAINS)
    @Pattern(regexp = "^\\d{4}\\.\\d{2}-\\d{4}$", message = "HSK 형식이 올바르지 않습니다 (예: 8481.80-2000)")
    private String hsCode;
    
    // Column H: 관세율
    @ExcelColumn(header = "관세율", column = "H")
    @DecimalMin(value = "0", message = "관세율은 0 이상이어야 합니다")
    @DecimalMax(value = "100", message = "관세율은 100 이하여야 합니다")
    private BigDecimal tariffRate;
    
    // Column I: 단가($)
    @ExcelColumn(header = "단가", column = "I", matchMode = HeaderMatchMode.CONTAINS)
    @DecimalMin(value = "0", message = "단가는 0 이상이어야 합니다")
    private BigDecimal unitPrice;
    
    // Column J: 소요량(제조용) - merged J-K, read from J
    @ExcelColumn(header = "제조용", column = "J", matchMode = HeaderMatchMode.CONTAINS)
    @Min(value = 0, message = "제조용 소요량은 0 이상이어야 합니다")
    private Integer qtyForManufacturing;
    
    // Column L: 소요량(수리용) - merged L-M, read from L
    @ExcelColumn(header = "수리용", column = "L", matchMode = HeaderMatchMode.CONTAINS)
    @Min(value = 0, message = "수리용 소요량은 0 이상이어야 합니다")
    private Integer qtyForRepair;
    
    // Column N: 연간수입예상금액($)
    @ExcelColumn(header = "연간수입", column = "N", matchMode = HeaderMatchMode.CONTAINS)
    @DecimalMin(value = "0", message = "연간수입 예상금액은 0 이상이어야 합니다")
    private BigDecimal annualImportEstimate;
    
    // Column O: 심의결과 - merged O-P, read from O (output field, optional)
    @ExcelColumn(header = "심의결과", column = "O")
    private String reviewResult;
    
    // Column Q: 연간예상소요량
    @ExcelColumn(header = "연간 예상소요량", column = "Q", matchMode = HeaderMatchMode.CONTAINS)
    @Min(value = 0, message = "연간 예상소요량은 0 이상이어야 합니다")
    private Integer annualExpectedQty;
    
    // ========== ExcelImportConfig Implementation ==========
    
    @Override
    public int getHeaderRow() {
        return 4;  // Row 4 contains primary column headers
    }
    
    @Override
    public int getDataStartRow() {
        return 7;  // Data starts at row 7
    }
    
    @Override
    public Set<String> getSkipColumns() {
        return Set.of("A", "G", "K", "M", "P");  // Decorative + merged slave columns
    }
    
    @Override
    public String getFooterMarker() {
        return "※";
    }
    
    @Override
    public String[] getNaturalKeyFields() {
        return new String[]{"itemName", "specification", "hsCode"};
    }
}
```

---

## File Conversion Service

```java
package com.foo.excel.service;

import org.apache.poi.hssf.usermodel.HSSFWorkbook;
import org.apache.poi.ss.usermodel.Workbook;
import org.apache.poi.ss.usermodel.WorkbookFactory;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;

@Service
public class ExcelConversionService {
    
    /**
     * Ensure the file is in .xlsx format.
     * If .xls is uploaded, convert to .xlsx.
     * 
     * @param file Uploaded file
     * @param tempDir Directory for temporary files
     * @return Path to .xlsx file (may be converted)
     */
    public Path ensureXlsxFormat(MultipartFile file, Path tempDir) throws IOException {
        String originalName = file.getOriginalFilename();
        
        if (originalName == null) {
            throw new IllegalArgumentException("파일명이 없습니다");
        }
        
        String lowerName = originalName.toLowerCase();
        
        if (lowerName.endsWith(".xlsx")) {
            // Already xlsx - save directly
            Path targetPath = tempDir.resolve(originalName);
            file.transferTo(targetPath);
            return targetPath;
            
        } else if (lowerName.endsWith(".xls")) {
            // Convert xls to xlsx
            return convertXlsToXlsx(file, tempDir, originalName);
            
        } else {
            throw new IllegalArgumentException(
                "지원하지 않는 파일 형식입니다. .xlsx 또는 .xls 파일만 업로드 가능합니다.");
        }
    }
    
    private Path convertXlsToXlsx(MultipartFile file, Path tempDir, String originalName) 
            throws IOException {
        
        // Read as HSSFWorkbook (.xls)
        try (InputStream is = file.getInputStream();
             HSSFWorkbook hssfWorkbook = new HSSFWorkbook(is)) {
            
            // Create new XSSFWorkbook and copy content
            XSSFWorkbook xssfWorkbook = convertWorkbook(hssfWorkbook);
            
            // Generate new filename
            String newName = originalName.substring(0, originalName.lastIndexOf('.')) + ".xlsx";
            Path targetPath = tempDir.resolve(newName);
            
            // Write xlsx
            try (OutputStream os = Files.newOutputStream(targetPath)) {
                xssfWorkbook.write(os);
            }
            
            xssfWorkbook.close();
            return targetPath;
        }
    }
    
    private XSSFWorkbook convertWorkbook(HSSFWorkbook hssfWorkbook) {
        // For simple conversion, we can use POI's built-in mechanisms
        // or implement sheet-by-sheet, row-by-row copy
        
        // Simple approach: Write to temp xlsx via streaming
        // Note: This is a simplified version. For production, consider
        // using a library like 'poi-converter' or manual cell-by-cell copy
        // to preserve all formatting.
        
        XSSFWorkbook xssfWorkbook = new XSSFWorkbook();
        
        for (int i = 0; i < hssfWorkbook.getNumberOfSheets(); i++) {
            var hssfSheet = hssfWorkbook.getSheetAt(i);
            var xssfSheet = xssfWorkbook.createSheet(hssfSheet.getSheetName());
            
            // Copy merged regions
            for (var mergedRegion : hssfSheet.getMergedRegions()) {
                xssfSheet.addMergedRegion(mergedRegion);
            }
            
            // Copy rows and cells
            for (var hssfRow : hssfSheet) {
                var xssfRow = xssfSheet.createRow(hssfRow.getRowNum());
                
                for (var hssfCell : hssfRow) {
                    var xssfCell = xssfRow.createCell(hssfCell.getColumnIndex());
                    copyCellValue(hssfCell, xssfCell);
                }
            }
        }
        
        return xssfWorkbook;
    }
    
    private void copyCellValue(org.apache.poi.ss.usermodel.Cell source,
                               org.apache.poi.ss.usermodel.Cell target) {
        switch (source.getCellType()) {
            case STRING -> target.setCellValue(source.getStringCellValue());
            case NUMERIC -> target.setCellValue(source.getNumericCellValue());
            case BOOLEAN -> target.setCellValue(source.getBooleanCellValue());
            case FORMULA -> target.setCellFormula(source.getCellFormula());
            case BLANK -> target.setBlank();
            default -> { /* ignore */ }
        }
    }
}
```

---

## Project Structure

```
src/main/java/com/foo/excel/
├── ExcelUploadApplication.java
├── controller/
│   └── ExcelUploadController.java
├── service/
│   ├── ExcelConversionService.java      # .xls → .xlsx conversion
│   ├── ExcelParserService.java          # Reads Excel → List<DTO>
│   ├── ExcelValidationService.java      # JSR-380 + custom validation
│   ├── ExcelErrorReportService.java     # Generates error-annotated Excel
│   └── ExcelImportOrchestrator.java     # Orchestrates full flow
├── validation/
│   ├── ExcelValidationResult.java
│   ├── RowError.java
│   ├── CellError.java
│   └── UniqueConstraintValidator.java
├── annotation/
│   ├── ExcelColumn.java
│   ├── ExcelUnique.java
│   ├── ExcelCompositeUnique.java
│   ├── ExcelCompositeUniques.java
│   └── HeaderMatchMode.java
├── config/
│   ├── ExcelImportConfig.java           # Interface for DTO config
│   └── ExcelImportProperties.java       # application.properties binding
├── dto/
│   └── TariffExemptionDto.java          # Example DTO
└── util/
    └── ExcelColumnUtil.java             # Letter ↔ index conversion

src/main/resources/
├── application.properties
├── templates/
│   ├── upload.html                      # Upload form
│   └── result.html                      # Success/Error summary
└── static/
    └── (minimal CSS for prototype)
```

---

## Configuration (application.properties)

```properties
# Server
server.port=8080

# Excel Import Settings
excel.import.max-file-size-mb=10
excel.import.max-rows=10000
excel.import.retention-days=30
excel.import.temp-directory=${java.io.tmpdir}/excel-imports

# Error Excel Styling
excel.import.error-column-name=_ERRORS
excel.import.error-cell-color=#FFCCCC

# H2 Database (for prototype)
spring.datasource.url=jdbc:h2:mem:testdb
spring.datasource.driver-class-name=org.h2.Driver
spring.h2.console.enabled=true
spring.jpa.hibernate.ddl-auto=create-drop

# File Upload Limits
spring.servlet.multipart.max-file-size=10MB
spring.servlet.multipart.max-request-size=10MB
```

---

## Flow Diagram (Updated)

```
                    ┌──────────────┐
                    │  User        │
                    │  uploads     │
                    │  .xls/.xlsx  │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  Check       │
                    │  extension   │
                    └──────┬───────┘
                           │
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
       ┌─────────────┐          ┌─────────────┐
       │  .xls       │          │  .xlsx      │
       │  detected   │          │  detected   │
       └──────┬──────┘          └──────┬──────┘
              │                        │
              ▼                        │
       ┌─────────────┐                 │
       │  Convert    │                 │
       │  to .xlsx   │                 │
       └──────┬──────┘                 │
              │                        │
              └────────────┬───────────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  Validate    │
                    │  headers     │
                    │  (row N)     │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  Parse rows  │
                    │  (skip A,    │
                    │   stop at ※) │
                    └──────┬───────┘
                           │
                           ▼
                    ┌──────────────┐
                    │  Validate    │
                    │  each row    │
                    │  (JSR-380 +  │
                    │   unique)    │
                    └──────┬───────┘
                           │
              ┌────────────┴────────────┐
              │                         │
              ▼                         ▼
       ┌─────────────┐          ┌─────────────┐
       │  ALL VALID  │          │  HAS ERRORS │
       └──────┬──────┘          └──────┬──────┘
              │                        │
              ▼                        ▼
       ┌─────────────┐          ┌─────────────┐
       │  Upsert to  │          │  Generate   │
       │  database   │          │  error xlsx │
       └──────┬──────┘          │  (add       │
              │                 │  _ERRORS    │
              │                 │   column)   │
              │                 └──────┬──────┘
              ▼                        │
       ┌─────────────┐                 ▼
       │  Success    │          ┌─────────────┐
       │  page       │          │  Summary    │
       │  + stats    │          │  page +     │
       └─────────────┘          │  download   │
                                └─────────────┘
```

---

## API Endpoints

```
POST /api/excel/upload/{templateType}
  - Request: multipart/form-data with file
  - Accepts: .xls or .xlsx
  - Response 200 (success):
    {
      "success": true,
      "rowsProcessed": 150,
      "rowsCreated": 100,
      "rowsUpdated": 50,
      "message": "데이터 업로드 완료"
    }
  - Response 400 (validation errors):
    {
      "success": false,
      "totalRows": 150,
      "errorRows": 8,
      "errorCount": 12,
      "downloadUrl": "/api/excel/download/{errorFileId}",
      "message": "8개 행에서 12개 오류가 발견되었습니다"
    }

GET /api/excel/download/{fileId}
  - Returns the error-annotated Excel file
  - Content-Type: application/vnd.openxmlformats-officedocument.spreadsheetml.sheet

GET /api/excel/template/{templateType}
  - Downloads blank template with headers
```

---

## Next Steps for Claude Code Implementation

### Phase 1: Core Infrastructure
1. Create project with `build.gradle`
2. Create annotation classes
3. Create `ExcelColumnUtil` (letter ↔ index)
4. Create `ExcelImportConfig` interface
5. Create `ExcelImportProperties` configuration

### Phase 2: Parsing
6. Create `ExcelConversionService` (.xls → .xlsx)
7. Create `ExcelParserService` (with merged cell handling)

### Phase 3: Validation
8. Create validation result DTOs
9. Create `ExcelValidationService`
10. Create `UniqueConstraintValidator`

### Phase 4: Error Reporting
11. Create `ExcelErrorReportService`

### Phase 5: Integration
12. Create `ExcelImportOrchestrator`
13. Create `ExcelUploadController`
14. Create Thymeleaf templates

### Phase 6: Testing
15. Create sample DTO (TariffExemptionDto)
16. Write integration tests
17. Test with SAMPLE.xls file
