package com.foo.excel.service.contract;

import com.foo.excel.validation.RowError;
import java.util.List;

public interface DatabaseUniquenessChecker<T, C extends MetaData> {

  List<RowError> check(
      List<T> rows, Class<T> dtoClass, List<Integer> sourceRowNumbers, C metaData);
}
