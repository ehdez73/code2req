package com.github.ehdez73.code2req.generation.adapter.writer;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.CrossReferencedResult;
import com.github.ehdez73.code2req.extraction.domain.model.*;
import com.github.ehdez73.code2req.generation.domain.model.manifest.*;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

public final class ManifestMapper {

    private ManifestMapper() {}

    public static SemanticManifest toManifest(CrossReferencedResult result,
                                               List<OrphanedMethod> orphanedMethods,
                                               List<AmbiguityGap> quarantineGaps) {
        List<ManifestFeature> features = result.features().stream()
            .map(f -> toManifestFeature(f, quarantineGaps))
            .toList();

        List<ManifestCrossFlowRelationship> relationships = result.crossFlowRelationships().stream()
            .map(ManifestMapper::toManifestCrossFlowRelationship)
            .toList();

        List<ManifestOrphanedMethod> orphans = orphanedMethods.stream()
            .map(ManifestMapper::toManifestOrphanedMethod)
            .toList();

        return new SemanticManifest(
            "3.0.0",
            "code2req-generated",
            Instant.now().toString(),
            features,
            relationships,
            orphans
        );
    }

    private static ManifestFeature toManifestFeature(FunctionalFeature feature, List<AmbiguityGap> quarantineGaps) {
        List<ManifestFlow> flows = feature.flows().stream()
            .map(f -> toManifestFlow(f, quarantineGaps))
            .toList();

        return new ManifestFeature(
            feature.featureId(),
            feature.name(),
            feature.description(),
            flows
        );
    }

    private static ManifestFlow toManifestFlow(FunctionalFlow flow, List<AmbiguityGap> quarantineGaps) {
        List<ManifestStep> steps = toManifestSteps(flow.steps());
        List<ManifestAcceptanceCriterion> acceptanceCriteria = flow.acceptanceCriteria().stream()
            .map(ManifestMapper::toManifestAcceptanceCriterion)
            .toList();
        List<ManifestBusinessRule> businessRules = flow.businessRules() == null ? List.of()
            : flow.businessRules().stream().map(ManifestMapper::toManifestBusinessRule).toList();
        List<ManifestEdgeCase> edgeCases = flow.edgeCases() == null ? List.of()
            : flow.edgeCases().stream().map(ManifestMapper::toManifestEdgeCase).toList();
        List<ManifestNonFunctionalRequirement> nfrs = flow.nonFunctionalRequirements() == null ? List.of()
            : flow.nonFunctionalRequirements().stream().map(ManifestMapper::toManifestNonFunctionalRequirement).toList();

        boolean reviewRequired = isReviewRequired(flow.flowId(), quarantineGaps);
        ManifestUnresolvedReason unresolvedReason = reviewRequired
            ? unresolvedReasonForFlow(flow.flowId(), quarantineGaps)
            : null;

        ManifestTraceabilityGraph traceabilityGraph = buildTraceabilityGraph(flow);

        return new ManifestFlow(
            flow.flowId(),
            toManifestEntryPoint(flow.entryPoint()),
            steps,
            flow.userStory() != null ? flow.userStory() : "",
            acceptanceCriteria,
            businessRules,
            edgeCases,
            nfrs,
            flow.mermaidDiagram(),
            flow.complexity().name(),
            reviewRequired,
            unresolvedReason,
            traceabilityGraph
        );
    }

    static ManifestEntryPoint toManifestEntryPoint(EntryPoint ep) {
        String httpMethod = null;
        String path = null;
        String schedule = null;
        String topicOrQueue = null;

        switch (ep) {
            case HttpEntryPoint h -> {
                httpMethod = h.httpMethod();
                path = h.path();
            }
            case ScheduledEntryPoint s -> schedule = s.schedule();
            case KafkaEntryPoint k -> topicOrQueue = k.topics();
            case RabbitMqEntryPoint r -> topicOrQueue = r.queues();
            case ActiveMqEntryPoint a -> topicOrQueue = a.destination();
            case EventListenerEntryPoint e -> {}
        }

        return new ManifestEntryPoint(
            ep.type().name(),
            httpMethod,
            path,
            ep.className(),
            ep.methodName(),
            ep.filePath(),
            schedule,
            topicOrQueue
        );
    }

    private static List<ManifestStep> toManifestSteps(List<FlowStep> steps) {
        return steps.stream()
            .map(s -> new ManifestStep(
                s.stepIndex(),
                s.componentType().name(),
                s.className(),
                s.methodName() != null ? s.methodName() : "",
                s.businessPurpose(),
                s.sourceFile() != null ? s.sourceFile() : "",
                s.startLine(),
                s.endLine()
            ))
            .toList();
    }

    private static ManifestAcceptanceCriterion toManifestAcceptanceCriterion(GherkinScenario gs) {
        return new ManifestAcceptanceCriterion(
            gs.scenarioId(),
            gs.name(),
            gs.givenSteps(),
            gs.whenSteps(),
            gs.thenSteps()
        );
    }

