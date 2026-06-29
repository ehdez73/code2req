package com.github.ehdez73.code2req.indexing.domain.analyzer.event.broker.rabbitmq;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;

public record RabbitMqInfo(
    String queues,
    String methodName,
    String className,
    String filePath
) implements AnalysisFinding {}
