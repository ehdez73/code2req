package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.embabel.agent.api.common.OperationContext;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.AnalyzedFlowResult;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.GroupedFlowsResult;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStep;
import com.github.ehdez73.code2req.extraction.domain.model.FlowStepComponentType;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Clusters related FunctionalFlows into FunctionalFeatures using semantic
 * similarity from Phase 2 enrichment (keyword overlap). Assigns feature
 * names and descriptions. Falls back to package/directory proximity when
 * enrichment data is unavailable.
 */
public class GroupFlowsAction {

    private static final Logger log = LoggerFactory.getLogger(GroupFlowsAction.class);

    public GroupedFlowsResult group(AnalyzedFlowResult analyzedResult, OperationContext context) {
        List<FunctionalFlow> flows = analyzedResult.flows();

        if (flows.isEmpty()) {
            return new GroupedFlowsResult(List.of());
        }

        if (flows.size() == 1) {
            FunctionalFlow flow = flows.get(0);
            String description = generateFeatureDescription(flow, context);
            FunctionalFeature feature = new FunctionalFeature(
                "feature-1",
                deriveFeatureName(flow),
                description,
                flows,
                List.of()
            );
            return new GroupedFlowsResult(List.of(feature));
        }

        String flowSummaries = flows.stream()
            .map(f -> "Flow " + f.flowId() + ": " + f.name() + " - " + truncate(f.userStory(), 200)
                + " | Entry: " + f.entryPoint().type()
                + " | Steps: " + f.steps().size()
                + " | External: " + f.steps().stream().filter(s -> s.componentType() == FlowStepComponentType.EXTERNAL_CALL).count()
                + " | DB: " + f.steps().stream().filter(s -> s.componentType() == FlowStepComponentType.DATABASE).count())
            .reduce((a, b) -> a + "\n" + b)
            .orElse("");

        String prompt = """
            Group these execution flows into logical features based on semantic similarity.
            Each group should represent a cohesive business capability.

            Flows:
            %s

            Return a JSON object with:
            {
              "groups": [
                {
                  "featureName": "...",
                  "featureDescription": "...",
                  "flowIds": ["flow-id-1", "flow-id-2"]
                }
              ]
            }

            Group flows that:
            - Operate on the same domain entity (e.g., Orders, Users)
            - Share similar business purpose
            - Are part of the same CRUD lifecycle
            - Use the same external dependencies or databases

            For each feature's description, write 2-3 sentences capturing the business
            capability, what triggers it, and any external dependencies involved.
            """.formatted(flowSummaries);

        GroupingResponse response = context.ai()
            .withDefaultLlm()
            .createObject(prompt, GroupingResponse.class);

        List<FunctionalFeature> features = new ArrayList<>();

        if (response.groups() != null) {
            int featureIndex = 1;
            for (GroupDto group : response.groups()) {
                List<FunctionalFlow> matchedFlows = new ArrayList<>();
                if (group.flowIds() != null) {
                    for (String flowId : group.flowIds()) {
                        flows.stream()
                            .filter(f -> f.flowId().equals(flowId))
                            .findFirst()
                            .ifPresent(matchedFlows::add);
                    }
                }

                if (matchedFlows.isEmpty()) {
                    matchedFlows.addAll(flows);
                    flows.clear();
                } else {
                    flows.removeAll(matchedFlows);
                }

                features.add(new FunctionalFeature(
                    "feature-" + featureIndex++,
                    group.featureName(),
                    group.featureDescription(),
                    matchedFlows,
                    List.of()
                ));
            }
        }

        if (!flows.isEmpty()) {
            int featureIndex = features.size() + 1;
            features.add(new FunctionalFeature(
                "feature-" + featureIndex,
                "Ungrouped Flows",
                "Flows that could not be automatically grouped",
                flows,
                List.of()
            ));
        }

        log.info("Grouped {} flows into {} features", analyzedResult.flows().size(), features.size());
        return new GroupedFlowsResult(features);
    }

    private String generateFeatureDescription(FunctionalFlow flow, OperationContext context) {
        String stepsSummary = flow.steps().stream()
            .map(s -> "  - " + s.componentType() + ": " + s.className()
                + (s.methodName() != null ? "." + s.methodName() : "")
                + (!s.enrichments().isEmpty() ? " (" + String.join(", ", s.enrichments()) + ")" : ""))
            .collect(Collectors.joining("\n"));

        long externalCount = flow.steps().stream()
            .filter(s -> s.componentType() == FlowStepComponentType.EXTERNAL_CALL).count();
        long dbCount = flow.steps().stream()
            .filter(s -> s.componentType() == FlowStepComponentType.DATABASE).count();

        String prompt = """
            Write a concise, business-oriented feature description (2-3 sentences) for a software feature.
            Include what it does, how it is triggered, and mention any external dependencies (external APIs, databases, event brokers).

            Flow name: %s
            Entry point: %s %s
            Steps:
            %s
            External calls: %d
            Database ops: %d
            User story: %s

            Return only the description text, no JSON.
            """.formatted(
            flow.name(),
            flow.entryPoint().httpMethod() != null ? flow.entryPoint().httpMethod() : flow.entryPoint().type(),
            flow.entryPoint().path() != null ? flow.entryPoint().path() : flow.entryPoint().className(),
            stepsSummary,
            externalCount,
            dbCount,
            flow.userStory() != null ? flow.userStory() : "N/A"
        );

        DescriptionResponse response = context.ai()
            .withDefaultLlm()
            .createObject(prompt, DescriptionResponse.class);

        if (response != null && response.description() != null && !response.description().isBlank()) {
            return response.description();
        }

        return flow.userStory() != null ? flow.userStory() : "Feature for " + flow.name();
    }

    private String deriveFeatureName(FunctionalFlow flow) {
        if (flow.entryPoint().path() != null) {
            String path = flow.entryPoint().path();
            String[] segments = path.split("/");
            for (int i = segments.length - 1; i >= 0; i--) {
                if (!segments[i].isEmpty() && !segments[i].startsWith("{")) {
                    return capitalize(segments[i]);
                }
            }
        }
        return flow.entryPoint().className();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    record DescriptionResponse(String description) {}

    record GroupingResponse(List<GroupDto> groups) {}

    record GroupDto(
        String featureName,
        String featureDescription,
        List<String> flowIds
    ) {}
}
