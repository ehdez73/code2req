package com.github.ehdez73.code2req.generation.adapter.writer;

import com.embabel.agent.api.common.OperationContext;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.CrossReferencedResult;
import com.github.ehdez73.code2req.extraction.domain.model.ActiveMqEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.AmbiguityGap;
import com.github.ehdez73.code2req.extraction.domain.model.BusinessRule;
import com.github.ehdez73.code2req.extraction.domain.model.EdgeCase;
import com.github.ehdez73.code2req.extraction.domain.model.ExternalCall;
import com.github.ehdez73.code2req.extraction.domain.model.EventListenerEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import com.github.ehdez73.code2req.extraction.domain.model.GherkinScenario;
import com.github.ehdez73.code2req.extraction.domain.model.HttpEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.KafkaEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.NonFunctionalRequirement;
import com.github.ehdez73.code2req.extraction.domain.model.OrphanedMethod;
import com.github.ehdez73.code2req.extraction.domain.model.RabbitMqEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.ScheduledEntryPoint;
import com.github.ehdez73.code2req.extraction.domain.model.EntryPoint;
import com.github.ehdez73.code2req.generation.domain.model.SpecResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Instant;
import java.util.List;

public class SynthesizeSpecAction {

    private static final Logger log = LoggerFactory.getLogger(SynthesizeSpecAction.class);

    private final Path outputDir;
    private final ObjectMapper mapper;

    public SynthesizeSpecAction(Path outputDir) {
        this.outputDir = outputDir;
        this.mapper = new ObjectMapper();
    }

    public SpecResult synthesize(CrossReferencedResult crossRefResult,
                                 List<OrphanedMethod> orphanedMethods,
                                 List<AmbiguityGap> quarantineGaps,
                                 OperationContext context) throws IOException {
        Files.createDirectories(outputDir);

        Path markdownPath = outputDir.resolve("spec.md");
        Path manifestPath = outputDir.resolve("semantic_manifest.json");

        String markdown = generateMarkdown(crossRefResult, orphanedMethods, quarantineGaps);
        Files.writeString(markdownPath, markdown);

        var manifest = ManifestMapper.toManifest(crossRefResult, orphanedMethods, quarantineGaps);
        String manifestJson = mapper.writerWithDefaultPrettyPrinter().writeValueAsString(manifest);
        Files.writeString(manifestPath, manifestJson);

        int featureCount = crossRefResult.features().size();
        int flowCount = crossRefResult.features().stream()
            .mapToInt(f -> f.flows().size())
            .sum();

        log.info("Synthesized spec: {} features, {} flows -> {}, {}",
            featureCount, flowCount, markdownPath, manifestPath);

        return new SpecResult(markdownPath, manifestPath, featureCount, flowCount);
    }

    private String generateMarkdown(CrossReferencedResult result,
                                     List<OrphanedMethod> orphanedMethods,
                                     List<AmbiguityGap> quarantineGaps) {
        StringBuilder sb = new StringBuilder();

        appendHeader(sb);
        appendTableOfContents(sb, result, orphanedMethods, quarantineGaps);
        sb.append("\n---\n\n");

        for (FunctionalFeature feature : result.features()) {
            appendFeatureSection(sb, feature);
        }

        appendCrossFlowRelationships(sb, result);
        appendOrphanedMethods(sb, orphanedMethods);
        appendUnresolvedDependencies(sb, quarantineGaps);

        return sb.toString();
    }

    private void appendHeader(StringBuilder sb) {
        sb.append("# Generated Specification\n\n");
        sb.append("> Generated at: ").append(Instant.now()).append("\n\n");
    }

