package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.embabel.agent.api.common.OperationContext;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ehdez73.code2req.extraction.domain.model.ExecutionFinding;
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
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStatus;
import com.github.ehdez73.code2req.extraction.domain.model.ExternalCall;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import com.github.ehdez73.code2req.extraction.domain.model.GherkinScenario;
import com.github.ehdez73.code2req.extraction.domain.model.NonFunctionalRequirement;
import com.github.ehdez73.code2req.infrastructure.persistence.ExecutionFindingStore;
import com.github.ehdez73.code2req.infrastructure.persistence.FindingType;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

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
    private final ExecutionFindingStore executionFindingStore;
    private final ObjectMapper objectMapper;
    private final boolean resume;

    public AnalyzeFlowAction(CodebaseKnowledge knowledge, ExecutionFindingStore executionFindingStore,
                             ObjectMapper objectMapper, boolean resume) {
        this.knowledge = knowledge;
        this.executionFindingStore = executionFindingStore;
        this.objectMapper = objectMapper;
        this.resume = resume;
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
        String flowKey = deterministicFlowKey(ep);

        Optional<FlowAnalysisResponse> cached = loadCachedAnalysis(flowKey);
        if (cached.isPresent()) {
            log.info("Reusing cached flow analysis for {}", flowKey);
            return buildFunctionalFlow(flow, complexity, cached.get());
        } else {
            log.info("Analyzing flow {}", flowKey);
        }

        var epData = collectEntryPointData(ep);
        Optional<ExecutionFinding> enrichment = knowledge.semanticEnrichment()
            .findByFilePath(ep.filePath());

        Set<String> tracedMethodsInFile = flow.steps().stream()
            .filter(s -> ep.filePath().equals(s.sourceFile()))
            .map(FlowStep::methodName)
            .filter(Objects::nonNull)
            .collect(Collectors.toSet());

        String enrichmentContext = enrichment
            .map(ef -> formatEnrichmentContext(ef, epData.epId(), tracedMethodsInFile))
            .orElse("No Phase 2 enrichment available.");

        String stepEnrichmentContext = buildStepEnrichmentContext(flow, ep);
        String stepsContext = buildStepsContext(flow.steps());
        String sourceCodeStr = buildSourceCodeContext(flow.steps());
        String configContext = buildConfigContext(ep);

        String prompt = buildPrompt(epData, complexity, stepsContext,
            enrichmentContext, stepEnrichmentContext, sourceCodeStr, configContext);

        FlowAnalysisResponse response = callLlmAndPersist(prompt, flowKey, context);

        return buildFunctionalFlow(flow, complexity, response);
    }

    private record EntryPointData(
        String entryMethodOrType,
        String entryPathOrClass,
        String filePath,
        String payloadType,
        String epId
    ) {}

    private Optional<FlowAnalysisResponse> loadCachedAnalysis(String flowKey) {
        if (!resume) return Optional.empty();
        List<Map<String, Object>> existing = executionFindingStore.findByTaskIdAndType(flowKey, FindingType.FLOW_ANALYSIS);
        if (existing.isEmpty()) return Optional.empty();
        try {
            String json = (String) existing.get(0).get("finding_json");
            return Optional.of(objectMapper.readValue(json, FlowAnalysisResponse.class));
        } catch (Exception e) {
            log.warn("Failed to deserialize cached FLOW_ANALYSIS for {}: {}", flowKey, e.getMessage());
            return Optional.empty();
        }
    }

    private EntryPointData collectEntryPointData(EntryPoint ep) {
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
        return new EntryPointData(entryMethodOrType, entryPathOrClass, ep.filePath(), payloadType, entryPointId(ep));
    }

    private String buildStepEnrichmentContext(ExecutionFlow flow, EntryPoint ep) {
        StringBuilder sb = new StringBuilder();
        flow.steps().stream()
            .filter(s -> s.sourceFile() != null && !s.sourceFile().equals(ep.filePath()))
            .collect(Collectors.groupingBy(FlowStep::sourceFile))
            .forEach((filePath, steps) -> {
                String fileName = filePath.contains("/")
                    ? filePath.substring(filePath.lastIndexOf('/') + 1)
                    : filePath;
                Set<String> methods = steps.stream()
                    .map(FlowStep::methodName)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toSet());
                knowledge.semanticEnrichment().findByFilePath(filePath)
                    .ifPresent(ef -> {
                        sb.append("  ").append(fileName).append(":\n");
                        sb.append(formatEnrichmentContext(ef, null, methods).indent(4));
                    });
            });
        return sb.isEmpty() ? "No Phase 2 enrichment available for intermediate steps." : sb.toString();
    }

    private static String buildStepsContext(List<FlowStep> steps) {
        return steps.stream()
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
    }

    private String buildSourceCodeContext(List<FlowStep> steps) {
        Map<String, List<FlowStep>> snippetsByFile = new LinkedHashMap<>();
        for (FlowStep step : steps) {
            if (step.sourceFile() != null && step.startLine() > 0 && step.endLine() > 0) {
                snippetsByFile.computeIfAbsent(step.sourceFile(), k -> new ArrayList<>()).add(step);
            }
        }
        StringBuilder sb = new StringBuilder();
        for (var entry : snippetsByFile.entrySet()) {
            String filePath = entry.getKey();
            String fileName = filePath.contains("/")
                ? filePath.substring(filePath.lastIndexOf('/') + 1)
                : filePath;
            List<FlowStep> fileSteps = entry.getValue();
            FlowStepComponentType componentType = fileSteps.get(0).componentType();
            sb.append("  ").append(fileName).append(": ").append(componentType.name()).append("\n");
            sb.append("  ```java\n");

            List<String> fileLines = readAllLines(filePath);
            if (fileLines.isEmpty()) {
                sb.append("  ```\n");
                continue;
            }

            String classHeader = findClassHeader(fileLines, fileSteps);
            if (!classHeader.isEmpty()) {
                sb.append(classHeader.indent(4));
            }

            Set<String> seenLineRanges = new LinkedHashSet<>();
            for (FlowStep step : fileSteps) {
                String rangeKey = step.startLine() + "-" + step.endLine();
                if (!seenLineRanges.add(rangeKey)) continue;
                String code = extractLines(fileLines, step.startLine(), step.endLine());
                if (!code.isEmpty()) {
                    sb.append("  // lines ").append(step.startLine()).append("-")
                        .append(step.endLine());
                    if (step.methodName() != null) {
                        sb.append(" (").append(step.methodName()).append(")");
                    }
                    sb.append("\n");
                    sb.append(code.indent(4));
                }
            }

            if (!classHeader.isEmpty()) {
                sb.append("}\n".indent(4));
            }
            sb.append("  ```\n");
        }
        return sb.isEmpty() ? "No source code context available." : sb.toString();
    }

    private String buildConfigContext(EntryPoint ep) {
        if (ep instanceof ScheduledEntryPoint se && se.configFilePath() != null) {
            return """
                ## XML Configuration

                Schedule: %s
                Config File: %s

                """.formatted(se.schedule(), se.configFilePath());
        }
        return "";
    }

    private static String findClassHeader(List<String> lines, List<FlowStep> stepsForFile) {
        String className = stepsForFile.get(0).className();
        int firstMethodLine = stepsForFile.stream()
            .mapToInt(FlowStep::startLine)
            .min().orElse(1);

        int classLineIdx = -1;
        for (int i = firstMethodLine - 2; i >= 0; i--) {
            String line = lines.get(i).trim();
            if (line.matches(".*\\b(class|interface|enum|@interface)\\s+" + Pattern.quote(className) + "\\b.*")) {
                classLineIdx = i;
                break;
            }
        }

        if (classLineIdx < 0) return "";

        int classStartIdx = classLineIdx;
        while (classStartIdx > 0) {
            String above = lines.get(classStartIdx - 1).trim();
            if (above.startsWith("@") || above.isEmpty() || above.startsWith("//")
                || above.startsWith("/*") || above.startsWith("*")) {
                classStartIdx--;
            } else {
                break;
            }
        }

        StringBuilder sb = new StringBuilder();
        for (int i = classStartIdx; i <= classLineIdx; i++) {
            String line = lines.get(i);
            if (i == classLineIdx && !line.trim().endsWith("{")) {
                sb.append(line).append(" {\n");
            } else {
                sb.append(line).append("\n");
            }
        }
        return sb.toString();
    }

    private static String extractLines(List<String> lines, int startLine, int endLine) {
        int from = Math.max(0, startLine - 1);
        int to = Math.min(lines.size(), endLine);
        if (from >= to) return "";
        return String.join("\n", lines.subList(from, to));
    }

    private static List<String> readAllLines(String filePath) {
        if (filePath == null || filePath.isBlank()) return List.of();
        try {
            return Files.readAllLines(Path.of(filePath));
        } catch (IOException e) {
            log.warn("Could not read file {}: {}", filePath, e.getMessage());
            return List.of();
        }
    }

    private FlowAnalysisResponse callLlmAndPersist(String prompt, String flowKey,
                                                     OperationContext context) {
        FlowAnalysisResponse response = context.ai()
            .withDefaultLlm()
            .createObject(prompt, FlowAnalysisResponse.class);

        try {
            String json = objectMapper.writeValueAsString(response);
            executionFindingStore.save(flowKey, FindingType.FLOW_ANALYSIS, json, true);
        } catch (JsonProcessingException e) {
            log.warn("Failed to serialize FLOW_ANALYSIS for {}: {}", flowKey, e.getMessage());
        }

        return response;
    }

    private String buildPrompt(EntryPointData epData, ComplexityLevel complexity,
                               String stepsContext, String enrichmentContext,
                               String stepEnrichmentContext, String sourceCodeStr,
                               String configContext) {
        String filePath = epData.filePath();
        return """
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
    - If Phase 2 enrichment is unavailable for a step, derive business rules, edge cases, and
      non-functional requirements directly from the Source Code section.
    - The Traced Steps section is fully resolved — do not re-derive call graphs, component types,
      or structural relationships. Focus on business semantics only.

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
    Output MUST start with `{` and end with `}`. No markdown code fences, no prose, no preamble, no
    trailing commentary, no explanation of your reasoning. Any non-JSON output will break the
    pipeline. The response must be valid JSON matching exactly this shape (no extra fields, no
    missing fields):

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

    %s
    Traced Steps:
    %s

    Phase 2 Enrichment (entry point):
    %s

    Phase 2 Enrichment (intermediate steps):
    %s

    Source Code (traced steps):
    %s
    """.formatted(
                epData.entryMethodOrType(),
                epData.entryPathOrClass(),
                filePath,
                epData.payloadType(),
                complexity,
                configContext,
                stepsContext,
                enrichmentContext,
                stepEnrichmentContext,
                sourceCodeStr
        );
    }

    private FunctionalFlow buildFunctionalFlow(ExecutionFlow flow, ComplexityLevel complexity, FlowAnalysisResponse response) {
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

    private static String deterministicFlowKey(EntryPoint ep) {
        return ep.type().name() + ":" + ep.filePath() + ":" + ep.className() + ":" + ep.methodName();
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

    private String formatEnrichmentContext(ExecutionFinding ef, String entryPointId,
                                            Set<String> tracedMethodsInFile) {
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
                    if (call.encapsulatedIn() != null && !tracedMethodsInFile.isEmpty()
                        && tracedMethodsInFile.stream().noneMatch(m -> call.encapsulatedIn().contains(m))) {
                        continue;
                    }
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
        if (ef.testInsights() != null && !ef.testInsights().isEmpty()) {
            sb.append("Test insights:\n");
            ef.testInsights().stream()
                .collect(Collectors.groupingBy(
                    ExecutionFinding.TestInsight::testFilePath,
                    LinkedHashMap::new,
                    Collectors.toList()))
                .forEach((filePath, insights) -> {
                    sb.append("  - ").append(filePath).append("\n");
                    for (var ti : insights) {
                        sb.append("    - ").append(ti.scenarioVerified()).append("\n");
                        if (ti.hiddenRuleUncovered() != null && !ti.hiddenRuleUncovered().isBlank()) {
                            sb.append("        Hidden rule: ").append(ti.hiddenRuleUncovered()).append("\n");
                        }
                    }
                });
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
