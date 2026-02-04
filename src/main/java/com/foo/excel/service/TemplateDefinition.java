package com.foo.excel.service;

import com.foo.excel.config.ExcelImportConfig;
import com.foo.excel.validation.RowError;
import lombok.Getter;

import java.util.Collections;
import java.util.List;

@Getter
public class TemplateDefinition<T> {

    private final String templateType;
    private final Class<T> dtoClass;
    private final ExcelImportConfig config;
    private final PersistenceHandler<T> persistenceHandler;
    private final DatabaseUniquenessChecker<T> dbUniquenessChecker;

    public TemplateDefinition(String templateType, Class<T> dtoClass, ExcelImportConfig config,
                              PersistenceHandler<T> persistenceHandler,
                              DatabaseUniquenessChecker<T> dbUniquenessChecker) {
        this.templateType = templateType;
        this.dtoClass = dtoClass;
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
