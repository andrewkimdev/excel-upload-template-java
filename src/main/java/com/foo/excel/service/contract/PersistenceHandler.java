package com.foo.excel.service.contract;

import java.util.List;

public interface PersistenceHandler<T, C extends MetaData> {

  SaveResult saveAll(List<T> rows, List<Integer> sourceRowNumbers, C metaData);

  record SaveResult(int created, int updated) {}
}
