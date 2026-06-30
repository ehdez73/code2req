package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.embabel.agent.api.common.OperationContext;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.AnalyzedFlowResult;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.TracedFlowResult;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.BusinessRule;
import com.github.ehdez73.code2req.extraction.domain.model.ComplexityLevel;
import com.github.ehdez73.code2req.extraction.domain.model.EdgeCase;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStatus;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import com.github.ehdez73.code2req.extraction.domain.model.GherkinScenario;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

/**
 * For a traced execution flow, extracts business semantics: user story
 * ("As a [role], I want [feature], so that [benefit]"), Gherkin scenarios
 * (Given/When/Then), business rules matrix, and edge cases. Uses Phase 2
 * enrichment when available, otherwise falls back to LLM inference.
 * Applies progressive disclosure based on flow complexity.
 */
public class AnalyzeFlowAction {

    private static final Logger log = LoggerFactory.getLogger(AnalyzeFlowAction.class);

    private final CodebaseKnowledge knowledge;

    public AnalyzeFlowAction(CodebaseKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public AnalyzedFlowResult analyze(TracedFlowResult tracedResult, OperationContext context) {
        List<FunctionalFlow> analyzedFlows = new ArrayList<>();

        for (ExecutionFlow flow : tracedResult.flows()) {
            if (flow.status() == FlowStatus.QUARANTINED) continue;

            FunctionalFlow analyzed = analyzeFlow(flow, context);
            analyzedFlows.add(analyzed);
        }

        log.info("Analyzed {} flows", analyzedFlows.size());
        return new AnalyzedFlowResult(analyzedFlows);
    }

    private FunctionalFlow analyzeFlow(ExecutionFlow flow, OperationContext context) {
        ComplexityLevel complexity = assessComplexity(flow);

        Optional<ExecutionFinding> enrichment = knowledge.semanticEnrichment()
            .findByFilePath(flow.entryPoint().filePath());

        String enrichmentContext = enrichment
            .map(ef -> formatEnrichmentContext(ef))
            .orElse("No Phase 2 enrichment available.");

        String stepsContext = flow.steps().stream()
            .map(s -> "  - " + s.componentType() + ": " + s.className() + "." + s.methodName()
                + (s.sourceFile() != null ? " (" + s.sourceFile() + ")" : ""))
            .reduce((a, b) -> a + "\n" + b)
            .orElse("  (no steps traced)");

        String prompt = """
            Analyze this execution flow and extract functional requirements.

            Entry Point: %s %s (%s)
            Complexity: %s
            Traced Steps:
            %s

            Phase 2 Enrichment:
            %s

            Extract:
            1. A concise user story (1-2 sentences) describing what this flow does for the user
            2. 1-3 Gherkin scenarios with Given/When/Then steps
            3. Business rules with ID, description, precondition, postcondition, error behavior
            4. Edge cases with scenario and business consequence

            Format your response as a JSON object with these fields:
            {
              "userStory": "...",
              "gherkinScenarios": [{"scenarioId": "GS-001", "name": "...", "givenSteps": ["..."], "whenSteps": ["..."], "thenSteps": ["..."]}],
              "businessRules": [{"ruleId": "BR-001", "description": "...", "precondition": "...", "postcondition": "...", "errorBehavior": "..."}],
              "edgeCases": [{"scenario": "...", "businessConsequence": "..."}]
            }
            """.formatted(
            flow.entryPoint().httpMethod() != null ? flow.entryPoint().httpMethod() : flow.entryPoint().type(),
            flow.entryPoint().path() != null ? flow.entryPoint().path() : flow.entryPoint().className(),
            flow.entryPoint().filePath(),
            complexity,
            stepsContext,
            enrichmentContext
        );

        FlowAnalysisResponse response = context.ai()
            .withDefaultLlm()
            .createObject(prompt, FlowAnalysisResponse.class);

        List<GherkinScenario> gherkinScenarios = new ArrayList<>();
        if (response.gherkinScenarios() != null) {
            for (GherkinScenarioDto dto : response.gherkinScenarios()) {
                gherkinScenarios.add(new GherkinScenario(
                    dto.scenarioId(), dto.name(),
                    dto.givenSteps() != null ? dto.givenSteps() : List.of(),
                    dto.whenSteps() != null ? dto.whenSteps() : List.of(),
                    dto.thenSteps() != null ? dto.thenSteps() : List.of(),
                    flow.flowId()
                ));
            }
        }

        List<BusinessRule> businessRules = new ArrayList<>();
        if (response.businessRules() != null) {
            for (BusinessRuleDto dto : response.businessRules()) {
                businessRules.add(new BusinessRule(
                    dto.ruleId(), dto.description(),
                    dto.precondition(), dto.postcondition(),
                    dto.errorBehavior(),
                    flow.entryPoint().filePath(), 0, 0
                ));
            }
        }

        List<EdgeCase> edgeCases = new ArrayList<>();
        if (response.edgeCases() != null) {
            for (EdgeCaseDto dto : response.edgeCases()) {
                edgeCases.add(new EdgeCase(
                    dto.scenario(), dto.businessConsequence(),
                    flow.entryPoint().filePath(), 0, 0
                ));
            }
        }

        String mermaid = complexity == ComplexityLevel.FULL
            ? generateMermaid(flow)
            : null;

        return new FunctionalFlow(
            flow.flowId(),
            flow.entryPoint().path() != null
                ? flow.entryPoint().httpMethod() + " " + flow.entryPoint().path()
                : flow.entryPoint().className() + "." + flow.entryPoint().methodName(),
            flow.entryPoint(),
            flow.steps(),
            response.userStory(),
            gherkinScenarios,
            businessRules,
            edgeCases,
            complexity,
            mermaid
        );
    }

    private ComplexityLevel assessComplexity(ExecutionFlow flow) {
        int stepCount = flow.steps().size();
        boolean hasExternalCalls = flow.steps().stream()
            .anyMatch(s -> s.componentType() == com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType.EXTERNAL_CALL);
        boolean hasDatabase = flow.steps().stream()
            .anyMatch(s -> s.componentType() == com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType.DATABASE);

        double score = 0.0;
        score += Math.min(stepCount / 5.0, 1.0) * 0.4;
        score += (hasExternalCalls ? 0.3 : 0.0);
        score += (hasDatabase ? 0.3 : 0.0);

        if (score >= 0.7) return ComplexityLevel.FULL;
        if (score >= 0.3) return ComplexityLevel.STANDARD;
        return ComplexityLevel.MINIMAL;
    }

    private String formatEnrichmentContext(ExecutionFinding ef) {
        StringBuilder sb = new StringBuilder();
        if (ef.businessAbstraction() != null) {
            if (ef.businessAbstraction().purpose() != null) {
                sb.append("Purpose: ").append(ef.businessAbstraction().purpose()).append("\n");
            }
            if (ef.businessAbstraction().happyPaths() != null) {
                for (ExecutionFinding.HappyPath hp : ef.businessAbstraction().happyPaths()) {
                    sb.append("Happy path: ").append(hp.flowName()).append("\n");
                    if (hp.description() != null) {
                        sb.append("  Description: ").append(hp.description()).append("\n");
                    }
                }
            }
        }
        if (ef.businessRulesAndGuardrails() != null) {
            if (ef.businessRulesAndGuardrails().validations() != null) {
                for (ExecutionFinding.Validation v : ef.businessRulesAndGuardrails().validations()) {
                    sb.append("Validation: ").append(v.fieldOrContext()).append(" - ").append(v.rule()).append("\n");
                }
            }
            if (ef.businessRulesAndGuardrails().edgeCases() != null) {
                for (ExecutionFinding.EdgeCase ec : ef.businessRulesAndGuardrails().edgeCases()) {
                    sb.append("Edge case: ").append(ec.scenario()).append(" - ").append(ec.businessConsequence()).append("\n");
                }
            }
        }
        return sb.isEmpty() ? "No enrichment context available." : sb.toString();
    }

    private String generateMermaid(ExecutionFlow flow) {
        StringBuilder sb = new StringBuilder();
        sb.append("graph TD\n");

        for (int i = 0; i < flow.steps().size(); i++) {
            FlowStep step = flow.steps().get(i);
            String nodeId = "step" + i;
            String label = step.className() + "\\n" + step.methodName();
            sb.append("    ").append(nodeId).append("[\"").append(label).append("\"]\n");

            if (i > 0) {
                sb.append("    step").append(i - 1).append(" --> ").append(nodeId).append("\n");
            }
        }

        return sb.toString();
    }

    record FlowAnalysisResponse(
        String userStory,
        List<GherkinScenarioDto> gherkinScenarios,
        List<BusinessRuleDto> businessRules,
        List<EdgeCaseDto> edgeCases
    ) {}

    record GherkinScenarioDto(
        String scenarioId,
        String name,
        List<String> givenSteps,
        List<String> whenSteps,
        List<String> thenSteps
    ) {}

    record BusinessRuleDto(
        String ruleId,
        String description,
        String precondition,
        String postcondition,
        String errorBehavior
    ) {}

    record EdgeCaseDto(
        String scenario,
        String businessConsequence
    ) {}
}
