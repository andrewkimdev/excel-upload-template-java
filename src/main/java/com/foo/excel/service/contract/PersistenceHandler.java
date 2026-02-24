package com.foo.excel.service.contract;

import java.util.List;

public interface PersistenceHandler<T, C extends CommonData> {

  SaveResult saveAll(List<T> rows, List<Integer> sourceRowNumbers, C commonData);

  record SaveResult(int created, int updated) {}
}