    private void appendTableOfContents(StringBuilder sb, CrossReferencedResult result,
                                        List<OrphanedMethod> orphanedMethods,
                                        List<AmbiguityGap> quarantineGaps) {
        sb.append("## Table of Contents\n\n");

        for (FunctionalFeature feature : result.features()) {
            sb.append("- [").append(feature.name()).append("](#").append(slugify(feature.name())).append(")\n");
            for (FunctionalFlow flow : feature.flows()) {
                sb.append("  - [").append(flow.name()).append("](#").append(slugify(flow.name())).append(")\n");
            }
        }

        if (!result.crossFlowRelationships().isEmpty()) {
            sb.append("- [Cross-Flow Relationships](#cross-flow-relationships)\n");
        }
        if (!orphanedMethods.isEmpty()) {
            sb.append("- [Orphaned Methods](#orphaned-methods)\n");
        }
        if (!quarantineGaps.isEmpty()) {
            sb.append("- [Unresolved Dependencies](#unresolved-dependencies)\n");
        }
    }

    private void appendFeatureSection(StringBuilder sb, FunctionalFeature feature) {
        sb.append("<a name=\"").append(slugify(feature.name())).append("\"></a>\n");
        sb.append("## ").append(feature.name()).append("\n\n");
        sb.append("**Description:** ").append(feature.description()).append("\n\n");

        for (FunctionalFlow flow : feature.flows()) {
            appendFlowSection(sb, flow);
        }
    }

    private void appendFlowSection(StringBuilder sb, FunctionalFlow flow) {
        sb.append("<a name=\"").append(slugify(flow.name())).append("\"></a>\n");
        sb.append("### ").append(flow.name()).append(" (`#").append(shortId(flow.flowId())).append("`)\n\n");

        if (flow.userStory() != null) {
            sb.append("**User Story:** ").append(flow.userStory()).append("\n\n");
        }

        appendFlowSummary(sb, flow);
        appendMermaidDiagram(sb, flow);
        appendExternalDependencies(sb, flow);
        appendDatabaseOperations(sb, flow);
        appendEventMessageDetails(sb, flow);
        appendFlowStepsTable(sb, flow);
        appendBusinessRules(sb, flow);
        appendEdgeCases(sb, flow);
        appendNonFunctionalRequirements(sb, flow);
        appendAcceptanceCriteria(sb, flow);

        sb.append("---\n\n");
    }

    private void appendFlowSummary(StringBuilder sb, FunctionalFlow flow) {
        long externalCount = flow.steps().stream()
            .filter(s -> s.componentType() == FlowStepComponentType.EXTERNAL_CALL).count();
        long dbCount = flow.steps().stream()
            .filter(s -> s.componentType() == FlowStepComponentType.DATABASE).count();
        long stepCount = flow.steps().size();
        sb.append("**Flow Summary:** Complexity: ").append(flow.complexity())
            .append(" | Steps: ").append(stepCount)
            .append(" | External Calls: ").append(externalCount)
            .append(" | Database Operations: ").append(dbCount)
            .append("\n\n");
    }

    private void appendMermaidDiagram(StringBuilder sb, FunctionalFlow flow) {
        if (flow.mermaidDiagram() == null) return;
        sb.append("#### Execution Flow\n\n");
        sb.append("```mermaid\n").append(flow.mermaidDiagram()).append("\n```\n\n");
    }

    private void appendExternalDependencies(StringBuilder sb, FunctionalFlow flow) {
        var externalSteps = flow.steps().stream()
            .filter(s -> s.componentType() == FlowStepComponentType.EXTERNAL_CALL)
            .toList();
        if (externalSteps.isEmpty()) return;

        sb.append("#### External Dependencies\n\n");
        sb.append("| Method | URL / Path | Client Type | Source File |\n");
        sb.append("|---|---|---|---|\n");
        for (var step : externalSteps) {
            String method = "";
            String url = "";
            for (String e : step.enrichments()) {
                if (e.matches("^(GET|POST|PUT|DELETE|PATCH|HEAD|OPTIONS)\\s.*")) {
                    String[] parts = e.split("\\s+", 2);
                    method = parts[0];
                    url = parts.length > 1 ? parts[1] : e;
                } else {
                    url = e;
                }
            }
            sb.append("| ").append(method)
              .append(" | ").append(url)
              .append(" | External Service")
              .append(" | ").append(step.sourceFile() != null ? step.sourceFile() : "")
              .append(" |\n");
        }
        sb.append("\n");
    }

