package com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.activemq;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record ActiveMqInfo(
    String destination,
    String methodName,
    String className,
    String filePath
) implements AnalysisFinding {}
