package com.github.ehdez73.code2req.analyzer.event.broker.rabbitmq;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record RabbitMqInfo(
    String queues,
    String methodName,
    String className,
    String filePath
) implements AnalysisFinding {}
