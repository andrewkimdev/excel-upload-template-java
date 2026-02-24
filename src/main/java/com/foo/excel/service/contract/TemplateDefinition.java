package com.foo.excel.service.contract;

import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.validation.RowError;
import java.util.Collections;
import java.util.List;
import lombok.Getter;

@Getter
public class TemplateDefinition<T, C extends CommonData> {

  private final String templateType;
  private final Class<T> dtoClass;
  private final Class<C> commonDataClass;
  private final ExcelImportConfig config;
  private final PersistenceHandler<T, C> persistenceHandler;
  private final DatabaseUniquenessChecker<T> dbUniquenessChecker;

  public TemplateDefinition(
      String templateType,
      Class<T> dtoClass,
      Class<C> commonDataClass,
      ExcelImportConfig config,
      PersistenceHandler<T, C> persistenceHandler,
      DatabaseUniquenessChecker<T> dbUniquenessChecker) {
    this.templateType = templateType;
    this.dtoClass = dtoClass;
    this.commonDataClass = commonDataClass;
    this.config = config;
    this.persistenceHandler = persistenceHandler;
    this.dbUniquenessChecker = dbUniquenessChecker;
  }

  public List<RowError> checkDbUniqueness(List<T> rows, List<Integer> sourceRowNumbers) {
    if (dbUniquenessChecker == null) {
      return Collections.emptyList();
    }
    return dbUniquenessChecker.check(rows, dtoClass, sourceRowNumbers);
  }
}
