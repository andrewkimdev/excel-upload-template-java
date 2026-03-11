package com.foo.excel.service.contract;

import java.util.List;

public interface PersistenceHandler<T, M extends MetaData> {

  SaveResult saveAll(List<T> rows, List<Integer> sourceRowNumbers, M metaData);

  record SaveResult(int created, int updated) {}
}
