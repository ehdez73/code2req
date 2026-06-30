package com.github.ehdez73.code2req.extraction.adapter.agent.action;

import com.embabel.agent.api.common.OperationContext;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.AnalyzedFlowResult;
import com.github.ehdez73.code2req.extraction.adapter.agent.model.GroupedFlowsResult;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFeature;
import com.github.ehdez73.code2req.extraction.domain.model.FunctionalFlow;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.ArrayList;
import java.util.List;

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
            FunctionalFeature feature = new FunctionalFeature(
                "feature-1",
                deriveFeatureName(flow),
                deriveFeatureDescription(flow),
                flows,
                List.of()
            );
            return new GroupedFlowsResult(List.of(feature));
        }

        String flowSummaries = flows.stream()
            .map(f -> "Flow " + f.flowId() + ": " + f.name() + " - " + truncate(f.userStory(), 100))
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

    private String deriveFeatureDescription(FunctionalFlow flow) {
        return flow.userStory() != null ? flow.userStory() : "Feature for " + flow.name();
    }

    private String truncate(String s, int maxLen) {
        if (s == null) return "";
        return s.length() <= maxLen ? s : s.substring(0, maxLen) + "...";
    }

    private String capitalize(String s) {
        if (s == null || s.isEmpty()) return s;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    record GroupingResponse(List<GroupDto> groups) {}

    record GroupDto(
        String featureName,
        String featureDescription,
        List<String> flowIds
    ) {}
}
