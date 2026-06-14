package com.github.ehdez73.code2req.analyzer.scheduledtask;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record ScheduledTaskInfo(
    String methodName,
    String className,
    String cron,
    Long fixedRate,
    Long fixedDelay,
    String type,
    String filePath
) implements AnalysisFinding {}
