package com.github.ehdez73.code2req.analyzer;

import java.util.List;

public record AnalysisResult(
    String filePath,
    List<ComponentInfo> components,
    List<EndpointInfo> endpoints,
    List<ScheduledTaskInfo> scheduledTasks
) {}
