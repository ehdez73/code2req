package com.github.ehdez73.code2req.indexing.domain.analyzer.event.listener;

import com.github.ehdez73.code2req.indexing.domain.analyzer.AnalysisFinding;
import java.util.List;

public record EventListenerInfo(
    String payLoadType,
    String methodName,
    String className,
    String filePath,
    List<MethodCallInfo> callChain
) implements AnalysisFinding {}
