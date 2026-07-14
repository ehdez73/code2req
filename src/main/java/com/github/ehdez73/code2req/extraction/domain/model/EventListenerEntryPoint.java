package com.github.ehdez73.code2req.extraction.domain.model;

public record EventListenerEntryPoint(
    String id,
    String className,
    String methodName,
    String filePath,
    double priorityScore,
    boolean trivial,
    String payloadType,
    int startLine,
    int endLine
) implements EntryPoint {
    public EventListenerEntryPoint(String id, String className, String methodName, String filePath,
                                   double priorityScore, boolean trivial, String payloadType) {
        this(id, className, methodName, filePath, priorityScore, trivial, payloadType, 0, 0);
    }

    @Override
    public EntryPointType type() { return EntryPointType.EVENT_LISTENER; }
}
