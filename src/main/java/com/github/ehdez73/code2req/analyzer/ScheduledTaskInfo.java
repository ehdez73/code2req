package com.github.ehdez73.code2req.analyzer;

public record ScheduledTaskInfo(
    String methodName,
    String className,
    String cron,
    Long fixedRate,
    Long fixedDelay,
    String type,
    String filePath
) {}
