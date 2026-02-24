package com.foo.excel.service.contract;

import com.foo.excel.validation.RowError;
import java.util.List;

public interface DatabaseUniquenessChecker<T> {

  List<RowError> check(List<T> rows, Class<T> dtoClass, List<Integer> sourceRowNumbers);
}
