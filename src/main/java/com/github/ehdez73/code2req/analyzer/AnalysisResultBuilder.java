package com.github.ehdez73.code2req.analyzer;

import java.util.ArrayList;
import java.util.List;

public class AnalysisResultBuilder {

    private final List<ComponentInfo> components = new ArrayList<>();
    private final List<EndpointInfo> endpoints = new ArrayList<>();
    private final List<ScheduledTaskInfo> scheduledTasks = new ArrayList<>();

    public void addComponent(ComponentInfo ci) {
        components.add(ci);
    }

    public void addEndpoint(EndpointInfo ei) {
        endpoints.add(ei);
    }

    public void addScheduledTask(ScheduledTaskInfo sti) {
        scheduledTasks.add(sti);
    }

    public boolean hasControllerComponent() {
        return components.stream()
            .anyMatch(c -> "Controller".equals(c.annotationType())
                || "RestController".equals(c.annotationType()));
    }

    public AnalysisResult build(String filePath) {
        return new AnalysisResult(
            filePath,
            List.copyOf(components),
            List.copyOf(endpoints),
            List.copyOf(scheduledTasks)
        );
    }
}
