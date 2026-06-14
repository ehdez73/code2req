package com.github.ehdez73.code2req.analyzer.kafka;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record KafkaInfo(
    String topics,
    String methodName,
    String className,
    String filePath,
    boolean isPattern
) implements AnalysisFinding {}
