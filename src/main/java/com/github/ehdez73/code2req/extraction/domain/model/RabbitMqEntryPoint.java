package com.github.ehdez73.code2req.extraction.domain.model;

public record RabbitMqEntryPoint(
    String id,
    String className,
    String methodName,
    String filePath,
    double priorityScore,
    boolean trivial,
    String queues,
    String payloadType,
    int startLine,
    int endLine
) implements EntryPoint {
    public RabbitMqEntryPoint(String id, String className, String methodName, String filePath,
                              double priorityScore, boolean trivial,
                              String queues, String payloadType) {
        this(id, className, methodName, filePath, priorityScore, trivial,
             queues, payloadType, 0, 0);
    }

    @Override
    public EntryPointType type() { return EntryPointType.RABBITMQ; }
}
