package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.embabel.agent.api.common.OperationContext;
import com.github.ehdez73.code2req.enrichment.domain.model.ExecutionFinding;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.AnalyzedFlowResult;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.TracedFlowResult;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.BusinessRule;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.ActiveMqEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.ComplexityLevel;
import com.github.ehdez73.code2req.extraction.domain.model.EventListenerEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.KafkaEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.RabbitMqEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.ScheduledEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EdgeCase;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFlow;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStatus;
import com.github.ehdez73.code2req.extraction.domain.model.ExternalCall;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import com.github.ehdez73.code2req.extraction.domain.model.GherkinScenario;
import com.github.ehdez73.code2req.extraction.domain.model.NonFunctionalRequirement;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

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

        EntryPoint ep = flow.entryPoint();
        Optional<ExecutionFinding> enrichment = knowledge.semanticEnrichment()
            .findByFilePath(ep.filePath());

        String entryMethodOrType = switch (ep) {
            case HttpEntryPoint h -> h.httpMethod();
            default -> ep.type().name();
        };
        String entryPathOrClass = switch (ep) {
            case HttpEntryPoint h -> h.path();
            default -> ep.className();
        };
        String payloadType = switch (ep) {
            case KafkaEntryPoint k -> !k.payloadType().isEmpty() ? k.payloadType() : k.topics();
            case RabbitMqEntryPoint r -> !r.payloadType().isEmpty() ? r.payloadType() : r.queues();
            case ActiveMqEntryPoint a -> !a.payloadType().isEmpty() ? a.payloadType() : a.destination();
            case EventListenerEntryPoint e -> e.payloadType();
            case HttpEntryPoint h -> !h.requestBodies().isEmpty() ? h.requestBodies().get(0) : "—";
            case ScheduledEntryPoint s -> "—";
        };
        String epId = entryPointId(ep);
        String enrichmentContext = enrichment
            .map(ef -> formatEnrichmentContext(ef, epId))
            .orElse("No Phase 2 enrichment available.");

        StringBuilder stepEnrichments = new StringBuilder();
        flow.steps().stream()
            .map(FlowStep::sourceFile)
            .filter(f -> f != null && !f.equals(flow.entryPoint().filePath()))
            .distinct().sorted()
            .forEach(f -> knowledge.semanticEnrichment().findByFilePath(f)
                .ifPresent(ef -> {
                    String fileName = f.contains("/") ? f.substring(f.lastIndexOf('/') + 1) : f;
                    stepEnrichments.append("  ").append(fileName).append(":\n");
                    stepEnrichments.append(formatEnrichmentContext(ef, null).indent(4));
                }));

        String stepEnrichmentContext = stepEnrichments.isEmpty()
            ? "No Phase 2 enrichment available for intermediate steps."
            : stepEnrichments.toString();

        String stepsContext = flow.steps().stream()
            .map(s -> {
                String base = "  - " + s.componentType() + ": " + s.className() + "."
                    + (s.methodName() != null ? s.methodName() : "(external call)")
                    + (s.sourceFile() != null ? " (" + s.sourceFile() + ")" : "");
                if (!s.enrichments().isEmpty()) {
                    base += "\n    Details: " + String.join(", ", s.enrichments());
                }
                return base;
            })
            .reduce((a, b) -> a + "\n" + b)
            .orElse("  (no steps traced)");

        Set<String> seenSourceKeys = new HashSet<>();
        StringBuilder sourceCodeContext = new StringBuilder();
        for (FlowStep step : flow.steps()) {
            if (step.sourceFile() != null && step.startLine() > 0 && step.endLine() > 0) {
                String key = step.sourceFile() + ":" + step.startLine() + "-" + step.endLine();
                if (seenSourceKeys.add(key)) {
                    String code = readFileContent(step.sourceFile(), step.startLine(), step.endLine());
                    if (!code.isEmpty()) {
                        String fileName = step.sourceFile().contains("/")
                            ? step.sourceFile().substring(step.sourceFile().lastIndexOf('/') + 1)
                            : step.sourceFile();
                        sourceCodeContext.append("  ").append(fileName)
                            .append(" lines ").append(step.startLine()).append("-").append(step.endLine())
                            .append(":\n");
                        sourceCodeContext.append("  ```java\n");
                        sourceCodeContext.append(code.indent(2));
                        sourceCodeContext.append("  ```\n");
                    }
                }
            }
        }
        String sourceCodeStr = sourceCodeContext.isEmpty()
            ? "No source code context available."
            : sourceCodeContext.toString();

        String prompt = """
    You are a senior software business analyst and reverse-engineering specialist.
    You read traced execution flows and source code and extract precise,
    verifiable functional and non-functional requirements — the kind that could be
    handed to a QA engineer to write automated tests, or to a PM to write a spec,
    without further clarification.

    ## Grounding rules (critical)
    - Base every statement ONLY on the data provided below (traced steps, enrichment, source code).
    - Never invent timeouts, retry counts, URLs, or behaviors that aren't evidenced in the input.
    - If a value is not explicitly present in the source, use JSON null for that field rather
      than guessing.
    - If a section below is empty or says "none provided", do not fabricate content for it —
      simply produce fewer items (including zero) for the categories it would affect.

    ## Extraction tasks

    1. **User story** — 1-2 sentences describing what this flow accomplishes for the end user.
    2. **Gherkin scenarios** — 1-3 scenarios (Given/When/Then) covering the success path, at least
       one failure path, and the fallback path if one exists in the trace. Number IDs sequentially
       starting at GS-001, no gaps or reused IDs.
    3. **Business rules** — one entry per distinct rule enforced by the code. Number IDs
       sequentially starting at BR-001, no gaps or reused IDs. Populate `sourceFile` with the file
       path (and line/method if available) from the traced source where the rule is implemented;
       use null if it can't be tied to a specific location.
    4. **External call rules** — for every outbound call to an external service found in the trace,
       emit a business rule whose `externalCall` field is populated with: HTTP method, URL (or URL
       template if parameterized), timeout in milliseconds, retry strategy, and the exact fallback
       behavior when the call fails or the service is unavailable. Use null for any of these
       sub-fields not evidenced in the source. For business rules with no external call, set the
       entire `externalCall` field to null.
    5. **Edge cases** — scenario + business consequence + severity, derived only from
       branches/conditions actually visible in the traced code (null checks, exception handlers,
       boundary conditions, etc.). `severity` must be exactly one of: LOW, MEDIUM, HIGH.
    6. **Non-functional requirements** — only where directly inferable from the code/config (e.g.
       an explicit timeout implies a response-time expectation; sanitization/escaping calls imply
       a security constraint; logger calls imply a monitoring requirement). `category` must be
       exactly one of: PERFORMANCE, SECURITY, LOGGING, MONITORING, OTHER. Omit requirements with
       no direct evidence rather than padding with generic best-practice statements. Populate
       `sourceFile` the same way as for business rules.

    ## Output contract
    Respond with a single JSON object and NOTHING else — no markdown code fences, no preamble, no
    trailing commentary, no explanation of your reasoning. The response must be valid JSON matching
    exactly this shape (no extra fields, no missing fields):

    {
      "userStory": "string",
      "gherkinScenarios": [
        {
          "scenarioId": "GS-001",
          "name": "string",
          "givenSteps": ["string"],
          "whenSteps": ["string"],
          "thenSteps": ["string"]
        }
      ],
      "businessRules": [
        {
          "ruleId": "BR-001",
          "description": "string",
          "precondition": "string",
          "postcondition": "string",
          "errorBehavior": "string",
          "sourceFile": "string or null",
          "externalCall": {
            "httpMethod": "string or null",
            "url": "string or null",
            "timeoutMs": "integer or null",
            "retryStrategy": "string or null",
            "fallbackBehavior": "string or null"
          }
        }
      ],
      "edgeCases": [
        {
          "scenario": "string",
          "businessConsequence": "string",
          "severity": "LOW"
        }
      ],
      "nonFunctionalRequirements": [
        {
          "category": "PERFORMANCE",
          "requirement": "string",
          "sourceFile": "string or null"
        }
      ]
    }

    Rules for the shape above:
    - `externalCall` must be a JSON object with all five sub-fields present (each individually
      null if unknown), or JSON null itself if the rule involves no external call. Never omit
      the key.
    - Every array key (`gherkinScenarios`, `businessRules`, `edgeCases`,
      `nonFunctionalRequirements`) must always be present, using an empty array `[]` if there is
      nothing to report — never omit the key, never use null for an array.
    - `timeoutMs` must be a JSON number (not a string) when present.
    - `severity` and `category` must match one of the listed enum values exactly, case-sensitive.
    
    ## Input

    Entry Point: %s %s (%s)
    Payload Type: %s
    Complexity: %s

    Traced Steps:
    %s

    Phase 2 Enrichment (entry point):
    %s

    Phase 2 Enrichment (intermediate steps):
    %s

    Source Code (traced steps):
    %s
    """.formatted(
                entryMethodOrType,
                entryPathOrClass,
                flow.entryPoint().filePath(),
                payloadType,
                complexity,
                stepsContext,
                enrichmentContext,
                stepEnrichmentContext,
                sourceCodeStr
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
                String ruleSourceFile = dto.sourceFile() != null && !dto.sourceFile().isEmpty()
                    ? dto.sourceFile() : flow.entryPoint().filePath();
                ExternalCall externalCall = null;
                if (dto.externalCall() != null) {
                    externalCall = new ExternalCall(
                        dto.externalCall().httpMethod(),
                        dto.externalCall().url(),
                        dto.externalCall().timeoutMs(),
                        dto.externalCall().retryStrategy(),
                        dto.externalCall().fallbackBehavior()
                    );
                }
                businessRules.add(new BusinessRule(
                    dto.ruleId(), dto.description(),
                    dto.precondition(), dto.postcondition(),
                    dto.errorBehavior(),
                    ruleSourceFile, 0, 0, externalCall
                ));
            }
        }

        List<EdgeCase> edgeCases = new ArrayList<>();
        if (response.edgeCases() != null) {
            for (EdgeCaseDto dto : response.edgeCases()) {
                edgeCases.add(new EdgeCase(
                    dto.scenario(), dto.businessConsequence(),
                    flow.entryPoint().filePath(), 0, 0,
                    dto.severity() != null ? dto.severity() : "MEDIUM"
                ));
            }
        }

        List<NonFunctionalRequirement> nonFunctionalRequirements = new ArrayList<>();
        if (response.nonFunctionalRequirements() != null) {
            for (NonFunctionalRequirementDto dto : response.nonFunctionalRequirements()) {
                nonFunctionalRequirements.add(new NonFunctionalRequirement(
                    dto.category(),
                    dto.requirement(),
                    dto.sourceFile() != null ? dto.sourceFile() : ""
                ));
            }
        }

        String mermaid = complexity == ComplexityLevel.FULL
            ? generateMermaid(flow)
            : null;

        String flowName = switch (flow.entryPoint()) {
            case HttpEntryPoint h -> h.httpMethod() + " " + h.path();
            case ScheduledEntryPoint s -> s.className() + "." + s.methodName();
            case KafkaEntryPoint k -> k.className() + "." + k.methodName();
            case RabbitMqEntryPoint r -> r.className() + "." + r.methodName();
            case ActiveMqEntryPoint a -> a.className() + "." + a.methodName();
            case EventListenerEntryPoint e -> e.className() + "." + e.methodName();
        };
        return new FunctionalFlow(
            flow.flowId(),
            flowName,
            flow.entryPoint(),
            flow.steps(),
            response.userStory(),
            gherkinScenarios,
            businessRules,
            edgeCases,
            complexity,
            mermaid,
            nonFunctionalRequirements
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

    private static String entryPointId(EntryPoint ep) {
        return switch (ep) {
            case HttpEntryPoint h -> h.httpMethod() + " " + h.path();
            case KafkaEntryPoint k -> "kafka:" + k.topics();
            case RabbitMqEntryPoint r -> "rabbitmq:" + r.queues();
            case ActiveMqEntryPoint a -> "activemq:" + a.destination();
            case EventListenerEntryPoint e -> "event-listener:" + e.payloadType();
            case ScheduledEntryPoint s -> "scheduled:" + s.schedule();
        };
    }

    private String formatEnrichmentContext(ExecutionFinding ef, String entryPointId) {
        StringBuilder sb = new StringBuilder();
        if (ef.businessRulesAndGuardrails() != null) {
            if (ef.businessRulesAndGuardrails().validations() != null) {
                for (ExecutionFinding.Validation v : ef.businessRulesAndGuardrails().validations()) {
                    if (entryPointId == null || v.entryPoint() == null || v.entryPoint().equals(entryPointId)) {
                        sb.append("Validation: ").append(v.fieldOrContext()).append(" - ").append(v.rule()).append("\n");
                    }
                }
            }
            if (ef.businessRulesAndGuardrails().edgeCases() != null) {
                for (ExecutionFinding.EdgeCase ec : ef.businessRulesAndGuardrails().edgeCases()) {
                    if (entryPointId == null || ec.entryPoint() == null || ec.entryPoint().equals(entryPointId)) {
                        sb.append("Edge case: ").append(ec.scenario()).append(" - ").append(ec.businessConsequence()).append("\n");
                    }
                }
            }
        }
        if (ef.architecturalConnections() != null && ef.architecturalConnections().outbound() != null) {
            var outbound = ef.architecturalConnections().outbound();
            if (outbound.httpCalls() != null && !outbound.httpCalls().isEmpty()) {
                sb.append("Outbound HTTP calls:\n");
                for (ExecutionFinding.HttpCall call : outbound.httpCalls()) {
                    sb.append("  - ").append(call.method()).append(" ").append(call.urlOrPath());
                    if (call.isExternal()) sb.append(" [external]");
                    if (call.externalContractHint() != null) {
                        sb.append(" (").append(call.externalContractHint()).append(")");
                    }
                    sb.append("\n");
                }
            }
            if (outbound.eventPublications() != null && !outbound.eventPublications().isEmpty()) {
                sb.append("Event publications:\n");
                for (ExecutionFinding.EventPublication pub : outbound.eventPublications()) {
                    sb.append("  - ").append(pub.broker()).append(": ").append(pub.topicOrQueue()).append("\n");
                }
            }
        }
        return sb.isEmpty() ? "No enrichment context available." : sb.toString();
    }

    private String readFileContent(String filePath, int startLine, int endLine) {
        if (filePath == null || filePath.isBlank()) return "";
        try {
            var lines = Files.readAllLines(Path.of(filePath));
            int from = Math.max(0, startLine - 1);
            int to = Math.min(lines.size(), endLine);
            if (from >= to) return "";
            return String.join("\n", lines.subList(from, to));
        } catch (IOException e) {
            log.warn("Could not read file {}: {}", filePath, e.getMessage());
            return "";
        }
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
            List<EdgeCaseDto> edgeCases,
            List<NonFunctionalRequirementDto> nonFunctionalRequirements
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
            String errorBehavior,
            String sourceFile,              // nullable
            ExternalCallDto externalCall    // nullable
    ) {}

    record ExternalCallDto(
            String httpMethod,
            String url,
            Integer timeoutMs,
            String retryStrategy,
            String fallbackBehavior
    ) {}

    record EdgeCaseDto(
            String scenario,
            String businessConsequence,
            String severity   // "LOW" | "MEDIUM" | "HIGH"
    ) {}

    record NonFunctionalRequirementDto(
            String category,   // "PERFORMANCE" | "SECURITY" | "LOGGING" | "MONITORING" | "OTHER"
            String requirement,
            String sourceFile
    ) {}
}
