package com.github.ehdez73.code2req.indexing.domain.analyzer.bean.xml;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record XmlScheduledTaskInfo(
    String ref,
    String method,
    String cron,
    Long fixedRate,
    Long fixedDelay,
    String taskType,
    String filePath,
    int lineNumber
) implements AnalysisFinding {
    @Override
    public String className() { return ref; }
}
