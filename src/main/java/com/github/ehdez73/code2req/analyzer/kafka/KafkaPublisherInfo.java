package com.github.ehdez73.code2req.analyzer.kafka;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record KafkaPublisherInfo(
    String topic,
    String methodName,
    String className,
    String filePath
) implements AnalysisFinding {}
