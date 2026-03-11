package com.foo.excel.service.contract;

import com.foo.excel.validation.RowError;
import java.util.List;

public interface DatabaseUniquenessChecker<M extends MetaData> {

  List<RowError> check(List<Integer> sourceRowNumbers, M metaData);
}
