package com.github.ehdez73.code2req.extraction.domain.model;

public record ScheduledEntryPoint(
    String id,
    String className,
    String methodName,
    String filePath,
    double priorityScore,
    boolean trivial,
    String schedule,
    int startLine,
    int endLine,
    String configFilePath
) implements EntryPoint {
    public ScheduledEntryPoint(String id, String className, String methodName, String filePath,
                               double priorityScore, boolean trivial, String schedule) {
        this(id, className, methodName, filePath, priorityScore, trivial, schedule, 0, 0, null);
    }

    public ScheduledEntryPoint(String id, String className, String methodName, String filePath,
                               double priorityScore, boolean trivial, String schedule,
                               int startLine, int endLine) {
        this(id, className, methodName, filePath, priorityScore, trivial, schedule, startLine, endLine, null);
    }

    @Override
    public EntryPointType type() { return EntryPointType.SCHEDULED; }
}