    private void appendDatabaseOperations(StringBuilder sb, FunctionalFlow flow) {
        var dbSteps = flow.steps().stream()
            .filter(s -> s.componentType() == FlowStepComponentType.DATABASE)
            .toList();
        if (dbSteps.isEmpty()) return;

        sb.append("#### Database Operations\n\n");
        sb.append("| Class | Method | SQL / Details | Source File |\n");
        sb.append("|---|---|---|---|\n");
        for (var step : dbSteps) {
            String sql = !step.enrichments().isEmpty() ? step.enrichments().get(0) : "";
            sb.append("| ").append(step.className())
              .append(" | ").append(step.methodName())
              .append(" | ").append(sql)
              .append(" | ").append(step.sourceFile() != null ? step.sourceFile() : "")
              .append(" |\n");
        }
        sb.append("\n");
    }

    private void appendEventMessageDetails(StringBuilder sb, FunctionalFlow flow) {
        var eventSteps = flow.steps().stream()
            .filter(s -> s.componentType() == FlowStepComponentType.SCHEDULED_TASK
                      || s.componentType() == FlowStepComponentType.EVENT_PUBLISHER)
            .toList();

        EntryPoint entryPoint = flow.entryPoint();
        boolean hasMessagingEntryPoint = entryPoint instanceof KafkaEntryPoint
            || entryPoint instanceof RabbitMqEntryPoint
            || entryPoint instanceof ActiveMqEntryPoint
            || entryPoint instanceof EventListenerEntryPoint;

        if (eventSteps.isEmpty() && !hasMessagingEntryPoint) return;

        String brokerType = switch (entryPoint) {
            case ScheduledEntryPoint s -> "@Scheduled";
            case EventListenerEntryPoint e -> "@EventListener";
            case KafkaEntryPoint k -> "Kafka";
            case RabbitMqEntryPoint r -> "RabbitMQ";
            case ActiveMqEntryPoint a -> "ActiveMQ";
            case HttpEntryPoint h -> "ApplicationEventPublisher";
        };

        sb.append("#### Event/Message Details\n\n");
        sb.append("| Component Type | Broker / Mechanism | Topic / Queue | Schedule | Event Type | Source File |\n");
        sb.append("|---|---|---|---|---|---|\n");

        for (var step : eventSteps) {
            String topic = step.componentType() == FlowStepComponentType.EVENT_PUBLISHER
                ? entryPointTopicOrQueue(entryPoint)
                : "\u2014";
            String schedule = step.componentType() == FlowStepComponentType.SCHEDULED_TASK
                ? entryPointSchedule(entryPoint)
                : "\u2014";
            String eventType = entryPointPayloadType(entryPoint);
            sb.append("| ").append(step.componentType())
              .append(" | ").append(brokerType)
              .append(" | ").append(topic)
              .append(" | ").append(schedule)
              .append(" | ").append(eventType)
              .append(" | ").append(step.sourceFile() != null ? step.sourceFile() : "")
              .append(" |\n");
        }

        if (hasMessagingEntryPoint && eventSteps.stream().noneMatch(s -> s.componentType() == FlowStepComponentType.EVENT_PUBLISHER)) {
            String topic = entryPointTopicOrQueue(entryPoint);
            String sourceFile = !flow.steps().isEmpty() ? flow.steps().getFirst().sourceFile() : "";
            String eventType = entryPointPayloadType(entryPoint);
            sb.append("| SERVICE")
              .append(" | ").append(brokerType)
              .append(" | ").append(topic)
              .append(" | \u2014")
              .append(" | ").append(eventType)
              .append(" | ").append(sourceFile != null ? sourceFile : "")
              .append(" |\n");
        }

        sb.append("\n");
    }

    private static String entryPointTopicOrQueue(EntryPoint entryPoint) {
        return switch (entryPoint) {
            case KafkaEntryPoint k -> k.topics();
            case RabbitMqEntryPoint r -> r.queues();
            case ActiveMqEntryPoint a -> a.destination();
            default -> null;
        };
    }

