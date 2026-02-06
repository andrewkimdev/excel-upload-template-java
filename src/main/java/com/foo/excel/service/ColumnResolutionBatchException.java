package com.foo.excel.service;

import lombok.Getter;

import java.util.List;
import java.util.stream.Collectors;

@Getter
public class ColumnResolutionBatchException extends RuntimeException {

    private final List<ColumnResolutionException> exceptions;

    public ColumnResolutionBatchException(List<ColumnResolutionException> exceptions) {
        super("Column resolution failed for %d field(s)".formatted(exceptions.size()));
        this.exceptions = List.copyOf(exceptions);
    }

    public String toKoreanMessage() {
        return exceptions.stream()
                .map(ColumnResolutionException::toKoreanMessage)
                .collect(Collectors.joining("\n"));
    }
}
