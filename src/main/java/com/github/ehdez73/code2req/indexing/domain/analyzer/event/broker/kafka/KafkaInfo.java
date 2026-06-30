package com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record KafkaInfo(
    String topics,
    String methodName,
    String className,
    String filePath,
    boolean isPattern,
    String payloadType
) implements AnalysisFinding {}
