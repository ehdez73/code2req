package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.github.ehdez73.code2req.extraction.adapter.agent.model.CrossReferencedResult;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.GroupedFlowsResult;
import com.github.ehdez73.code2req.indexing.domain.analyzer.event.link.TopicLink;
import com.github.ehdez73.code2req.indexing.domain.analyzer.httpclient.FloatingLinkInfo;
import com.github.ehdez73.code2req.extraction.domain.model.CodebaseKnowledge;
import com.github.ehdez73.code2req.extraction.domain.model.FlowRelationship;
import com.github.ehdez73.code2req.extraction.domain.model.FlowRelationshipType;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/**
 * Resolves inter-flow dependencies by matching floating HTTP calls and topic
 * publications to known endpoints. Records FlowRelationships between flows
 * (DELEGATES_TO, PUBLISHES_EVENT, CONSUMES_EVENT, CALLS_EXTERNAL) for
 * inclusion in the final specification's cross-flow relationships section.
 */
public class CrossReferenceFlowsAction {

    private static final Logger log = LoggerFactory.getLogger(CrossReferenceFlowsAction.class);

    private final CodebaseKnowledge knowledge;

    public CrossReferenceFlowsAction(CodebaseKnowledge knowledge) {
        this.knowledge = knowledge;
    }

    public CrossReferencedResult crossReference(GroupedFlowsResult groupedResult) {
        List<FlowRelationship> relationships = new ArrayList<>();

        List<FlowRelationship> httpRelationships = crossReferenceHttpLinks(groupedResult);
        relationships.addAll(httpRelationships);

        List<FlowRelationship> topicRelationships = crossReferenceTopicLinks(groupedResult);
        relationships.addAll(topicRelationships);

        List<FunctionalFeature> updatedFeatures = attachRelationships(groupedResult.features(), relationships);

        log.info("Cross-referenced {} relationships across {} features",
            relationships.size(), updatedFeatures.size());

        return new CrossReferencedResult(updatedFeatures, relationships);
    }

    private List<FlowRelationship> crossReferenceHttpLinks(GroupedFlowsResult groupedResult) {
        List<FlowRelationship> relationships = new ArrayList<>();

        List<FloatingLinkInfo> floatingLinks = knowledge.findAllFloatingLinks();

        Map<String, List<FunctionalFlow>> flowsByPath = groupedResult.features().stream()
            .flatMap(f -> f.flows().stream())
            .filter(f -> f.entryPoint().path() != null)
            .collect(Collectors.toMap(
                f -> normalizePath(f.entryPoint().path()),
                f -> List.of(f),
                (a, b) -> { List<FunctionalFlow> merged = new ArrayList<>(a); merged.addAll(b); return merged; }
            ));

        for (FloatingLinkInfo link : floatingLinks) {
            matchFlowByUrl(relationships, groupedResult, flowsByPath,
                link.sourceFilePath(), link.method(), link.urlPattern());
        }

        for (FunctionalFeature feature : groupedResult.features()) {
            for (FunctionalFlow flow : feature.flows()) {
                flow.steps().stream()
                    .map(FlowStep::sourceFile)
                    .filter(f -> f != null)
                    .distinct()
                    .forEach(sourceFile -> {
                        knowledge.semanticEnrichment().findByFilePath(sourceFile)
                            .filter(ef -> ef.architecturalConnections() != null
                                && ef.architecturalConnections().outbound() != null
                                && ef.architecturalConnections().outbound().httpCalls() != null)
                            .ifPresent(ef -> {
                                for (var httpCall : ef.architecturalConnections().outbound().httpCalls()) {
                                    matchFlowByUrl(relationships, groupedResult, flowsByPath,
                                        sourceFile, httpCall.method(), httpCall.urlOrPath());
                                }
                            });
                    });
            }
        }

        return relationships;
    }

