package com.github.ehdez73.code2req.analyzer.activemq;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record ActiveMqPublisherInfo(
    String destination,
    String methodName,
    String className,
    String filePath
) implements AnalysisFinding {}
