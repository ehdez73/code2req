package com.github.ehdez73.code2req.extraction.domain.model;

public record EventListenerEntryPoint(
    String id,
    String className,
    String methodName,
    String filePath,
    double priorityScore,
    boolean trivial,
    String payloadType
) implements EntryPoint {
    @Override
    public EntryPointType type() { return EntryPointType.EVENT_LISTENER; }
}
