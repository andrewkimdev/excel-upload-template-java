package com.foo.excel.service.contract;

import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.validation.RowError;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
public class TemplateDefinition<T, M extends MetaData> {

  private final String templateType;
  private final Class<T> dtoClass;
  private final Class<M> metaDataClass;
  private final ExcelImportConfig config;
  private final PersistenceHandler<T, M> persistenceHandler;
  private final DatabaseUniquenessChecker<T, M> dbUniquenessChecker;

  public TemplateDefinition(
      String templateType,
      Class<T> dtoClass,
      Class<M> metaDataClass,
      ExcelImportConfig config,
      PersistenceHandler<T, M> persistenceHandler,
      DatabaseUniquenessChecker<T, M> dbUniquenessChecker) {
    this.templateType = templateType;
    this.dtoClass = dtoClass;
    this.metaDataClass = metaDataClass;
    this.config = config;
    this.persistenceHandler = persistenceHandler;
    this.dbUniquenessChecker = dbUniquenessChecker;
  }

  public List<RowError> checkDbUniqueness(
      List<T> rows, List<Integer> sourceRowNumbers, M metaData) {
    if (dbUniquenessChecker == null) {
      return Collections.emptyList();
    }
    return dbUniquenessChecker.check(rows, dtoClass, sourceRowNumbers, metaData);
  }
}
