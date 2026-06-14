package com.github.ehdez73.code2req.analyzer.eventlistener;

import com.github.ehdez73.code2req.analyzer.AnalysisFinding;
import java.util.List;

public record EventListenerInfo(
    String eventType,
    String methodName,
    String className,
    String filePath,
    List<MethodCallInfo> callChain
) implements AnalysisFinding {}
