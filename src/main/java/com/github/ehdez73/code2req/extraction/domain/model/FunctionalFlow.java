package com.github.ehdez73.code2req.extraction.domain.model;

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
    String mermaidDiagram,
    List<NonFunctionalRequirement> nonFunctionalRequirements
) {
    public FunctionalFlow(String flowId, String name, EntryPoint entryPoint,
                          List<FlowStep> steps, String userStory,
                          List<GherkinScenario> acceptanceCriteria,
                          List<BusinessRule> businessRules, List<EdgeCase> edgeCases,
                          ComplexityLevel complexity, String mermaidDiagram) {
        this(flowId, name, entryPoint, steps, userStory, acceptanceCriteria,
             businessRules, edgeCases, complexity, mermaidDiagram, List.of());
    }
}
