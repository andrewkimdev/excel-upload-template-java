package com.foo.excel.service;

import java.util.List;

public interface PersistenceHandler<T> {

    SaveResult saveAll(List<T> rows);

    record SaveResult(int created, int updated) {}
}
