package com.github.ehdez73.code2req.analyzer.event.listener;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;

public record EventPublisherInfo(
    String className,
    String eventType,
    String filePath
) implements AnalysisFinding {}
