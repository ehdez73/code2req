package com.github.ehdez73.code2req.extraction.domain.model;

public record KafkaEntryPoint(
    String id,
    String className,
    String methodName,
    String filePath,
    double priorityScore,
    boolean trivial,
    String topics,
    boolean isPattern,
    String payloadType,
    int startLine,
    int endLine
) implements EntryPoint {
    public KafkaEntryPoint(String id, String className, String methodName, String filePath,
                           double priorityScore, boolean trivial,
                           String topics, boolean isPattern, String payloadType) {
        this(id, className, methodName, filePath, priorityScore, trivial,
             topics, isPattern, payloadType, 0, 0);
    }

    @Override
    public EntryPointType type() { return EntryPointType.KAFKA; }
}
