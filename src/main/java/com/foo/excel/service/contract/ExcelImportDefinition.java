package com.foo.excel.service.contract;

import com.foo.excel.validation.RowError;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
public class ExcelImportDefinition<T, M extends ImportMetadata> {

  private final String importType;
  private final Class<T> rowClass;
  private final Class<M> metadataClass;
  private final ExcelSheetSpec sheetSpec;
  private final List<ExcelMergeRegion> mergeRegions;
  private final PersistenceHandler<T, M> persistenceHandler;
  private final ImportPrecheck<M> importPrecheck;
  private final DatabaseUniquenessChecker<T, M> dbUniquenessChecker;

  public ExcelImportDefinition(
      String importType,
      Class<T> rowClass,
      Class<M> metadataClass,
      PersistenceHandler<T, M> persistenceHandler,
      ImportPrecheck<M> importPrecheck,
      DatabaseUniquenessChecker<T, M> dbUniquenessChecker) {
    this.importType = importType;
    this.rowClass = rowClass;
    this.metadataClass = metadataClass;
    this.sheetSpec = ExcelSheetSpecResolver.resolve(rowClass);
    this.mergeRegions = ExcelMergeRegionResolver.resolve(rowClass);
    this.persistenceHandler = persistenceHandler;
    this.importPrecheck = importPrecheck;
    this.dbUniquenessChecker = dbUniquenessChecker;
  }

  public Optional<ImportPrecheckFailure> runImportPrecheck(M metadata) {
    if (importPrecheck == null) {
      return Optional.empty();
    }
    return importPrecheck.check(metadata);
  }

  public List<RowError> checkDbUniqueness(
      List<T> rows, List<Integer> sourceRowNumbers, M metadata) {
    if (dbUniquenessChecker == null) {
      return Collections.emptyList();
    }
    return dbUniquenessChecker.check(rows, rowClass, sourceRowNumbers, metadata);
  }

  public String resolveTempSubdirectory(M metadata) {
    return null;
  }
}