    private static String entryPointSchedule(EntryPoint entryPoint) {
        return switch (entryPoint) {
            case ScheduledEntryPoint s -> s.schedule();
            default -> null;
        };
    }

    private static String entryPointPayloadType(EntryPoint entryPoint) {
        String type = switch (entryPoint) {
            case KafkaEntryPoint k -> k.payloadType();
            case RabbitMqEntryPoint r -> r.payloadType();
            case ActiveMqEntryPoint a -> a.payloadType();
            case EventListenerEntryPoint e -> e.payloadType();
            case HttpEntryPoint h -> !h.requestBodies().isEmpty() ? h.requestBodies().get(0) : null;
            default -> null;
        };
        return type != null && !type.isEmpty() ? type : "\u2014";
    }

    private void appendFlowStepsTable(StringBuilder sb, FunctionalFlow flow) {
        sb.append("#### Flow Steps\n\n");
        sb.append("| # | Component Type | Class | Method | Source File |\n");
        sb.append("|---|---|---|---|---|\n");
        for (var step : flow.steps()) {
            sb.append("| ").append(step.stepIndex())
              .append(" | ").append(step.componentType())
              .append(" | ").append(step.className())
              .append(" | ").append(step.methodName() != null ? step.methodName() : "-")
              .append(" | ").append(step.sourceFile() != null ? step.sourceFile() : "")
              .append(" |\n");
        }
        sb.append("\n");
    }

    private void appendBusinessRules(StringBuilder sb, FunctionalFlow flow) {
        if (flow.businessRules().isEmpty()) return;

        sb.append("#### Business Rules\n\n");
        sb.append("| ID | Rule | Precondition | Postcondition | Error Behavior | Source |\n");
        sb.append("|---|---|---|---|---|---|\n");
        for (BusinessRule rule : flow.businessRules()) {
            sb.append("| ").append(rule.ruleId())
              .append(" | ").append(rule.description())
              .append(" | ").append(rule.precondition())
              .append(" | ").append(rule.postcondition())
              .append(" | ").append(rule.errorBehavior())
              .append(" | ").append(formatSourceRef(rule.sourceFile(), rule.startLine(), rule.endLine()))
              .append(" |\n");
            if (rule.externalCall() != null) {
                ExternalCall ec = rule.externalCall();
                sb.append("  - **External call:** ").append(ec.httpMethod()).append(" ").append(ec.url());
                if (ec.timeoutMs() != null) sb.append(" (").append(ec.timeoutMs()).append("ms timeout)");
                if (ec.retryStrategy() != null && !ec.retryStrategy().isEmpty()) sb.append(", retry: ").append(ec.retryStrategy());
                if (ec.fallbackBehavior() != null && !ec.fallbackBehavior().isEmpty()) sb.append(", fallback: ").append(ec.fallbackBehavior());
                sb.append("\n");
            }
        }
        sb.append("\n");
    }

    private void appendEdgeCases(StringBuilder sb, FunctionalFlow flow) {
        if (flow.edgeCases().isEmpty()) return;

        sb.append("#### Edge Cases\n\n");
        sb.append("| Scenario | Business Consequence | Severity | Source |\n");
        sb.append("|---|---|---|---|\n");
        flow.edgeCases().forEach(ec ->
            sb.append("| ").append(ec.scenario())
              .append(" | ").append(ec.businessConsequence())
              .append(" | ").append(ec.severity())
              .append(" | ").append(formatSourceRef(ec.sourceFile(), ec.startLine(), ec.endLine()))
              .append(" |\n")
        );
        sb.append("\n");
    }

    private void appendNonFunctionalRequirements(StringBuilder sb, FunctionalFlow flow) {
        if (flow.nonFunctionalRequirements() == null || flow.nonFunctionalRequirements().isEmpty()) return;

        sb.append("#### Non-Functional Requirements\n\n");
        sb.append("| Category | Requirement | Source |\n");
        sb.append("|---|---|---|\n");
        for (NonFunctionalRequirement nfr : flow.nonFunctionalRequirements()) {
            sb.append("| ").append(nfr.category())
              .append(" | ").append(nfr.requirement())
              .append(" | ").append(nfr.sourceFile())
              .append(" |\n");
        }
        sb.append("\n");
    }

