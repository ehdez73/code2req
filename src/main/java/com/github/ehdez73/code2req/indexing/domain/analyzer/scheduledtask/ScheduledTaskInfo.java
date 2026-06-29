package com.github.ehdez73.code2req.indexing.domain.analyzer.scheduledtask;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record ScheduledTaskInfo(
    String methodName,
    String className,
    String cron,
    Long fixedRate,
    Long fixedDelay,
    String type,
    String filePath
) implements AnalysisFinding {}
