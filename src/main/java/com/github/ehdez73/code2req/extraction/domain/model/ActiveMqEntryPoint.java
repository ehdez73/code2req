package com.github.ehdez73.code2req.extraction.domain.model;

public record ActiveMqEntryPoint(
    String id,
    String className,
    String methodName,
    String filePath,
    double priorityScore,
    boolean trivial,
    String destination,
    String payloadType
) implements EntryPoint {
    @Override
    public EntryPointType type() { return EntryPointType.ACTIVEMQ; }
}
