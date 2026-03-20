package com.foo.excel.service.contract;

public record ImportPrecheckFailure(String message, MetadataConflict uploadMetadataConflict) {}
