package com.foo.excel.service;

import java.util.List;

public interface PersistenceHandler<T> {

    SaveResult saveAll(List<T> rows, List<Integer> sourceRowNumbers, UploadCommonData commonData);

    record SaveResult(int created, int updated) {}
}
