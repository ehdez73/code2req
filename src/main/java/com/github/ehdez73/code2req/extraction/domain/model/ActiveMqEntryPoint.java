package com.github.ehdez73.code2req.extraction.domain.model;

public record ActiveMqEntryPoint(
    String id,
    String className,
    String methodName,
    String filePath,
    double priorityScore,
    boolean trivial,
    String destination,
    String payloadType,
    int startLine,
    int endLine
) implements EntryPoint {
    public ActiveMqEntryPoint(String id, String className, String methodName, String filePath,
                              double priorityScore, boolean trivial,
                              String destination, String payloadType) {
        this(id, className, methodName, filePath, priorityScore, trivial,
             destination, payloadType, 0, 0);
    }

    @Override
    public EntryPointType type() { return EntryPointType.ACTIVEMQ; }
}