    private static ManifestBusinessRule toManifestBusinessRule(BusinessRule rule) {
        ManifestExternalCall externalCall = rule.externalCall() != null
            ? toManifestExternalCall(rule.externalCall())
            : null;

        return new ManifestBusinessRule(
            rule.ruleId(),
            rule.description(),
            rule.precondition() != null && !rule.precondition().isEmpty() ? rule.precondition() : null,
            rule.postcondition() != null && !rule.postcondition().isEmpty() ? rule.postcondition() : null,
            rule.errorBehavior(),
            rule.sourceFile() != null && !rule.sourceFile().isEmpty() ? rule.sourceFile() : null,
            rule.startLine(),
            rule.endLine(),
            externalCall
        );
    }

    private static ManifestExternalCall toManifestExternalCall(ExternalCall ec) {
        return new ManifestExternalCall(
            ec.httpMethod(),
            ec.url(),
            ec.timeoutMs(),
            ec.retryStrategy() != null && !ec.retryStrategy().isEmpty() ? ec.retryStrategy() : null,
            ec.fallbackBehavior() != null && !ec.fallbackBehavior().isEmpty() ? ec.fallbackBehavior() : null
        );
    }

    private static ManifestEdgeCase toManifestEdgeCase(EdgeCase ec) {
        return new ManifestEdgeCase(
            ec.scenario(),
            ec.businessConsequence(),
            ec.severity(),
            ec.sourceFile() != null && !ec.sourceFile().isEmpty() ? ec.sourceFile() : null
        );
    }

    private static ManifestNonFunctionalRequirement toManifestNonFunctionalRequirement(NonFunctionalRequirement nfr) {
        return new ManifestNonFunctionalRequirement(
            nfr.category(),
            nfr.requirement(),
            nfr.sourceFile() != null && !nfr.sourceFile().isEmpty() ? nfr.sourceFile() : null
        );
    }

    private static ManifestCrossFlowRelationship toManifestCrossFlowRelationship(FlowRelationship rel) {
        return new ManifestCrossFlowRelationship(
            rel.sourceFlowId(),
            rel.targetFlowId(),
            rel.type().name(),
            rel.description()
        );
    }

    private static ManifestOrphanedMethod toManifestOrphanedMethod(OrphanedMethod m) {
        return new ManifestOrphanedMethod(
            m.className(),
            m.methodName(),
            m.filePath(),
            m.startLine(),
            m.endLine(),
            m.reason()
        );
    }

    static ManifestTraceabilityGraph buildTraceabilityGraph(FunctionalFlow flow) {
        List<ManifestTraceabilityNode> nodes = flow.steps().stream()
            .map(s -> {
                String nodeId = s.className() + "." + s.methodName();
                String lines = s.startLine() > 0 ? s.startLine() + "-" + s.endLine() : null;
                return new ManifestTraceabilityNode(
                    nodeId,
                    s.sourceFile() != null ? s.sourceFile() : "",
                    nodeId,
                    lines
                );
            })
            .toList();

        List<ManifestTraceabilityEdge> edges = new ArrayList<>();
        for (int i = 0; i < flow.steps().size() - 1; i++) {
            FlowStep from = flow.steps().get(i);
            FlowStep to = flow.steps().get(i + 1);
            edges.add(new ManifestTraceabilityEdge(
                from.className() + "." + from.methodName(),
                to.className() + "." + to.methodName(),
                linkTypeForComponent(to.componentType(), flow.entryPoint())
            ));
        }

        return new ManifestTraceabilityGraph(nodes, edges);
    }

    private static String linkTypeForComponent(FlowStepComponentType type, EntryPoint ep) {
        return switch (type) {
            case EXTERNAL_CALL -> "FLOATING_HTTP";
            case DATABASE -> "DATABASE_CALL";
            case EVENT_PUBLISHER -> switch (ep) {
                case KafkaEntryPoint k -> "TOPIC_KAFKA";
                case RabbitMqEntryPoint r -> "TOPIC_RABBITMQ";
                case ActiveMqEntryPoint a -> "TOPIC_ACTIVEMQ";
                default -> "DETERMINISTIC_CALL";
            };
            default -> "DETERMINISTIC_CALL";
        };
    }

    private static boolean isReviewRequired(String flowId, List<AmbiguityGap> gaps) {
        return gaps != null && gaps.stream().anyMatch(g -> g.flowId().equals(flowId));
    }

    private static ManifestUnresolvedReason unresolvedReasonForFlow(String flowId, List<AmbiguityGap> gaps) {
        return gaps.stream()
            .filter(g -> g.flowId().equals(flowId))
            .findFirst()
            .map(gap -> new ManifestUnresolvedReason(
                gap.reason().name(),
                gap.missingContext(),
                gap.confidence()
            ))
            .orElse(null);
    }
}
