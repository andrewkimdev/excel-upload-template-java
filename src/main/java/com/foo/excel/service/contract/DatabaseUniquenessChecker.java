package com.foo.excel.service.contract;

import com.foo.excel.validation.RowError;
import java.util.List;

/**
 * Strategy contract for import-specific duplicate checks against already persisted data.
 *
 * <p>This contract is intentionally row-oriented. From a reader's perspective, a database
 * uniqueness check is naturally understood as "given the uploaded rows, determine which ones
 * would conflict with existing records in the database." Even if a concrete implementation does
 * not need to inspect row DTO fields directly, the abstraction still communicates that the check
 * conceptually belongs to the uploaded row set rather than to an unrelated process step.
 *
 * <p>The generic parameters follow the import contract naming used elsewhere in this module:
 *
 * <ul>
 *   <li>{@code T}: parsed Excel row DTO type
 *   <li>{@code M}: import-specific {@link ImportMetadata} type supplied alongside the upload
 * </ul>
 *
 * <p>Implementations are free to use any subset of the provided inputs. Some imports may inspect
 * row field values, annotations, or DTO type information. Other imports may derive uniqueness
 * entirely from upload-level metadata plus source row numbers. The current sample tariff checker is
 * an example of the latter case.
 *
 * <p>The returned {@link RowError} list is merged into the normal validation pipeline so that DB
 * conflicts are reported in the same row/cell-oriented format as parsing and bean validation
 * errors.
 *
 * @param <T> parsed Excel row DTO type
 * @param <M> import-specific metadata type
 */
public interface DatabaseUniquenessChecker<T, M extends ImportMetadata> {

  /**
   * Checks the uploaded data set against existing persisted data and returns row-level errors for
   * any conflicts that should block import.
   *
   * <p>The arguments are intentionally redundant from some implementation viewpoints:
   *
   * <ul>
   *   <li>{@code rows} exposes the parsed DTO values for implementations that need row contents
   *   <li>{@code rowClass} exposes the DTO type for implementations that need type-level metadata
   *   <li>{@code sourceRowNumbers} preserves original Excel row numbers for precise error reporting
   *   <li>{@code metadata} carries upload-level fields entered outside the Excel file
   * </ul>
   *
   * <p>Not every implementation must use all four inputs. The contract keeps them available so the
   * extension point remains expressive for future imports and readable to maintainers.
   *
   * @param rows parsed DTO rows from the uploaded Excel file
   * @param rowClass concrete DTO class for the parsed rows
   * @param sourceRowNumbers original Excel row numbers aligned with {@code rows}
   * @param metadata import-specific upload metadata entered by the user
   * @return row-level validation errors representing database conflicts; empty if no conflicts were
   *     found
   */
  List<RowError> check(List<T> rows, Class<T> rowClass, List<Integer> sourceRowNumbers, M metadata);
}