    private void appendAcceptanceCriteria(StringBuilder sb, FunctionalFlow flow) {
        if (flow.acceptanceCriteria().isEmpty()) return;

        sb.append("#### Acceptance Criteria (Gherkin)\n\n");
        for (GherkinScenario gs : flow.acceptanceCriteria()) {
            sb.append("```gherkin\n");
            sb.append("Scenario: ").append(gs.name()).append("\n");
            gs.givenSteps().forEach(g -> sb.append("  Given ").append(g).append("\n"));
            gs.whenSteps().forEach(w -> sb.append("  When ").append(w).append("\n"));
            gs.thenSteps().forEach(t -> sb.append("  Then ").append(t).append("\n"));
            sb.append("```\n\n");
        }
    }

    private void appendCrossFlowRelationships(StringBuilder sb, CrossReferencedResult result) {
        if (result.crossFlowRelationships().isEmpty()) return;

        var flowLookup = result.features().stream()
            .flatMap(f -> f.flows().stream())
            .collect(java.util.stream.Collectors.toMap(FunctionalFlow::flowId, f -> f));

        sb.append("## Cross-Flow Relationships\n\n");
        sb.append("| Source Flow | Target Flow | Type | Description |\n");
        sb.append("|---|---|---|---|\n");
        result.crossFlowRelationships().forEach(rel ->
            sb.append("| ").append(flowLink(rel.sourceFlowId(), flowLookup.get(rel.sourceFlowId())))
              .append(" | ").append(flowLink(rel.targetFlowId(), flowLookup.get(rel.targetFlowId())))
              .append(" | ").append(rel.type())
              .append(" | ").append(rel.description())
              .append(" |\n")
        );
        sb.append("\n");
    }

    private static String flowLink(String rawId, FunctionalFlow flow) {
        String hash = shortId(rawId);
        if (flow == null) return hash;
        return "[`#" + hash + "` - " + flow.name() + "](#" + slugify(flow.name()) + ")";
    }

    private void appendOrphanedMethods(StringBuilder sb, List<OrphanedMethod> orphanedMethods) {
        if (orphanedMethods.isEmpty()) return;

        sb.append("## Orphaned Methods\n\n");
        sb.append("Methods not reachable from any discovered entry point:\n\n");
        sb.append("| Class | Method | File | Reason |\n");
        sb.append("|---|---|---|---|\n");
        orphanedMethods.forEach(m ->
            sb.append("| ").append(m.className())
              .append(" | ").append(m.methodName())
              .append(" | ").append(m.filePath())
              .append(" | ").append(m.reason())
              .append(" |\n")
        );
        sb.append("\n");
    }

    private void appendUnresolvedDependencies(StringBuilder sb, List<AmbiguityGap> quarantineGaps) {
        if (quarantineGaps.isEmpty()) return;

        sb.append("## Unresolved Dependencies\n\n");
        sb.append("Flows flagged for human review:\n\n");
        quarantineGaps.forEach(gap -> {
            sb.append("- **").append(gap.flowName()).append("** (`").append(gap.filePath()).append("`): ").append(gap.missingContext()).append("\n");
            sb.append("  - Suggested: ").append(gap.suggestedApproach()).append("\n");
            sb.append("  - Confidence: ").append(String.format("%.0f", gap.confidence() * 100)).append("%\n");
            sb.append("  - Reason: ").append(gap.reason()).append("\n\n");
        });
    }

    private static String shortId(String id) {
        if (id == null || id.isBlank()) return "";
        return Integer.toHexString(id.hashCode());
    }

    private static String slugify(String text) {
        return text.toLowerCase().replaceAll("[^a-z0-9]+", "-").replaceAll("^-|-$", "");
    }

    private String formatSourceRef(String file, int startLine, int endLine) {
        if (file == null || file.isBlank()) return "";
        if (startLine > 0 && endLine > 0) {
            return file + ":" + startLine + "-" + endLine;
        }
        return file;
    }
}
