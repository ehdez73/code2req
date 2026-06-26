package com.github.ehdez73.code2req.synthesis.domain;

import java.util.List;

public record FunctionalFlow(
    String flowId,
    String name,
    EntryPoint entryPoint,
    List<FlowStep> steps,
    String userStory,
    List<GherkinScenario> acceptanceCriteria,
    List<BusinessRule> businessRules,
    List<EdgeCase> edgeCases,
    ComplexityLevel complexity,
    String mermaidDiagram
) {}
