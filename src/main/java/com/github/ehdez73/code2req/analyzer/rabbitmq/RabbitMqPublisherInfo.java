package com.github.ehdez73.code2req.analyzer.rabbitmq;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record RabbitMqPublisherInfo(
    String exchange,
    String routingKey,
    String methodName,
    String className,
    String filePath
) implements AnalysisFinding {}
