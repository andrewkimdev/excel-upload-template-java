package com.foo.excel.service.contract;

public record UploadPrecheckFailure(String message, MetadataConflict metadataConflict) {}