    private void matchFlowByUrl(List<FlowRelationship> relationships, GroupedFlowsResult groupedResult,
                                 Map<String, List<FunctionalFlow>> flowsByPath,
                                 String sourceFile, String method, String urlPattern) {
        String targetPath = normalizePath(urlPattern);

        for (Map.Entry<String, List<FunctionalFlow>> entry : flowsByPath.entrySet()) {
            if (targetPath.contains(entry.getKey()) || entry.getKey().contains(targetPath)) {
                List<FunctionalFlow> sourceFlows = groupedResult.features().stream()
                    .flatMap(f -> f.flows().stream())
                    .filter(f -> f.entryPoint().filePath().equals(sourceFile)
                        || f.steps().stream().anyMatch(s -> sourceFile.equals(s.sourceFile())))
                    .collect(Collectors.toList());

                for (FunctionalFlow sourceFlow : sourceFlows) {
                    for (FunctionalFlow targetFlow : entry.getValue()) {
                        if (!sourceFlow.flowId().equals(targetFlow.flowId())) {
                            relationships.add(new FlowRelationship(
                                sourceFlow.flowId(),
                                targetFlow.flowId(),
                                FlowRelationshipType.CALLS_EXTERNAL,
                                method + " " + urlPattern
                            ));
                        }
                    }
                }
            }
        }
    }

    private List<FlowRelationship> crossReferenceTopicLinks(GroupedFlowsResult groupedResult) {
        List<FlowRelationship> relationships = new ArrayList<>();

        List<TopicLink> topicLinks = knowledge.findAllTopicLinks();

        Map<String, List<FunctionalFlow>> flowsByTopic = groupedResult.features().stream()
            .flatMap(f -> f.flows().stream())
            .filter(f -> f.entryPoint().topicOrQueue() != null)
            .collect(Collectors.toMap(
                f -> f.entryPoint().topicOrQueue(),
                f -> List.of(f),
                (a, b) -> { List<FunctionalFlow> merged = new ArrayList<>(a); merged.addAll(b); return merged; }
            ));

        for (TopicLink link : topicLinks) {
            List<FunctionalFlow> consumers = flowsByTopic.getOrDefault(link.topic(), List.of());

            for (FunctionalFlow consumer : consumers) {
                List<FunctionalFlow> producers = findFlowsPublishingToTopic(groupedResult, link);
                for (FunctionalFlow producer : producers) {
                    if (!producer.flowId().equals(consumer.flowId())) {
                        FlowRelationshipType type = link.resolvedStatus().equals("RESOLVED")
                            ? FlowRelationshipType.PUBLISHES_EVENT
                            : FlowRelationshipType.CONSUMES_EVENT;

                        relationships.add(new FlowRelationship(
                            producer.flowId(),
                            consumer.flowId(),
                            FlowRelationshipType.PUBLISHES_EVENT,
                            "Topic: " + link.topic()
                        ));
                    }
                }
            }
        }

        return relationships;
    }

    private List<FunctionalFlow> findFlowsPublishingToTopic(GroupedFlowsResult groupedResult, TopicLink link) {
        String producerFile = link.producerFilePath();

        if (producerFile != null) {
            return groupedResult.features().stream()
                .flatMap(f -> f.flows().stream())
                .filter(f -> f.entryPoint().filePath().equals(producerFile)
                    || f.steps().stream().anyMatch(s -> producerFile.equals(s.sourceFile())))
                .collect(Collectors.toList());
        }

        String topic = link.topic();
        if (topic == null) return List.of();

        return groupedResult.features().stream()
            .flatMap(f -> f.flows().stream())
            .filter(f -> f.steps().stream().anyMatch(s ->
                s.enrichments().stream().anyMatch(e -> e.contains(topic))))
            .collect(Collectors.toList());
    }

    private String normalizePath(String path) {
        if (path == null) return "";
        return path.replaceAll("\\{[^}]+\\}", "").replaceAll("/+", "/").replaceAll("^/|/$", "");
    }

    private List<FunctionalFeature> attachRelationships(List<FunctionalFeature> features, List<FlowRelationship> relationships) {
        Map<String, List<FlowRelationship>> byFeature = relationships.stream()
            .collect(Collectors.groupingBy(r -> {
                for (FunctionalFeature f : features) {
                    boolean containsSource = f.flows().stream().anyMatch(fl -> fl.flowId().equals(r.sourceFlowId()));
                    boolean containsTarget = f.flows().stream().anyMatch(fl -> fl.flowId().equals(r.targetFlowId()));
                    if (containsSource || containsTarget) return f.featureId();
                }
                return "unknown";
            }));

        return features.stream()
            .map(f -> new FunctionalFeature(
                f.featureId(), f.name(), f.description(), f.flows(),
                byFeature.getOrDefault(f.featureId(), List.of())
            ))
            .collect(Collectors.toList());
    }
}
