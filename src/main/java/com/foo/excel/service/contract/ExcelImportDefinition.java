package com.foo.excel.service.contract;

import com.foo.excel.validation.RowError;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import lombok.Getter;

@Getter
public class ExcelImportDefinition<T, M extends Metadata> {

  private final String importType;
  private final Class<T> dtoClass;
  private final Class<M> metadataClass;
  private final ExcelSheetSpec sheetSpec;
  private final List<ExcelMergeRegion> mergeRegions;
  private final PersistenceHandler<T, M> persistenceHandler;
  private final ImportPrecheck<M> importPrecheck;
  private final DatabaseUniquenessChecker<T, M> dbUniquenessChecker;

  public ExcelImportDefinition(
      String importType,
      Class<T> dtoClass,
      Class<M> metadataClass,
      PersistenceHandler<T, M> persistenceHandler,
      ImportPrecheck<M> importPrecheck,
      DatabaseUniquenessChecker<T, M> dbUniquenessChecker) {
    this.importType = importType;
    this.dtoClass = dtoClass;
    this.metadataClass = metadataClass;
    this.sheetSpec = ExcelSheetSpecResolver.resolve(dtoClass);
    this.mergeRegions = ExcelMergeRegionResolver.resolve(dtoClass);
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
    return dbUniquenessChecker.check(rows, dtoClass, sourceRowNumbers, metadata);
  }

  public String resolveTempSubdirectory(M metadata) {
    return null;
  }
}
