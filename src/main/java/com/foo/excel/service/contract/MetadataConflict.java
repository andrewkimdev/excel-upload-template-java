package com.foo.excel.service.contract;

import java.util.List;

public record MetadataConflict(String type, String description, List<FieldValue> fields) {

  public record FieldValue(String fieldName, String label, String value) {}
}
