package com.foo.excel.service.contract;

import java.util.List;

public interface PersistenceHandler<T, M extends Metadata> {

  SaveResult saveAll(List<T> rows, List<Integer> sourceRowNumbers, M metadata);

  record SaveResult(int created, int updated) {}
}
