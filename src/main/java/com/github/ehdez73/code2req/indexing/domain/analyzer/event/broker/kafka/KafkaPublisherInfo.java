package com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.kafka;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record KafkaPublisherInfo(
    String topic,
    String methodName,
    String className,
    String filePath
) implements AnalysisFinding {}
