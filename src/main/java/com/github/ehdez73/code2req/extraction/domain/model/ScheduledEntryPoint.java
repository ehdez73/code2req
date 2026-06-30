package com.github.ehdez73.code2req.extraction.domain.model;

public record ScheduledEntryPoint(
    String id,
    String className,
    String methodName,
    String filePath,
    double priorityScore,
    boolean trivial,
    String schedule
) implements EntryPoint {
    @Override
    public EntryPointType type() { return EntryPointType.SCHEDULED; }
}
